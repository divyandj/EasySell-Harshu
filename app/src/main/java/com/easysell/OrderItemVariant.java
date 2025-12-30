package com.easysell;

import java.util.Map;

// Represents the nested variant map within an OrderItem
public class OrderItemVariant {
    private String imageUrl;
    private Map<String, String> options;
    private String skuOverride;

    // Firestore requires an empty constructor
    public OrderItemVariant() {}

    // Getters and Setters (Generate these in Android Studio)
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public Map<String, String> getOptions() { return options; }
    public void setOptions(Map<String, String> options) { this.options = options; }
    public String getSkuOverride() { return skuOverride; }
    public void setSkuOverride(String skuOverride) { this.skuOverride = skuOverride; }
}