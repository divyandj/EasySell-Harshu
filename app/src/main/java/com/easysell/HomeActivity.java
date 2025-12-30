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
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.easysell.databinding.ActivityHomeBinding;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;

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

    // --- 1. PERMISSION LAUNCHER (New for Android 13+) ---
    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    Log.d(TAG, "Notification permission granted");
                } else {
                    Toast.makeText(this, "Notifications disabled. You won't see new order alerts.", Toast.LENGTH_LONG).show();
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

        // --- 2. ASK FOR PERMISSION ON STARTUP ---
        askNotificationPermission();

        // --- CLICK LISTENERS ---

        // 1. Add Category FAB
        binding.fabAddCategory.setOnClickListener(view -> {
            Intent intent = new Intent(HomeActivity.this, AddCatalogueActivity.class);
            startActivity(intent);
        });

        // 2. Orders Card
        binding.ordersCard.setOnClickListener(view -> {
            Intent intent = new Intent(HomeActivity.this, OrdersActivity.class);
            startActivity(intent);
        });

        // 3. User Requests Card
        binding.requestsCard.setOnClickListener(view -> {
            Intent intent = new Intent(HomeActivity.this, UserRequestsActivity.class);
            startActivity(intent);
        });

        // 4. Analytics Card (Placeholder for future update)
        binding.analyticsCard.setOnClickListener(view -> {
            Toast.makeText(this, "Analytics feature coming soon!", Toast.LENGTH_SHORT).show();
        });

        // 5. Profile Icon (Top Left) - Navigates to Notification/Profile Settings
        binding.profileIcon.setOnClickListener(view -> {
            Intent intent = new Intent(HomeActivity.this, ProfileActivity.class);
            startActivity(intent);
        });

        // 6. View All Categories Text
        binding.viewAllText.setOnClickListener(v -> {
            // Optional: You could scroll to top or open a full list activity
            binding.categoriesRecyclerView.smoothScrollToPosition(0);
        });

        setupRecyclerView();
    }

    // --- 3. PERMISSION LOGIC ---
    private void askNotificationPermission() {
        // This is only necessary for API level >= 33 (Android 13)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED) {
                // Permission is already granted
            } else if (shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                // (Optional) Show UI explaining why you need notifications, then request
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            } else {
                // Directly ask for the permission
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
        }
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

            catalogueListener = query.addSnapshotListener(new EventListener<QuerySnapshot>() {
                @Override
                public void onEvent(@Nullable QuerySnapshot snapshots,
                                    @Nullable FirebaseFirestoreException e) {
                    if (e != null) {
                        Log.w(TAG, "Catalogue listen failed.", e);
                        return;
                    }

                    catalogueList.clear();
                    if (snapshots != null) {
                        Log.d(TAG, "Received " + snapshots.size() + " catalogues.");
                        catalogueList.addAll(snapshots.toObjects(Catalogue.class));
                        // Update the catalogue count text
                        binding.totalCategoriesText.setText(String.valueOf(snapshots.size()));
                    }
                    adapter.notifyDataSetChanged();

                    // Toggle empty state visibility
                    if (catalogueList.isEmpty()) {
                        binding.emptyViewContainer.setVisibility(View.VISIBLE);
                        binding.categoriesRecyclerView.setVisibility(View.GONE);
                    } else {
                        binding.emptyViewContainer.setVisibility(View.GONE);
                        binding.categoriesRecyclerView.setVisibility(View.VISIBLE);
                    }
                }
            });
        }
    }

    // --- LISTENER 2: ORDERS ---
    private void attachOrderCountListener() {
        if (currentAccount != null && currentAccount.getId() != null && orderCountListener == null) {
            String userId = currentAccount.getId(); // This is the SELLER's ID
            Log.d(TAG, "Attaching order count listener for sellerId: " + userId);

            // Collection Group Query to count all orders for this seller
            orderCountListener = db.collectionGroup("orders")
                    .whereEqualTo("sellerId", userId)
                    .addSnapshotListener(new EventListener<QuerySnapshot>() {
                        @Override
                        public void onEvent(@Nullable QuerySnapshot snapshots,
                                            @Nullable FirebaseFirestoreException e) {
                            if (e != null) {
                                Log.e(TAG, "Order count query failed.", e);
                                // Check if the error is due to missing index
                                if (e.getCode() == FirebaseFirestoreException.Code.FAILED_PRECONDITION) {
                                    Log.e(TAG, "MISSING INDEX! Please check Firebase Console.");
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
                        }
                    });
        }
    }

    // --- LISTENER 3: PENDING REQUESTS ---
    private void attachRequestCountListener() {
        if (requestCountListener == null) {
            Log.d(TAG, "Attaching request count listener.");

            // Query: Count all users where status is 'pending'
            requestCountListener = db.collection("users")
                    .whereEqualTo("status", "pending")
                    .addSnapshotListener(new EventListener<QuerySnapshot>() {
                        @Override
                        public void onEvent(@Nullable QuerySnapshot snapshots,
                                            @Nullable FirebaseFirestoreException e) {
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
                        }
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