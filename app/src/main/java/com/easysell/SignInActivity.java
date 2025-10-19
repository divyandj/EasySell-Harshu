package com.easysell;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.easysell.databinding.ActivitySignInBinding;
import com.google.android.gms.auth.GoogleAuthUtil;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.tasks.Task;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class SignInActivity extends AppCompatActivity {

    private ActivitySignInBinding binding;
    private final Executor executor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private GoogleSignInClient googleSignInClient;

    private final ActivityResultLauncher<Intent> signInLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK) {
                    Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(result.getData());
                    handleSignInResult(task);
                } else {
                    binding.progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Sign in cancelled", Toast.LENGTH_SHORT).show();
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySignInBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestProfile()
                .requestScopes(new Scope("https://www.googleapis.com/auth/drive.file"))
                .build();
        googleSignInClient = GoogleSignIn.getClient(this, gso);

        binding.signInButton.setOnClickListener(v -> startSignIn());
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Check for an existing signed-in user
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
        if (account != null) {
            binding.progressBar.setVisibility(View.VISIBLE);
            binding.signInButton.setVisibility(View.GONE);
            getAccessTokenAndNavigate(account);
        }
    }

    private void startSignIn() {
        binding.progressBar.setVisibility(View.VISIBLE);
        Intent signInIntent = googleSignInClient.getSignInIntent();
        signInLauncher.launch(signInIntent);
    }

    private void handleSignInResult(Task<GoogleSignInAccount> completedTask) {
        completedTask.addOnSuccessListener(this::getAccessTokenAndNavigate)
                .addOnFailureListener(e -> {
                    Log.e("SignInActivity", "Sign-in failed", e);
                    binding.progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Sign in failed.", Toast.LENGTH_SHORT).show();
                });
    }

    private void getAccessTokenAndNavigate(GoogleSignInAccount account) {
        executor.execute(() -> {
            try {
                String scope = "oauth2:https://www.googleapis.com/auth/drive.file";
                String token = GoogleAuthUtil.getToken(getApplicationContext(), account.getAccount(), scope);

                // Save to our SessionManager
                SessionManager.getInstance().setAccount(account);
                SessionManager.getInstance().setAccessToken(token);

                Log.d("SignInActivity", "Access Token retrieved successfully.");
                handler.post(this::navigateToHome);

            } catch (Exception e) {
                Log.e("SignInActivity", "Error retrieving access token", e);
                handler.post(() -> {
                    binding.progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Could not get authorization.", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void navigateToHome() {
        Intent intent = new Intent(SignInActivity.this, HomeActivity.class);
        startActivity(intent);
        finish(); // Finish SignInActivity so user can't go back to it
    }
}