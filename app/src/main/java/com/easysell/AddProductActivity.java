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
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.easysell.databinding.ActivityAddProductBinding;
import com.easysell.network.RetrofitClient;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.firebase.firestore.FirebaseFirestore;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AddProductActivity extends AppCompatActivity {

    //region Member Variables
    private static final String TAG = "AddProductActivity";

    private ActivityAddProductBinding binding;
    private FirebaseFirestore db;
    private String accessToken;
    private String catalogueId;
    private final List<Uri> selectedMediaUris = new ArrayList<>();

    private final List<MediaItem> existingMediaItems = new ArrayList<>();

    // A variable to keep track of which variant's image is being selected
    private ImageView targetVariantImageView;

    private final Executor executor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private String productIdToEdit = null;
    // Launcher for the MAIN product media
    private final ActivityResultLauncher<Intent> mediaPickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    selectedMediaUris.clear();
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

    // A separate launcher just for picking a single variant image
    private final ActivityResultLauncher<String> variantImagePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null && targetVariantImageView != null) {
                    Glide.with(this).load(uri).into(targetVariantImageView);
                    // Store the Uri in the ImageView's tag so we can retrieve it later
                    targetVariantImageView.setTag(uri);
                    targetVariantImageView = null; // Reset the target
                }
            }
    );
    //endregion

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAddProductBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }

        db = FirebaseFirestore.getInstance();
        accessToken = SessionManager.getInstance().getAccessToken();
        catalogueId = getIntent().getStringExtra("CATALOGUE_ID");
        productIdToEdit = getIntent().getStringExtra("PRODUCT_ID"); // Check if we received a Product ID

        if (catalogueId == null || accessToken == null) {
            Toast.makeText(this, "Error: Critical data missing. Please restart.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        setupUI();
        if (productIdToEdit != null && !productIdToEdit.isEmpty()) {
            setTitle("Edit Product"); // Change toolbar title
            fetchAndPopulateProductData(productIdToEdit);
        } else {
            setTitle("Add New Product");
            // Optionally open the first card by default only for new products
            binding.mediaContent.setVisibility(View.VISIBLE);
            binding.mediaArrow.setRotation(180);
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    //region UI Setup and Listeners
    private void setupUI() {
        // Setup for expandable cards
        setupExpandableCard(binding.mediaHeader, binding.mediaContent, binding.mediaArrow);
        setupExpandableCard(binding.basicInfoHeader, binding.basicInfoContent, binding.basicInfoArrow);
        setupExpandableCard(binding.pricingHeader, binding.pricingContent, binding.pricingArrow);
        setupExpandableCard(binding.inventoryHeader, binding.inventoryContent, binding.inventoryArrow);
        setupExpandableCard(binding.shippingHeader, binding.shippingContent, binding.shippingArrow);
        setupExpandableCard(binding.customFieldsHeader, binding.customFieldsContent, binding.customFieldsArrow);
        setupExpandableCard(binding.variantsHeader, binding.variantsContent, binding.variantsArrow);

        // Listener for the main variants switch
        binding.hasVariantsSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> toggleVariantMode(isChecked));

        // Set the initial UI state (simple product mode)
        toggleVariantMode(false);

        // Listener for the simple product "In Stock" switch
        binding.inStockSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            binding.allowBackordersSwitch.setEnabled(!isChecked);
            binding.hideWhenOutOfStockSwitch.setEnabled(!isChecked);
            if (isChecked) {
                binding.allowBackordersSwitch.setChecked(false);
                binding.hideWhenOutOfStockSwitch.setChecked(false);
            }
        });

        // Open the first card by default for better UX
        binding.mediaContent.setVisibility(View.VISIBLE);
        binding.mediaArrow.setRotation(180);

        // Setup spinners and button clicks
        setupSpinners();
        setupClickListeners();
        setupDiscountCalculation();
    }

    // ADD this entire method to your class
    private void setupClickListeners() {
        binding.buttonSelectMedia.setOnClickListener(v -> openMediaPicker());
        binding.buttonSaveProduct.setOnClickListener(v -> handleSaveProduct());
        binding.buttonAddPriceSlab.setOnClickListener(v -> addPriceSlabRow(null));
        binding.buttonAddCustomField.setOnClickListener(v -> addCustomFieldRow(null, null));
        binding.buttonAddVariantOption.setOnClickListener(v -> addVariantOptionRow());
        binding.buttonGenerateVariants.setOnClickListener(v -> generateAndDisplayVariants());
    }

    private void setupSpinners() {
        // For Price Unit
        ArrayAdapter<CharSequence> priceAdapter = ArrayAdapter.createFromResource(this,
                R.array.price_units_array, android.R.layout.simple_spinner_item);
        priceAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.priceUnitSpinner.setAdapter(priceAdapter);

        // For Weight Unit
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

    // In AddProductActivity.java
// REPLACE this entire method

    private void updateMediaPreview() {
        binding.mediaPreviewContainer.removeAllViews(); // Clear the existing previews
        LayoutInflater inflater = LayoutInflater.from(this);

        // --- 1. Display Existing Media (from Firestore, tracked in existingMediaItems) ---
        for (MediaItem item : existingMediaItems) {
            // Inflate the new preview layout
            View previewView = inflater.inflate(R.layout.item_media_preview, binding.mediaPreviewContainer, false);
            ImageView imageView = previewView.findViewById(R.id.preview_image_view);
            View removeButton = previewView.findViewById(R.id.button_remove_media);

            // Load the image using Glide
            Glide.with(this).load(item.getUrl()).placeholder(R.color.gray_200).into(imageView);

            // Store the MediaItem object in the button's tag
            removeButton.setTag(item);
            removeButton.setOnClickListener(v -> {
                MediaItem itemToRemove = (MediaItem) v.getTag();
                existingMediaItems.remove(itemToRemove); // Remove from the list of existing items
                updateMediaPreview(); // Refresh the entire preview area
                // Note: Actual file deletion from Drive isn't handled here yet.
            });
            binding.mediaPreviewContainer.addView(previewView); // Add the preview to the layout
        }

        // --- 2. Display Newly Selected Media (from gallery, tracked in selectedMediaUris) ---
        for (Uri uri : selectedMediaUris) {
            // Inflate the new preview layout
            View previewView = inflater.inflate(R.layout.item_media_preview, binding.mediaPreviewContainer, false);
            ImageView imageView = previewView.findViewById(R.id.preview_image_view);
            View removeButton = previewView.findViewById(R.id.button_remove_media);

            // Load the image using Glide from the local Uri
            Glide.with(this).load(uri).placeholder(R.color.gray_200).into(imageView);

            // Store the Uri object in the button's tag
            removeButton.setTag(uri);
            removeButton.setOnClickListener(v -> {
                Uri uriToRemove = (Uri) v.getTag();
                selectedMediaUris.remove(uriToRemove); // Remove from the list of newly selected items
                updateMediaPreview(); // Refresh the entire preview area
            });
            binding.mediaPreviewContainer.addView(previewView); // Add the preview to the layout
        }
    }
    //endregion

    //region Dynamic UI Logic
    // REPLACE this entire method
    // REPLACE this entire method
    private void toggleVariantMode(boolean hasVariants) {
        // This now ONLY toggles the visibility of the variant management UI.
        // The main pricing and inventory cards will ALWAYS remain visible.
        binding.variantsManagementContainer.setVisibility(hasVariants ? View.VISIBLE : View.GONE);
    }

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
            double originalPrice = Double.parseDouble(binding.productPriceEditText.getText().toString());
            double discountedPrice = Double.parseDouble(binding.productDiscountPriceEditText.getText().toString());

            if (discountedPrice > 0 && originalPrice > 0 && discountedPrice < originalPrice) {
                double percentage = ((originalPrice - discountedPrice) / originalPrice) * 100;
                binding.discountPercentageText.setText(String.format(Locale.US, "(%.0f%% OFF)", percentage));
                binding.discountPercentageText.setVisibility(View.VISIBLE);
            } else {
                binding.discountPercentageText.setVisibility(View.GONE);
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
    //endregion

    //region Variant UI Logic
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

    // REPLACE this entire method
    private void addGeneratedVariantRow(Map<String, String> combination) {
        LayoutInflater inflater = LayoutInflater.from(this);
        // Inflates the new layout with "Price Modifier" etc.
        View rowView = inflater.inflate(R.layout.layout_generated_variant_row, binding.generatedVariantsContainer, false);

        TextView name = rowView.findViewById(R.id.variant_name_text);
        ImageView variantImage = rowView.findViewById(R.id.variant_image);
        View selectImageButton = rowView.findViewById(R.id.button_select_variant_image);
        View removeButton = rowView.findViewById(R.id.button_remove_variant);

        // Build and set the variant name (e.g., "Red / Large")
        StringBuilder comboName = new StringBuilder();
        for (String value : combination.values()) {
            if (comboName.length() > 0) comboName.append(" / ");
            comboName.append(value);
        }
        name.setText(comboName.toString());

        // Store the combination data in the view's tag for later retrieval
        rowView.setTag(combination);

        // Set listener for this row's image selection
        selectImageButton.setOnClickListener(v -> {
            targetVariantImageView = variantImage; // Set the target
            variantImagePickerLauncher.launch("image/*"); // Launch the picker
        });

        removeButton.setOnClickListener(v -> binding.generatedVariantsContainer.removeView(rowView));

        binding.generatedVariantsContainer.addView(rowView);
    }

    //region Save Logic
    private void handleSaveProduct() {
        String title = binding.productTitleEditText.getText().toString().trim();
        // A simple validation: if not in variant mode, price is required.
        if (!binding.hasVariantsSwitch.isChecked() && binding.productPriceEditText.getText().toString().trim().isEmpty()) {
            Toast.makeText(this, "Product Title and Price are required for a simple product.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (title.isEmpty()) {
            Toast.makeText(this, "Product Title is required.", Toast.LENGTH_SHORT).show();
            return;
        }
        // Corrected Validation: Check if *both* existing and new selections are empty
        if (existingMediaItems.isEmpty() && selectedMediaUris.isEmpty()) {
            Toast.makeText(this, "Please select at least one main product image or video.", Toast.LENGTH_SHORT).show();
            setLoading(false); // Make sure to stop loading indicator
            return;
        }
        setLoading(true);
        collectDataAndInitiateUpload();
    }

    // In AddProductActivity.java

    // In AddProductActivity.java

    // In AddProductActivity.java
// REPLACE your entire method with this one.

    // In AddProductActivity.java
// REPLACE your entire method with this one.

    private void collectDataAndInitiateUpload() {
        GoogleSignInAccount account = SessionManager.getInstance().getAccount();
        if (account == null || account.getId() == null) {
            showError("User session expired. Please sign in again.");
            return;
        }

        Product product = new Product();
        product.setUserId(account.getId());
        product.setCatalogueId(catalogueId);

        // --- 1. ALWAYS Collect Base Product Information ---
        // This information is now collected regardless of whether variants are enabled.
        product.setTitle(binding.productTitleEditText.getText().toString().trim());
        product.setDescription(binding.productDescriptionEditText.getText().toString().trim());
        product.setSku(binding.productSkuEditText.getText().toString().trim());
        product.setInStock(binding.inStockSwitch.isChecked());

        if (binding.priceUnitSpinner.getSelectedItem() != null) {
            product.setPriceUnit(binding.priceUnitSpinner.getSelectedItem().toString());
        }

        try {
            product.setPrice(Double.parseDouble(binding.productPriceEditText.getText().toString().trim()));
            String discountStr = binding.productDiscountPriceEditText.getText().toString().trim();
            product.setDiscountedPrice(discountStr.isEmpty() ? 0.0 : Double.parseDouble(discountStr));
            product.setAvailableQuantity(Integer.parseInt(binding.quantityEditText.getText().toString().trim()));
            product.setMinOrderQty(Integer.parseInt(binding.productMoqEditText.getText().toString().trim()));
        } catch (NumberFormatException e) {
            Log.e(TAG, "Invalid number in base product fields. Defaulting to 0 or 1.");
            if (product.getMinOrderQty() == 0) product.setMinOrderQty(1);
        }

        String tagsStr = binding.productTagsEditText.getText().toString().trim();
        if (!tagsStr.isEmpty()) {
            product.setTags(Arrays.asList(tagsStr.split("\\s*,\\s*")));
        }


        // --- 2. IF variants are enabled, ALSO collect variant data ---
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
                    variant.setBarcode(((Uri) imageTag).toString()); // Temporarily store Uri
                }

                try {
                    variant.setPriceModifier(Double.parseDouble(priceModifierEt.getText().toString().trim()));
                    variant.setSkuOverride(skuOverrideEt.getText().toString().trim());
                    variant.setQuantity(Integer.parseInt(quantityEt.getText().toString().trim()));
                    variant.setInStock(variant.getQuantity() > 0);
                } catch (NumberFormatException e) {
                    Log.e(TAG, "Skipping a variant row due to invalid number format.");
                    continue;
                }
                generatedVariants.add(variant);
            }
            product.setVariants(generatedVariants);
        }

        // --- 3. Collect all other shared data ---
        product.setAllowBackorders(binding.allowBackordersSwitch.isChecked());
        product.setHideWhenOutOfStock(binding.hideWhenOutOfStockSwitch.isChecked());

        try {
            product.setTaxRate(Double.parseDouble(binding.taxRateEditText.getText().toString().trim()));
        } catch (NumberFormatException e) { product.setTaxRate(0.0); }
        try {
            product.setWeight(Double.parseDouble(binding.weightEditText.getText().toString().trim()));
        } catch (NumberFormatException e) { product.setWeight(0.0); }
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
            } catch (NumberFormatException e) {
                Log.e(TAG, "Could not parse bulk price slab row, skipping.");
            }
        }
        product.setBulkDiscounts(bulkDiscounts);

        Map<String, String> customFields = new HashMap<>();
        for (int i = 0; i < binding.customFieldsContainer.getChildCount(); i++) {
            View rowView = binding.customFieldsContainer.getChildAt(i);
            EditText fieldKey = rowView.findViewById(R.id.custom_field_key_edit_text);
            EditText fieldValue = rowView.findViewById(R.id.custom_field_value_edit_text);
            String key = fieldKey.getText().toString().trim();
            String value = fieldValue.getText().toString().trim();
            if (!key.isEmpty()) {
                customFields.put(key, value);
            }
        }
        product.setCustomFields(customFields);

        // --- 4. Start the upload process ---
        uploadAllMediaAndSaveProduct(product);
    }
    //endregion

    //region Upload and Save to DB
    // REPLACE this entire method
    // REPLACE this entire method
    // In AddProductActivity.java
