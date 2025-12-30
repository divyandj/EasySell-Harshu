package com.easysell;

// Represents the nested shippingAddress map in Firestore
public class ShippingAddress {
    private String name;
    private String address;
    private String city;
    private String pincode;
    private String phone;

    // Firestore requires an empty constructor
    public ShippingAddress() {}

    // Getters and Setters (Generate these in Android Studio)
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getPincode() { return pincode; }
    public void setPincode(String pincode) { this.pincode = pincode; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
}