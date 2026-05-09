package com.easysell;

import com.google.firebase.firestore.Exclude;
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
    private double rewardDiscount;
    private RewardRedeemed rewardRedeemed;
    private String sellerId;
    private ShippingAddress shippingAddress;
    private String status;
    private String paymentStatus;
    private String paymentOrderId;
    private String paymentUtrNumber;
    private String utrNumber;
    private Double paymentUniquePayableAmount;
    private Double uniquePayableAmount;
    private Long paymentExpiresAtMs;
    private Long paymentCancelledAtMs;
    private double totalAmount;
    private String userId;
    private String transportName;

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

    public double getRewardDiscount() { return rewardDiscount; }
    public void setRewardDiscount(double rewardDiscount) { this.rewardDiscount = rewardDiscount; }

    public RewardRedeemed getRewardRedeemed() { return rewardRedeemed; }
    public void setRewardRedeemed(RewardRedeemed rewardRedeemed) { this.rewardRedeemed = rewardRedeemed; }

    public String getSellerId() { return sellerId; }
    public void setSellerId(String sellerId) { this.sellerId = sellerId; }

    public ShippingAddress getShippingAddress() { return shippingAddress; }
    public void setShippingAddress(ShippingAddress shippingAddress) { this.shippingAddress = shippingAddress; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getPaymentStatus() { return paymentStatus; }
    public void setPaymentStatus(String paymentStatus) { this.paymentStatus = paymentStatus; }

    public String getPaymentOrderId() { return paymentOrderId; }
    public void setPaymentOrderId(String paymentOrderId) { this.paymentOrderId = paymentOrderId; }

    public String getPaymentUtrNumber() { return paymentUtrNumber; }
    public void setPaymentUtrNumber(String paymentUtrNumber) { this.paymentUtrNumber = paymentUtrNumber; }

    public String getUtrNumber() { return utrNumber; }
    public void setUtrNumber(String utrNumber) { this.utrNumber = utrNumber; }

    public Double getPaymentUniquePayableAmount() { return paymentUniquePayableAmount; }
    public void setPaymentUniquePayableAmount(Double paymentUniquePayableAmount) { this.paymentUniquePayableAmount = paymentUniquePayableAmount; }

    public Double getUniquePayableAmount() { return uniquePayableAmount; }
    public void setUniquePayableAmount(Double uniquePayableAmount) { this.uniquePayableAmount = uniquePayableAmount; }

    public Long getPaymentExpiresAtMs() { return paymentExpiresAtMs; }
    public void setPaymentExpiresAtMs(Long paymentExpiresAtMs) { this.paymentExpiresAtMs = paymentExpiresAtMs; }

    public Long getPaymentCancelledAtMs() { return paymentCancelledAtMs; }
    public void setPaymentCancelledAtMs(Long paymentCancelledAtMs) { this.paymentCancelledAtMs = paymentCancelledAtMs; }

    public double getTotalAmount() { return totalAmount; }
    public void setTotalAmount(double totalAmount) { this.totalAmount = totalAmount; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getTransportName() { return transportName; }
    public void setTransportName(String transportName) { this.transportName = transportName; }

    @Exclude
    public String getResolvedPaymentStatus() {
        String statusValue = firstNonEmpty(paymentStatus);
        return statusValue != null ? statusValue.trim() : null;
    }

    @Exclude
    public String getResolvedUtrNumber() {
        String utrValue = firstNonEmpty(paymentUtrNumber, utrNumber);
        return utrValue != null ? utrValue.trim() : null;
    }

    @Exclude
    public double getResolvedPayableAmount() {
        if (paymentUniquePayableAmount != null && paymentUniquePayableAmount > 0d) {
            return paymentUniquePayableAmount;
        }
        if (uniquePayableAmount != null && uniquePayableAmount > 0d) {
            return uniquePayableAmount;
        }
        return totalAmount;
    }

    @Exclude
    public boolean hasPaymentRecord() {
        return firstNonEmpty(paymentOrderId, paymentStatus, paymentUtrNumber, utrNumber) != null
                || paymentUniquePayableAmount != null
                || uniquePayableAmount != null;
    }

    @Exclude
    private String firstNonEmpty(String... values) {
        if (values == null) return null;
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value;
            }
        }
        return null;
    }

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

    public static class RewardRedeemed {
        private String id;
        private String title;
        private String type;
        private Double pointsCost;

        public RewardRedeemed() {}

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }

        public Double getPointsCost() { return pointsCost; }
        public void setPointsCost(Double pointsCost) { this.pointsCost = pointsCost; }
    }
}