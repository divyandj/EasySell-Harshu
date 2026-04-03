package com.easysell;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.easysell.databinding.ActivityRewardsConfigBinding;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class RewardsConfigActivity extends AppCompatActivity {

    private ActivityRewardsConfigBinding binding;
    private FirebaseFirestore db;
    private String userId;

    private List<Map<String, Object>> rewardItems = new ArrayList<>();
    private RewardAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityRewardsConfigBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        db = FirebaseFirestore.getInstance();
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) { finish(); return; }
        userId = user.getUid();

        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnAddReward.setOnClickListener(v -> showAddRewardDialog());
        binding.btnSave.setOnClickListener(v -> saveConfig());

        setupRecyclerView();
        setupPreviewUpdater();
        loadConfig();
    }

    private void setupRecyclerView() {
        adapter = new RewardAdapter(rewardItems,
                (position) -> { // Delete
                    rewardItems.remove(position);
                    adapter.notifyItemRemoved(position);
                    updateEmptyState();
                },
                (position, active) -> { // Toggle
                    rewardItems.get(position).put("active", active);
                }
        );
        binding.rvRewards.setLayoutManager(new LinearLayoutManager(this));
        binding.rvRewards.setAdapter(adapter);
    }

    private void setupPreviewUpdater() {
        binding.etPointsPerRupee.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
            @Override
            public void afterTextChanged(Editable s) {
                updatePreview();
            }
        });
    }

    private void updatePreview() {
        try {
            double rate = Double.parseDouble(binding.etPointsPerRupee.getText().toString());
            int earned = (int)(1000 * rate);
            binding.tvPreview.setText("₹1000 spent → " + earned + " points earned");
        } catch (NumberFormatException e) {
            binding.tvPreview.setText("₹1000 spent → ? points earned");
        }
    }

    private void loadConfig() {
        db.collection("users").document(userId).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        Double ppr = doc.getDouble("rewardsPointsPerRupee");
                        Long minR = doc.getLong("rewardsMinRedeem");
                        Long wb = doc.getLong("rewardsWelcomeBonus");
                        Boolean checkout = doc.getBoolean("rewardsAllowCheckoutRedeem");

                        if (ppr != null) binding.etPointsPerRupee.setText(String.valueOf(ppr));
                        if (minR != null) binding.etMinRedeem.setText(String.valueOf(minR));
                        if (wb != null) binding.etWelcomeBonus.setText(String.valueOf(wb));
                        binding.switchCheckoutRedeem.setChecked(checkout == null || checkout);

                        updatePreview();
                    }
                });

        // Load reward items
        db.collection("users").document(userId).collection("reward_items")
                .get()
                .addOnSuccessListener(snap -> {
                    rewardItems.clear();
                    for (DocumentSnapshot d : snap.getDocuments()) {
                        Map<String, Object> item = new HashMap<>(d.getData());
                        item.put("id", d.getId());
                        rewardItems.add(item);
                    }
                    adapter.notifyDataSetChanged();
                    updateEmptyState();
                });
    }

    private void saveConfig() {
        String pprStr = binding.etPointsPerRupee.getText().toString().trim();
        String minStr = binding.etMinRedeem.getText().toString().trim();
        String wbStr = binding.etWelcomeBonus.getText().toString().trim();

        if (pprStr.isEmpty()) {
            Toast.makeText(this, "Points per rupee is required", Toast.LENGTH_SHORT).show();
            return;
        }

        Map<String, Object> config = new HashMap<>();
        config.put("rewardsPointsPerRupee", Double.parseDouble(pprStr));
        config.put("rewardsMinRedeem", minStr.isEmpty() ? 0 : Long.parseLong(minStr));
        config.put("rewardsWelcomeBonus", wbStr.isEmpty() ? 0 : Long.parseLong(wbStr));
        config.put("rewardsAllowCheckoutRedeem", binding.switchCheckoutRedeem.isChecked());

        binding.btnSave.setEnabled(false);
        binding.btnSave.setText("Saving...");

        db.collection("users").document(userId).update(config)
                .addOnSuccessListener(v -> {
                    // Save reward items
                    saveRewardItems();
                })
                .addOnFailureListener(e -> {
                    binding.btnSave.setEnabled(true);
                    binding.btnSave.setText("Save Settings");
                    Toast.makeText(this, "Failed to save: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void saveRewardItems() {
        // Delete all existing and re-write — simple approach for small collections
        db.collection("users").document(userId).collection("reward_items")
                .get()
                .addOnSuccessListener(snap -> {
                    // Delete existing
                    for (DocumentSnapshot d : snap.getDocuments()) {
                        d.getReference().delete();
                    }

                    // Write current items
                    for (Map<String, Object> item : rewardItems) {
                        String id = (String) item.get("id");
                        if (id == null) id = UUID.randomUUID().toString();

                        Map<String, Object> data = new HashMap<>(item);
                        data.remove("id");

                        db.collection("users").document(userId)
                                .collection("reward_items").document(id)
                                .set(data);
                    }

                    binding.btnSave.setEnabled(true);
                    binding.btnSave.setText("Save Settings");
                    Toast.makeText(this, "Rewards saved! ✅", Toast.LENGTH_SHORT).show();
                    finish();
                });
    }

    private void showAddRewardDialog() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_add_reward, null);
        dialog.setContentView(view);

        TextInputEditText etTitle = view.findViewById(R.id.et_reward_title);
        TextInputEditText etDesc = view.findViewById(R.id.et_reward_description);
        TextInputEditText etPoints = view.findViewById(R.id.et_reward_points);
        TextInputEditText etValue = view.findViewById(R.id.et_reward_value);
        TextInputLayout tilValue = view.findViewById(R.id.til_reward_value);
        ChipGroup chipGroup = view.findViewById(R.id.chip_group_type);

        final String[] selectedType = {"custom"};

        chipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int id = checkedIds.get(0);
            if (id == R.id.chip_custom) {
                selectedType[0] = "custom";
                tilValue.setVisibility(View.GONE);
            } else if (id == R.id.chip_percent_off) {
                selectedType[0] = "percent_off";
                tilValue.setVisibility(View.VISIBLE);
                tilValue.setHint("Percentage off");
            } else if (id == R.id.chip_flat_off) {
                selectedType[0] = "flat_off";
                tilValue.setVisibility(View.VISIBLE);
                tilValue.setHint("Amount (₹)");
            } else if (id == R.id.chip_free_shipping) {
                selectedType[0] = "free_shipping";
                tilValue.setVisibility(View.GONE);
            }
        });

        view.findViewById(R.id.btn_add_reward).setOnClickListener(v -> {
            String title = etTitle.getText().toString().trim();
            String points = etPoints.getText().toString().trim();

            if (title.isEmpty()) {
                Toast.makeText(this, "Please enter a reward name", Toast.LENGTH_SHORT).show();
                return;
            }
            if (points.isEmpty()) {
                Toast.makeText(this, "Please enter points required", Toast.LENGTH_SHORT).show();
                return;
            }

            Map<String, Object> reward = new HashMap<>();
            reward.put("id", UUID.randomUUID().toString());
            reward.put("title", title);
            reward.put("description", etDesc.getText().toString().trim());
            reward.put("pointsCost", Long.parseLong(points));
            reward.put("type", selectedType[0]);
            reward.put("active", true);

            String valueStr = etValue.getText().toString().trim();
            if (!valueStr.isEmpty()) {
                reward.put("value", Double.parseDouble(valueStr));
            } else {
                reward.put("value", 0);
            }

            rewardItems.add(reward);
            adapter.notifyItemInserted(rewardItems.size() - 1);
            updateEmptyState();
            dialog.dismiss();
        });

        dialog.show();
    }

    private void updateEmptyState() {
        binding.emptyRewards.setVisibility(rewardItems.isEmpty() ? View.VISIBLE : View.GONE);
        binding.rvRewards.setVisibility(rewardItems.isEmpty() ? View.GONE : View.VISIBLE);
    }

    // --- Inner Adapter ---
    static class RewardAdapter extends RecyclerView.Adapter<RewardAdapter.VH> {
        private final List<Map<String, Object>> items;
        private final OnDeleteListener deleteListener;
        private final OnToggleListener toggleListener;

        interface OnDeleteListener { void onDelete(int position); }
        interface OnToggleListener { void onToggle(int position, boolean active); }

        RewardAdapter(List<Map<String, Object>> items, OnDeleteListener del, OnToggleListener tog) {
            this.items = items;
            this.deleteListener = del;
            this.toggleListener = tog;
        }

        @Override
        public VH onCreateViewHolder(android.view.ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_reward, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(VH h, int position) {
            Map<String, Object> item = items.get(position);
            h.title.setText((String) item.getOrDefault("title", "Untitled"));

            String type = (String) item.getOrDefault("type", "custom");
            String typeLabel;
            switch (type) {
                case "percent_off": typeLabel = "Percentage Off"; break;
                case "flat_off": typeLabel = "₹ Off"; break;
                case "free_shipping": typeLabel = "Free Shipping"; break;
                default: typeLabel = "Custom Reward"; break;
            }
            h.type.setText(typeLabel);

            Long pts = item.get("pointsCost") instanceof Long ? (Long) item.get("pointsCost") : 0L;
            h.points.setText(pts + " points");

            String desc = (String) item.getOrDefault("description", "");
            if (desc != null && !desc.isEmpty()) {
                h.description.setText(desc);
                h.description.setVisibility(View.VISIBLE);
            } else {
                h.description.setVisibility(View.GONE);
            }

            Boolean active = (Boolean) item.getOrDefault("active", true);
            h.toggle.setOnCheckedChangeListener(null);
            h.toggle.setChecked(active != null && active);
            h.toggle.setOnCheckedChangeListener((b, c) -> toggleListener.onToggle(position, c));

            h.delete.setOnClickListener(v -> deleteListener.onDelete(position));
        }

        @Override public int getItemCount() { return items.size(); }

        static class VH extends RecyclerView.ViewHolder {
            android.widget.TextView title, type, points, description;
            com.google.android.material.switchmaterial.SwitchMaterial toggle;
            android.widget.ImageView delete;

            VH(View v) {
                super(v);
                title = v.findViewById(R.id.tv_reward_title);
                type = v.findViewById(R.id.tv_reward_type);
                points = v.findViewById(R.id.tv_points_cost);
                description = v.findViewById(R.id.tv_reward_description);
                toggle = v.findViewById(R.id.switch_active);
                delete = v.findViewById(R.id.btn_delete);
            }
        }
    }
}
