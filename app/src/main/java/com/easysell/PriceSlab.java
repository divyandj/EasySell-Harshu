package com.easysell;

public class PriceSlab {
    private int startQty;
    private int endQty;
    private double pricePerUnit;

    // Required empty constructor for Firestore
    public PriceSlab() {}

    public PriceSlab(int startQty, int endQty, double pricePerUnit) {
        this.startQty = startQty;
        this.endQty = endQty;
        this.pricePerUnit = pricePerUnit;
    }

    // --- Getters and Setters ---
    public int getStartQty() {
        return startQty;
    }

    public void setStartQty(int startQty) {
        this.startQty = startQty;
    }

    public int getEndQty() {
        return endQty;
    }

    public void setEndQty(int endQty) {
        this.endQty = endQty;
    }

    public double getPricePerUnit() {
        return pricePerUnit;
    }

    public void setPricePerUnit(double pricePerUnit) {
        this.pricePerUnit = pricePerUnit;
    }
}