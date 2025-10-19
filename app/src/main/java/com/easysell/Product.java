package com.easysell;

import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.ServerTimestamp;
import java.util.Date;
import java.util.List;
import java.util.Map;

public class Product {

    //region Core Properties
    @DocumentId
    private String id;
    private String catalogueId;
    private String userId;
    @ServerTimestamp
    private Date createdAt;
    //endregion

    //region Shared Information
    private String title;
    private String description;
    private List<String> tags;
    private List<MediaItem> media; // Shared images/videos for the product page
    //endregion

    //region Simple Product Properties (Used when hasVariants is false)
    private String sku;
    private double price;
    private String priceUnit;
    private double discountedPrice;
    private int minOrderQty;
    private boolean inStock;
    private int availableQuantity;
    //endregion

    //region Complex Product Properties (Used when hasVariants is true)
    private boolean hasVariants; // The flag to control which mode we are in
    private Map<String, List<String>> variantOptions; // e.g., {"Color": ["Red", "Blue"]}
    private List<ProductVariant> variants; // The list of all generated, sellable variants
    //endregion

    //region Other Properties
    private List<PriceSlab> bulkDiscounts;
    private boolean hideWhenOutOfStock;
    private boolean allowBackorders;
    private double taxRate;
    private double weight;
    private String weightUnit;
    private Dimension dimensions;
    private Map<String, String> customFields;
    //endregion

    public Product() {}

    // --- GETTERS AND SETTERS ---
    // (Generate getters and setters for all fields)


    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getCatalogueId() {
        return catalogueId;
    }

    public void setCatalogueId(String catalogueId) {
        this.catalogueId = catalogueId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public List<MediaItem> getMedia() {
        return media;
    }

    public void setMedia(List<MediaItem> media) {
        this.media = media;
    }

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public String getPriceUnit() {
        return priceUnit;
    }

    public void setPriceUnit(String priceUnit) {
        this.priceUnit = priceUnit;
    }

    public double getDiscountedPrice() {
        return discountedPrice;
    }

    public void setDiscountedPrice(double discountedPrice) {
        this.discountedPrice = discountedPrice;
    }

    public int getMinOrderQty() {
        return minOrderQty;
    }

    public void setMinOrderQty(int minOrderQty) {
        this.minOrderQty = minOrderQty;
    }

    public boolean isInStock() {
        return inStock;
    }

    public void setInStock(boolean inStock) {
        this.inStock = inStock;
    }

    public int getAvailableQuantity() {
        return availableQuantity;
    }

    public void setAvailableQuantity(int availableQuantity) {
        this.availableQuantity = availableQuantity;
    }

    public boolean isHasVariants() {
        return hasVariants;
    }

    public void setHasVariants(boolean hasVariants) {
        this.hasVariants = hasVariants;
    }

    public Map<String, List<String>> getVariantOptions() {
        return variantOptions;
    }

    public void setVariantOptions(Map<String, List<String>> variantOptions) {
        this.variantOptions = variantOptions;
    }

    public List<ProductVariant> getVariants() {
        return variants;
    }

    public void setVariants(List<ProductVariant> variants) {
        this.variants = variants;
    }

    public List<PriceSlab> getBulkDiscounts() {
        return bulkDiscounts;
    }

    public void setBulkDiscounts(List<PriceSlab> bulkDiscounts) {
        this.bulkDiscounts = bulkDiscounts;
    }

    public boolean isHideWhenOutOfStock() {
        return hideWhenOutOfStock;
    }

    public void setHideWhenOutOfStock(boolean hideWhenOutOfStock) {
        this.hideWhenOutOfStock = hideWhenOutOfStock;
    }

    public boolean isAllowBackorders() {
        return allowBackorders;
    }

    public void setAllowBackorders(boolean allowBackorders) {
        this.allowBackorders = allowBackorders;
    }

    public double getTaxRate() {
        return taxRate;
    }

    public void setTaxRate(double taxRate) {
        this.taxRate = taxRate;
    }

    public double getWeight() {
        return weight;
    }

    public void setWeight(double weight) {
        this.weight = weight;
    }

    public String getWeightUnit() {
        return weightUnit;
    }

    public void setWeightUnit(String weightUnit) {
        this.weightUnit = weightUnit;
    }

    public Dimension getDimensions() {
        return dimensions;
    }

    public void setDimensions(Dimension dimensions) {
        this.dimensions = dimensions;
    }

    public Map<String, String> getCustomFields() {
        return customFields;
    }

    public void setCustomFields(Map<String, String> customFields) {
        this.customFields = customFields;
    }
}