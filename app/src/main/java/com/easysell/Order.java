package com.easysell;

import com.google.firebase.firestore.Exclude;
import com.google.firebase.firestore.PropertyName;
import java.util.Date;
import java.util.List;

public class Order {
    @Exclude
    private String id; // Document ID

    private String billingType; // "withBill" or "withoutBill"
    private String catalogueId;
    private List<OrderItem> items;
    private Date orderDate;
    private double orderSubtotal;
    private double orderTax;
    private String sellerId;
    private ShippingAddress shippingAddress;
    private String status;
    private double totalAmount;
    private String userId;

    public Order() {} // Empty constructor for Firestore

    // --- Getters and Setters ---

    @Exclude
    public String getId() { return id; }
    @Exclude
    public void setId(String id) { this.id = id; }

    public String getBillingType() { return billingType; }
    public void setBillingType(String billingType) { this.billingType = billingType; }

    public String getCatalogueId() { return catalogueId; }
    public void setCatalogueId(String catalogueId) { this.catalogueId = catalogueId; }

    public List<OrderItem> getItems() { return items; }
    public void setItems(List<OrderItem> items) { this.items = items; }

    public Date getOrderDate() { return orderDate; }
    public void setOrderDate(Date orderDate) { this.orderDate = orderDate; }

    public double getOrderSubtotal() { return orderSubtotal; }
    public void setOrderSubtotal(double orderSubtotal) { this.orderSubtotal = orderSubtotal; }

    public double getOrderTax() { return orderTax; }
    public void setOrderTax(double orderTax) { this.orderTax = orderTax; }

    public String getSellerId() { return sellerId; }
    public void setSellerId(String sellerId) { this.sellerId = sellerId; }

    public ShippingAddress getShippingAddress() { return shippingAddress; }
    public void setShippingAddress(ShippingAddress shippingAddress) { this.shippingAddress = shippingAddress; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public double getTotalAmount() { return totalAmount; }
    public void setTotalAmount(double totalAmount) { this.totalAmount = totalAmount; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    // --- Nested Class: ShippingAddress ---
    public static class ShippingAddress {
        private String address;
        private String city;
        private String name;
        private String phone;
        private String pincode;

        public ShippingAddress() {}

        public String getAddress() { return address; }
        public void setAddress(String address) { this.address = address; }

        public String getCity() { return city; }
        public void setCity(String city) { this.city = city; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getPhone() { return phone; }
        public void setPhone(String phone) { this.phone = phone; }

        public String getPincode() { return pincode; }
        public void setPincode(String pincode) { this.pincode = pincode; }

        @Override
        public String toString() {
            return address + ", " + city + " - " + pincode;
        }
    }
}