package com.easysell;

public class SellerProfile {
    private String businessName;
    private String ownerName;
    private String address;
    private String phone;
    private String gstin;
    private String profileImageUrl;
    private String signatureImageUrl;

    // Empty constructor required for Firestore serialization
    public SellerProfile() {}

    // Getters
    public String getBusinessName() { return businessName; }
    public String getOwnerName() { return ownerName; }
    public String getAddress() { return address; }
    public String getPhone() { return phone; }
    public String getGstin() { return gstin; }
    public String getProfileImageUrl() { return profileImageUrl; }
    public String getSignatureImageUrl() { return signatureImageUrl; }

    // Setters (Optional, but good practice if needed manually)
    public void setBusinessName(String businessName) { this.businessName = businessName; }
    public void setOwnerName(String ownerName) { this.ownerName = ownerName; }
    public void setAddress(String address) { this.address = address; }
    public void setPhone(String phone) { this.phone = phone; }
    public void setGstin(String gstin) { this.gstin = gstin; }
    public void setProfileImageUrl(String profileImageUrl) { this.profileImageUrl = profileImageUrl; }
    public void setSignatureImageUrl(String signatureImageUrl) { this.signatureImageUrl = signatureImageUrl; }
}