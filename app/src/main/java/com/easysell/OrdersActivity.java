package com.easysell;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.easysell.databinding.ActivityOrdersBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.android.material.tabs.TabLayout;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.List;

public class OrdersActivity extends AppCompatActivity implements OrderAdapter.OnOrderClickListener {

    private static final String TAG = "OrdersActivity";
    private ActivityOrdersBinding binding;
    private FirebaseFirestore db;
    private OrderAdapter adapter;
    private List<Order> orderList;
    private ListenerRegistration ordersListener;
    private String currentSellerId;
    private String currentStatusFilter = "All"; // Default

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityOrdersBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Toolbar
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        db = FirebaseFirestore.getInstance();

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "User not signed in.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        currentSellerId = user.getUid();

        setupRecyclerView();
        setupTabs();
    }

    private void setupRecyclerView() {
        orderList = new ArrayList<>();
        adapter = new OrderAdapter(orderList, this);
        binding.ordersRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        binding.ordersRecyclerView.setAdapter(adapter);
    }

    private void setupTabs() {
        binding.tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                currentStatusFilter = tab.getText().toString();
                attachOrdersListener(); // Reload data with new filter
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        attachOrdersListener();
    }

    @Override
    protected void onStop() {
        super.onStop();
        detachOrdersListener();
    }

    private void attachOrdersListener() {
        if (currentSellerId == null)
            return;

        detachOrdersListener(); // Remove old listener before adding new one

        binding.progressBarOrders.setVisibility(View.VISIBLE);
        binding.emptyOrdersView.setVisibility(View.GONE);
        binding.ordersRecyclerView.setVisibility(View.GONE);

        Query query = db.collectionGroup("orders")
                .whereEqualTo("sellerId", currentSellerId);

        // Apply Filter if not "All"
        if (!currentStatusFilter.equals("All")) {
            query = query.whereEqualTo("status", currentStatusFilter);
        }

        // Add Sorting (Requires Composite Index for EACH status combination)
        // If query fails, check Logcat for index link!
        query = query.orderBy("orderDate", Query.Direction.DESCENDING);

        ordersListener = query.addSnapshotListener(new EventListener<QuerySnapshot>() {
            @Override
            public void onEvent(@Nullable QuerySnapshot snapshots,
                    @Nullable FirebaseFirestoreException e) {
                binding.progressBarOrders.setVisibility(View.GONE);

                if (e != null) {
                    Log.w(TAG, "Orders listen failed.", e);
                    // Handle Missing Index
                    if (e.getCode() == FirebaseFirestoreException.Code.FAILED_PRECONDITION) {
                        Log.e(TAG, "INDEX MISSING: Check Logcat for link.");
                        Toast.makeText(OrdersActivity.this, "Setup required: Check logs for Index link.",
                                Toast.LENGTH_LONG).show();
                    }
                    binding.emptyOrdersView.setVisibility(View.VISIBLE);
                    binding.emptyText.setText("Error loading orders");
                    return;
                }

                orderList.clear();
                if (snapshots != null && !snapshots.isEmpty()) {
                    for (Order order : snapshots.toObjects(Order.class)) {
                        // Manually set ID as it's not in the document fields usually
                        // Assuming you need document ID for clicks
                        int index = orderList.size();
                        // Note: snapshots.getDocuments().get(index).getId() is risky if list size
                        // mismatches loop
                        // Better:
                    }
                    // Safer loop:
                    for (int i = 0; i < snapshots.size(); i++) {
                        Order order = snapshots.getDocuments().get(i).toObject(Order.class);
                        if (order != null) {
                            order.setId(snapshots.getDocuments().get(i).getId());
                            orderList.add(order);
                        }
                    }

                    binding.ordersRecyclerView.setVisibility(View.VISIBLE);
                } else {
                    binding.emptyOrdersView.setVisibility(View.VISIBLE);
                    binding.emptyText.setText("No " + currentStatusFilter + " orders");
                }
                adapter.notifyDataSetChanged();
            }
        });
    }

    private void detachOrdersListener() {
        if (ordersListener != null) {
            ordersListener.remove();
            ordersListener = null;
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
    public void onOrderClick(Order order) {
        Intent intent = new Intent(this, OrderDetailActivity.class);
        intent.putExtra("ORDER_ID", order.getId());
        intent.putExtra("CATALOGUE_ID", order.getCatalogueId());
        startActivity(intent);
    }
}