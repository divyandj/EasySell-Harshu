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
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.List;

public class UserRequestsActivity extends AppCompatActivity implements UserRequestAdapter.OnRequestActionListener {

    private static final String TAG = "UserRequestsActivity";
    private ActivityUserRequestsBinding binding;
    private FirebaseFirestore db;
    private UserRequestAdapter adapter;
    private List<UserRequest> requestList;
    private String currentFilterStatus = "pending"; // Default tab

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

        // Load default (Pending)
        loadRequests("pending");

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
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    private void loadRequests(String status) {
        binding.progressBar.setVisibility(View.VISIBLE);
        binding.emptyView.setVisibility(View.GONE);
        binding.recyclerRequests.setVisibility(View.GONE);

        db.collection("users")
                .whereEqualTo("status", status)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    binding.progressBar.setVisibility(View.GONE);
                    requestList.clear();

                    if (!queryDocumentSnapshots.isEmpty()) {
                        for (DocumentSnapshot doc : queryDocumentSnapshots) {
                            UserRequest req = doc.toObject(UserRequest.class);
                            if (req != null) {
                                if (req.getUid() == null) req.setUid(doc.getId());
                                requestList.add(req);
                            }
                        }
                        adapter.updateList(requestList, status); // Update adapter with new list and status
                        binding.recyclerRequests.setVisibility(View.VISIBLE);
                    } else {
                        binding.emptyView.setVisibility(View.VISIBLE);
                        // Update text based on context
                        if ("pending".equals(status)) binding.emptyViewText.setText("No pending requests");
                        else if ("approved".equals(status)) binding.emptyViewText.setText("No approved users");
                        else binding.emptyViewText.setText("No rejected users");
                    }
                })
                .addOnFailureListener(e -> {
                    binding.progressBar.setVisibility(View.GONE);
                    Log.e(TAG, "Error loading requests", e);
                    Toast.makeText(this, "Failed to load data.", Toast.LENGTH_SHORT).show();

                    if (e.getMessage() != null && e.getMessage().contains("index")) {
                        Log.e(TAG, "INDEX REQUIRED: Check Logcat for URL to create index.");
                    }
                });
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
        if (request.getUid() == null) return;

        // Visual feedback immediately
        Toast.makeText(this, "Updating...", Toast.LENGTH_SHORT).show();

        db.collection("users").document(request.getUid())
                .update("status", newStatus)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "User " + newStatus, Toast.LENGTH_SHORT).show();
                    // Reload current list to refresh UI
                    loadRequests(currentFilterStatus);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Action failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }
}