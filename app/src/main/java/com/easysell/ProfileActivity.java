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
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.messaging.FirebaseMessaging;

public class ProfileActivity extends AppCompatActivity {

    private static final String TAG = "ProfileActivity";
    private ActivityProfileBinding binding;
    private FirebaseFirestore db;
    private String userId;
    private SharedPreferences prefs;

    // TOPIC NAMES (Must match backend)
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
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
        if (account != null) {
            userId = account.getId();
        } else if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
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

        binding.tvBusinessNameHeader.setText(businessName != null && !businessName.isEmpty() ? businessName : "Business Name");
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
    }

    // --- NEW: FULL SCREEN IMAGE DIALOG ---

    private void showFullScreenImage(String imageUrl) {
        if (imageUrl == null || imageUrl.isEmpty()) return;

        Dialog dialog = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.setContentView(R.layout.dialog_fullscreen_image);

        ImageView fullScreenView = dialog.findViewById(R.id.fullscreen_image);
        View closeBtn = dialog.findViewById(R.id.btn_close_fullscreen);

        // Load image without cropping
        Glide.with(this).load(imageUrl).into(fullScreenView);

        closeBtn.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    // --- NOTIFICATIONS (PRESERVED) ---

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

    private void toggleSubscription(String topic, boolean enable, CompoundButton switchButton) {
        if (enable) {
            FirebaseMessaging.getInstance().subscribeToTopic(topic)
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            prefs.edit().putBoolean(topic, true).apply();
                        } else {
                            switchButton.setChecked(false);
                            Toast.makeText(this, "Failed to subscribe", Toast.LENGTH_SHORT).show();
                        }
                    });
        } else {
            FirebaseMessaging.getInstance().unsubscribeFromTopic(topic)
                    .addOnCompleteListener(task -> {
                        prefs.edit().putBoolean(topic, false).apply();
                    });
        }
    }
}