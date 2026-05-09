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
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.Transaction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

public class RewardClaimRequestsActivity extends AppCompatActivity implements RewardClaimRequestAdapter.OnRequestActionListener {

    private static final String TAG = "RewardClaimRequests";
    private static final String STATUS_PENDING = "pending";
    private static final String STATUS_APPROVED = "approved";
    private static final String STATUS_FULFILLED = "fulfilled";
    private static final String STATUS_REJECTED = "rejected";
    private static final String STATUS_ALL = "all";

    private ActivityRewardClaimRequestsBinding binding;
    private FirebaseFirestore db;
    private RewardClaimRequestAdapter adapter;
    private List<RewardClaimRequest> requestList;
    private String currentFilterStatus = STATUS_PENDING;
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
                    currentFilterStatus = STATUS_PENDING;
                } else if (position == 1) {
                    currentFilterStatus = STATUS_APPROVED;
                } else if (position == 2) {
                    currentFilterStatus = STATUS_FULFILLED;
                } else if (position == 3) {
                    currentFilterStatus = STATUS_REJECTED;
                } else {
                    currentFilterStatus = STATUS_ALL;
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
                            String requestStatus = normalizeStatus(request.getStatus());
                            request.setStatus(requestStatus);
                            if (STATUS_ALL.equals(status) || status.equals(requestStatus)) {
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
                        adapter.updateList(requestList);
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
        if (STATUS_PENDING.equals(status)) {
            binding.emptyViewText.setText("No pending claims");
        } else if (STATUS_APPROVED.equals(status)) {
            binding.emptyViewText.setText("No approved claims");
        } else if (STATUS_FULFILLED.equals(status)) {
            binding.emptyViewText.setText("No fulfilled claims");
        } else if (STATUS_REJECTED.equals(status)) {
            binding.emptyViewText.setText("No rejected claims");
        } else {
            binding.emptyViewText.setText("No claim requests");
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
    public void onChangeStatus(RewardClaimRequest request, String newStatus) {
        if (request.getDocId() == null) return;
        String normalizedTarget = normalizeStatus(newStatus);
        String currentStatus = normalizeStatus(request.getStatus());
        if (normalizedTarget.equals(currentStatus)) {
            Toast.makeText(this, "Claim is already " + normalizedTarget + ".", Toast.LENGTH_SHORT).show();
            return;
        }

        transitionStatus(request, normalizedTarget);
    }

    private void transitionStatus(RewardClaimRequest request, String newStatus) {
        if (request.getDocId() == null) return;

        DocumentReference claimRef = db.collection("reward_claim_requests").document(request.getDocId());

        db.runTransaction((Transaction.Function<Void>) transaction -> {
            DocumentSnapshot claimSnap = transaction.get(claimRef);
            if (!claimSnap.exists()) {
                throw new RuntimeException("Claim request not found.");
            }

            String claimStoreHandle = claimSnap.getString("storeHandle");
            if (claimStoreHandle == null || !claimStoreHandle.equals(currentStoreHandle)) {
                throw new RuntimeException("Cannot update claim from another store.");
            }

            Boolean deductedAtRequest = claimSnap.getBoolean("pointsDeductedAtRequest");
            String currentStatus = normalizeStatus(claimSnap.getString("status"));
            if (currentStatus.equals(newStatus)) {
                throw new RuntimeException("Claim is already " + newStatus + ".");
            }
            String buyerUid = claimSnap.getString("buyerUid");
            Number pointsCostNum = (Number) claimSnap.get("pointsCost");
            long pointsCost = pointsCostNum != null ? pointsCostNum.longValue() : 0;

            Boolean refundedOnReject = claimSnap.getBoolean("pointsRefundedOnReject");
            boolean isRefundedOnReject = Boolean.TRUE.equals(refundedOnReject);
            boolean enteringRejected = !STATUS_REJECTED.equals(currentStatus) && STATUS_REJECTED.equals(newStatus);
            boolean leavingRejected = STATUS_REJECTED.equals(currentStatus) && !STATUS_REJECTED.equals(newStatus);

            if (Boolean.TRUE.equals(deductedAtRequest)
                    && buyerUid != null
                    && !buyerUid.isEmpty()
                    && pointsCost > 0) {
                DocumentReference pointsRef = db.collection("buyer_points").document(buyerUid + "__" + currentStoreHandle);

                if (enteringRejected && !isRefundedOnReject) {
                    DocumentSnapshot pointsSnap = transaction.get(pointsRef);
                    long currentPoints = 0;
                    long totalRedeemed = 0;
                    if (pointsSnap.exists()) {
                        Long pointsVal = pointsSnap.getLong("points");
                        Long redeemedVal = pointsSnap.getLong("totalRedeemed");
                        currentPoints = pointsVal != null ? pointsVal : 0;
                        totalRedeemed = redeemedVal != null ? redeemedVal : 0;
                    }

                    HashMap<String, Object> pointsUpdate = new HashMap<>();
                    pointsUpdate.put("points", currentPoints + pointsCost);
                    pointsUpdate.put("totalRedeemed", Math.max(0, totalRedeemed - pointsCost));
                    pointsUpdate.put("lastUpdatedAt", FieldValue.serverTimestamp());
                    transaction.set(pointsRef, pointsUpdate, SetOptions.merge());
                }

                if (leavingRejected && isRefundedOnReject) {
                    DocumentSnapshot pointsSnap = transaction.get(pointsRef);
                    long currentPoints = 0;
                    long totalRedeemed = 0;
                    if (pointsSnap.exists()) {
                        Long pointsVal = pointsSnap.getLong("points");
                        Long redeemedVal = pointsSnap.getLong("totalRedeemed");
                        currentPoints = pointsVal != null ? pointsVal : 0;
                        totalRedeemed = redeemedVal != null ? redeemedVal : 0;
                    }

                    if (currentPoints < pointsCost) {
                        throw new RuntimeException("Buyer has insufficient points to revert from rejected.");
                    }

                    HashMap<String, Object> pointsUpdate = new HashMap<>();
                    pointsUpdate.put("points", currentPoints - pointsCost);
                    pointsUpdate.put("totalRedeemed", totalRedeemed + pointsCost);
                    pointsUpdate.put("lastUpdatedAt", FieldValue.serverTimestamp());
                    transaction.set(pointsRef, pointsUpdate, SetOptions.merge());
                }
            }

            HashMap<String, Object> claimUpdates = new HashMap<>();
            claimUpdates.put("status", newStatus);
            claimUpdates.put("updatedAt", FieldValue.serverTimestamp());
            claimUpdates.put("updatedBy", currentSellerUid);

            if (STATUS_APPROVED.equals(newStatus)) {
                claimUpdates.put("approvedAt", FieldValue.serverTimestamp());
                claimUpdates.put("approvedBy", currentSellerUid);
            }
            if (STATUS_FULFILLED.equals(newStatus)) {
                claimUpdates.put("fulfilledAt", FieldValue.serverTimestamp());
                claimUpdates.put("fulfilledBy", currentSellerUid);
            }
            if (enteringRejected && Boolean.TRUE.equals(deductedAtRequest) && pointsCost > 0 && !isRefundedOnReject) {
                claimUpdates.put("pointsRefundedOnReject", true);
                claimUpdates.put("pointsRefundedAt", FieldValue.serverTimestamp());
            }
            if (leavingRejected && Boolean.TRUE.equals(deductedAtRequest) && pointsCost > 0 && isRefundedOnReject) {
                claimUpdates.put("pointsRefundedOnReject", false);
                claimUpdates.put("pointsRefundReversedAt", FieldValue.serverTimestamp());
            }

            transaction.update(claimRef, claimUpdates);
            return null;
        }).addOnSuccessListener(unused -> {
            Toast.makeText(this, "Claim moved to " + newStatus + ".", Toast.LENGTH_SHORT).show();
            loadRequests(currentFilterStatus);
        }).addOnFailureListener(e -> {
            Toast.makeText(this, "Update failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        });
    }

    private String normalizeStatus(String status) {
        if (status == null || status.trim().isEmpty()) return STATUS_PENDING;
        return status.trim().toLowerCase(Locale.ROOT);
    }
}
