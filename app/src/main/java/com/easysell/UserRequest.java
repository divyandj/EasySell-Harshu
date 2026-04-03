package com.easysell;

import com.google.firebase.firestore.PropertyName;
import com.google.firebase.firestore.ServerTimestamp;
import java.util.Date;

public class UserRequest {
    @PropertyName("buyerUid")
    private String uid;

    @PropertyName("buyerName")
    private String displayName;

    @PropertyName("buyerEmail")
    private String email;

    @PropertyName("buyerPhone")
    private String phoneNumber;

    @PropertyName("buyerGst")
    private String gstPan;

    @PropertyName("buyerBusinessName")
    private String businessName;

    @PropertyName("buyerAddress")
    private String address;

    @PropertyName("buyerCardPhotoUrl")
    private String cardPhotoUrl;

    private String status; // "pending", "approved", "rejected"
    private String storeHandle;

    @PropertyName("buyerUid")
    public String getUid() {
        return uid;
    }

    @PropertyName("buyerUid")
    public void setUid(String uid) {
        this.uid = uid;
    }

    @PropertyName("buyerName")
    public String getDisplayName() {
        return displayName;
    }

    @PropertyName("buyerName")
    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    @PropertyName("buyerEmail")
    public String getEmail() {
        return email;
    }

    @PropertyName("buyerEmail")
    public void setEmail(String email) {
        this.email = email;
    }

    @PropertyName("buyerPhone")
    public String getPhoneNumber() {
        return phoneNumber;
    }

    @PropertyName("buyerPhone")
    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    @PropertyName("buyerGst")
    public String getGstPan() {
        return gstPan;
    }

    @PropertyName("buyerGst")
    public void setGstPan(String gstPan) {
        this.gstPan = gstPan;
    }

    @ServerTimestamp
    private Date createdAt;

    // Empty constructor for Firestore
    public UserRequest() {
    }

    public UserRequest(String uid, String displayName, String email, String phoneNumber, String gstPan, String status,
            String storeHandle) {
        this.uid = uid;
        this.displayName = displayName;
        this.email = email;
        this.phoneNumber = phoneNumber;
        this.gstPan = gstPan;
        this.status = status;
        this.storeHandle = storeHandle;
    }

    @PropertyName("buyerBusinessName")
    public String getBusinessName() {
        return businessName;
    }

    @PropertyName("buyerBusinessName")
    public void setBusinessName(String businessName) {
        this.businessName = businessName;
    }

    @PropertyName("buyerAddress")
    public String getAddress() {
        return address;
    }

    @PropertyName("buyerAddress")
    public void setAddress(String address) {
        this.address = address;
    }

    @PropertyName("buyerCardPhotoUrl")
    public String getCardPhotoUrl() {
        return cardPhotoUrl;
    }

    @PropertyName("buyerCardPhotoUrl")
    public void setCardPhotoUrl(String cardPhotoUrl) {
        this.cardPhotoUrl = cardPhotoUrl;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getStoreHandle() {
        return storeHandle;
    }

    public void setStoreHandle(String storeHandle) {
        this.storeHandle = storeHandle;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }
}