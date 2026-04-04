package com.easysell;

import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.easysell.databinding.ActivityUserRequestsBinding;
import com.google.android.material.tabs.TabLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;

public class UserRequestsActivity extends AppCompatActivity implements UserRequestAdapter.OnRequestActionListener {

    private static final String TAG = "UserRequestsActivity";
    private ActivityUserRequestsBinding binding;
    private FirebaseFirestore db;
    private UserRequestAdapter adapter;
    private List<UserRequest> requestList;
    private String currentFilterStatus = "pending"; // Default tab
    private String currentStoreHandle = "";
    private int queryGen = 0; // prevents stale callbacks from overwriting

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityUserRequestsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Setup Toolbar
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        db = FirebaseFirestore.getInstance();
        requestList = new ArrayList<>();
        adapter = new UserRequestAdapter(requestList, this);

        binding.recyclerRequests.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerRequests.setAdapter(adapter);

        // Fetch seller's store handle before loading requests
        fetchStoreHandleAndLoad();

        // Handle Tab Selection
        binding.tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                int position = tab.getPosition();
                switch (position) {
                    case 0:
                        currentFilterStatus = "pending";
                        break;
                    case 1:
                        currentFilterStatus = "approved";
                        break;
                    case 2:
                        currentFilterStatus = "rejected";
                        break;
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
        String currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        db.collection("users").document(currentUserId).get().addOnSuccessListener(doc -> {
            if (doc.exists() && doc.getString("storeHandle") != null) {
                currentStoreHandle = doc.getString("storeHandle");
                loadRequests("pending");
            } else {
                binding.progressBar.setVisibility(View.GONE);
                binding.emptyView.setVisibility(View.VISIBLE);
                binding.emptyViewText.setText("Store Handle not configured for this account.");
            }
        }).addOnFailureListener(e -> {
            binding.progressBar.setVisibility(View.GONE);
            Toast.makeText(this, "Failed to load store profile.", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Error fetching store handle", e);
        });
    }

    private void loadRequests(String status) {
        if (currentStoreHandle == null || currentStoreHandle.isEmpty())
            return;

        final int gen = ++queryGen;

        binding.progressBar.setVisibility(View.VISIBLE);
        binding.emptyView.setVisibility(View.GONE);
        binding.recyclerRequests.setVisibility(View.GONE);

        db.collection("store_access_requests")
                .whereEqualTo("storeHandle", currentStoreHandle)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    // Skip if user already switched to another tab
                    if (gen != queryGen) return;

                    binding.progressBar.setVisibility(View.GONE);
                    requestList.clear();

                    if (!queryDocumentSnapshots.isEmpty()) {
                        for (DocumentSnapshot doc : queryDocumentSnapshots) {
                            UserRequest req = doc.toObject(UserRequest.class);
                            if (req != null) {
                                if (req.getUid() == null) {
                                    String docId = doc.getId();
                                    if (docId.contains("_"))
                                        req.setUid(docId.split("_")[0]);
                                }

                                // IN-MEMORY FILTERING
                                if (status.equals(req.getStatus())) {
                                    requestList.add(req);
                                }
                            }
                        }

                        // IN-MEMORY SORTING (DESCENDING by createdAt)
                        requestList.sort((r1, r2) -> {
                            if (r1.getCreatedAt() == null && r2.getCreatedAt() == null)
                                return 0;
                            if (r1.getCreatedAt() == null)
                                return 1;
                            if (r2.getCreatedAt() == null)
                                return -1;
                            return r2.getCreatedAt().compareTo(r1.getCreatedAt());
                        });

                        if (requestList.isEmpty()) {
                            showEmptyView(status);
                        } else {
                            adapter.updateList(requestList, status);
                            binding.recyclerRequests.setVisibility(View.VISIBLE);
                        }
                    } else {
                        showEmptyView(status);
                    }
                })
                .addOnFailureListener(e -> {
                    if (gen != queryGen) return;
                    binding.progressBar.setVisibility(View.GONE);
                    Log.e(TAG, "Error loading requests", e);
                    Toast.makeText(this, "Failed to load data.", Toast.LENGTH_SHORT).show();
                });
    }

    private void showEmptyView(String status) {
        binding.emptyView.setVisibility(View.VISIBLE);
        if ("pending".equals(status))
            binding.emptyViewText.setText("No pending requests");
        else if ("approved".equals(status))
            binding.emptyViewText.setText("No approved users");
        else
            binding.emptyViewText.setText("No rejected users");
        binding.recyclerRequests.setVisibility(View.GONE);
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
    public void onApprove(UserRequest request) {
        updateStatus(request, "approved");
    }

    @Override
    public void onReject(UserRequest request) {
        updateStatus(request, "rejected");
    }

    private void updateStatus(UserRequest request, String newStatus) {
        if (request.getUid() == null || currentStoreHandle.isEmpty())
            return;

        Toast.makeText(this, "Updating...", Toast.LENGTH_SHORT).show();

        String docId = request.getUid() + "_" + currentStoreHandle;

        db.collection("store_access_requests").document(docId)
                .update("status", newStatus)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "User " + newStatus, Toast.LENGTH_SHORT).show();
                    loadRequests(currentFilterStatus);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Action failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }
}