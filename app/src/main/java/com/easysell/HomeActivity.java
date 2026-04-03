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
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.Query;
import com.google.firebase.messaging.FirebaseMessaging; // REQUIRED FOR NOTIFICATIONS
import com.bumptech.glide.Glide;
import java.util.ArrayList;
import java.util.List;

public class HomeActivity extends AppCompatActivity implements CatalogueAdapter.OnCatalogueClickListener {

    private static final String TAG = "HomeActivity";
    private ActivityHomeBinding binding;
    // Removed googleSignInClient
    private FirebaseFirestore db;
    private CatalogueAdapter adapter;
    private List<Catalogue> catalogueList;

    // Listeners for Real-time updates
    private ListenerRegistration catalogueListener;
    private ListenerRegistration orderCountListener;
    private ListenerRegistration requestCountListener;

    private FirebaseUser currentAccount; // Store the account info
    private String currentStoreHandle = "";

    // --- 1. PERMISSION LAUNCHER ---
    private final ActivityResultLauncher<String> requestPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    Log.d(TAG, "Notification permission granted");
                    // Fix: Initialize subscriptions immediately after permission
                    initNotifications();
                } else {
                    Toast.makeText(this, "Notifications disabled. You won't receive order alerts.", Toast.LENGTH_LONG)
                            .show();
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

        // Initialize Firebase Auth is handled via FirebaseAuth.getInstance() when
        // needed

        // --- 2. SETUP NOTIFICATIONS ---
        askNotificationPermission();

        // --- CLICK LISTENERS ---
        setupClickListeners();

        setupRecyclerView();

        // --- HANDLE NOTIFICATION CLICKS ---
        handleNotificationIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleNotificationIntent(intent);
    }

    private void handleNotificationIntent(Intent intent) {
        if (intent != null && intent.getExtras() != null) {
            String type = intent.getStringExtra("type");
            if (type == null)
                return;

            if ("order".equals(type)) {
                String orderId = intent.getStringExtra("orderId");
                String catalogueId = intent.getStringExtra("catalogueId");
                if (orderId != null) {
                    Intent orderIntent = new Intent(this, OrderDetailActivity.class);
                    orderIntent.putExtra("ORDER_ID", orderId);
                    if (catalogueId != null && !catalogueId.isEmpty()) {
                        orderIntent.putExtra("CATALOGUE_ID", catalogueId);
                    }
                    startActivity(orderIntent);
                }
            } else if ("user".equals(type)) {
                Intent requestIntent = new Intent(this, UserRequestsActivity.class);
                startActivity(requestIntent);
            }
        }
    }

    // --- 3. NOTIFICATION LOGIC (CRITICAL FIX) ---
    private void askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
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
     * Subscribes the device to the backend topics ("admin_orders" and
     * "admin_new_users").
     * Without this, the backend sends messages into the void.
     */
    private void initNotifications() {
        if (currentStoreHandle == null || currentStoreHandle.isEmpty()) {
            return; // Wait until store handle is loaded
        }

        String ordersTopic = "admin_orders_" + currentStoreHandle;
        String usersTopic = "admin_new_users_" + currentStoreHandle;

        // Subscribe to store-specific Orders topic
        FirebaseMessaging.getInstance().subscribeToTopic(ordersTopic)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Log.d(TAG, "✅ Subscribed to " + ordersTopic);
                    } else {
                        Log.e(TAG, "❌ Failed to subscribe to " + ordersTopic, task.getException());
                    }
                });

        // Subscribe to store-specific New Users topic
        FirebaseMessaging.getInstance().subscribeToTopic(usersTopic)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Log.d(TAG, "✅ Subscribed to " + usersTopic);
                    }
                });

        // Cleanup: Unsubscribe from the old global topics
        FirebaseMessaging.getInstance().unsubscribeFromTopic("admin_orders");
        FirebaseMessaging.getInstance().unsubscribeFromTopic("admin_new_users");

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
            Intent intent = new Intent(HomeActivity.this, AnalyticsActivity.class);
            startActivity(intent);
        });

        binding.profileIcon.setOnClickListener(view -> {
            Intent intent = new Intent(HomeActivity.this, ProfileActivity.class);
            startActivity(intent);
        });

        binding.logoutIcon.setOnClickListener(view -> showSignOutConfirmationDialog());
        binding.previewIcon.setOnClickListener(view -> openStorefrontPreview());

        binding.viewAllText.setOnClickListener(v -> {
            binding.categoriesRecyclerView.smoothScrollToPosition(0);
        });
    }

    private void updateProfileUI(FirebaseUser account) {
        if (account == null)
            return;

        // Fetch the latest data from Firestore (Matches ProfileActivity logic)
        db.collection("users").document(account.getUid()).get()
                .addOnSuccessListener(document -> {
                    if (document.exists()) {
                        // 1. Get Name (Try Business Name -> Owner Name -> Google Name)
                        String businessName = document.getString("businessName");
                        String ownerName = document.getString("ownerName");
                        String googleName = account.getDisplayName();

                        if (businessName != null && !businessName.isEmpty()) {
                            binding.profileName.setText(businessName);
                        } else if (ownerName != null && !ownerName.isEmpty()) {
                            binding.profileName.setText(ownerName);
                        } else {
                            binding.profileName.setText(googleName != null ? googleName : "Seller");
                        }

                        // 2. Get Profile Image (Try Firestore URL -> Google URL)
                        String firestoreUrl = document.getString("profileImageUrl");

                        if (firestoreUrl != null && !firestoreUrl.isEmpty()) {
                            loadProfileImage(firestoreUrl);
                        } else if (account.getPhotoUrl() != null) {
                            loadProfileImage(account.getPhotoUrl().toString());
                        }

                        // 3. Get Store Handle
                        String handle = document.getString("storeHandle");
                        if (handle != null && !handle.isEmpty()) {
                            currentStoreHandle = handle;
                        } else {
                            currentStoreHandle = "";
                        }

                        // Attach request listener and init notifications now that store handle is known
                        initNotifications();
                        if (requestCountListener == null) {
                            attachRequestCountListener();
                        }
                    } else {
                        // First time login or no profile set yet, use Google defaults
                        binding.profileName.setText(account.getDisplayName());
                        if (account.getPhotoUrl() != null) {
                            loadProfileImage(account.getPhotoUrl().toString());
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    // If fetch fails, fall back to Google defaults
                    Log.e(TAG, "Failed to fetch profile", e);
                    binding.profileName.setText(account.getDisplayName());
                });
    }

    // Helper to load image with clean styling
    private void loadProfileImage(String url) {
        // Remove tint and padding so the image looks like a proper photo
        binding.profileIcon.setImageTintList(null);
        binding.profileIcon.setPadding(0, 0, 0, 0);

        Glide.with(this)
                .load(url)
                .circleCrop()
                .placeholder(R.drawable.ic_person)
                .into(binding.profileIcon);
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Check for signed-in user EVERY time the activity starts to ensure security
        currentAccount = FirebaseAuth.getInstance().getCurrentUser();
        if (currentAccount == null) {
            Log.w(TAG, "User not signed in onStart, redirecting to login.");
            signOutAndGoToLogin();
        } else {
            Log.d(TAG, "User " + currentAccount.getEmail() + " signed in. Loading data.");

            updateProfileUI(currentAccount);
            // Attach all listeners to fetch live data
            attachCatalogueListener();
            attachOrderCountListener();
            attachRequestCountListener();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Detach all listeners when the activity is no longer visible to save
        // battery/data
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
        if (currentAccount != null && currentAccount.getUid() != null && catalogueListener == null) {
            String userId = currentAccount.getUid();
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
        if (currentAccount != null && currentAccount.getUid() != null && orderCountListener == null) {
            String userId = currentAccount.getUid(); // This is the SELLER's ID
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
        if (requestCountListener == null && currentStoreHandle != null && !currentStoreHandle.isEmpty()) {
            requestCountListener = db.collection("store_access_requests")
                    .whereEqualTo("storeHandle", currentStoreHandle)
                    .addSnapshotListener((snapshots, e) -> {
                        if (e != null) {
                            Log.w(TAG, "Request count listen failed.", e);
                            binding.totalRequestsText.setText("-");
                            return;
                        }

                        int pendingCount = 0;
                        if (snapshots != null) {
                            // Filter pending requests in-memory to avoid needing a composite index
                            for (DocumentSnapshot doc : snapshots) {
                                if ("pending".equals(doc.getString("status"))) {
                                    pendingCount++;
                                }
                            }
                        }
                        binding.totalRequestsText.setText(String.valueOf(pendingCount));
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
        intent.putExtra("STORE_HANDLE", currentStoreHandle);
        startActivity(intent);
    }

    @Override
    public void onOptionsClick(Catalogue catalogue, View anchor) {
        androidx.appcompat.widget.PopupMenu popup = new androidx.appcompat.widget.PopupMenu(this, anchor);
        popup.getMenu().add("Delete Catalogue");
        popup.setOnMenuItemClickListener(item -> {
            if (item.getTitle().equals("Delete Catalogue")) {
                showDeleteCatalogueConfirmation(catalogue);
            }
            return true;
        });
        popup.show();
    }

    private void showDeleteCatalogueConfirmation(Catalogue catalogue) {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Delete Catalogue")
                .setMessage(
                        "Are you sure you want to delete '" + catalogue.getName() + "'? This action cannot be undone.")
                .setPositiveButton("Delete", (dialog, which) -> deleteCatalogue(catalogue))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteCatalogue(Catalogue catalogue) {
        db.collection("catalogues").document(catalogue.getId()).delete()
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Catalogue deleted", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to delete catalogue", Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "Error deleting catalogue", e);
                });
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

    private void openStorefrontPreview() {
        if (currentStoreHandle.isEmpty()) {
            Toast.makeText(this, "Please set your Store Link Prefix in Profile first.", Toast.LENGTH_LONG).show();
            return;
        }
        String url = "https://" + currentStoreHandle + ".store.bydj.dev";
        try {
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url));
            startActivity(browserIntent);
        } catch (Exception e) {
            Toast.makeText(this, "No browser app found.", Toast.LENGTH_SHORT).show();
        }
    }

    private void showSignOutConfirmationDialog() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Sign Out")
                .setMessage("Are you sure you want to sign out?")
                .setPositiveButton("Sign Out", (dialog, which) -> signOutAndGoToLogin())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void signOutAndGoToLogin() {
        // Detach all listeners before signing out to prevent crashes or leaks
        detachCatalogueListener();
        detachOrderCountListener();
        detachRequestCountListener();

        FirebaseAuth.getInstance().signOut();
        SessionManager.getInstance().clear(); // Clear local session data
        Toast.makeText(this, "Signed out", Toast.LENGTH_SHORT).show();

        Intent intent = new Intent(HomeActivity.this, SignInActivity.class);
        // Clear back stack so user can't go back to Home
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}