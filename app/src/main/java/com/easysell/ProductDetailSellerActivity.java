package com.easysell;

import android.content.Intent;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.easysell.databinding.ActivityProductDetailSellerBinding; // Use ViewBinding
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import com.google.android.material.tabs.TabLayoutMediator;

public class ProductDetailSellerActivity extends AppCompatActivity {

    private static final String TAG = "ProductDetailSeller";
    private ActivityProductDetailSellerBinding binding;
    private FirebaseFirestore db;
    private String productId;
    private Product currentProduct; // Store the fetched product

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityProductDetailSellerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }

        productId = getIntent().getStringExtra("PRODUCT_ID");
        if (productId == null || productId.isEmpty()) {
            Toast.makeText(this, "Error: Product ID missing.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        db = FirebaseFirestore.getInstance();
        loadProductDetails();
        setupVisibilitySwitchListener();
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.product_detail_seller_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.action_edit_product) {
            navigateToEditProduct();
            return true;
        } else if (itemId == R.id.action_delete_product) {
            confirmAndDeleteProduct();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void loadProductDetails() {
        DocumentReference productRef = db.collection("products").document(productId);
        // Use addSnapshotListener for real-time updates if needed, or get() for one-time fetch
        productRef.addSnapshotListener(this, (snapshot, error) -> {
            if (error != null) {
                Log.w(TAG, "Listen failed.", error);
                Toast.makeText(this, "Error loading product details.", Toast.LENGTH_SHORT).show();
                return;
            }

            if (snapshot != null && snapshot.exists()) {
                currentProduct = snapshot.toObject(Product.class);
                if (currentProduct != null) {
                    currentProduct.setId(snapshot.getId()); // Store the document ID
                    populateUI(currentProduct);
                } else {
                    Toast.makeText(this, "Failed to parse product data.", Toast.LENGTH_SHORT).show();
                }
            } else {
                Log.d(TAG, "Current data: null");
                Toast.makeText(this, "Product not found.", Toast.LENGTH_SHORT).show();
                finish(); // Close activity if product doesn't exist
            }
        });
    }

    private void populateUI(Product product) {
        if (product == null) return; // Safety check

        // Toolbar Title
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(product.getTitle());
        }

        // Visibility Switch (remove listener temporarily to set state)
        binding.visibilitySwitch.setOnCheckedChangeListener(null);
        binding.visibilitySwitch.setChecked(product.isVisibleInCatalogue());
        setupVisibilitySwitchListener(); // Re-attach listener

        // Media
        if (product.getMedia() != null && !product.getMedia().isEmpty()) {
            MediaPagerAdapter pagerAdapter = new MediaPagerAdapter(this, product.getMedia());
            binding.mediaViewPager.setAdapter(pagerAdapter);

            // Connect TabLayout (dots) to ViewPager2
            new TabLayoutMediator(binding.mediaTabIndicator, binding.mediaViewPager,
                    (tab, position) -> {
                        // No text needed for dots
                    }
            ).attach();
            binding.mediaTabIndicator.setVisibility(product.getMedia().size() > 1 ? View.VISIBLE : View.GONE); // Show dots only if multiple items
        } else {
            // Handle case with no media (e.g., hide ViewPager and indicator)
            binding.mediaViewPager.setVisibility(View.GONE);
            binding.mediaTabIndicator.setVisibility(View.GONE);
            // Optionally show a placeholder in a different view if needed
        }

        // Basic Info
        binding.productTitleDetail.setText(product.getTitle());
        binding.productDescriptionDetail.setText(product.getDescription() != null ? product.getDescription() : "N/A");
        binding.productSkuDetail.setText(product.getSku() != null && !product.getSku().isEmpty() ? product.getSku() : "N/A");
        binding.productTagsDetail.setText(product.getTags() != null ? String.join(", ", product.getTags()) : "N/A");

        // Pricing Info
        NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("en", "IN")); // For ₹ format
        binding.productPriceDetail.setText(currencyFormat.format(product.getPrice()));
        binding.productPriceUnitDetail.setText(product.getPriceUnit() != null ? product.getPriceUnit() : "");

        boolean isOnSale = product.getDiscountedPrice() > 0 && product.getDiscountedPrice() < product.getPrice();
        binding.discountedPriceLabel.setVisibility(isOnSale ? View.VISIBLE : View.GONE);
        binding.productDiscountedPriceDetail.setVisibility(isOnSale ? View.VISIBLE : View.GONE);
        binding.productPriceDetail.setPaintFlags(isOnSale ? (binding.productPriceDetail.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG) : (binding.productPriceDetail.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG)));
        binding.productPriceDetail.setTextColor(ContextCompat.getColor(this, isOnSale ? R.color.gray_500 : R.color.primary)); // Dim if on sale, highlight otherwise

        if (isOnSale) {
            double percentage = ((product.getPrice() - product.getDiscountedPrice()) / product.getPrice()) * 100;
            String discountText = String.format(Locale.getDefault(), "%s (%.0f%% OFF)",
                    currencyFormat.format(product.getDiscountedPrice()), percentage);
            binding.productDiscountedPriceDetail.setText(discountText);
        }

        // Inventory Info
        boolean hasVariants = product.isHasVariants(); // Check if product uses variants
        binding.moqDetailLabel.setVisibility(hasVariants ? View.GONE : View.VISIBLE); // Use binding here
        binding.moqDetail.setVisibility(hasVariants ? View.GONE : View.VISIBLE);
        binding.stockStatusDetail.setVisibility(hasVariants ? View.GONE : View.VISIBLE); // Hide base stock if variants exist

        if(!hasVariants) {
            binding.stockStatusDetail.setText(product.isInStock() ?
                    String.format(Locale.getDefault(), "In Stock (%d available)", product.getAvailableQuantity()) : "Out of Stock");
            binding.moqDetail.setText(String.valueOf(product.getMinOrderQty()));
        }
        binding.backorderDetail.setText(String.format("Allow backorders: %s", product.isAllowBackorders() ? "Yes" : "No"));
        binding.hideOosDetail.setText(String.format("Hide when OOS: %s", product.isHideWhenOutOfStock() ? "Yes" : "No"));


        // Variants
        if (hasVariants && product.getVariants() != null && !product.getVariants().isEmpty()) {
            binding.variantsDetailCard.setVisibility(View.VISIBLE);
            populateVariants(product.getVariants());
        } else {
            binding.variantsDetailCard.setVisibility(View.GONE);
        }

        // Shipping & Taxes
        binding.weightDetail.setText(String.format(Locale.getDefault(), "%.2f %s", product.getWeight(), product.getWeightUnit() != null ? product.getWeightUnit() : ""));
        binding.taxRateDetail.setText(String.format(Locale.getDefault(), "%.1f%%", product.getTaxRate()));

        // Custom Fields
        if (product.getCustomFields() != null && !product.getCustomFields().isEmpty()) {
            binding.customFieldsDetailCard.setVisibility(View.VISIBLE);
            populateCustomFields(product.getCustomFields());
        } else {
            binding.customFieldsDetailCard.setVisibility(View.GONE);
        }
    }

    private void populateVariants(List<ProductVariant> variants) {
        binding.variantsDetailContainer.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));

        for (ProductVariant variant : variants) {
            // Inflate a simple layout just for displaying variant details
            LinearLayout variantLayout = new LinearLayout(this); // You could inflate a dedicated XML here
            variantLayout.setOrientation(LinearLayout.VERTICAL);
            variantLayout.setPadding(0, 0, 0, getResources().getDimensionPixelSize(R.dimen.spacing_md));

            TextView nameView = new TextView(this);
            StringBuilder nameBuilder = new StringBuilder();
            if (variant.getOptions() != null) {
                for (String value : variant.getOptions().values()) {
                    if (nameBuilder.length() > 0) nameBuilder.append(" / ");
                    nameBuilder.append(value);
                }
            }
            nameView.setText(nameBuilder.toString());
            // --- Corrected Style Reference ---
            nameView.setTextAppearance(this, com.google.android.material.R.style.TextAppearance_Material3_BodyMedium);
            variantLayout.addView(nameView);

            TextView priceView = new TextView(this);
            // Show price adjustment relative to base price
            priceView.setText(String.format("Price Adj.: %s%s", variant.getPriceModifier() >= 0 ? "+" : "", currencyFormat.format(variant.getPriceModifier())));
            // --- Corrected Style Reference ---
            priceView.setTextAppearance(this, com.google.android.material.R.style.TextAppearance_Material3_BodySmall);
            priceView.setTextColor(ContextCompat.getColor(this, R.color.gray_600));
            variantLayout.addView(priceView);

            TextView skuView = new TextView(this);
            skuView.setText(String.format("SKU: %s", variant.getSkuOverride() != null && !variant.getSkuOverride().isEmpty() ? variant.getSkuOverride() : "N/A"));
            // --- Corrected Style Reference ---
            skuView.setTextAppearance(this, com.google.android.material.R.style.TextAppearance_Material3_BodySmall);
            skuView.setTextColor(ContextCompat.getColor(this, R.color.gray_600));
            variantLayout.addView(skuView);

            TextView stockView = new TextView(this);
            stockView.setText(String.format("Stock: %s (%d)", variant.isInStock() ? "In Stock" : "Out of Stock", variant.getQuantity()));
            // --- Corrected Style Reference ---
            stockView.setTextAppearance(this, com.google.android.material.R.style.TextAppearance_Material3_BodySmall);
            stockView.setTextColor(ContextCompat.getColor(this, R.color.gray_600));
            variantLayout.addView(stockView);

            // Add a simple divider between variants
            View divider = new View(this);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1);
            params.setMargins(0, getResources().getDimensionPixelSize(R.dimen.spacing_sm), 0, getResources().getDimensionPixelSize(R.dimen.spacing_sm));
            divider.setLayoutParams(params);
            divider.setBackgroundColor(ContextCompat.getColor(this, R.color.divider_light)); // Use your divider color
            variantLayout.addView(divider);

            binding.variantsDetailContainer.addView(variantLayout);
        }
    }

    private void populateCustomFields(Map<String, String> customFields) {
        binding.customFieldsDetailContainer.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);

        for (Map.Entry<String, String> entry : customFields.entrySet()) {
            LinearLayout fieldLayout = new LinearLayout(this);
            fieldLayout.setOrientation(LinearLayout.HORIZONTAL);
            fieldLayout.setPadding(0, 0, 0, getResources().getDimensionPixelSize(R.dimen.spacing_xs));

            TextView keyView = new TextView(this);
            keyView.setText(String.format("%s:", entry.getKey()));
            keyView.setTextAppearance(this, com.google.android.material.R.style.TextAppearance_Material3_LabelMedium); // Corrected style ref
            keyView.setTextColor(ContextCompat.getColor(this, R.color.gray_600));
            keyView.setPadding(0,0,getResources().getDimensionPixelSize(R.dimen.spacing_sm),0);
            fieldLayout.addView(keyView);

            TextView valueView = new TextView(this);
            valueView.setText(entry.getValue());
            valueView.setTextAppearance(this, com.google.android.material.R.style.TextAppearance_Material3_BodyMedium); // Corrected style ref
            fieldLayout.addView(valueView);

            binding.customFieldsDetailContainer.addView(fieldLayout);
        }
    }

    private void setupVisibilitySwitchListener() {
        binding.visibilitySwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (currentProduct == null || !buttonView.isPressed()) return; // Prevent triggering when setting initial state
            updateProductVisibility(isChecked);
        });
    }

    private void updateProductVisibility(boolean isVisible) {
        DocumentReference productRef = db.collection("products").document(productId);
        productRef.update("visibleInCatalogue", isVisible)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, isVisible ? "Product visible" : "Product hidden", Toast.LENGTH_SHORT).show();
                    if (currentProduct != null) currentProduct.setVisibleInCatalogue(isVisible);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error updating visibility", e);
                    Toast.makeText(this, "Failed to update visibility", Toast.LENGTH_SHORT).show();
                    // Revert switch state
                    binding.visibilitySwitch.setOnCheckedChangeListener(null);
                    binding.visibilitySwitch.setChecked(!isVisible);
                    setupVisibilitySwitchListener();
                });
    }

    private void navigateToEditProduct() {
        if (currentProduct == null) return;
        Intent intent = new Intent(this, AddProductActivity.class);
        intent.putExtra("CATALOGUE_ID", currentProduct.getCatalogueId());
        intent.putExtra("PRODUCT_ID", productId); // Pass ID to indicate edit mode
        startActivity(intent);
        // Note: AddProductActivity needs to be updated to handle receiving a PRODUCT_ID,
        // fetching the product data, and pre-filling the form in 'edit mode'.
    }

    private void confirmAndDeleteProduct() {
        if (currentProduct == null) return;
        new AlertDialog.Builder(this)
                .setTitle("Delete Product")
                .setMessage("Are you sure you want to delete '" + currentProduct.getTitle() + "'? This action cannot be undone.")
                .setPositiveButton("Delete", (dialog, which) -> deleteProduct())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteProduct() {
        DocumentReference productRef = db.collection("products").document(productId);
        productRef.delete()
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Product deleted", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error deleting product", e);
                    Toast.makeText(this, "Failed to delete product", Toast.LENGTH_SHORT).show();
                });
    }
}