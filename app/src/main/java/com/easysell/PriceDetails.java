package com.easysell;

// Represents the nested priceDetails map for each item in Firestore
public class PriceDetails {
    private double baseUnitPrice;
    private double discountAmountUnit;
    private double bulkDiscountAmountUnit;
    private double variantModifierUnit;
    private double effectiveUnitPricePreTax;
    private double taxAmountUnit;
    private double finalUnitPriceWithTax;
    private double lineItemSubtotal;
    private double lineItemTax;
    private double lineItemTotal;

    // Firestore requires an empty constructor
    public PriceDetails() {}

    // Getters and Setters (Generate these in Android Studio)
    public double getBaseUnitPrice() { return baseUnitPrice; }
    public void setBaseUnitPrice(double baseUnitPrice) { this.baseUnitPrice = baseUnitPrice; }
    public double getDiscountAmountUnit() { return discountAmountUnit; }
    public void setDiscountAmountUnit(double discountAmountUnit) { this.discountAmountUnit = discountAmountUnit; }
    public double getBulkDiscountAmountUnit() { return bulkDiscountAmountUnit; }
    public void setBulkDiscountAmountUnit(double bulkDiscountAmountUnit) { this.bulkDiscountAmountUnit = bulkDiscountAmountUnit; }
    public double getVariantModifierUnit() { return variantModifierUnit; }
    public void setVariantModifierUnit(double variantModifierUnit) { this.variantModifierUnit = variantModifierUnit; }
    public double getEffectiveUnitPricePreTax() { return effectiveUnitPricePreTax; }
    public void setEffectiveUnitPricePreTax(double effectiveUnitPricePreTax) { this.effectiveUnitPricePreTax = effectiveUnitPricePreTax; }
    public double getTaxAmountUnit() { return taxAmountUnit; }
    public void setTaxAmountUnit(double taxAmountUnit) { this.taxAmountUnit = taxAmountUnit; }
    public double getFinalUnitPriceWithTax() { return finalUnitPriceWithTax; }
    public void setFinalUnitPriceWithTax(double finalUnitPriceWithTax) { this.finalUnitPriceWithTax = finalUnitPriceWithTax; }
    public double getLineItemSubtotal() { return lineItemSubtotal; }
    public void setLineItemSubtotal(double lineItemSubtotal) { this.lineItemSubtotal = lineItemSubtotal; }
    public double getLineItemTax() { return lineItemTax; }
    public void setLineItemTax(double lineItemTax) { this.lineItemTax = lineItemTax; }
    public double getLineItemTotal() { return lineItemTotal; }
    public void setLineItemTotal(double lineItemTotal) { this.lineItemTotal = lineItemTotal; }
}