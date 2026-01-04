package com.easysell;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.cloudinary.android.MediaManager;
import com.cloudinary.android.callback.ErrorInfo;
import com.cloudinary.android.callback.UploadCallback;
import com.easysell.databinding.ActivityEditProfileBinding;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class EditProfileActivity extends AppCompatActivity {

    private static final String TAG = "EditProfileActivity";
    private ActivityEditProfileBinding binding;
    private FirebaseFirestore db;
    private String userId;

    // Helper to ensure UI updates happen on Main Thread
    private final Handler handler = new Handler(Looper.getMainLooper());

    // State Variables for Images
    private Uri selectedLogoUri = null;
    private Uri selectedSignatureUri = null;

    // To preserve existing URLs if user doesn't upload new ones
    private String currentLogoUrl = "";
    private String currentSignatureUrl = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityEditProfileBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        db = FirebaseFirestore.getInstance();

        // 1. Get User ID
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
        if (account != null) {
            userId = account.getId();
        } else {
            Toast.makeText(this, "User not identified.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // 2. Setup UI
        setupToolbar();
        setupImagePickers();

        // 3. Load Existing Data
        loadCurrentData();

        // 4. Save Button
        binding.btnSaveChanges.setOnClickListener(v -> startSaveProcess());
    }

    private void setupToolbar() {
        binding.toolbar.setNavigationOnClickListener(v -> finish());
    }

    // --- IMAGE PICKERS ---

    private final ActivityResultLauncher<Intent> logoPickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    selectedLogoUri = result.getData().getData();
                    // Update Preview
                    Glide.with(this).load(selectedLogoUri).circleCrop().into(binding.imgLogoPreview);
                }
            });

    private final ActivityResultLauncher<Intent> signaturePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    selectedSignatureUri = result.getData().getData();
                    // Update Preview
                    binding.imgSignaturePreview.setAlpha(1.0f);
                    Glide.with(this).load(selectedSignatureUri).fitCenter().into(binding.imgSignaturePreview);
                }
            });

    private void setupImagePickers() {
        binding.btnUploadLogo.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT).setType("image/*");
            logoPickerLauncher.launch(intent);
        });

        binding.btnUploadSignature.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT).setType("image/*");
            signaturePickerLauncher.launch(intent);
        });
    }

    // --- LOAD DATA ---

    private void loadCurrentData() {
        setLoading(true, "Loading Profile...");
        db.collection("users").document(userId).get()
                .addOnSuccessListener(document -> {
                    setLoading(false, "");
                    if (document.exists()) {
                        // Populate Text Fields
                        binding.etBusinessName.setText(document.getString("businessName"));
                        binding.etOwnerName.setText(document.getString("ownerName"));
                        binding.etPhone.setText(document.getString("phone"));
                        binding.etGst.setText(document.getString("gstin"));
                        binding.etAddress.setText(document.getString("address"));

                        // Populate Images & Store Current URLs
                        currentLogoUrl = document.getString("profileImageUrl");
                        if (currentLogoUrl != null && !currentLogoUrl.isEmpty()) {
                            Glide.with(this).load(currentLogoUrl).circleCrop().into(binding.imgLogoPreview);
                        }

                        currentSignatureUrl = document.getString("signatureImageUrl");
                        if (currentSignatureUrl != null && !currentSignatureUrl.isEmpty()) {
                            binding.imgSignaturePreview.setAlpha(1.0f);
                            Glide.with(this).load(currentSignatureUrl).fitCenter().into(binding.imgSignaturePreview);
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    setLoading(false, "");
                    Toast.makeText(this, "Failed to load profile data", Toast.LENGTH_SHORT).show();
                });
    }

    // --- SAVE WORKFLOW (Waterfall Strategy) ---

    private void startSaveProcess() {
        String businessName = binding.etBusinessName.getText().toString().trim();
        if (businessName.isEmpty()) {
            Toast.makeText(this, "Business Name is required", Toast.LENGTH_SHORT).show();
            return;
        }

        setLoading(true, "Saving Profile...");

        // Step 1: Handle Logo Upload
        processLogoUpload();
    }

    private void processLogoUpload() {
        if (selectedLogoUri != null) {
            setLoading(true, "Uploading Logo...");
            uploadToCloudinary(selectedLogoUri, new OnUploadResult() {
                @Override
                public void onSuccess(String url) {
                    // Logo done, move to Signature
                    processSignatureUpload(url);
                }

                @Override
                public void onFailure(String error) {
                    showError("Logo Upload Failed: " + error);
                }
            });
        } else {
            // No new logo, keep existing URL and move next
            processSignatureUpload(currentLogoUrl);
        }
    }

    private void processSignatureUpload(String finalLogoUrl) {
        if (selectedSignatureUri != null) {
            setLoading(true, "Uploading Signature...");
            uploadToCloudinary(selectedSignatureUri, new OnUploadResult() {
                @Override
                public void onSuccess(String url) {
                    // Signature done, save everything to Firestore
                    saveToFirestore(finalLogoUrl, url);
                }

                @Override
                public void onFailure(String error) {
                    showError("Signature Upload Failed: " + error);
                }
            });
        } else {
            // No new signature, keep existing URL
            saveToFirestore(finalLogoUrl, currentSignatureUrl);
        }
    }

    // --- CLOUDINARY UPLOAD LOGIC ---

    interface OnUploadResult {
        void onSuccess(String url);
        void onFailure(String error);
    }

    private void uploadToCloudinary(Uri uri, OnUploadResult listener) {
        MediaManager.get().upload(uri)
                .unsigned("easysell_preset") // Uses your specific preset
                .option("resource_type", "image")
                .callback(new UploadCallback() {
                    @Override
                    public void onStart(String requestId) {
                        // Optional: Update progress
                    }

                    @Override
                    public void onProgress(String requestId, long bytes, long totalBytes) {
                        // Optional: Update progress bar
                    }

                    @Override
                    public void onSuccess(String requestId, Map resultData) {
                        // Cloudinary returns a generic Map. Extract the secure URL.
                        String url = (String) resultData.get("secure_url");
                        listener.onSuccess(url);
                    }

                    @Override
                    public void onError(String requestId, ErrorInfo error) {
                        listener.onFailure(error.getDescription());
                    }

                    @Override
                    public void onReschedule(String requestId, ErrorInfo error) {
                        // Retry logic handled by SDK
                    }
                })
                .dispatch();
    }

    // --- FIRESTORE SAVE ---

    private void saveToFirestore(String logoUrl, String signatureUrl) {
        setLoading(true, "Updating Database...");

        Map<String, Object> data = new HashMap<>();
        data.put("businessName", binding.etBusinessName.getText().toString().trim());
        data.put("ownerName", binding.etOwnerName.getText().toString().trim());
        data.put("phone", binding.etPhone.getText().toString().trim());
        data.put("gstin", binding.etGst.getText().toString().trim());
        data.put("address", binding.etAddress.getText().toString().trim());

        // Save URLs
        if (logoUrl != null) data.put("profileImageUrl", logoUrl);
        if (signatureUrl != null) data.put("signatureImageUrl", signatureUrl);

        db.collection("users").document(userId).set(data)
                .addOnSuccessListener(aVoid -> handler.post(() -> {
                    setLoading(false, "");
                    Toast.makeText(this, "Profile Updated Successfully!", Toast.LENGTH_SHORT).show();
                    finish(); // Close activity and return to Profile View
                }))
                .addOnFailureListener(e -> showError("Database Error: " + e.getMessage()));
    }

    // --- HELPER METHODS ---

    private void setLoading(boolean isLoading, String message) {
        handler.post(() -> {
            if (isLoading) {
                binding.progressOverlay.setVisibility(View.VISIBLE);
                binding.tvProgressText.setText(message);
                binding.btnSaveChanges.setEnabled(false);
            } else {
                binding.progressOverlay.setVisibility(View.GONE);
                binding.btnSaveChanges.setEnabled(true);
            }
        });
    }

    private void showError(String message) {
        handler.post(() -> {
            setLoading(false, "");
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            Log.e(TAG, message);
        });
    }
}