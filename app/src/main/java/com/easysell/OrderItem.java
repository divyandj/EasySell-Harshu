package com.easysell;

import java.util.Map;

public class OrderItem {
    private String imageUrl;
    private String productId;
    private int quantity;
    private String title; // <--- ADDED: This matches your Firestore structure

    // Nested Maps
    private PriceDetails priceDetails;
    private ProductSnapshot productSnapshot;
    private Variant variant;

    public OrderItem() {}

    // --- Getters & Setters ---

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public PriceDetails getPriceDetails() { return priceDetails; }
    public void setPriceDetails(PriceDetails priceDetails) { this.priceDetails = priceDetails; }

    public ProductSnapshot getProductSnapshot() { return productSnapshot; }
    public void setProductSnapshot(ProductSnapshot productSnapshot) { this.productSnapshot = productSnapshot; }

    public Variant getVariant() { return variant; }
    public void setVariant(Variant variant) { this.variant = variant; }

    // --- Nested Classes ---

    public static class PriceDetails {
        private double baseUnitPrice;
        private double finalUnitPriceWithTax;
        private double lineItemSubtotal;
        private double lineItemTax;
        private double lineItemTotal;
        private double taxRate;

        public PriceDetails() {}

        public double getBaseUnitPrice() { return baseUnitPrice; }
        public void setBaseUnitPrice(double baseUnitPrice) { this.baseUnitPrice = baseUnitPrice; }

        public double getFinalUnitPriceWithTax() { return finalUnitPriceWithTax; }
        public void setFinalUnitPriceWithTax(double finalUnitPriceWithTax) { this.finalUnitPriceWithTax = finalUnitPriceWithTax; }

        public double getLineItemSubtotal() { return lineItemSubtotal; }
        public void setLineItemSubtotal(double lineItemSubtotal) { this.lineItemSubtotal = lineItemSubtotal; }

        public double getLineItemTax() { return lineItemTax; }
        public void setLineItemTax(double lineItemTax) { this.lineItemTax = lineItemTax; }

        public double getLineItemTotal() { return lineItemTotal; }
        public void setLineItemTotal(double lineItemTotal) { this.lineItemTotal = lineItemTotal; }

        public double getTaxRate() { return taxRate; }
        public void setTaxRate(double taxRate) { this.taxRate = taxRate; }
    }

    public static class ProductSnapshot {
        // Even if title is here sometimes, we prioritize the root title
        private String title;
        private String priceUnit;
        private double taxRate;

        public ProductSnapshot() {}

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }

        public String getPriceUnit() { return priceUnit; }
        public void setPriceUnit(String priceUnit) { this.priceUnit = priceUnit; }

        public double getTaxRate() { return taxRate; }
        public void setTaxRate(double taxRate) { this.taxRate = taxRate; }
    }

    public static class Variant {
        private Map<String, String> options;

        public Variant() {}

        public Map<String, String> getOptions() { return options; }
        public void setOptions(Map<String, String> options) { this.options = options; }
    }
}