package com.easysell;

import com.google.firebase.firestore.ServerTimestamp;
import java.util.Date;

public class UserRequest {
    private String uid;
    private String displayName;
    private String email;
    private String phoneNumber;
    private String gstPan;
    private String status; // "pending", "approved", "rejected"
    private String userType;
    private String photoURL;

    @ServerTimestamp
    private Date createdAt;

    // Empty constructor for Firestore
    public UserRequest() {}

    public UserRequest(String uid, String displayName, String email, String phoneNumber, String gstPan, String status, String userType) {
        this.uid = uid;
        this.displayName = displayName;
        this.email = email;
        this.phoneNumber = phoneNumber;
        this.gstPan = gstPan;
        this.status = status;
        this.userType = userType;
    }

    // Getters and Setters
    public String getUid() { return uid; }
    public void setUid(String uid) { this.uid = uid; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    public String getGstPan() { return gstPan; }
    public void setGstPan(String gstPan) { this.gstPan = gstPan; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getUserType() { return userType; }
    public void setUserType(String userType) { this.userType = userType; }

    public String getPhotoURL() { return photoURL; }
    public void setPhotoURL(String photoURL) { this.photoURL = photoURL; }

    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
}