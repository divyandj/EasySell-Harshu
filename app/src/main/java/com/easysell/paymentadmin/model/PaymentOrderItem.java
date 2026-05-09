package com.easysell.paymentadmin.model;

public class PaymentOrderItem {
    public String orderId;
    public double orderAmount;
    public double uniquePayableAmount;
    public String utrNumber;
    public String paymentStatus;
    public Long createdAt;
    public Long cancelledAt;
}
