package com.easysell;

import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.ServerTimestamp;
import java.util.Date;

public class Catalogue {
    @DocumentId
    private String id;
    private String name;
    private String userId; // To know who created it
    private String imageUrl; // <--- Added field for Cloudinary URL

    @ServerTimestamp
    private Date createdAt;

    // Firestore requires an empty constructor
    public Catalogue() {}

    public Catalogue(String name, String userId, String imageUrl) {
        this.name = name;
        this.userId = userId;
        this.imageUrl = imageUrl;
    }

    // --- Getters & Setters ---
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
}