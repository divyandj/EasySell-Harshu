package com.easysell;

public class MediaItem {
    private String url;
    private String type; // Can be "image" or "video"

    // Required empty constructor for Firestore
    public MediaItem() {}

    public MediaItem(String url, String type) {
        this.url = url;
        this.type = type;
    }

    // --- Getters and Setters ---
    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}