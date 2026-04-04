package com.easysell;

import android.net.Uri;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/**
 * A simple Singleton to hold session data.
 * Now backed by FirebaseAuth instead of GoogleSignInAccount.
 */
public class SessionManager {
    private static SessionManager instance;

    private SessionManager() {
    }

    public static synchronized SessionManager getInstance() {
        if (instance == null) {
            instance = new SessionManager();
        }
        return instance;
    }

    /**
     * Returns the currently signed-in FirebaseUser, or null if not signed in.
     */
    public FirebaseUser getFirebaseUser() {
        return FirebaseAuth.getInstance().getCurrentUser();
    }

    /**
     * Convenience: returns the Firebase UID, or null if not signed in.
     */
    public String getUserId() {
        FirebaseUser user = getFirebaseUser();
        return user != null ? user.getUid() : null;
    }

    /**
     * Convenience: returns the display name from the Firebase user profile.
     */
    public String getDisplayName() {
        FirebaseUser user = getFirebaseUser();
        return user != null ? user.getDisplayName() : null;
    }

    /**
     * Convenience: returns the photo URL from the Firebase user profile.
     */
    public Uri getPhotoUrl() {
        FirebaseUser user = getFirebaseUser();
        return user != null ? user.getPhotoUrl() : null;
    }

    /**
     * Convenience: returns the email from the Firebase user profile.
     */
    public String getEmail() {
        FirebaseUser user = getFirebaseUser();
        return user != null ? user.getEmail() : null;
    }

    /**
     * Signs out the user from FirebaseAuth.
     */
    public void clear() {
        FirebaseAuth.getInstance().signOut();
    }
}