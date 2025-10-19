package com.easysell;

import com.google.android.gms.auth.api.signin.GoogleSignInAccount;

// A simple Singleton to hold session data
public class SessionManager {
    private static SessionManager instance;
    private GoogleSignInAccount account;
    private String accessToken;

    private SessionManager() {}

    public static synchronized SessionManager getInstance() {
        if (instance == null) {
            instance = new SessionManager();
        }
        return instance;
    }

    public GoogleSignInAccount getAccount() {
        return account;
    }

    public void setAccount(GoogleSignInAccount account) {
        this.account = account;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public void clear() {
        account = null;
        accessToken = null;
    }
}