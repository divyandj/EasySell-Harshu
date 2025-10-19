package com.easysell;

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
import com.easysell.databinding.ActivityAddCategoryBinding;
import com.easysell.network.RetrofitClient;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
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

public class AddCategoryActivity extends AppCompatActivity {

    private ActivityAddCategoryBinding binding;
    private String accessToken;
    private Uri selectedImageUri;
    private static final String FOLDER_NAME = "categories";
    private final Executor executor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final ActivityResultLauncher<String> imagePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    selectedImageUri = uri;
                    Glide.with(this).load(selectedImageUri).into(binding.imageViewPreview);
                }
            }
    );

    interface FolderCallback {
        void onFolderFound(String folderId);
        void onError(String message);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAddCategoryBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setTitle("Add New Category");

        accessToken = SessionManager.getInstance().getAccessToken();
        if (accessToken == null) {
            showError("Authentication error. Please sign in again.");
            finish();
            return;
        }

        binding.buttonSelectImage.setOnClickListener(v -> imagePickerLauncher.launch("image/*"));
        binding.buttonSaveCategory.setOnClickListener(v -> handleSaveCategory());
    }

    private void handleSaveCategory() {
        String categoryName = binding.categoryNameEditText.getText().toString().trim();
        if (categoryName.isEmpty()) {
            Toast.makeText(this, "Please enter a category name.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (selectedImageUri == null) {
            Toast.makeText(this, "Please select an image.", Toast.LENGTH_SHORT).show();
            return;
        }
        setLoading(true);

        getOrCreateFolderId(new FolderCallback() {
            @Override
            public void onFolderFound(String folderId) {
                uploadImageToFolder(folderId, selectedImageUri, categoryName);
            }

            @Override
            public void onError(String message) {
                showError(message);
            }
        });
    }

    private void getOrCreateFolderId(FolderCallback callback) {
        String authHeader = "Bearer " + accessToken;
        String query = "name='" + FOLDER_NAME + "' and mimeType='application/vnd.google-apps.folder' and trashed=false";

        RetrofitClient.getInstance().getApiService().findFolder(authHeader, query, "files(id)", "drive").enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        JSONObject jsonObject = new JSONObject(response.body().string());
                        JSONArray files = jsonObject.getJSONArray("files");
                        if (files.length() > 0) {
                            String folderId = files.getJSONObject(0).getString("id");
                            callback.onFolderFound(folderId);
                        } else {
                            createFolder(callback);
                        }
                    } catch (IOException | JSONException e) {
                        callback.onError("Failed to parse folder search response.");
                    }
                } else {
                    callback.onError("Failed to find folder.");
                }
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                callback.onError("Folder search failed: " + t.getMessage());
            }
        });
    }

    private void createFolder(FolderCallback callback) {
        String authHeader = "Bearer " + accessToken;
        JSONObject metadataJson = new JSONObject();
        try {
            metadataJson.put("name", FOLDER_NAME);
            metadataJson.put("mimeType", "application/vnd.google-apps.folder");
        } catch (JSONException e) {
            // Should not happen
        }
        RequestBody body = RequestBody.create(metadataJson.toString(), MediaType.parse("application/json; charset=utf-8"));
        RetrofitClient.getInstance().getApiService().createFolder(authHeader, body).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        String folderId = new JSONObject(response.body().string()).getString("id");
                        callback.onFolderFound(folderId);
                    } catch (IOException | JSONException e) {
                        callback.onError("Failed to parse folder creation response.");
                    }
                } else {
                    callback.onError("Failed to create folder.");
                }
            }
            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                callback.onError("Folder creation failed: " + t.getMessage());
            }
        });
    }

    private void uploadImageToFolder(String folderId, Uri imageUri, String categoryName) {
        String authHeader = "Bearer " + accessToken;
        executor.execute(() -> {
            try {
                InputStream inputStream = getContentResolver().openInputStream(imageUri);
                byte[] imageBytes = getBytes(inputStream);
                if (inputStream != null) inputStream.close();
                RequestBody imageRequestBody = RequestBody.create(imageBytes, MediaType.parse("image/jpeg"));
                MultipartBody.Part imagePart = MultipartBody.Part.createFormData("file", categoryName + ".jpg", imageRequestBody);

                JSONObject metadataJson = new JSONObject();
                metadataJson.put("name", categoryName + "_" + System.currentTimeMillis() + ".jpg");
                metadataJson.put("parents", new JSONArray(Collections.singletonList(folderId)));
                RequestBody metadataRequestBody = RequestBody.create(metadataJson.toString(), MediaType.parse("application/json; charset=utf-8"));

                RetrofitClient.getInstance().getApiService().uploadFileMultipart(authHeader, metadataRequestBody, imagePart).enqueue(new Callback<ResponseBody>() {
                    @Override
                    public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            try {
                                String fileId = new JSONObject(response.body().string()).getString("id");
                                setPermissionsAndGetLink(authHeader, fileId);
                            } catch (IOException | JSONException e) {
                                showError("Failed to parse upload response.");
                            }
                        } else {
                            showError("Upload failed.");
                        }
                    }
                    @Override
                    public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                        showError("Upload failed: " + t.getMessage());
                    }
                });
            } catch (IOException | JSONException e) {
                showError("Failed to prepare file for upload.");
            }
        });
    }

    private void setPermissionsAndGetLink(String authHeader, String fileId) {
        Map<String, String> permissionBody = new HashMap<>();
        permissionBody.put("role", "reader");
        permissionBody.put("type", "anyone");

        RetrofitClient.getInstance().getApiService().createPermissions(authHeader, fileId, permissionBody).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    getWebViewLink(authHeader, fileId);
                } else {
                    showError("Failed to set permissions.");
                }
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                showError("Permission setting failed: " + t.getMessage());
            }
        });
    }

    private void getWebViewLink(String authHeader, String fileId) {
        RetrofitClient.getInstance().getApiService().getFileWebViewLink(authHeader, fileId).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                if (response.isSuccessful() && response.body() != null) {
                    String directLink = "https://drive.google.com/uc?export=download&id=" + fileId;
                    handler.post(() -> {
                        setLoading(false);
                        binding.textViewUrl.setText(directLink);
                        Toast.makeText(AddCategoryActivity.this, "Category Saved!", Toast.LENGTH_LONG).show();
                        new Handler(Looper.getMainLooper()).postDelayed(() -> finish(), 2000);
                    });
                } else {
                    showError("Failed to get file link.");
                }
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                showError("Link retrieval failed: " + t.getMessage());
            }
        });
    }

    private byte[] getBytes(InputStream inputStream) throws IOException {
        ByteArrayOutputStream byteBuffer = new ByteArrayOutputStream();
        int bufferSize = 1024;
        byte[] buffer = new byte[bufferSize];
        int len;
        while ((len = inputStream.read(buffer)) != -1) {
            byteBuffer.write(buffer, 0, len);
        }
        return byteBuffer.toByteArray();
    }

    private void showError(String message) {
        handler.post(() -> {
            setLoading(false);
            Toast.makeText(this, "Error: " + message, Toast.LENGTH_LONG).show();
            Log.e("AddCategoryActivity", message);
        });
    }

    private void setLoading(boolean isLoading) {
        binding.progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        binding.buttonSaveCategory.setEnabled(!isLoading);
        binding.buttonSelectImage.setEnabled(!isLoading);
    }
}