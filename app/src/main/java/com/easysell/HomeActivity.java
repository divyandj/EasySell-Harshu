package com.easysell;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.easysell.databinding.ActivityHomeBinding;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.messaging.FirebaseMessaging; // REQUIRED

import java.util.ArrayList;
import java.util.List;

public class HomeActivity extends AppCompatActivity implements CatalogueAdapter.OnCatalogueClickListener {

    private static final String TAG = "HomeActivity";
    private ActivityHomeBinding binding;
    private GoogleSignInClient googleSignInClient;
    private FirebaseFirestore db;
    private CatalogueAdapter adapter;
    private List<Catalogue> catalogueList;

    // Listeners for Real-time updates
    private ListenerRegistration catalogueListener;
    private ListenerRegistration orderCountListener;
    private ListenerRegistration requestCountListener;

    private GoogleSignInAccount currentAccount; // Store the account info

    // --- 1. PERMISSION LAUNCHER ---
    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    Log.d(TAG, "Notification permission granted");
                    // Fix: Initialize subscriptions immediately after permission
                    initNotifications();
                } else {
                    Toast.makeText(this, "Notifications disabled. You won't receive order alerts.", Toast.LENGTH_LONG).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityHomeBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Setup Toolbar
        setSupportActionBar(binding.toolbar);

        // Initialize Firebase
        db = FirebaseFirestore.getInstance();

        // Initialize GoogleSignInClient for sign out
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).build();
        googleSignInClient = GoogleSignIn.getClient(this, gso);

        // --- 2. SETUP NOTIFICATIONS ---
        askNotificationPermission();

        // --- CLICK LISTENERS ---
        setupClickListeners();

        setupRecyclerView();
    }

    // --- 3. NOTIFICATION LOGIC (CRITICAL FIX) ---
    private void askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED) {
                // Permission already granted, subscribe to topics
                initNotifications();
            } else if (shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            } else {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
        } else {
            // For older Android versions, permission is granted at install time
            initNotifications();
        }
    }

    /**
     * Subscribes the device to the backend topics ("admin_orders" and "admin_new_users").
     * Without this, the backend sends messages into the void.
     */
    private void initNotifications() {
        // 1. Subscribe to Orders (Matches your backend: topic: "admin_orders")
        FirebaseMessaging.getInstance().subscribeToTopic("admin_orders")
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Log.d(TAG, "✅ Subscribed to admin_orders");
                    } else {
                        Log.e(TAG, "❌ Failed to subscribe to admin_orders", task.getException());
                    }
                });

        // 2. Subscribe to New Users (Matches your backend: topic: "admin_new_users")
        FirebaseMessaging.getInstance().subscribeToTopic("admin_new_users")
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Log.d(TAG, "✅ Subscribed to admin_new_users");
                    }
                });

        // 3. Log Token for Debugging
        FirebaseMessaging.getInstance().getToken()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Log.d("FCM_TOKEN", "Token: " + task.getResult());
                    }
                });
    }

    private void setupClickListeners() {
        binding.fabAddCategory.setOnClickListener(view -> {
            Intent intent = new Intent(HomeActivity.this, AddCatalogueActivity.class);
            startActivity(intent);
        });

        binding.ordersCard.setOnClickListener(view -> {
            Intent intent = new Intent(HomeActivity.this, OrdersActivity.class);
            startActivity(intent);
        });

        binding.requestsCard.setOnClickListener(view -> {
            Intent intent = new Intent(HomeActivity.this, UserRequestsActivity.class);
            startActivity(intent);
        });

        binding.analyticsCard.setOnClickListener(view -> {
            Toast.makeText(this, "Analytics feature coming soon!", Toast.LENGTH_SHORT).show();
        });

        binding.profileIcon.setOnClickListener(view -> {
            Intent intent = new Intent(HomeActivity.this, ProfileActivity.class);
            startActivity(intent);
        });

        binding.viewAllText.setOnClickListener(v -> {
            binding.categoriesRecyclerView.smoothScrollToPosition(0);
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Check for signed-in user EVERY time the activity starts to ensure security
        currentAccount = GoogleSignIn.getLastSignedInAccount(this);
        if (currentAccount == null) {
            Log.w(TAG, "User not signed in onStart, redirecting to login.");
            signOutAndGoToLogin();
        } else {
            Log.d(TAG, "User " + currentAccount.getEmail() + " signed in. Loading data.");
            // Attach all listeners to fetch live data
            attachCatalogueListener();
            attachOrderCountListener();
            attachRequestCountListener();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Detach all listeners when the activity is no longer visible to save battery/data
        detachCatalogueListener();
        detachOrderCountListener();
        detachRequestCountListener();
    }

    private void setupRecyclerView() {
        catalogueList = new ArrayList<>();
        adapter = new CatalogueAdapter(catalogueList, this);
        binding.categoriesRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        binding.categoriesRecyclerView.setAdapter(adapter);
    }

    // --- LISTENER 1: CATALOGUES ---
    private void attachCatalogueListener() {
        if (currentAccount != null && currentAccount.getId() != null && catalogueListener == null) {
            String userId = currentAccount.getId();
            Log.d(TAG, "Attaching catalogue listener for userId: " + userId);

            Query query = db.collection("catalogues")
                    .whereEqualTo("userId", userId)
                    .orderBy("createdAt", Query.Direction.DESCENDING);

            catalogueListener = query.addSnapshotListener((snapshots, e) -> {
                if (e != null) {
                    Log.w(TAG, "Catalogue listen failed.", e);
                    return;
                }

                catalogueList.clear();
                if (snapshots != null) {
                    catalogueList.addAll(snapshots.toObjects(Catalogue.class));
                    binding.totalCategoriesText.setText(String.valueOf(snapshots.size()));
                }
                adapter.notifyDataSetChanged();

                if (catalogueList.isEmpty()) {
                    binding.emptyViewContainer.setVisibility(View.VISIBLE);
                    binding.categoriesRecyclerView.setVisibility(View.GONE);
                } else {
                    binding.emptyViewContainer.setVisibility(View.GONE);
                    binding.categoriesRecyclerView.setVisibility(View.VISIBLE);
                }
            });
        }
    }

    // --- LISTENER 2: ORDERS ---
    private void attachOrderCountListener() {
        if (currentAccount != null && currentAccount.getId() != null && orderCountListener == null) {
            String userId = currentAccount.getId(); // This is the SELLER's ID
            orderCountListener = db.collectionGroup("orders")
                    .whereEqualTo("sellerId", userId)
                    .addSnapshotListener((snapshots, e) -> {
                        if (e != null) {
                            Log.e(TAG, "Order count query failed.", e);
                            if (e.getCode() == FirebaseFirestoreException.Code.FAILED_PRECONDITION) {
                                binding.totalOrdersText.setText("!");
                            } else {
                                binding.totalOrdersText.setText("0");
                            }
                            return;
                        }

                        int orderCount = 0;
                        if (snapshots != null) {
                            orderCount = snapshots.size();
                        }
                        binding.totalOrdersText.setText(String.valueOf(orderCount));
                    });
        }
    }

    // --- LISTENER 3: PENDING REQUESTS ---
    private void attachRequestCountListener() {
        if (requestCountListener == null) {
            requestCountListener = db.collection("users")
                    .whereEqualTo("status", "pending")
                    .addSnapshotListener((snapshots, e) -> {
                        if (e != null) {
                            Log.w(TAG, "Request count listen failed.", e);
                            binding.totalRequestsText.setText("-");
                            return;
                        }

                        int requestCount = 0;
                        if (snapshots != null) {
                            requestCount = snapshots.size();
                        }
                        binding.totalRequestsText.setText(String.valueOf(requestCount));
                    });
        }
    }

    // --- DETACH METHODS ---
    private void detachCatalogueListener() {
        if (catalogueListener != null) {
            catalogueListener.remove();
            catalogueListener = null;
        }
    }

    private void detachOrderCountListener() {
        if (orderCountListener != null) {
            orderCountListener.remove();
            orderCountListener = null;
        }
    }

    private void detachRequestCountListener() {
        if (requestCountListener != null) {
            requestCountListener.remove();
            requestCountListener = null;
        }
    }

    // --- NAVIGATION ---
    @Override
    public void onCatalogueClick(Catalogue catalogue) {
        Intent intent = new Intent(this, CatalogueDetailActivity.class);
        intent.putExtra("CATALOGUE_ID", catalogue.getId());
        intent.putExtra("CATALOGUE_NAME", catalogue.getName());
        startActivity(intent);
    }

    // --- MENU & SIGN OUT ---
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_sign_out) {
            signOutAndGoToLogin();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void signOutAndGoToLogin() {
        // Detach all listeners before signing out to prevent crashes or leaks
        detachCatalogueListener();
        detachOrderCountListener();
        detachRequestCountListener();

        googleSignInClient.signOut().addOnCompleteListener(this, task -> {
            SessionManager.getInstance().clear(); // Clear local session data
            Toast.makeText(this, "Signed out", Toast.LENGTH_SHORT).show();

            Intent intent = new Intent(HomeActivity.this, SignInActivity.class);
            // Clear back stack so user can't go back to Home
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });
    }
}