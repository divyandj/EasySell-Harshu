package com.easysell;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class AnalyticsActivity extends AppCompatActivity {

    private static final String TAG = "AnalyticsActivity";

    private TextView tvGMV, tvOrdersCount, tvVisitors, tvAbandonedValue;
    private AutoCompleteTextView dropdownTimeFilter;
    private LineChart growthChart;
    private RecyclerView rvTopBuyers, rvTopProducts;

    private FirebaseFirestore db;
    private FirebaseUser currentUser;

    private String storeHandle = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_analytics);

        db = FirebaseFirestore.getInstance();
        currentUser = FirebaseAuth.getInstance().getCurrentUser();

        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        tvGMV = findViewById(R.id.tvGMV);
        tvOrdersCount = findViewById(R.id.tvOrdersCount);
        tvVisitors = findViewById(R.id.tvVisitors);
        tvAbandonedValue = findViewById(R.id.tvAbandonedValue);
        dropdownTimeFilter = findViewById(R.id.dropdownTimeFilter);
        growthChart = findViewById(R.id.growthChart);
        rvTopBuyers = findViewById(R.id.rvTopBuyers);
        rvTopProducts = findViewById(R.id.rvTopProducts);

        setupCoachingDialogs();
        setupDropdown();
        setupRecyclerViews();

        if (currentUser != null) {
            // First we need the storeHandle. A seller's UID usually maps to their
            // storeHandle.
            // Let's fetch the seller's profile.
            db.collection("users").document(currentUser.getUid()).get()
                    .addOnSuccessListener(snap -> {
                        if (snap.exists() && snap.contains("storeHandle")) {
                            storeHandle = snap.getString("storeHandle").toLowerCase();
                            loadAnalyticsData(7); // Default 7 days
                        }
                    });
        }
    }

    private void setupDropdown() {
        String[] options = { "Today", "Last 7 Days", "Last 30 Days" };
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, options);
        dropdownTimeFilter.setAdapter(adapter);
        dropdownTimeFilter.setText(options[1], false);

        dropdownTimeFilter.setOnItemClickListener((parent, view, position, id) -> {
            if (position == 0)
                loadAnalyticsData(1);
            else if (position == 1)
                loadAnalyticsData(7);
            else
                loadAnalyticsData(30);
        });
    }

    private void setupRecyclerViews() {
        rvTopBuyers.setLayoutManager(new LinearLayoutManager(this));
        rvTopProducts.setLayoutManager(new LinearLayoutManager(this));
    }

    private void setupCoachingDialogs() {
        ImageView infoGMV = findViewById(R.id.infoGMV);
        ImageView infoVisitors = findViewById(R.id.infoVisitors);
        ImageView infoAbandoned = findViewById(R.id.infoAbandoned);

        infoGMV.setOnClickListener(v -> showCoachingDialog("Gross Revenue (GMV)",
                "GMV is the total value of merchandise sold over a given period of time. It tells you your top-line growth. To increase this, try offering volume discounts (B2B Tiers) to encourage larger cart sizes!"));

        infoVisitors.setOnClickListener(v -> showCoachingDialog("Store Visitors",
                "This counts how many unique people viewed your storefront. If visitors are high but orders are low, your Conversion Rate is failing. You might need better product photos or more competitive pricing."));

        infoAbandoned.setOnClickListener(v -> showCoachingDialog("Lost Revenue (Abandoned Carts)",
                "This tracks users who added items to their cart but left before paying. This is money left on the table. High abandonment usually means your Minimum Order Quantities (MOQ) or shipping policies are scaring buyers away at the last second."));
    }

    private void showCoachingDialog(String title, String message) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("Got it", null)
                .show();
    }

    private void loadAnalyticsData(int daysBound) {
        if (storeHandle == null || storeHandle.isEmpty())
            return;

        // 1. Fetch Daily Aggregations for line chart and main cards
        db.collection("analytics").document(storeHandle).collection("daily")
                .orderBy("date", Query.Direction.DESCENDING)
                .limit(daysBound)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    double totalGMV = 0, totalAbandoned = 0;
                    int totalOrders = 0, totalVisits = 0;

                    List<Entry> chartEntries = new ArrayList<>();
                    List<String> xLabels = new ArrayList<>();

                    // Firestore returns newest first. We need oldest first for the chart.
                    List<QueryDocumentSnapshot> docs = new ArrayList<>();
                    for (QueryDocumentSnapshot d : queryDocumentSnapshots)
                        docs.add(d);
                    Collections.reverse(docs);

                    int index = 0;
                    for (QueryDocumentSnapshot doc : docs) {
                        double gmv = doc.getDouble("gmv") != null ? doc.getDouble("gmv") : 0;
                        double abValue = doc.getDouble("abandonedValue") != null ? doc.getDouble("abandonedValue") : 0;
                        int orders = doc.getLong("orders") != null ? doc.getLong("orders").intValue() : 0;
                        int visits = doc.getLong("visits") != null ? doc.getLong("visits").intValue() : 0;
                        String dateStr = doc.getString("date"); // YYYY-MM-DD

                        totalGMV += gmv;
                        totalAbandoned += abValue;
                        totalOrders += orders;
                        totalVisits += visits;

                        chartEntries.add(new Entry(index, (float) gmv));
                        // Format date to MM-DD for x-axis
                        if (dateStr != null && dateStr.length() == 10) {
                            xLabels.add(dateStr.substring(5));
                        } else {
                            xLabels.add("");
                        }
                        index++;
                    }

                    NumberFormat format = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));
                    tvGMV.setText(format.format(totalGMV));
                    tvAbandonedValue.setText(format.format(totalAbandoned));
                    tvOrdersCount.setText(String.valueOf(totalOrders));
                    tvVisitors.setText(String.valueOf(totalVisits));

                    updateChart(chartEntries, xLabels);
                })
                .addOnFailureListener(e -> Log.e(TAG, "Failed daily stats", e));

        // 2. Fetch Top Buyers
        db.collection("analytics").document(storeHandle).collection("topBuyers")
                .orderBy("ltv", Query.Direction.DESCENDING)
                .limit(5)
                .get()
                .addOnSuccessListener(snap -> {
                    List<TopItem> buyers = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : snap) {
                        String name = doc.getString("name");
                        if (name == null || name.isEmpty())
                            name = doc.getString("email");
                        double ltv = doc.getDouble("ltv") != null ? doc.getDouble("ltv") : 0;
                        buyers.add(new TopItem(name, ltv));
                    }
                    rvTopBuyers.setAdapter(new TopListAdapter(buyers));
                });

        // 3. Fetch Top Products
        db.collection("analytics").document(storeHandle).collection("topProducts")
                .orderBy("revenueGenerated", Query.Direction.DESCENDING)
                .limit(5)
                .get()
                .addOnSuccessListener(snap -> {
                    List<TopItem> prods = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : snap) {
                        String name = doc.getString("name");
                        double rev = doc.getDouble("revenueGenerated") != null ? doc.getDouble("revenueGenerated") : 0;
                        prods.add(new TopItem(name, rev));
                    }
                    rvTopProducts.setAdapter(new TopListAdapter(prods));
                });
    }

    private void updateChart(List<Entry> entries, List<String> xLabels) {
        if (entries.isEmpty()) {
            growthChart.clear();
            return;
        }

        LineDataSet dataSet = new LineDataSet(entries, "Revenue (₹)");
        dataSet.setColor(getResources().getColor(R.color.primary, null));
        dataSet.setCircleColor(getResources().getColor(R.color.primary, null));
        dataSet.setLineWidth(3f);
        dataSet.setCircleRadius(5f);
        dataSet.setDrawValues(false);
        dataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);

        LineData lineData = new LineData(dataSet);
        growthChart.setData(lineData);

        XAxis xAxis = growthChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setValueFormatter(new IndexAxisValueFormatter(xLabels));
        xAxis.setGranularity(1f);
        xAxis.setLabelCount(Math.min(xLabels.size(), 7));

        growthChart.getDescription().setEnabled(false);
        growthChart.getAxisRight().setEnabled(false);
        growthChart.animateX(1000);
        growthChart.invalidate();
    }

    // --- HELPER CLASSES FOR LISTS ---
    static class TopItem {
        String name;
        double value;

        TopItem(String n, double v) {
            name = n;
            value = v;
        }
    }

    class TopListAdapter extends RecyclerView.Adapter<TopListAdapter.ViewHolder> {
        List<TopItem> items;
        NumberFormat format = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));

        TopListAdapter(List<TopItem> items) {
            this.items = items;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_analytics_list, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            TopItem item = items.get(position);
            holder.tvTitle.setText(item.name != null ? item.name : "Unknown");
            holder.tvValue.setText(format.format(item.value));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvTitle, tvValue;

            ViewHolder(View v) {
                super(v);
                tvTitle = v.findViewById(R.id.tvItemTitle);
                tvValue = v.findViewById(R.id.tvItemValue);
            }
        }
    }
}
