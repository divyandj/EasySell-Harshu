package com.easysell;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;

import com.easysell.databinding.ActivityCatalogueDetailBinding;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration; // ADDED: Import ListenerRegistration
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

public class CatalogueDetailActivity extends AppCompatActivity {

    private static final String TAG = "CatalogueDetail";
    private ActivityCatalogueDetailBinding binding;
    private FirebaseFirestore db;
    private ProductAdapter adapter;
    private List<Product> productList;
    private String catalogueId;

    // ADDED: A variable to hold our listener
    private ListenerRegistration productListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCatalogueDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        catalogueId = getIntent().getStringExtra("CATALOGUE_ID");
        String catalogueName = getIntent().getStringExtra("CATALOGUE_NAME");
        setTitle(catalogueName);

        if (catalogueId == null) {
            finish();
            return;
        }

        db = FirebaseFirestore.getInstance();
        setupRecyclerView();
        // CHANGED: We will now call loadProducts() from onStart()

        binding.fabAddProduct.setOnClickListener(v -> {
            Intent intent = new Intent(this, AddProductActivity.class);
            intent.putExtra("CATALOGUE_ID", catalogueId);
            startActivity(intent);
        });
    }

    // ADDED: onStart lifecycle method
    @Override
    protected void onStart() {
        super.onStart();
        loadProducts(); // Load data every time the activity becomes visible
    }

    // ADDED: onStop lifecycle method
    @Override
    protected void onStop() {
        super.onStop();
        // Detach the listener when the activity is no longer visible
        if (productListener != null) {
            productListener.remove();
        }
    }


    private void setupRecyclerView() {
        productList = new ArrayList<>();
        adapter = new ProductAdapter(this, productList);
        binding.productsRecyclerView.setLayoutManager(new GridLayoutManager(this, 2));
        binding.productsRecyclerView.setAdapter(adapter);
    }

    private void loadProducts() {
        Query query = db.collection("products")
                .whereEqualTo("catalogueId", catalogueId)
                .orderBy("createdAt", Query.Direction.DESCENDING);

        // CHANGED: Assign the listener to our variable
        productListener = query.addSnapshotListener((value, error) -> {
            if (error != null) {
                Log.w(TAG, "Listen failed.", error);
                return;
            }
            productList.clear();
            if (value != null) {
                for (QueryDocumentSnapshot doc : value) {
                    productList.add(doc.toObject(Product.class));
                }
                adapter.notifyDataSetChanged();
            }
            binding.emptyViewTextProducts.setVisibility(productList.isEmpty() ? View.VISIBLE : View.GONE);
        });
    }
}