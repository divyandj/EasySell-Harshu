package com.easysell;

import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.ServerTimestamp;
import java.util.Date;

public class Catalogue {
    @DocumentId
    private String id;
    private String name;
    private String userId; // To know who created it
    @ServerTimestamp
    private Date createdAt;

    // Firestore requires an empty constructor
    public Catalogue() {}

    public Catalogue(String name, String userId) {
        this.name = name;
        this.userId = userId;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getUserId() { return userId; }
    public Date getCreatedAt() { return createdAt; }
}