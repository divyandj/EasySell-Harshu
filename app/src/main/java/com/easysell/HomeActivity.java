package com.easysell;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.easysell.databinding.ActivityHomeBinding;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration; // ADDED: Import ListenerRegistration
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

public class HomeActivity extends AppCompatActivity implements CatalogueAdapter.OnCatalogueClickListener {

    private static final String TAG = "HomeActivity";
    private ActivityHomeBinding binding;
    private GoogleSignInClient googleSignInClient;
    private FirebaseFirestore db;
    private CatalogueAdapter adapter;
    private List<Catalogue> catalogueList;

    // ADDED: A variable to hold our listener
    private ListenerRegistration catalogueListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityHomeBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setSupportActionBar(binding.toolbar);

        db = FirebaseFirestore.getInstance();

        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).build();
        googleSignInClient = GoogleSignIn.getClient(this, gso);

        binding.fabAddCategory.setOnClickListener(view -> {
            Intent intent = new Intent(HomeActivity.this, AddCatalogueActivity.class);
            startActivity(intent);
        });

        setupRecyclerView();
        // CHANGED: We will now call loadCatalogues() from onStart() instead of here.
    }

    // ADDED: onStart lifecycle method
    @Override
    protected void onStart() {
        super.onStart();
        loadCatalogues(); // Load data every time the activity becomes visible
    }

    // ADDED: onStop lifecycle method
    @Override
    protected void onStop() {
        super.onStop();
        // Detach the listener when the activity is no longer visible
        if (catalogueListener != null) {
            catalogueListener.remove();
        }
    }

    private void setupRecyclerView() {
        catalogueList = new ArrayList<>();
        adapter = new CatalogueAdapter(catalogueList, this);
        binding.categoriesRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        binding.categoriesRecyclerView.setAdapter(adapter);
    }

    private void loadCatalogues() {
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
        if (account == null) {
            signOut();
            return;
        }
        String userId = account.getId();

        Query query = db.collection("catalogues")
                .whereEqualTo("userId", userId)
                .orderBy("createdAt", Query.Direction.DESCENDING);

        // CHANGED: Assign the listener to our variable
        catalogueListener = query.addSnapshotListener((value, error) -> {
            if (error != null) {
                Log.w(TAG, "Listen failed.", error);
                return;
            }

            catalogueList.clear();
            if (value != null) {
                for (QueryDocumentSnapshot doc : value) {
                    catalogueList.add(doc.toObject(Catalogue.class));
                }
                adapter.notifyDataSetChanged();
            }
            binding.emptyViewText.setVisibility(catalogueList.isEmpty() ? View.VISIBLE : View.GONE);
        });
    }

    @Override
    public void onCatalogueClick(Catalogue catalogue) {
        Intent intent = new Intent(this, CatalogueDetailActivity.class);
        intent.putExtra("CATALOGUE_ID", catalogue.getId());
        intent.putExtra("CATALOGUE_NAME", catalogue.getName());
        startActivity(intent);
    }

    // --- Sign Out Menu Logic (Unchanged) ---
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_sign_out) {
            signOut();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void signOut() {
        googleSignInClient.signOut().addOnCompleteListener(this, task -> {
            SessionManager.getInstance().clear();
            Toast.makeText(this, "Signed out successfully", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(HomeActivity.this, SignInActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });
    }
}