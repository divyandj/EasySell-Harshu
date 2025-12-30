package com.easysell;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.easysell.databinding.ActivityCatalogueDetailBinding; // Use ViewBinding
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.WriteBatch; // Import WriteBatch

import java.util.ArrayList;
import java.util.List;

public class CatalogueDetailActivity extends AppCompatActivity implements ProductAdapter.OnProductActionClickListener { // Implement the listener

    private static final String TAG = "CatalogueDetail";
    private ActivityCatalogueDetailBinding binding; // Use ViewBinding
    private FirebaseFirestore db;
    private ProductAdapter adapter;
    private List<Product> productList;
    private String catalogueId;
    private ListenerRegistration productListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCatalogueDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        catalogueId = getIntent().getStringExtra("CATALOGUE_ID");
        String catalogueName = getIntent().getStringExtra("CATALOGUE_NAME");

        if (catalogueId == null) {
            Toast.makeText(this, "Error: Catalogue ID missing.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Setup Toolbar from the new layout
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(catalogueName);
            // Assuming you have a back arrow / menu icon setup in XML or theme
            getSupportActionBar().setDisplayHomeAsUpEnabled(false); // Adjust if you want a back arrow
        }

        db = FirebaseFirestore.getInstance();
        setupRecyclerView();

        // Setup FAB and Empty State Button clicks
        binding.fabAddProduct.setOnClickListener(v -> navigateToAddProduct(null));
        binding.buttonAddFirstProduct.setOnClickListener(v -> navigateToAddProduct(null));

        // Setup Preview Button click (using ID from your new stats card)
        binding.buttonFilter.setOnClickListener(v -> previewCatalogue());

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

    // Handle Toolbar Menu clicks (for Preview if you add it there)
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate your menu (e.g., menu/catalogue_detail_menu.xml with a preview item)
        // getMenuInflater().inflate(R.menu.catalogue_detail_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        // Handle menu item clicks (e.g., Preview)
        // if (item.getItemId() == R.id.action_preview) {
        //     previewCatalogue();
        //     return true;
        // }
        return super.onOptionsItemSelected(item);
    }


    private void setupRecyclerView() {
        productList = new ArrayList<>();
        // Pass 'this' as the listener
        adapter = new ProductAdapter(this, productList, this);
        // Use the correct RecyclerView ID from your new layout
        binding.productsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        binding.productsRecyclerView.setAdapter(adapter);
    }

    private void loadProducts() {
        if (productListener != null) {
            productListener.remove(); // Remove previous listener if any
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

            // Use the new empty state container ID
            binding.emptyStateContainer.setVisibility(productList.isEmpty() ? View.VISIBLE : View.GONE);
            binding.productsRecyclerView.setVisibility(productList.isEmpty() ? View.GONE : View.VISIBLE);
        });
    }

    private void navigateToAddProduct(String productId) {
        Intent intent = new Intent(this, AddProductActivity.class);
        intent.putExtra("CATALOGUE_ID", catalogueId);
        if (productId != null) {
            intent.putExtra("PRODUCT_ID", productId); // Pass ID for editing
        }
        startActivity(intent);
    }

    private void previewCatalogue() {
        // TODO: Implement logic to open the customer-facing web view/app
        // You might generate a URL like: "https://your-web-app.com/catalogue/" + catalogueId
        Toast.makeText(this, "Preview functionality not yet implemented.", Toast.LENGTH_SHORT).show();
        // Example:
        // String previewUrl = "https://your-preview-domain.com/cat/" + catalogueId;
        // Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(previewUrl));
        // startActivity(browserIntent);
    }

    // --- Implementation of ProductAdapter.OnProductActionClickListener ---

    @Override
    public void onEditClick(Product product) {
        navigateToAddProduct(product.getId()); // Pass product ID to edit mode
    }

    @Override
    public void onVisibilityToggleClick(Product product) {
        // Update the product's visibility in Firestore
        DocumentReference productRef = db.collection("products").document(product.getId());
        boolean newVisibility = !product.isVisibleInCatalogue(); // Toggle the current state

        productRef.update("visibleInCatalogue", newVisibility)
                .addOnSuccessListener(aVoid -> {
                    // Firestore listener will automatically update the UI,
                    // but a quick feedback message is good UX.
                    String message = newVisibility ? "Product made visible" : "Product hidden";
                    Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error updating product visibility", e);
                    Toast.makeText(this, "Failed to update visibility", Toast.LENGTH_SHORT).show();
                });
    }

    @Override
    public void onItemClick(Product product) {
        // Navigate to the Seller's Product Detail Screen
        Intent intent = new Intent(this, ProductDetailSellerActivity.class); // Use the correct activity name
        intent.putExtra("PRODUCT_ID", product.getId());
        // You might want to pass the whole product object (make Product Parcelable)
        // or just fetch it again in the detail activity using the ID.
        startActivity(intent);
    }
}