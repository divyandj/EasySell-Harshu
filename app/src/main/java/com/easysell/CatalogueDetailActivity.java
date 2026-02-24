package com.easysell;

import android.content.Intent;
import android.net.Uri; // Import Uri
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.easysell.databinding.ActivityCatalogueDetailBinding;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.List;

public class CatalogueDetailActivity extends AppCompatActivity implements ProductAdapter.OnProductActionClickListener {

    private static final String TAG = "CatalogueDetail";
    private ActivityCatalogueDetailBinding binding;
    private FirebaseFirestore db;
    private ProductAdapter adapter;
    private List<Product> productList;
    private String catalogueId;
    private String catalogueName;
    private String storeHandle;
    private ListenerRegistration productListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCatalogueDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        catalogueId = getIntent().getStringExtra("CATALOGUE_ID");
        catalogueName = getIntent().getStringExtra("CATALOGUE_NAME");
        storeHandle = getIntent().getStringExtra("STORE_HANDLE");

        if (storeHandle == null) {
            storeHandle = "";
        }

        if (catalogueId == null) {
            Toast.makeText(this, "Error: Catalogue ID missing.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Setup Toolbar
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(catalogueName != null ? catalogueName : "Catalogue Details");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        db = FirebaseFirestore.getInstance();
        setupRecyclerView();

        // --- BUTTON LISTENERS ---

        // 1. FAB & Empty State (Add Product)
        binding.fabAddProduct.setOnClickListener(v -> navigateToAddProduct(null));
        binding.buttonAddFirstProduct.setOnClickListener(v -> navigateToAddProduct(null));

        // 2. Share Catalogue Button
        binding.btnShareCatalogue.setOnClickListener(v -> shareCatalogue());

        // 3. Preview Catalogue Button (NEW)
        binding.btnPreviewCatalogue.setOnClickListener(v -> previewCatalogue());
    }

    @Override
    protected void onStart() {
        super.onStart();
        loadProducts();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (productListener != null) {
            productListener.remove();
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

    private void setupRecyclerView() {
        productList = new ArrayList<>();
        adapter = new ProductAdapter(this, productList, this);
        binding.productsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        binding.productsRecyclerView.setAdapter(adapter);
    }

    private void loadProducts() {
        if (productListener != null) {
            productListener.remove();
        }

        Query query = db.collection("products")
                .whereEqualTo("catalogueId", catalogueId)
                .orderBy("createdAt", Query.Direction.DESCENDING);

        productListener = query.addSnapshotListener((value, error) -> {
            if (error != null) {
                Log.w(TAG, "Listen failed.", error);
                Toast.makeText(this, "Error loading products.", Toast.LENGTH_SHORT).show();
                return;
            }
            productList.clear();
            if (value != null) {
                productList.addAll(value.toObjects(Product.class));
            }
            adapter.notifyDataSetChanged();

            // UI Updates
            boolean isEmpty = productList.isEmpty();
            binding.emptyStateContainer.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
            binding.productsRecyclerView.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
            binding.textTotalProductsCount.setText(String.valueOf(productList.size()));
        });
    }

    // --- SHARE FEATURE ---
    private void shareCatalogue() {
        if (catalogueId == null)
            return;

        if (storeHandle == null || storeHandle.isEmpty()) {
            Toast.makeText(this, "Please set your Store Link Prefix in Profile to share.", Toast.LENGTH_LONG).show();
            return;
        }

        String deepLink = "https://" + storeHandle + ".mmproperty.in/catalogue/" + catalogueId;
        String messageBody = String.format(
                "Check out my catalogue \"%s\" on Easy Sell!\n\nBrowse my products here:\n%s",
                catalogueName != null ? catalogueName : "My Store",
                deepLink);

        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, "My Catalogue: " + catalogueName);
        shareIntent.putExtra(Intent.EXTRA_TEXT, messageBody);

        try {
            startActivity(Intent.createChooser(shareIntent, "Share Catalogue via"));
        } catch (Exception e) {
            Toast.makeText(this, "No app found to share.", Toast.LENGTH_SHORT).show();
        }
    }

    // --- PREVIEW FEATURE (Opens in Browser) ---
    private void previewCatalogue() {
        if (catalogueId == null)
            return;

        if (storeHandle == null || storeHandle.isEmpty()) {
            Toast.makeText(this, "Please set your Store Link Prefix in Profile to preview.", Toast.LENGTH_LONG).show();
            return;
        }

        // The URL to open
        String url = "https://" + storeHandle + ".mmproperty.in/catalogue/" + catalogueId;

        try {
            // Create an Intent to view the URL
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(browserIntent);
        } catch (Exception e) {
            // Handle case where no browser is installed
            Toast.makeText(this, "No browser app found.", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Error opening browser", e);
        }
    }

    private void navigateToAddProduct(String productId) {
        Intent intent = new Intent(this, AddProductActivity.class);
        intent.putExtra("CATALOGUE_ID", catalogueId);
        if (productId != null) {
            intent.putExtra("PRODUCT_ID", productId);
        }
        startActivity(intent);
    }

    // --- ADAPTER LISTENERS ---

    @Override
    public void onEditClick(Product product) {
        navigateToAddProduct(product.getId());
    }

    @Override
    public void onVisibilityToggleClick(Product product) {
        DocumentReference productRef = db.collection("products").document(product.getId());
        boolean newVisibility = !product.isVisibleInCatalogue();

        productRef.update("visibleInCatalogue", newVisibility)
                .addOnSuccessListener(aVoid -> {
                    String message = newVisibility ? "Product is now visible" : "Product hidden";
                    Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error updating visibility", e);
                    Toast.makeText(this, "Failed to update visibility", Toast.LENGTH_SHORT).show();
                });
    }

    @Override
    public void onDeleteClick(Product product) {
        showDeleteProductConfirmation(product);
    }

    private void showDeleteProductConfirmation(Product product) {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Delete Product")
                .setMessage(
                        "Are you sure you want to delete '" + product.getTitle() + "'? This action cannot be undone.")
                .setPositiveButton("Delete", (dialog, which) -> {
                    db.collection("products").document(product.getId()).delete()
                            .addOnSuccessListener(
                                    aVoid -> Toast.makeText(this, "Product deleted", Toast.LENGTH_SHORT).show())
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "Error deleting product", e);
                                Toast.makeText(this, "Failed to delete product", Toast.LENGTH_SHORT).show();
                            });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public void onItemClick(Product product) {
        try {
            Intent intent = new Intent(this, ProductDetailSellerActivity.class);
            intent.putExtra("PRODUCT_ID", product.getId());
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Selected: " + product.getTitle(), Toast.LENGTH_SHORT).show();
        }
    }
}