package com.easysell.paymentadmin.model;

public class ApiEnvelope<T> {
    public boolean success;
    public String message;
    public T data;
}
