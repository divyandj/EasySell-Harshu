package com.easysell;

import android.app.Dialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.easysell.databinding.ActivityProfileBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.messaging.FirebaseMessaging;

public class ProfileActivity extends AppCompatActivity {

    private static final String TAG = "ProfileActivity";
    private ActivityProfileBinding binding;
    private FirebaseFirestore db;
    private String userId;
    private SharedPreferences prefs;
    private String currentStoreHandle = "";

    // TOPIC NAMES (Base topics, actual topics append "_" + storeHandle)
    private static final String TOPIC_ORDERS = "admin_orders";
    private static final String TOPIC_USERS = "admin_new_users";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityProfileBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // 1. Initialize Firebase
        db = FirebaseFirestore.getInstance();

        // 2. Identify User
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            userId = user.getUid();
        } else {
            Toast.makeText(this, "User not identified. Please login.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // 3. Setup UI
        setupToolbar();
        setupEditButton();
        setupNotificationSwitches();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Always reload data when returning to this screen
        loadProfileData();
    }

    // --- TOOLBAR & NAVIGATION ---

    private void setupToolbar() {
        binding.btnBack.setOnClickListener(v -> onBackPressed());
    }

    private void setupEditButton() {
        binding.btnEditProfile.setOnClickListener(v -> {
            Intent intent = new Intent(ProfileActivity.this, EditProfileActivity.class);
            startActivity(intent);
        });
    }

    // --- DATA LOADING ---

    private void loadProfileData() {
        db.collection("users").document(userId).get()
                .addOnSuccessListener(document -> {
                    if (document.exists()) {
                        populateUI(document);
                    } else {
                        binding.tvBusinessNameHeader.setText("Business Name Not Set");
                        binding.tvOwnerNameHeader.setText("Tap Edit to setup profile");
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error loading profile", e);
                    Toast.makeText(this, "Failed to load profile.", Toast.LENGTH_SHORT).show();
                });
    }

    private void populateUI(DocumentSnapshot doc) {
        // 1. Text Fields
        String businessName = doc.getString("businessName");
        String ownerName = doc.getString("ownerName");
        String phone = doc.getString("phone");
        String gst = doc.getString("gstin");
        String address = doc.getString("address");
        String handle = doc.getString("storeHandle");

        if (handle != null && !handle.isEmpty()) {
            currentStoreHandle = handle;
            binding.tvStoreHandleHeader.setText("@" + handle);
            binding.tvStoreHandleHeader.setVisibility(View.VISIBLE);
        } else {
            currentStoreHandle = "";
            binding.tvStoreHandleHeader.setVisibility(View.GONE);
        }

        binding.tvBusinessNameHeader
                .setText(businessName != null && !businessName.isEmpty() ? businessName : "Business Name");
        binding.tvOwnerNameHeader.setText(ownerName != null && !ownerName.isEmpty() ? ownerName : "Owner Name");

        binding.tvPhoneView.setText(phone != null && !phone.isEmpty() ? phone : "Phone Not Set");
        binding.tvGstView.setText(gst != null && !gst.isEmpty() ? gst : "GSTIN Not Set");
        binding.tvAddressView.setText(address != null && !address.isEmpty() ? address : "Address Not Set");

        // 2. Profile Image (With Full Screen Click Listener)
        String profileUrl = doc.getString("profileImageUrl");
        if (profileUrl != null && !profileUrl.isEmpty()) {
            Glide.with(this)
                    .load(profileUrl)
                    .placeholder(R.drawable.bg_circle_gray)
                    .circleCrop()
                    .into(binding.imgProfileView);

            // NEW: Add click listener to open full screen
            binding.imgProfileView.setOnClickListener(v -> showFullScreenImage(profileUrl));
        }

        // 3. Signature Image
        String signatureUrl = doc.getString("signatureImageUrl");
        if (signatureUrl != null && !signatureUrl.isEmpty()) {
            binding.imgSignatureView.setAlpha(1.0f);
            Glide.with(this)
                    .load(signatureUrl)
                    .fitCenter()
                    .into(binding.imgSignatureView);
        } else {
            binding.imgSignatureView.setAlpha(0.3f);
            binding.imgSignatureView.setImageResource(R.drawable.ic_image_placeholder);
        }

        // 4. Store Mode
        String storeMode = doc.getString("storeMode");
        boolean isPublic = "public".equals(storeMode);
        binding.switchStoreMode.setOnCheckedChangeListener(null); // Prevent trigger on load
        binding.switchStoreMode.setChecked(isPublic);
        binding.switchStoreMode.setOnCheckedChangeListener(this::onStoreModeToggle);

        // 5. Inventory Tracking (default: true)
        Boolean inventoryTracking = doc.getBoolean("inventoryTracking");
        boolean isInventoryOn = (inventoryTracking == null || inventoryTracking);
        binding.switchInventoryTracking.setOnCheckedChangeListener(null);
        binding.switchInventoryTracking.setChecked(isInventoryOn);
        binding.switchInventoryTracking.setOnCheckedChangeListener(this::onInventoryTrackingToggle);
    }

    // --- NEW: FULL SCREEN IMAGE DIALOG ---

    private void showFullScreenImage(String imageUrl) {
        if (imageUrl == null || imageUrl.isEmpty())
            return;

        Dialog dialog = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.setContentView(R.layout.dialog_fullscreen_image);

        ImageView fullScreenView = dialog.findViewById(R.id.fullscreen_image);
        View closeBtn = dialog.findViewById(R.id.btn_close_fullscreen);

        // Load image without cropping
        Glide.with(this).load(imageUrl).into(fullScreenView);

        closeBtn.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    // --- NOTIFICATIONS & SETTINGS (PRESERVED & EXTENDED) ---

    private void onStoreModeToggle(CompoundButton buttonView, boolean isChecked) {
        String newMode = isChecked ? "public" : "private";
        db.collection("users").document(userId)
                .update("storeMode", newMode)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Store set to " + newMode, Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to update store mode", Toast.LENGTH_SHORT).show();
                    // Revert UI
                    binding.switchStoreMode.setOnCheckedChangeListener(null);
                    binding.switchStoreMode.setChecked(!isChecked);
                    binding.switchStoreMode.setOnCheckedChangeListener(this::onStoreModeToggle);
                });
    }

    private void onInventoryTrackingToggle(CompoundButton buttonView, boolean isChecked) {
        db.collection("users").document(userId)
                .update("inventoryTracking", isChecked)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Inventory tracking " + (isChecked ? "enabled" : "disabled"), Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to update setting", Toast.LENGTH_SHORT).show();
                    binding.switchInventoryTracking.setOnCheckedChangeListener(null);
                    binding.switchInventoryTracking.setChecked(!isChecked);
                    binding.switchInventoryTracking.setOnCheckedChangeListener(this::onInventoryTrackingToggle);
                });
    }

    private void setupNotificationSwitches() {
        prefs = getSharedPreferences("AdminPrefs", MODE_PRIVATE);

        boolean isOrdersEnabled = prefs.getBoolean(TOPIC_ORDERS, true);
        boolean isUsersEnabled = prefs.getBoolean(TOPIC_USERS, true);

        binding.switchNotifyOrders.setChecked(isOrdersEnabled);
        binding.switchNotifyUsers.setChecked(isUsersEnabled);

        binding.switchNotifyOrders.setOnCheckedChangeListener(this::onOrdersToggle);
        binding.switchNotifyUsers.setOnCheckedChangeListener(this::onUsersToggle);
    }

    private void onOrdersToggle(CompoundButton buttonView, boolean isChecked) {
        toggleSubscription(TOPIC_ORDERS, isChecked, binding.switchNotifyOrders);
    }

    private void onUsersToggle(CompoundButton buttonView, boolean isChecked) {
        toggleSubscription(TOPIC_USERS, isChecked, binding.switchNotifyUsers);
    }

    private void toggleSubscription(String baseTopic, boolean enable, CompoundButton switchButton) {
        if (currentStoreHandle.isEmpty()) {
            Toast.makeText(this, "Store Handle not configured.", Toast.LENGTH_SHORT).show();
            switchButton.setChecked(!enable); // Revert switch
            return;
        }

        String fullTopic = baseTopic + "_" + currentStoreHandle;

        if (enable) {
            FirebaseMessaging.getInstance().subscribeToTopic(fullTopic)
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            prefs.edit().putBoolean(baseTopic, true).apply();
                        } else {
                            switchButton.setChecked(false);
                            Toast.makeText(this, "Failed to subscribe", Toast.LENGTH_SHORT).show();
                        }
                    });
        } else {
            FirebaseMessaging.getInstance().unsubscribeFromTopic(fullTopic)
                    .addOnCompleteListener(task -> {
                        prefs.edit().putBoolean(baseTopic, false).apply();
                    });
        }
    }
}