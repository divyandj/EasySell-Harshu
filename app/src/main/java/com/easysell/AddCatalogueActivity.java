package com.easysell;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.cloudinary.android.MediaManager;
import com.cloudinary.android.callback.ErrorInfo;
import com.cloudinary.android.callback.UploadCallback;
import com.easysell.databinding.ActivityAddCatalogueBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Map;

public class AddCatalogueActivity extends AppCompatActivity {

    private ActivityAddCatalogueBinding binding;
    private FirebaseFirestore db;
    private Uri selectedImageUri = null;
    private final Handler handler = new Handler(Looper.getMainLooper());

    // Launcher to pick an image from the gallery
    private final ActivityResultLauncher<String> imagePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    selectedImageUri = uri;
                    // Show preview using Glide
                    Glide.with(this)
                            .load(uri)
                            .centerCrop()
                            .into(binding.catalogueImagePreview);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAddCatalogueBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Setup Toolbar
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Create Catalogue");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        db = FirebaseFirestore.getInstance();

        // 1. Image Selection Listener
        // Note: In your XML, the clickable view is 'btn_select_image' overlay
        binding.btnSelectImage.setOnClickListener(v -> imagePickerLauncher.launch("image/*"));

        // 2. Save Button Listener
        binding.saveCatalogueButton.setOnClickListener(v -> validateAndSave());
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    private void validateAndSave() {
        String name = binding.catalogueNameEditText.getText().toString().trim();

        if (name.isEmpty()) {
            Toast.makeText(this, "Please enter a catalogue name", Toast.LENGTH_SHORT).show();
            return;
        }

        if (selectedImageUri == null) {
            Toast.makeText(this, "Please select a cover image", Toast.LENGTH_SHORT).show();
            return;
        }

        setLoading(true);

        // Step 1: Upload Image to Cloudinary
        uploadImageToCloudinary(selectedImageUri, name);
    }

    private void uploadImageToCloudinary(Uri imageUri, String catalogueName) {
        MediaManager.get().upload(imageUri)
                .unsigned("easysell_preset") // Using your preset
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
                        // Step 2: Get the secure URL and Save to Firestore
                        String imageUrl = (String) resultData.get("secure_url");
                        saveCatalogueToFirestore(catalogueName, imageUrl);
                    }

                    @Override
                    public void onError(String requestId, ErrorInfo error) {
                        showError("Image Upload Failed: " + error.getDescription());
                    }

                    @Override
                    public void onReschedule(String requestId, ErrorInfo error) {
                        // Retry logic handled by SDK
                    }
                })
                .dispatch();
    }

    private void saveCatalogueToFirestore(String name, String imageUrl) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            showError("User not signed in!");
            return;
        }

        String userId = user.getUid();

        // Use the updated Model constructor
        Catalogue newCatalogue = new Catalogue(name, userId, imageUrl);

        db.collection("catalogues")
                .add(newCatalogue)
                .addOnSuccessListener(documentReference -> handler.post(() -> {
                    setLoading(false);
                    Toast.makeText(this, "Catalogue Created Successfully!", Toast.LENGTH_SHORT).show();
                    finish(); // Return to Home Screen
                }))
                .addOnFailureListener(e -> showError("Database Error: " + e.getMessage()));
    }

    private void showError(String message) {
        handler.post(() -> {
            setLoading(false);
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        });
    }

    private void setLoading(boolean isLoading) {
        handler.post(() -> {
            binding.progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
            binding.saveCatalogueButton.setEnabled(!isLoading);
            binding.btnSelectImage.setEnabled(!isLoading); // Disable picker while uploading
        });
    }
}