// REPLACE this entire method

    private void uploadAllMediaAndSaveProduct(Product product) {
        Map<Uri, Object> uploadsToPerform = new HashMap<>();

        // 1. Queue NEWLY selected main media files for upload
        // Iterate through the URIs the user currently sees selected in the UI
        for (Uri uri : selectedMediaUris) {
            uploadsToPerform.put(uri, "main_media"); // Mark as needing upload
        }

        // 2. Queue NEWLY selected variant images for upload
        if (product.isHasVariants() && product.getVariants() != null) {
            for (ProductVariant variant : product.getVariants()) {
                // Check the barcode field for a Uri string (our temporary storage)
                if (variant.getBarcode() != null && variant.getBarcode().startsWith("content://")) {
                    Uri variantUri = Uri.parse(variant.getBarcode());
                    uploadsToPerform.put(variantUri, variant); // Mark variant image for upload
                    variant.setBarcode(null); // Clear the temporary field
                }
                // If barcode is null/not a Uri, it means either no image was selected
                // or the existing image URL (if editing) is still valid and doesn't need re-upload.
            }
        }

        // If there's nothing NEW to upload, just save with the current state of existing media
        if (uploadsToPerform.isEmpty()) {
            // The existingMediaItems list now correctly reflects items the user *didn't* delete.
            product.setMedia(new ArrayList<>(existingMediaItems));
            saveProductToFirestore(product);
            return;
        }

        // List to hold results of NEW uploads
        List<MediaItem> newlyUploadedMedia = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger uploadCounter = new AtomicInteger(uploadsToPerform.size());

        // 3. Loop through and upload only the NEW files
        for (Map.Entry<Uri, Object> entry : uploadsToPerform.entrySet()) {
            Uri uriToUpload = entry.getKey();
            Object uploadTarget = entry.getValue();

            uploadSingleFile(uriToUpload, new MediaUploadCallback() {
                @Override
                public void onSuccess(MediaItem mediaItem) {
                    // Check where this URL needs to go
                    if (uploadTarget.equals("main_media")) {
                        newlyUploadedMedia.add(mediaItem);
                    } else if (uploadTarget instanceof ProductVariant) {
                        // Update the variant's imageUrl (handles both adding new / replacing existing)
                        ((ProductVariant) uploadTarget).setImageUrl(mediaItem.getUrl());
                    }

                    // If this was the last NEW upload, combine lists and save
                    if (uploadCounter.decrementAndGet() == 0) {
                        // Start with the existing media items that were NOT removed by the user
                        List<MediaItem> finalMediaList = new ArrayList<>(existingMediaItems);
                        // Add all the newly uploaded items
                        finalMediaList.addAll(newlyUploadedMedia);
                        // Set the combined list to the product
                        product.setMedia(finalMediaList);
                        saveProductToFirestore(product);
                        // Optional TODO: Implement Drive file deletion here for items
                        // that were originally loaded but are NOT in existingMediaItems anymore.
                    }
                }
                @Override
                public void onFailure(String message) {
                    if (uploadCounter.getAndSet(-1) > 0) { // Ensures error is shown only once
                        showError("Upload failed: " + message);
                    }
                }
            });
        }
    }

    private void saveProductToFirestore(Product product) {
        if (productIdToEdit != null) {
            // --- UPDATE Existing Product ---
            db.collection("products").document(productIdToEdit).set(product) // Use set() to overwrite
                    .addOnSuccessListener(aVoid -> handler.post(() -> {
                        Toast.makeText(this, "Product Updated!", Toast.LENGTH_SHORT).show();
                        finish(); // Go back after successful update
                    }))
                    .addOnFailureListener(e -> showError("Failed to update product: " + e.getMessage()));
        } else {
            // --- CREATE New Product ---
            db.collection("products").add(product)
                    .addOnSuccessListener(documentReference -> handler.post(() -> {
                        Toast.makeText(this, "Product Saved!", Toast.LENGTH_SHORT).show();
                        finish(); // Go back after successful save
                    }))
                    .addOnFailureListener(e -> showError("Failed to save product data: " + e.getMessage()));
        }
    }

    interface MediaUploadCallback {
        void onSuccess(MediaItem mediaItem);
        void onFailure(String message);
    }

    private void uploadSingleFile(final Uri uri, final MediaUploadCallback callback) {
        // This is your existing, working upload logic. It remains unchanged.
        final String mimeType = getContentResolver().getType(uri);
        String determinedFileType = "image";
        if (mimeType != null && mimeType.startsWith("video")) {
            determinedFileType = "video";
        }
        final String fileType = determinedFileType;
        final String authHeader = "Bearer " + accessToken;

        executor.execute(() -> {
            try {
                InputStream inputStream = getContentResolver().openInputStream(uri);
                byte[] fileBytes = getBytes(inputStream);
                if (inputStream != null) inputStream.close();

                RequestBody fileRequestBody = RequestBody.create(fileBytes, MediaType.parse(mimeType));
                MultipartBody.Part filePart = MultipartBody.Part.createFormData("file", "media" + System.currentTimeMillis(), fileRequestBody);
                JSONObject metadataJson = new JSONObject();
                metadataJson.put("name", "media_" + System.currentTimeMillis());
                RequestBody metadataRequestBody = RequestBody.create(metadataJson.toString(), MediaType.parse("application/json; charset=utf-8"));

                RetrofitClient.getInstance().getApiService().uploadFileMultipart(authHeader, metadataRequestBody, filePart).enqueue(new Callback<ResponseBody>() {
                    @Override
                    public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            try {
                                String fileId = new JSONObject(response.body().string()).getString("id");
                                setPermissionsAndFinalize(authHeader, fileId, fileType, callback);
                            } catch (Exception e) {
                                callback.onFailure("Failed to parse upload response.");
                            }
                        } else {
                            callback.onFailure("Upload failed (Code: " + response.code() + ")");
                        }
                    }
                    @Override
                    public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                        callback.onFailure("Upload execution failed: " + t.getMessage());
                    }
                });
            } catch (Exception e) {
                callback.onFailure("Failed to prepare file for upload.");
            }
        });
    }

    private void setPermissionsAndFinalize(String authHeader, String fileId, String fileType, MediaUploadCallback callback) {
        // This is your existing, working permission logic. It remains unchanged.
        Map<String, String> permissionBody = new HashMap<>();
        permissionBody.put("role", "reader");
        permissionBody.put("type", "anyone");
        RetrofitClient.getInstance().getApiService().createPermissions(authHeader, fileId, permissionBody).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    String directLink = "https://drive.google.com/uc?export=download&id=" + fileId;
                    MediaItem mediaItem = new MediaItem(directLink, fileType);
                    callback.onSuccess(mediaItem);
                } else {
                    callback.onFailure("Failed to set file permissions.");
                }
            }
            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                callback.onFailure("Permission setting failed: " + t.getMessage());
            }
        });
    }
    //endregion

    //region Helper Methods
    private byte[] getBytes(InputStream inputStream) throws IOException {
        ByteArrayOutputStream byteBuffer = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
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
            Log.e(TAG, message);
        });
    }

    private void setLoading(boolean isLoading) {
        handler.post(() -> {
            binding.progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
            binding.buttonSaveProduct.setVisibility(isLoading ? View.INVISIBLE : View.VISIBLE);
        });
    }
    //endregion



    // In AddProductActivity.java

    // ADD THIS ENTIRE METHOD
    private void fetchAndPopulateProductData(String productId) {
        setLoading(true); // Show progress while loading
        db.collection("products").document(productId).get()
                .addOnSuccessListener(documentSnapshot -> {
                    setLoading(false);
                    if (documentSnapshot.exists()) {
                        Product product = documentSnapshot.toObject(Product.class);
                        if (product != null) {
                            product.setId(documentSnapshot.getId()); // Store the ID
                            populateForm(product);
                        } else {
                            showError("Failed to parse product data.");
                        }
                    } else {
                        showError("Product not found.");
                        finish(); // Close if product doesn't exist
                    }
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    showError("Failed to fetch product: " + e.getMessage());
                    Log.e(TAG, "Error fetching product", e);
                    finish();
                });
    }

    // ADD THIS ENTIRE METHOD (This is the complex part)
    private void populateForm(Product product) {
        // --- Populate Basic Info ---
        binding.productTitleEditText.setText(product.getTitle());
        binding.productDescriptionEditText.setText(product.getDescription());
        binding.productSkuEditText.setText(product.getSku());
        binding.productMoqEditText.setText(String.valueOf(product.getMinOrderQty()));
        if (product.getTags() != null) {
            binding.productTagsEditText.setText(String.join(", ", product.getTags()));
        }

        // --- Populate Pricing ---
        binding.productPriceEditText.setText(String.format(Locale.US, "%.2f", product.getPrice()));
        binding.productDiscountPriceEditText.setText(String.format(Locale.US, "%.2f", product.getDiscountedPrice()));
        // Set Spinner Selection (requires comparing string values)
        if (product.getPriceUnit() != null) {
            ArrayAdapter<CharSequence> adapter = (ArrayAdapter<CharSequence>) binding.priceUnitSpinner.getAdapter();
            if (adapter != null) {
                for (int i = 0; i < adapter.getCount(); i++) {
                    if (product.getPriceUnit().equals(adapter.getItem(i).toString())) {
                        binding.priceUnitSpinner.setSelection(i);
                        break;
                    }
                }
            }
        }
        // Populate Bulk Pricing
        binding.bulkPricingContainer.removeAllViews(); // Clear existing (if any)
        if (product.getBulkDiscounts() != null) {
            for (PriceSlab slab : product.getBulkDiscounts()) {
                addPriceSlabRow(slab); // Reuse your existing method
            }
        }

        // --- Populate Inventory ---
        binding.inStockSwitch.setChecked(product.isInStock());
        binding.quantityEditText.setText(String.valueOf(product.getAvailableQuantity()));
        binding.allowBackordersSwitch.setChecked(product.isAllowBackorders());
        binding.hideWhenOutOfStockSwitch.setChecked(product.isHideWhenOutOfStock());
        // Trigger listener manually to set initial enabled state of OOS options
        binding.allowBackordersSwitch.setEnabled(!product.isInStock());
        binding.hideWhenOutOfStockSwitch.setEnabled(!product.isInStock());


        // --- Populate Shipping & Taxes ---
        binding.taxRateEditText.setText(String.format(Locale.US, "%.1f", product.getTaxRate()));
        binding.weightEditText.setText(String.format(Locale.US, "%.2f", product.getWeight()));
        // Set Weight Spinner Selection
        if (product.getWeightUnit() != null) {
            ArrayAdapter<CharSequence> adapter = (ArrayAdapter<CharSequence>) binding.weightUnitSpinner.getAdapter();
            if (adapter != null) {
                for (int i = 0; i < adapter.getCount(); i++) {
                    if (product.getWeightUnit().equals(adapter.getItem(i).toString())) {
                        binding.weightUnitSpinner.setSelection(i);
                        break;
                    }
                }
            }
        }

        // --- Populate Custom Fields ---
        binding.customFieldsContainer.removeAllViews(); // Clear existing
        if (product.getCustomFields() != null) {
            for (Map.Entry<String, String> entry : product.getCustomFields().entrySet()) {
                addCustomFieldRow(entry.getKey(), entry.getValue()); // Reuse method
            }
        }

        // --- Populate Variants ---
        binding.hasVariantsSwitch.setChecked(product.isHasVariants());
        toggleVariantMode(product.isHasVariants()); // Update UI visibility

        if (product.isHasVariants()) {
            binding.variantOptionsContainer.removeAllViews();
            if (product.getVariantOptions() != null) {
                for (Map.Entry<String, List<String>> entry : product.getVariantOptions().entrySet()) {
                    // Need a modified addVariantOptionRow or a new method to pre-fill
                    addVariantOptionRowWithData(entry.getKey(), String.join(",", entry.getValue()));
                }
            }
            // Generate and populate the variant rows
            generateAndDisplayVariantsForEdit(product.getVariants());
        }

        // --- Populate Media ---
        // Note: We cannot easily pre-select local files. We will just show existing URLs.
        // If you want to allow *replacing* images, the logic becomes much more complex.
        // --- Populate Media (Handles Existing Media for Edit Mode) ---
        existingMediaItems.clear(); // Clear any previous state from other edits
        selectedMediaUris.clear();  // Clear any newly selected URIs from previous state
        if (product.getMedia() != null) {
            existingMediaItems.addAll(product.getMedia()); // Store the currently existing media items
        }
        updateMediaPreview(); // Call the updated preview method to display them

        // Ensure all cards start collapsed in edit mode for clarity
        binding.mediaContent.setVisibility(View.GONE); binding.mediaArrow.setRotation(0);
        binding.basicInfoContent.setVisibility(View.GONE); binding.basicInfoArrow.setRotation(0);
        binding.pricingContent.setVisibility(View.GONE); binding.pricingArrow.setRotation(0);
        binding.inventoryContent.setVisibility(View.GONE); binding.inventoryArrow.setRotation(0);
        binding.shippingContent.setVisibility(View.GONE); binding.shippingArrow.setRotation(0);
        binding.customFieldsContent.setVisibility(View.GONE); binding.customFieldsArrow.setRotation(0);
        binding.variantsContent.setVisibility(View.GONE); binding.variantsArrow.setRotation(0);

    }

    // ADD HELPER: Modified version to pre-fill variant options
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

    // ADD HELPER: Modified version to generate rows AND fill them with existing variant data
    private void generateAndDisplayVariantsForEdit(List<ProductVariant> existingVariants) {
        binding.generatedVariantsContainer.removeAllViews();
        if (existingVariants == null || existingVariants.isEmpty()) return;

        for (ProductVariant variant : existingVariants) {
            Map<String, String> combination = variant.getOptions();
            if (combination == null) continue; // Skip if options are missing

            LayoutInflater inflater = LayoutInflater.from(this);
            View rowView = inflater.inflate(R.layout.layout_generated_variant_row, binding.generatedVariantsContainer, false);

            TextView name = rowView.findViewById(R.id.variant_name_text);
            ImageView variantImage = rowView.findViewById(R.id.variant_image);
            View selectImageButton = rowView.findViewById(R.id.button_select_variant_image);
            View removeButton = rowView.findViewById(R.id.button_remove_variant);
            EditText priceModifierEt = rowView.findViewById(R.id.variant_price_modifier_edit_text);
            EditText skuOverrideEt = rowView.findViewById(R.id.variant_sku_override_edit_text);
            EditText quantityEt = rowView.findViewById(R.id.variant_quantity_edit_text);

            // Build and set the variant name
            StringBuilder comboName = new StringBuilder();
            for (String value : combination.values()) {
                if (comboName.length() > 0) comboName.append(" / ");
                comboName.append(value);
            }
            name.setText(comboName.toString());

            // Set existing data
            priceModifierEt.setText(String.format(Locale.US, "%.2f", variant.getPriceModifier()));
            skuOverrideEt.setText(variant.getSkuOverride());
            quantityEt.setText(String.valueOf(variant.getQuantity()));

            // Display existing image
            if (variant.getImageUrl() != null && !variant.getImageUrl().isEmpty()) {
                Glide.with(this).load(variant.getImageUrl()).placeholder(R.drawable.ic_add_photo).error(R.drawable.ic_add_photo).into(variantImage);
                // Store the URL in the tag. If user selects a new image, the tag will be updated to a Uri.
                variantImage.setTag(variant.getImageUrl());
            } else {
                variantImage.setImageResource(R.drawable.ic_add_photo);
            }

            rowView.setTag(combination); // Store combination options

            selectImageButton.setOnClickListener(v -> {
                targetVariantImageView = variantImage;
                variantImagePickerLauncher.launch("image/*");
            });

            removeButton.setOnClickListener(v -> binding.generatedVariantsContainer.removeView(rowView));
            binding.generatedVariantsContainer.addView(rowView);
        }
    }
}