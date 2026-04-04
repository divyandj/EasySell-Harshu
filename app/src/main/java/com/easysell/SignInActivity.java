package com.easysell;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.easysell.databinding.ActivitySignInBinding;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;

public class SignInActivity extends AppCompatActivity {

    private static final String TAG = "SignInActivity";
    private ActivitySignInBinding binding;
    private GoogleSignInClient googleSignInClient;
    private FirebaseAuth firebaseAuth;

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
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySignInBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Initialize Firebase Auth
        firebaseAuth = FirebaseAuth.getInstance();

        // Configure Google Sign-In to request idToken for Firebase Auth
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken("661744584495-vvocboui24dm25pm3khhio82m92ju2fa.apps.googleusercontent.com")
                .requestEmail()
                .requestProfile()
                .build();
        googleSignInClient = GoogleSignIn.getClient(this, gso);

        // Click Listeners
        binding.btnGoogleSignIn.setOnClickListener(v -> startGoogleSignIn());
        binding.btnEmailSignIn.setOnClickListener(v -> startEmailSignIn());
        binding.tvSignUp.setOnClickListener(v -> navigateToSignUp());
        binding.tvForgotPassword.setOnClickListener(v -> handleForgotPassword());
    }

    private void startEmailSignIn() {
        String email = binding.etEmail.getText() != null ? binding.etEmail.getText().toString().trim() : "";
        String password = binding.etPassword.getText() != null ? binding.etPassword.getText().toString().trim() : "";

        if (TextUtils.isEmpty(email)) {
            binding.tilEmail.setError("Email is required");
            return;
        }
        binding.tilEmail.setError(null);

        if (TextUtils.isEmpty(password)) {
            binding.tilPassword.setError("Password is required");
            return;
        }
        binding.tilPassword.setError(null);

        setLoading(true);
        firebaseAuth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    setLoading(false);
                    navigateToHome();
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    Log.e(TAG, "Email Sign In failed", e);
                    Toast.makeText(this, "Sign in failed: " + e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void handleForgotPassword() {
        String email = binding.etEmail.getText() != null ? binding.etEmail.getText().toString().trim() : "";

        EditText resetMail = new EditText(SignInActivity.this);
        resetMail.setText(email);
        resetMail.setHint("Enter your email");

        AlertDialog.Builder passwordResetDialog = new AlertDialog.Builder(SignInActivity.this);
        passwordResetDialog.setTitle("Reset Password?");
        passwordResetDialog.setMessage("Enter your email to receive a reset link.");
        passwordResetDialog.setView(resetMail);

        passwordResetDialog.setPositiveButton("Yes", (dialog, which) -> {
            String mail = resetMail.getText().toString().trim();
            if (TextUtils.isEmpty(mail)) {
                Toast.makeText(SignInActivity.this, "Email is required.", Toast.LENGTH_SHORT).show();
                return;
            }
            firebaseAuth.sendPasswordResetEmail(mail).addOnSuccessListener(unused -> {
                Toast.makeText(SignInActivity.this, "Reset link sent to your email.", Toast.LENGTH_SHORT).show();
            }).addOnFailureListener(e -> {
                Toast.makeText(SignInActivity.this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            });
        });

        passwordResetDialog.setNegativeButton("No", (dialog, which) -> {
            // Close dialog
        });

        passwordResetDialog.create().show();
    }

    private void navigateToSignUp() {
        startActivity(new Intent(SignInActivity.this, SignUpActivity.class));
    }

    private void setLoading(boolean isLoading) {
        binding.progressOverlay.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        binding.btnEmailSignIn.setEnabled(!isLoading);
        binding.btnGoogleSignIn.setEnabled(!isLoading);
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Check if user is already signed in to Firebase
        FirebaseUser currentUser = firebaseAuth.getCurrentUser();
        if (currentUser != null) {
            setLoading(true);
            navigateToHome();
        }
    }

    private void startGoogleSignIn() {
        setLoading(true);
        Intent signInIntent = googleSignInClient.getSignInIntent();
        signInLauncher.launch(signInIntent);
    }

    private void handleSignInResult(Task<GoogleSignInAccount> completedTask) {
        completedTask.addOnSuccessListener(account -> {
            // Got Google account, now authenticate with Firebase
            firebaseAuthWithGoogle(account);
        }).addOnFailureListener(e -> {
            Log.e(TAG, "Google Sign-in failed", e);
            setLoading(false);
            Toast.makeText(this, "Sign in failed.", Toast.LENGTH_SHORT).show();
        });
    }

    /**
     * Exchanges the Google ID token for a Firebase credential and signs in.
     */
    private void firebaseAuthWithGoogle(GoogleSignInAccount account) {
        String idToken = account.getIdToken();
        if (idToken == null) {
            Log.e(TAG, "Google ID Token is null");
            setLoading(false);
            Toast.makeText(this, "Authentication failed: No ID token.", Toast.LENGTH_SHORT).show();
            return;
        }

        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        firebaseAuth.signInWithCredential(credential)
                .addOnSuccessListener(authResult -> {
                    FirebaseUser user = authResult.getUser();
                    Log.d(TAG, "Firebase Auth success. UID: " + (user != null ? user.getUid() : "null"));
                    navigateToHome();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Firebase Auth failed", e);
                    setLoading(false);
                    Toast.makeText(this, "Authentication failed.", Toast.LENGTH_SHORT).show();
                });
    }

    private void navigateToHome() {
        Intent intent = new Intent(SignInActivity.this, HomeActivity.class);
        if (getIntent() != null && getIntent().getExtras() != null) {
            intent.putExtras(getIntent().getExtras());
        }
        startActivity(intent);
        finish();
    }
}