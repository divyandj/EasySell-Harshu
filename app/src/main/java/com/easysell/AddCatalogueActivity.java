package com.easysell;

import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.easysell.databinding.ActivityAddCatalogueBinding;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.firebase.firestore.FirebaseFirestore;

public class AddCatalogueActivity extends AppCompatActivity {

    private ActivityAddCatalogueBinding binding;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAddCatalogueBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setTitle("Create New Catalogue");

        db = FirebaseFirestore.getInstance();

        binding.saveCatalogueButton.setOnClickListener(v -> saveCatalogue());
    }

    private void saveCatalogue() {
        String name = binding.catalogueNameEditText.getText().toString().trim();
        if (name.isEmpty()) {
            Toast.makeText(this, "Please enter a name", Toast.LENGTH_SHORT).show();
            return;
        }

        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
        if (account == null || account.getId() == null) {
            Toast.makeText(this, "User not signed in!", Toast.LENGTH_SHORT).show();
            return;
        }

        String userId = account.getId();
        Catalogue newCatalogue = new Catalogue(name, userId);

        db.collection("catalogues")
                .add(newCatalogue)
                .addOnSuccessListener(documentReference -> {
                    Toast.makeText(this, "Catalogue created!", Toast.LENGTH_SHORT).show();
                    finish(); // Go back to home screen
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }
}