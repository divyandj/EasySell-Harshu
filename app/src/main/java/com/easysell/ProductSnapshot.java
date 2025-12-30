package com.easysell;

// Represents the nested productSnapshot map for each item in Firestore
public class ProductSnapshot {
    private String priceUnit;
    private double taxRate;

    // Firestore requires an empty constructor
    public ProductSnapshot() {}

    // Getters and Setters (Generate these in Android Studio)
    public String getPriceUnit() { return priceUnit; }
    public void setPriceUnit(String priceUnit) { this.priceUnit = priceUnit; }
    public double getTaxRate() { return taxRate; }
    public void setTaxRate(double taxRate) { this.taxRate = taxRate; }
}