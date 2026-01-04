package com.easysell;

import androidx.multidex.MultiDexApplication;
import com.cloudinary.android.MediaManager;
import com.google.firebase.FirebaseApp; // Import needed for explicit initialization
import java.util.HashMap;
import java.util.Map;

public class EasySellApplication extends MultiDexApplication {

    @Override
    public void onCreate() {
        super.onCreate();

        // 1. Force Initialize Firebase first
        // This ensures Firebase has the security context needed for Notifications
        FirebaseApp.initializeApp(this);

        // 2. Initialize Cloudinary second
        initCloudinary();
    }

    private void initCloudinary() {
        Map<String, String> config = new HashMap<>();
        config.put("cloud_name", "dqplhh4y3");
        config.put("secure", "true");

        try {
            MediaManager.init(this, config);
        } catch (Exception e) {
            // Already initialized, ignore
        }
    }
}