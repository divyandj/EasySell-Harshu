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
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.easysell.databinding.ActivityEditProfileBinding;
import com.easysell.network.RetrofitClient;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.firebase.firestore.FirebaseFirestore;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class EditProfileActivity extends AppCompatActivity {

    private static final String TAG = "EditProfileActivity";
    private ActivityEditProfileBinding binding;
    private FirebaseFirestore db;
    private String userId;
    private String accessToken;

    // Background Threading for Network Calls
    private final Executor executor = Executors.newSingleThreadExecutor();
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

        // 1. Get User ID & Auth Token
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
        if (account != null) {
            userId = account.getId();
            // Get Access Token from SessionManager (same as AddProductActivity)
            accessToken = SessionManager.getInstance().getAccessToken();

            if (accessToken == null) {
                Toast.makeText(this, "Session expired. Please restart the app.", Toast.LENGTH_LONG).show();
                finish();
                return;
            }
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
            uploadSingleFile(selectedLogoUri, "profile_logo_" + System.currentTimeMillis(), new UploadCallback() {
                @Override
                public void onSuccess(String url) {
                    // Logo done, move to Signature
                    processSignatureUpload(url);
                }

                @Override
                public void onFailure(String message) {
                    showError("Logo Upload Failed: " + message);
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
            uploadSingleFile(selectedSignatureUri, "profile_sign_" + System.currentTimeMillis(), new UploadCallback() {
                @Override
                public void onSuccess(String url) {
                    // Signature done, save everything to Firestore
                    saveToFirestore(finalLogoUrl, url);
                }

                @Override
                public void onFailure(String message) {
                    showError("Signature Upload Failed: " + message);
                }
            });
        } else {
            // No new signature, keep existing URL
            saveToFirestore(finalLogoUrl, currentSignatureUrl);
        }
    }

    private void saveToFirestore(String logoUrl, String signatureUrl) {
        setLoading(true, "Updating Database...");

        Map<String, Object> data = new HashMap<>();
        data.put("businessName", binding.etBusinessName.getText().toString().trim());
        data.put("ownerName", binding.etOwnerName.getText().toString().trim());
        data.put("phone", binding.etPhone.getText().toString().trim());
        data.put("gstin", binding.etGst.getText().toString().trim());
        data.put("address", binding.etAddress.getText().toString().trim());

        // Save URLs (Handle potential nulls if no previous url existed)
        if (logoUrl != null) data.put("profileImageUrl", logoUrl);
        if (signatureUrl != null) data.put("signatureImageUrl", signatureUrl);

        // Using set() to save. This overwrites the document or creates it.
        // Since we loaded the existing data first, we are safely updating the whole profile.
        db.collection("users").document(userId).set(data)
                .addOnSuccessListener(aVoid -> handler.post(() -> {
                    setLoading(false, "");
                    Toast.makeText(this, "Profile Updated Successfully!", Toast.LENGTH_SHORT).show();
                    finish(); // Close activity and return to Profile View
                }))
                .addOnFailureListener(e -> showError("Database Error: " + e.getMessage()));
    }

    // --- GOOGLE DRIVE UPLOAD LOGIC ---

    interface UploadCallback {
        void onSuccess(String url);
        void onFailure(String message);
    }

    private void uploadSingleFile(final Uri uri, final String fileName, final UploadCallback callback) {
        final String mimeType = getContentResolver().getType(uri);
        final String authHeader = "Bearer " + accessToken;

        executor.execute(() -> {
            try {
                // 1. Prepare File
                InputStream inputStream = getContentResolver().openInputStream(uri);
                byte[] fileBytes = getBytes(inputStream);
                if (inputStream != null) inputStream.close();

                RequestBody fileRequestBody = RequestBody.create(fileBytes, MediaType.parse(mimeType != null ? mimeType : "image/jpeg"));
                MultipartBody.Part filePart = MultipartBody.Part.createFormData("file", fileName, fileRequestBody);

                // 2. Prepare Metadata
                JSONObject metadataJson = new JSONObject();
                metadataJson.put("name", fileName);
                // Note: We upload to root folder to keep logic simple and identical to AddProductActivity
                RequestBody metadataRequestBody = RequestBody.create(metadataJson.toString(), MediaType.parse("application/json; charset=utf-8"));

                // 3. Upload Call
                RetrofitClient.getInstance().getApiService().uploadFileMultipart(authHeader, metadataRequestBody, filePart)
                        .enqueue(new Callback<ResponseBody>() {
                            @Override
                            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                                if (response.isSuccessful() && response.body() != null) {
                                    try {
                                        String fileId = new JSONObject(response.body().string()).getString("id");
                                        // 4. Set Permissions
                                        setPermissionsAndFinalize(authHeader, fileId, callback);
                                    } catch (Exception e) {
                                        callback.onFailure("Response Parse Error: " + e.getMessage());
                                    }
                                } else {
                                    callback.onFailure("Upload Failed (Code: " + response.code() + ")");
                                }
                            }

                            @Override
                            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                                callback.onFailure("Network Error: " + t.getMessage());
                            }
                        });

            } catch (Exception e) {
                callback.onFailure("File Prep Error: " + e.getMessage());
            }
        });
    }

    private void setPermissionsAndFinalize(String authHeader, String fileId, UploadCallback callback) {
        Map<String, String> permissionBody = new HashMap<>();
        permissionBody.put("role", "reader");
        permissionBody.put("type", "anyone");

        RetrofitClient.getInstance().getApiService().createPermissions(authHeader, fileId, permissionBody)
                .enqueue(new Callback<ResponseBody>() {
                    @Override
                    public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                        if (response.isSuccessful()) {
                            // 5. Generate Direct Link
                            String directLink = "https://drive.google.com/uc?export=download&id=" + fileId;
                            callback.onSuccess(directLink);
                        } else {
                            callback.onFailure("Permission Error (Code: " + response.code() + ")");
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                        callback.onFailure("Permission Network Error: " + t.getMessage());
                    }
                });
    }

    // --- HELPER METHODS ---

    private byte[] getBytes(InputStream inputStream) throws IOException {
        ByteArrayOutputStream byteBuffer = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int len;
        while ((len = inputStream.read(buffer)) != -1) {
            byteBuffer.write(buffer, 0, len);
        }
        return byteBuffer.toByteArray();
    }

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