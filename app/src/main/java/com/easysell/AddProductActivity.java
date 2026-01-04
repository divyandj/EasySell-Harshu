package com.easysell;

import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.cloudinary.android.MediaManager;
import com.cloudinary.android.callback.ErrorInfo;
import com.cloudinary.android.callback.UploadCallback;
import com.easysell.databinding.ActivityAddProductBinding;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class AddProductActivity extends AppCompatActivity {

    private static final String TAG = "AddProductActivity";

    private ActivityAddProductBinding binding;
    private FirebaseFirestore db;
    private String catalogueId;

    // Media lists
    private final List<Uri> selectedMediaUris = new ArrayList<>();
    private final List<MediaItem> existingMediaItems = new ArrayList<>();

    // Variant Image Selection
    private ImageView targetVariantImageView;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private String productIdToEdit = null;

    // --- LAUNCHERS ---
    private final ActivityResultLauncher<Intent> mediaPickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    if (result.getData().getClipData() != null) {
                        ClipData clipData = result.getData().getClipData();
                        for (int i = 0; i < clipData.getItemCount(); i++) {
                            selectedMediaUris.add(clipData.getItemAt(i).getUri());
                        }
                    } else if (result.getData().getData() != null) {
                        selectedMediaUris.add(result.getData().getData());
                    }
                    updateMediaPreview();
                }
            }
    );

    private final ActivityResultLauncher<String> variantImagePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null && targetVariantImageView != null) {
                    Glide.with(this).load(uri).into(targetVariantImageView);
                    targetVariantImageView.setTag(uri); // Tag holds Uri for new selections
                    targetVariantImageView = null;
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAddProductBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        db = FirebaseFirestore.getInstance();
        catalogueId = getIntent().getStringExtra("CATALOGUE_ID");
        productIdToEdit = getIntent().getStringExtra("PRODUCT_ID");

        if (catalogueId == null) {
            Toast.makeText(this, "Error: Catalogue ID missing.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        setupUI();

        if (productIdToEdit != null && !productIdToEdit.isEmpty()) {
            setTitle("Edit Product");
            fetchAndPopulateProductData(productIdToEdit);
        } else {
            setTitle("Add New Product");
            // Default view state for new products
            binding.mediaContent.setVisibility(View.VISIBLE);
            binding.mediaArrow.setRotation(180);
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    // --- UI SETUP ---
    private void setupUI() {
        setupExpandableCard(binding.mediaHeader, binding.mediaContent, binding.mediaArrow);
        setupExpandableCard(binding.basicInfoHeader, binding.basicInfoContent, binding.basicInfoArrow);
        setupExpandableCard(binding.pricingHeader, binding.pricingContent, binding.pricingArrow);
        setupExpandableCard(binding.inventoryHeader, binding.inventoryContent, binding.inventoryArrow);
        setupExpandableCard(binding.shippingHeader, binding.shippingContent, binding.shippingArrow);
        setupExpandableCard(binding.customFieldsHeader, binding.customFieldsContent, binding.customFieldsArrow);
        setupExpandableCard(binding.variantsHeader, binding.variantsContent, binding.variantsArrow);

        binding.hasVariantsSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> toggleVariantMode(isChecked));
        toggleVariantMode(false);

        binding.inStockSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            binding.allowBackordersSwitch.setEnabled(!isChecked);
            binding.hideWhenOutOfStockSwitch.setEnabled(!isChecked);
            if (isChecked) {
                binding.allowBackordersSwitch.setChecked(false);
                binding.hideWhenOutOfStockSwitch.setChecked(false);
            }
        });

        setupSpinners();
        setupClickListeners();
        setupDiscountCalculation();
    }

    private void setupClickListeners() {
        binding.buttonSelectMedia.setOnClickListener(v -> openMediaPicker());
        binding.buttonSaveProduct.setOnClickListener(v -> handleSaveProduct());
        binding.buttonAddPriceSlab.setOnClickListener(v -> addPriceSlabRow(null));
        binding.buttonAddCustomField.setOnClickListener(v -> addCustomFieldRow(null, null));
        binding.buttonAddVariantOption.setOnClickListener(v -> addVariantOptionRow());
        binding.buttonGenerateVariants.setOnClickListener(v -> generateAndDisplayVariants());
    }

    private void setupSpinners() {
        ArrayAdapter<CharSequence> priceAdapter = ArrayAdapter.createFromResource(this,
                R.array.price_units_array, android.R.layout.simple_spinner_item);
        priceAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.priceUnitSpinner.setAdapter(priceAdapter);

        ArrayAdapter<CharSequence> weightAdapter = ArrayAdapter.createFromResource(this,
                R.array.weight_units_array, android.R.layout.simple_spinner_item);
        weightAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.weightUnitSpinner.setAdapter(weightAdapter);
    }

    private void setupExpandableCard(View header, View content, ImageView arrow) {
        header.setOnClickListener(v -> {
            boolean isVisible = content.getVisibility() == View.VISIBLE;
            content.setVisibility(isVisible ? View.GONE : View.VISIBLE);
            arrow.animate().rotation(isVisible ? 0 : 180).start();
        });
    }

    private void openMediaPicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"image/*", "video/*"});
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        mediaPickerLauncher.launch(Intent.createChooser(intent, "Select Media"));
    }

    private void updateMediaPreview() {
        binding.mediaPreviewContainer.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);

        // 1. Existing Media (From Firestore)
        for (MediaItem item : existingMediaItems) {
            View previewView = inflater.inflate(R.layout.item_media_preview, binding.mediaPreviewContainer, false);
            ImageView imageView = previewView.findViewById(R.id.preview_image_view);
            View removeButton = previewView.findViewById(R.id.button_remove_media);

            Glide.with(this).load(item.getUrl()).placeholder(R.color.gray_200).into(imageView);
            removeButton.setTag(item);
            removeButton.setOnClickListener(v -> {
                existingMediaItems.remove((MediaItem) v.getTag());
                updateMediaPreview();
            });
            binding.mediaPreviewContainer.addView(previewView);
        }

        // 2. New Media (Local URIs)
        for (Uri uri : selectedMediaUris) {
            View previewView = inflater.inflate(R.layout.item_media_preview, binding.mediaPreviewContainer, false);
            ImageView imageView = previewView.findViewById(R.id.preview_image_view);
            View removeButton = previewView.findViewById(R.id.button_remove_media);

            Glide.with(this).load(uri).placeholder(R.color.gray_200).into(imageView);
            removeButton.setTag(uri);
            removeButton.setOnClickListener(v -> {
                selectedMediaUris.remove((Uri) v.getTag());
                updateMediaPreview();
            });
            binding.mediaPreviewContainer.addView(previewView);
        }
    }

    private void toggleVariantMode(boolean hasVariants) {
        binding.variantsManagementContainer.setVisibility(hasVariants ? View.VISIBLE : View.GONE);
    }

    // --- FORM LOGIC (Pricing, Slabs, Fields) ---

    private void setupDiscountCalculation() {
        TextWatcher discountWatcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                calculateAndShowDiscount();
            }
        };
        binding.productPriceEditText.addTextChangedListener(discountWatcher);
        binding.productDiscountPriceEditText.addTextChangedListener(discountWatcher);
    }

    private void calculateAndShowDiscount() {
        try {
            String priceStr = binding.productPriceEditText.getText().toString();
            String discountStr = binding.productDiscountPriceEditText.getText().toString();

            if (!priceStr.isEmpty() && !discountStr.isEmpty()) {
                double originalPrice = Double.parseDouble(priceStr);
                double discountedPrice = Double.parseDouble(discountStr);

                if (discountedPrice > 0 && originalPrice > 0 && discountedPrice < originalPrice) {
                    double percentage = ((originalPrice - discountedPrice) / originalPrice) * 100;
                    binding.discountPercentageText.setText(String.format(Locale.US, "(%.0f%% OFF)", percentage));
                    binding.discountPercentageText.setVisibility(View.VISIBLE);
                } else {
                    binding.discountPercentageText.setVisibility(View.GONE);
                }
            }
        } catch (NumberFormatException e) {
            binding.discountPercentageText.setVisibility(View.GONE);
        }
    }

    private void addPriceSlabRow(PriceSlab slab) {
        LayoutInflater inflater = LayoutInflater.from(this);
        View rowView = inflater.inflate(R.layout.layout_price_slab_row, binding.bulkPricingContainer, false);
        EditText startQty = rowView.findViewById(R.id.start_qty_edit_text);
        EditText endQty = rowView.findViewById(R.id.end_qty_edit_text);
        EditText slabPrice = rowView.findViewById(R.id.slab_price_edit_text);
        View removeButton = rowView.findViewById(R.id.button_remove_slab);

        if (slab != null) {
            startQty.setText(String.valueOf(slab.getStartQty()));
            endQty.setText(String.valueOf(slab.getEndQty()));
            slabPrice.setText(String.valueOf(slab.getPricePerUnit()));
        }
        removeButton.setOnClickListener(v -> binding.bulkPricingContainer.removeView(rowView));
        binding.bulkPricingContainer.addView(rowView);
    }

    private void addCustomFieldRow(String key, String value) {
        LayoutInflater inflater = LayoutInflater.from(this);
        View rowView = inflater.inflate(R.layout.layout_custom_field_row, binding.customFieldsContainer, false);
        EditText fieldKey = rowView.findViewById(R.id.custom_field_key_edit_text);
        EditText fieldValue = rowView.findViewById(R.id.custom_field_value_edit_text);
        View removeButton = rowView.findViewById(R.id.button_remove_custom_field);

        if (key != null) fieldKey.setText(key);
        if (value != null) fieldValue.setText(value);
        removeButton.setOnClickListener(v -> binding.customFieldsContainer.removeView(rowView));
        binding.customFieldsContainer.addView(rowView);
    }

    // --- VARIANT LOGIC ---
    private void addVariantOptionRow() {
        LayoutInflater inflater = LayoutInflater.from(this);
        View rowView = inflater.inflate(R.layout.layout_variant_option_row, binding.variantOptionsContainer, false);
        View removeButton = rowView.findViewById(R.id.button_remove_option);
        removeButton.setOnClickListener(v -> binding.variantOptionsContainer.removeView(rowView));
        binding.variantOptionsContainer.addView(rowView);
    }

    private void generateAndDisplayVariants() {
        binding.generatedVariantsContainer.removeAllViews();
        Map<String, List<String>> optionsMap = new HashMap<>();
        for (int i = 0; i < binding.variantOptionsContainer.getChildCount(); i++) {
            View rowView = binding.variantOptionsContainer.getChildAt(i);
            EditText optionName = rowView.findViewById(R.id.variant_option_name_edit_text);
            EditText optionValues = rowView.findViewById(R.id.variant_option_values_edit_text);
            String name = optionName.getText().toString().trim();
            String valuesStr = optionValues.getText().toString().trim();
            if (!name.isEmpty() && !valuesStr.isEmpty()) {
                optionsMap.put(name, Arrays.asList(valuesStr.split("\\s*,\\s*")));
            }
        }
        if (optionsMap.isEmpty()) {
            Toast.makeText(this, "Please add at least one variant option and its values.", Toast.LENGTH_SHORT).show();
            return;
        }
        List<Map<String, String>> combinations = new ArrayList<>();
        List<String> optionKeys = new ArrayList<>(optionsMap.keySet());
        generateCombinationsRecursive(optionsMap, optionKeys, 0, new HashMap<>(), combinations);
        for (Map<String, String> combo : combinations) {
            addGeneratedVariantRow(combo);
        }
    }

    private void generateCombinationsRecursive(Map<String, List<String>> options, List<String> keys, int index,
                                               Map<String, String> current, List<Map<String, String>> result) {
        if (index == keys.size()) {
            result.add(new HashMap<>(current));
            return;
        }
        String key = keys.get(index);
        List<String> values = options.get(key);
        for (String value : values) {
            current.put(key, value);
            generateCombinationsRecursive(options, keys, index + 1, current, result);
            current.remove(key);
        }
    }

    private void addGeneratedVariantRow(Map<String, String> combination) {
        LayoutInflater inflater = LayoutInflater.from(this);
        View rowView = inflater.inflate(R.layout.layout_generated_variant_row, binding.generatedVariantsContainer, false);

        TextView name = rowView.findViewById(R.id.variant_name_text);
        ImageView variantImage = rowView.findViewById(R.id.variant_image);
        View selectImageButton = rowView.findViewById(R.id.button_select_variant_image);
        View removeButton = rowView.findViewById(R.id.button_remove_variant);

        StringBuilder comboName = new StringBuilder();
        for (String value : combination.values()) {
            if (comboName.length() > 0) comboName.append(" / ");
            comboName.append(value);
        }
        name.setText(comboName.toString());
        rowView.setTag(combination);

        selectImageButton.setOnClickListener(v -> {
            targetVariantImageView = variantImage;
            variantImagePickerLauncher.launch("image/*");
        });

        removeButton.setOnClickListener(v -> binding.generatedVariantsContainer.removeView(rowView));
        binding.generatedVariantsContainer.addView(rowView);
    }

    // --- SAVE LOGIC ---

    private void handleSaveProduct() {
        String title = binding.productTitleEditText.getText().toString().trim();
        if (!binding.hasVariantsSwitch.isChecked() && binding.productPriceEditText.getText().toString().trim().isEmpty()) {
            Toast.makeText(this, "Product Title and Price are required for a simple product.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (title.isEmpty()) {
            Toast.makeText(this, "Product Title is required.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (existingMediaItems.isEmpty() && selectedMediaUris.isEmpty()) {
            Toast.makeText(this, "Please select at least one main product image.", Toast.LENGTH_SHORT).show();
            return;
        }
        setLoading(true);
        collectDataAndInitiateUpload();
    }

    private void collectDataAndInitiateUpload() {
        GoogleSignInAccount account = SessionManager.getInstance().getAccount();
        String userId = (account != null) ? account.getId() : "";
        if (userId == null || userId.isEmpty()) {
            // Fallback just in case session manager is empty but auth is valid
            if (com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser() != null) {
                userId = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser().getUid();
            } else {
                showError("User session expired. Please sign in again.");
                return;
            }
        }

        Product product = new Product();
        product.setUserId(userId);
        product.setCatalogueId(catalogueId);

        // 1. Basic Info
        product.setTitle(binding.productTitleEditText.getText().toString().trim());
        product.setDescription(binding.productDescriptionEditText.getText().toString().trim());
        product.setSku(binding.productSkuEditText.getText().toString().trim());
        product.setInStock(binding.inStockSwitch.isChecked());
        if (binding.priceUnitSpinner.getSelectedItem() != null) {
            product.setPriceUnit(binding.priceUnitSpinner.getSelectedItem().toString());
        }

        try {
            String priceStr = binding.productPriceEditText.getText().toString().trim();
            product.setPrice(priceStr.isEmpty() ? 0.0 : Double.parseDouble(priceStr));

            String discountStr = binding.productDiscountPriceEditText.getText().toString().trim();
            product.setDiscountedPrice(discountStr.isEmpty() ? 0.0 : Double.parseDouble(discountStr));

            String qtyStr = binding.quantityEditText.getText().toString().trim();
            product.setAvailableQuantity(qtyStr.isEmpty() ? 0 : Integer.parseInt(qtyStr));

            String moqStr = binding.productMoqEditText.getText().toString().trim();
            product.setMinOrderQty(moqStr.isEmpty() ? 1 : Integer.parseInt(moqStr));
        } catch (NumberFormatException e) {
            Log.e(TAG, "Number format error in basic fields");
        }

        String tagsStr = binding.productTagsEditText.getText().toString().trim();
        if (!tagsStr.isEmpty()) {
            product.setTags(Arrays.asList(tagsStr.split("\\s*,\\s*")));
        }

        // 2. Variants
        boolean hasVariants = binding.hasVariantsSwitch.isChecked();
        product.setHasVariants(hasVariants);

        if (hasVariants) {
            Map<String, List<String>> variantOptionsMap = new HashMap<>();
            for (int i = 0; i < binding.variantOptionsContainer.getChildCount(); i++) {
                View row = binding.variantOptionsContainer.getChildAt(i);
                EditText nameEt = row.findViewById(R.id.variant_option_name_edit_text);
                EditText valuesEt = row.findViewById(R.id.variant_option_values_edit_text);
                String name = nameEt.getText().toString().trim();
                String values = valuesEt.getText().toString().trim();
                if (!name.isEmpty() && !values.isEmpty()) {
                    variantOptionsMap.put(name, Arrays.asList(values.split("\\s*,\\s*")));
                }
            }
            product.setVariantOptions(variantOptionsMap);

            List<ProductVariant> generatedVariants = new ArrayList<>();
            for (int i = 0; i < binding.generatedVariantsContainer.getChildCount(); i++) {
                View row = binding.generatedVariantsContainer.getChildAt(i);
                EditText priceModifierEt = row.findViewById(R.id.variant_price_modifier_edit_text);
                EditText skuOverrideEt = row.findViewById(R.id.variant_sku_override_edit_text);
                EditText quantityEt = row.findViewById(R.id.variant_quantity_edit_text);
                ImageView variantImage = row.findViewById(R.id.variant_image);

                ProductVariant variant = new ProductVariant();
                variant.setOptions((Map<String, String>) row.getTag());

                Object imageTag = variantImage.getTag();
                if (imageTag instanceof Uri) {
                    variant.setBarcode(((Uri) imageTag).toString()); // Temporary storage
                } else if (imageTag instanceof String) {
                    variant.setImageUrl((String) imageTag); // Existing URL
                }

                try {
                    String pmStr = priceModifierEt.getText().toString().trim();
                    variant.setPriceModifier(pmStr.isEmpty() ? 0.0 : Double.parseDouble(pmStr));

                    variant.setSkuOverride(skuOverrideEt.getText().toString().trim());

                    String vQtyStr = quantityEt.getText().toString().trim();
                    int vQty = vQtyStr.isEmpty() ? 0 : Integer.parseInt(vQtyStr);
                    variant.setQuantity(vQty);
                    variant.setInStock(vQty > 0);
                } catch (Exception e) { continue; }
                generatedVariants.add(variant);
            }
            product.setVariants(generatedVariants);
        }

        // 3. Other Data
        product.setAllowBackorders(binding.allowBackordersSwitch.isChecked());
        product.setHideWhenOutOfStock(binding.hideWhenOutOfStockSwitch.isChecked());

        try {
            String taxStr = binding.taxRateEditText.getText().toString().trim();
            product.setTaxRate(taxStr.isEmpty() ? 0.0 : Double.parseDouble(taxStr));

            String wStr = binding.weightEditText.getText().toString().trim();
            product.setWeight(wStr.isEmpty() ? 0.0 : Double.parseDouble(wStr));
        } catch (Exception e) {}

        if (binding.weightUnitSpinner.getSelectedItem() != null) {
            product.setWeightUnit(binding.weightUnitSpinner.getSelectedItem().toString());
        }

        List<PriceSlab> bulkDiscounts = new ArrayList<>();
        for (int i = 0; i < binding.bulkPricingContainer.getChildCount(); i++) {
            View rowView = binding.bulkPricingContainer.getChildAt(i);
            EditText startQty = rowView.findViewById(R.id.start_qty_edit_text);
            EditText endQty = rowView.findViewById(R.id.end_qty_edit_text);
            EditText slabPrice = rowView.findViewById(R.id.slab_price_edit_text);
            try {
                int start = Integer.parseInt(startQty.getText().toString());
                int end = Integer.parseInt(endQty.getText().toString());
                double price = Double.parseDouble(slabPrice.getText().toString());
                if (start > 0 && end > 0 && price > 0) {
                    bulkDiscounts.add(new PriceSlab(start, end, price));
                }
            } catch (Exception e) {}
        }
        product.setBulkDiscounts(bulkDiscounts);

        Map<String, String> customFields = new HashMap<>();
        for (int i = 0; i < binding.customFieldsContainer.getChildCount(); i++) {
            View rowView = binding.customFieldsContainer.getChildAt(i);
            EditText fieldKey = rowView.findViewById(R.id.custom_field_key_edit_text);
            EditText fieldValue = rowView.findViewById(R.id.custom_field_value_edit_text);
            String key = fieldKey.getText().toString().trim();
            String value = fieldValue.getText().toString().trim();
            if (!key.isEmpty()) customFields.put(key, value);
        }
        product.setCustomFields(customFields);

        // 4. Start Upload
        uploadAllMediaAndSaveProduct(product);
    }

    // --- CLOUDINARY UPLOAD LOGIC ---

    private void uploadAllMediaAndSaveProduct(Product product) {
        Map<Uri, Object> uploadsToPerform = new HashMap<>();

        // 1. Queue Main Product Media
        for (Uri uri : selectedMediaUris) {
            uploadsToPerform.put(uri, "main_media");
        }

        // 2. Queue Variant Images
        if (product.isHasVariants() && product.getVariants() != null) {
            for (ProductVariant variant : product.getVariants()) {
                if (variant.getBarcode() != null && variant.getBarcode().startsWith("content://")) {
                    Uri variantUri = Uri.parse(variant.getBarcode());
                    uploadsToPerform.put(variantUri, variant);
                    variant.setBarcode(null); // Clear temp
                }
            }
        }

        if (uploadsToPerform.isEmpty()) {
            product.setMedia(new ArrayList<>(existingMediaItems));
            saveProductToFirestore(product);
            return;
        }

        // 3. Upload Queue
        List<MediaItem> newlyUploadedMedia = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger uploadCounter = new AtomicInteger(uploadsToPerform.size());
        final boolean[] errorOccurred = {false};

        for (Map.Entry<Uri, Object> entry : uploadsToPerform.entrySet()) {
            Uri uriToUpload = entry.getKey();
            Object target = entry.getValue();

            uploadSingleFile(uriToUpload, new MediaUploadCallback() {
                @Override
                public void onSuccess(MediaItem mediaItem) {
                    if (target.equals("main_media")) {
                        newlyUploadedMedia.add(mediaItem);
                    } else if (target instanceof ProductVariant) {
                        ((ProductVariant) target).setImageUrl(mediaItem.getUrl());
                    }
                    checkAndFinalize();
                }

                @Override
                public void onFailure(String message) {
                    if (!errorOccurred[0]) {
                        errorOccurred[0] = true;
                        showError("Upload failed: " + message);
                    }
                }

                private void checkAndFinalize() {
                    if (uploadCounter.decrementAndGet() == 0 && !errorOccurred[0]) {
                        List<MediaItem> finalMediaList = new ArrayList<>(existingMediaItems);
                        finalMediaList.addAll(newlyUploadedMedia);
                        product.setMedia(finalMediaList);
                        saveProductToFirestore(product);
                    }
                }
            });
        }
    }

    // NEW CLOUDINARY UPLOAD METHOD
    private void uploadSingleFile(final Uri uri, final MediaUploadCallback callback) {
        MediaManager.get().upload(uri)
                .unsigned("easysell_preset") // Using your preset name
                .option("resource_type", "auto") // Detects image vs video
                .callback(new UploadCallback() {
                    @Override
                    public void onStart(String requestId) {}

                    @Override
                    public void onProgress(String requestId, long bytes, long totalBytes) {}

                    @Override
                    public void onSuccess(String requestId, Map resultData) {
                        String url = (String) resultData.get("secure_url"); // Use HTTPS url
                        String type = (String) resultData.get("resource_type");
                        MediaItem item = new MediaItem(url, type);
                        callback.onSuccess(item);
                    }

                    @Override
                    public void onError(String requestId, ErrorInfo error) {
                        callback.onFailure(error.getDescription());
                    }

                    @Override
                    public void onReschedule(String requestId, ErrorInfo error) {}
                })
                .dispatch();
    }

    private void saveProductToFirestore(Product product) {
        if (productIdToEdit != null) {
            db.collection("products").document(productIdToEdit).set(product)
                    .addOnSuccessListener(aVoid -> handler.post(() -> {
                        Toast.makeText(this, "Product Updated!", Toast.LENGTH_SHORT).show();
                        finish();
                    }))
                    .addOnFailureListener(e -> showError("Failed to update: " + e.getMessage()));
        } else {
            db.collection("products").add(product)
                    .addOnSuccessListener(doc -> handler.post(() -> {
                        Toast.makeText(this, "Product Saved!", Toast.LENGTH_SHORT).show();
                        finish();
                    }))
                    .addOnFailureListener(e -> showError("Failed to save: " + e.getMessage()));
        }
    }

    // --- HELPER METHODS ---

    interface MediaUploadCallback {
        void onSuccess(MediaItem mediaItem);
        void onFailure(String message);
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
            binding.buttonSaveProduct.setVisibility(isLoading ? View.INVISIBLE : View.VISIBLE);
        });
    }

    // --- POPULATE FOR EDIT ---
    private void fetchAndPopulateProductData(String productId) {
        setLoading(true);
        db.collection("products").document(productId).get()
                .addOnSuccessListener(documentSnapshot -> {
                    setLoading(false);
                    if (documentSnapshot.exists()) {
                        Product product = documentSnapshot.toObject(Product.class);
                        if (product != null) {
                            product.setId(documentSnapshot.getId());
                            populateForm(product);
                        }
                    } else {
                        showError("Product not found.");
                        finish();
                    }
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    showError("Error: " + e.getMessage());
                    finish();
                });
    }

    private void populateForm(Product product) {
        binding.productTitleEditText.setText(product.getTitle());
        binding.productDescriptionEditText.setText(product.getDescription());
        binding.productSkuEditText.setText(product.getSku());
        binding.productMoqEditText.setText(String.valueOf(product.getMinOrderQty()));
        if (product.getTags() != null) {
            binding.productTagsEditText.setText(String.join(", ", product.getTags()));
        }

        binding.productPriceEditText.setText(String.format(Locale.US, "%.2f", product.getPrice()));
        binding.productDiscountPriceEditText.setText(String.format(Locale.US, "%.2f", product.getDiscountedPrice()));

        if (product.getPriceUnit() != null) {
            ArrayAdapter<CharSequence> adapter = (ArrayAdapter<CharSequence>) binding.priceUnitSpinner.getAdapter();
            for (int i = 0; i < adapter.getCount(); i++) {
                if (product.getPriceUnit().equals(adapter.getItem(i).toString())) {
                    binding.priceUnitSpinner.setSelection(i);
                    break;
                }
            }
        }

        binding.bulkPricingContainer.removeAllViews();
        if (product.getBulkDiscounts() != null) {
            for (PriceSlab slab : product.getBulkDiscounts()) {
                addPriceSlabRow(slab);
            }
        }

        binding.inStockSwitch.setChecked(product.isInStock());
        binding.quantityEditText.setText(String.valueOf(product.getAvailableQuantity()));
        binding.allowBackordersSwitch.setChecked(product.isAllowBackorders());
        binding.hideWhenOutOfStockSwitch.setChecked(product.isHideWhenOutOfStock());
        binding.allowBackordersSwitch.setEnabled(!product.isInStock());
        binding.hideWhenOutOfStockSwitch.setEnabled(!product.isInStock());

        binding.taxRateEditText.setText(String.format(Locale.US, "%.1f", product.getTaxRate()));
        binding.weightEditText.setText(String.format(Locale.US, "%.2f", product.getWeight()));

        if (product.getWeightUnit() != null) {
            ArrayAdapter<CharSequence> adapter = (ArrayAdapter<CharSequence>) binding.weightUnitSpinner.getAdapter();
            for (int i = 0; i < adapter.getCount(); i++) {
                if (product.getWeightUnit().equals(adapter.getItem(i).toString())) {
                    binding.weightUnitSpinner.setSelection(i);
                    break;
                }
            }
        }

        binding.customFieldsContainer.removeAllViews();
        if (product.getCustomFields() != null) {
            for (Map.Entry<String, String> entry : product.getCustomFields().entrySet()) {
                addCustomFieldRow(entry.getKey(), entry.getValue());
            }
        }

        binding.hasVariantsSwitch.setChecked(product.isHasVariants());
        toggleVariantMode(product.isHasVariants());

        if (product.isHasVariants()) {
            binding.variantOptionsContainer.removeAllViews();
            if (product.getVariantOptions() != null) {
                for (Map.Entry<String, List<String>> entry : product.getVariantOptions().entrySet()) {
                    addVariantOptionRowWithData(entry.getKey(), String.join(",", entry.getValue()));
                }
            }
            generateAndDisplayVariantsForEdit(product.getVariants());
        }

        existingMediaItems.clear();
        selectedMediaUris.clear();
        if (product.getMedia() != null) {
            existingMediaItems.addAll(product.getMedia());
        }
        updateMediaPreview();
    }

    private void addVariantOptionRowWithData(String name, String values) {
        LayoutInflater inflater = LayoutInflater.from(this);
        View rowView = inflater.inflate(R.layout.layout_variant_option_row, binding.variantOptionsContainer, false);
        EditText nameEt = rowView.findViewById(R.id.variant_option_name_edit_text);
        EditText valuesEt = rowView.findViewById(R.id.variant_option_values_edit_text);
        View removeButton = rowView.findViewById(R.id.button_remove_option);

        nameEt.setText(name);
        valuesEt.setText(values);

        removeButton.setOnClickListener(v -> binding.variantOptionsContainer.removeView(rowView));
        binding.variantOptionsContainer.addView(rowView);
    }

    private void generateAndDisplayVariantsForEdit(List<ProductVariant> existingVariants) {
        binding.generatedVariantsContainer.removeAllViews();
        if (existingVariants == null) return;

        for (ProductVariant variant : existingVariants) {
            Map<String, String> combination = variant.getOptions();
            if (combination == null) continue;

            LayoutInflater inflater = LayoutInflater.from(this);
            View rowView = inflater.inflate(R.layout.layout_generated_variant_row, binding.generatedVariantsContainer, false);

            TextView name = rowView.findViewById(R.id.variant_name_text);
            ImageView variantImage = rowView.findViewById(R.id.variant_image);
            View selectImageButton = rowView.findViewById(R.id.button_select_variant_image);
            View removeButton = rowView.findViewById(R.id.button_remove_variant);
            EditText priceModifierEt = rowView.findViewById(R.id.variant_price_modifier_edit_text);
            EditText skuOverrideEt = rowView.findViewById(R.id.variant_sku_override_edit_text);
            EditText quantityEt = rowView.findViewById(R.id.variant_quantity_edit_text);

            StringBuilder comboName = new StringBuilder();
            for (String value : combination.values()) {
                if (comboName.length() > 0) comboName.append(" / ");
                comboName.append(value);
            }
            name.setText(comboName.toString());

            priceModifierEt.setText(String.format(Locale.US, "%.2f", variant.getPriceModifier()));
            skuOverrideEt.setText(variant.getSkuOverride());
            quantityEt.setText(String.valueOf(variant.getQuantity()));

            if (variant.getImageUrl() != null && !variant.getImageUrl().isEmpty()) {
                Glide.with(this).load(variant.getImageUrl()).placeholder(R.drawable.ic_add_photo).into(variantImage);
                variantImage.setTag(variant.getImageUrl());
            }

            rowView.setTag(combination);
            selectImageButton.setOnClickListener(v -> {
                targetVariantImageView = variantImage;
                variantImagePickerLauncher.launch("image/*");
            });
            removeButton.setOnClickListener(v -> binding.generatedVariantsContainer.removeView(rowView));
            binding.generatedVariantsContainer.addView(rowView);
        }
    }
}