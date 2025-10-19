package com.easysell;

import java.util.Map;

public class ProductVariant {
    // Defines this variant, e.g., {"Color": "Red", "Size": "L"}
    private Map<String, String> options;

    private String imageUrl;
    private String skuOverride;
    private double priceModifier;
    private int quantity;
    private boolean inStock;
    private String barcode; // <-- ADD THIS FIELD

    public ProductVariant() {}

    // --- Getters and Setters ---

    public Map<String, String> getOptions() {
        return options;
    }

    public void setOptions(Map<String, String> options) {
        this.options = options;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getSkuOverride() {
        return skuOverride;
    }

    public void setSkuOverride(String skuOverride) {
        this.skuOverride = skuOverride;
    }

    public double getPriceModifier() {
        return priceModifier;
    }

    public void setPriceModifier(double priceModifier) {
        this.priceModifier = priceModifier;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public boolean isInStock() {
        return inStock;
    }

    public void setInStock(boolean inStock) {
        this.inStock = inStock;
    }

    // --- ADD THESE TWO METHODS ---
    public String getBarcode() {
        return barcode;
    }

    public void setBarcode(String barcode) {
        this.barcode = barcode;
    }
}