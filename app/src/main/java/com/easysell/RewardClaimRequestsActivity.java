package com.easysell;

import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.easysell.databinding.ActivityRewardClaimRequestsBinding;
import com.google.android.material.tabs.TabLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Transaction;

import java.util.ArrayList;
import java.util.List;

public class RewardClaimRequestsActivity extends AppCompatActivity implements RewardClaimRequestAdapter.OnRequestActionListener {

    private static final String TAG = "RewardClaimRequests";

    private ActivityRewardClaimRequestsBinding binding;
    private FirebaseFirestore db;
    private RewardClaimRequestAdapter adapter;
    private List<RewardClaimRequest> requestList;
    private String currentFilterStatus = "pending";
    private String currentStoreHandle = "";
    private String currentSellerUid = "";
    private int queryGen = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityRewardClaimRequestsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        db = FirebaseFirestore.getInstance();
        requestList = new ArrayList<>();
        adapter = new RewardClaimRequestAdapter(requestList, this);

        binding.recyclerRequests.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerRequests.setAdapter(adapter);

        fetchStoreHandleAndLoad();

        binding.tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                int position = tab.getPosition();
                if (position == 0) {
                    currentFilterStatus = "pending";
                } else if (position == 1) {
                    currentFilterStatus = "approved";
                } else if (position == 2) {
                    currentFilterStatus = "fulfilled";
                } else {
                    currentFilterStatus = "rejected";
                }
                loadRequests(currentFilterStatus);
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });
    }

    private void fetchStoreHandleAndLoad() {
        binding.progressBar.setVisibility(View.VISIBLE);
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            finish();
            return;
        }
        currentSellerUid = FirebaseAuth.getInstance().getCurrentUser().getUid();

        db.collection("users").document(currentSellerUid).get().addOnSuccessListener(doc -> {
            if (doc.exists() && doc.getString("storeHandle") != null) {
                currentStoreHandle = doc.getString("storeHandle");
                loadRequests(currentFilterStatus);
            } else {
                binding.progressBar.setVisibility(View.GONE);
                binding.emptyView.setVisibility(View.VISIBLE);
                binding.emptyViewText.setText("Store handle not configured.");
            }
        }).addOnFailureListener(e -> {
            binding.progressBar.setVisibility(View.GONE);
            Toast.makeText(this, "Failed to load store profile.", Toast.LENGTH_SHORT).show();
        });
    }

    private void loadRequests(String status) {
        if (currentStoreHandle == null || currentStoreHandle.isEmpty()) return;

        final int gen = ++queryGen;
        binding.progressBar.setVisibility(View.VISIBLE);
        binding.emptyView.setVisibility(View.GONE);
        binding.recyclerRequests.setVisibility(View.GONE);

        db.collection("reward_claim_requests")
                .whereEqualTo("storeHandle", currentStoreHandle)
                .get()
                .addOnSuccessListener(snapshots -> {
                    if (gen != queryGen) return;

                    binding.progressBar.setVisibility(View.GONE);
                    requestList.clear();

                    if (snapshots != null && !snapshots.isEmpty()) {
                        for (DocumentSnapshot doc : snapshots.getDocuments()) {
                            RewardClaimRequest request = doc.toObject(RewardClaimRequest.class);
                            if (request == null) continue;
                            request.setDocId(doc.getId());
                            if (status.equals(request.getStatus())) {
                                requestList.add(request);
                            }
                        }

                        requestList.sort((r1, r2) -> {
                            if (r1.getCreatedAt() == null && r2.getCreatedAt() == null) return 0;
                            if (r1.getCreatedAt() == null) return 1;
                            if (r2.getCreatedAt() == null) return -1;
                            return r2.getCreatedAt().compareTo(r1.getCreatedAt());
                        });
                    }

                    if (requestList.isEmpty()) {
                        showEmptyView(status);
                    } else {
                        adapter.updateList(requestList, status);
                        binding.recyclerRequests.setVisibility(View.VISIBLE);
                    }
                })
                .addOnFailureListener(e -> {
                    if (gen != queryGen) return;
                    binding.progressBar.setVisibility(View.GONE);
                    Log.e(TAG, "Failed to load claim requests", e);
                    Toast.makeText(this, "Failed to load claim requests.", Toast.LENGTH_SHORT).show();
                });
    }

    private void showEmptyView(String status) {
        binding.emptyView.setVisibility(View.VISIBLE);
        if ("pending".equals(status)) {
            binding.emptyViewText.setText("No pending claims");
        } else if ("approved".equals(status)) {
            binding.emptyViewText.setText("No approved claims");
        } else if ("fulfilled".equals(status)) {
            binding.emptyViewText.setText("No fulfilled claims");
        } else {
            binding.emptyViewText.setText("No rejected claims");
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onApprove(RewardClaimRequest request) {
        if (request.getDocId() == null) return;

        db.collection("reward_claim_requests").document(request.getDocId())
                .update(
                        "status", "approved",
                        "approvedAt", FieldValue.serverTimestamp(),
                        "approvedBy", currentSellerUid
                )
                .addOnSuccessListener(unused -> {
                    Toast.makeText(this, "Claim approved.", Toast.LENGTH_SHORT).show();
                    loadRequests(currentFilterStatus);
                }).addOnFailureListener(e -> {
                    Toast.makeText(this, "Approval failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    @Override
    public void onReject(RewardClaimRequest request) {
        updateStatus(request, "rejected");
    }

    @Override
    public void onFulfill(RewardClaimRequest request) {
        if (request.getDocId() == null) return;

        db.collection("reward_claim_requests").document(request.getDocId())
                .update(
                        "status", "fulfilled",
                        "fulfilledAt", FieldValue.serverTimestamp(),
                        "fulfilledBy", currentSellerUid
                )
                .addOnSuccessListener(unused -> {
                    Toast.makeText(this, "Claim marked fulfilled.", Toast.LENGTH_SHORT).show();
                    loadRequests(currentFilterStatus);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Fulfill failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void updateStatus(RewardClaimRequest request, String newStatus) {
        if (request.getDocId() == null) return;

        DocumentReference claimRef = db.collection("reward_claim_requests").document(request.getDocId());
        DocumentReference pointsRef = db.collection("buyer_points").document(request.getBuyerUid() + "__" + currentStoreHandle);

        db.runTransaction((Transaction.Function<Void>) transaction -> {
            DocumentSnapshot claimSnap = transaction.get(claimRef);
            if (!claimSnap.exists()) {
                throw new RuntimeException("Claim request not found.");
            }

            Boolean deductedAtRequest = claimSnap.getBoolean("pointsDeductedAtRequest");
            String status = claimSnap.getString("status");
            long pointsCost = request.getPointsCost() != null ? request.getPointsCost() : 0;

            if ("rejected".equals(newStatus)
                    && Boolean.TRUE.equals(deductedAtRequest)
                    && ("pending".equals(status) || "approved".equals(status))
                    && request.getBuyerUid() != null
                    && !request.getBuyerUid().isEmpty()) {

                DocumentSnapshot pointsSnap = transaction.get(pointsRef);
                long currentPoints = 0;
                long totalRedeemed = 0;
                if (pointsSnap.exists()) {
                    Long pointsVal = pointsSnap.getLong("points");
                    Long redeemedVal = pointsSnap.getLong("totalRedeemed");
                    currentPoints = pointsVal != null ? pointsVal : 0;
                    totalRedeemed = redeemedVal != null ? redeemedVal : 0;
                }

                long adjustedRedeemed = Math.max(0, totalRedeemed - pointsCost);
                java.util.HashMap<String, Object> pointsUpdate = new java.util.HashMap<>();
                pointsUpdate.put("points", currentPoints + pointsCost);
                pointsUpdate.put("totalRedeemed", adjustedRedeemed);
                pointsUpdate.put("lastUpdatedAt", FieldValue.serverTimestamp());
                transaction.set(pointsRef, pointsUpdate, com.google.firebase.firestore.SetOptions.merge());
            }

            transaction.update(claimRef,
                    "status", newStatus,
                    "updatedAt", FieldValue.serverTimestamp(),
                    "updatedBy", currentSellerUid);
            return null;
        }).addOnSuccessListener(unused -> {
            Toast.makeText(this, "Claim " + newStatus + ".", Toast.LENGTH_SHORT).show();
            loadRequests(currentFilterStatus);
        }).addOnFailureListener(e -> {
            Toast.makeText(this, "Update failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        });
    }
}
