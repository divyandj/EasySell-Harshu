package com.easysell;

public class Dimension {
    private double length;
    private double width;
    private double height;
    private String unit; // "cm", "inch", etc.

    // Required empty constructor for Firestore
    public Dimension() {}

    // --- Getters and Setters ---
    public double getLength() {
        return length;
    }

    public void setLength(double length) {
        this.length = length;
    }

    public double getWidth() {
        return width;
    }

    public void setWidth(double width) {
        this.width = width;
    }

    public double getHeight() {
        return height;
    }

    public void setHeight(double height) {
        this.height = height;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }
}