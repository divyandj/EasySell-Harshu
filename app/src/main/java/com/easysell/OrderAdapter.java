package com.easysell;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.FirebaseFirestore;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public class OrderAdapter extends RecyclerView.Adapter<OrderAdapter.OrderViewHolder> {

    private static final String TAG = "OrderAdapter";
    private final List<Order> orderList;
    private final OnOrderClickListener listener;
    private final FirebaseFirestore db;
    private final SimpleDateFormat dateFormat;

    /**
     * Interface for handling clicks on an order item.
     */
    public interface OnOrderClickListener {
        void onOrderClick(Order order);
    }

    public OrderAdapter(List<Order> orderList, OnOrderClickListener listener) {
        this.orderList = orderList;
        this.listener = listener;
        this.db = FirebaseFirestore.getInstance();
        // Updated format to show time as well (e.g., Oct 26, 10:30 AM)
        this.dateFormat = new SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault());
        this.dateFormat.setTimeZone(TimeZone.getDefault());
    }

    @NonNull
    @Override
    public OrderViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Inflate the NEW professional card layout
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_order, parent, false);
        return new OrderViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull OrderViewHolder holder, int position) {
        Order order = orderList.get(position);
        holder.bind(order, listener, db, dateFormat);
    }

    @Override
    public int getItemCount() {
        return orderList.size();
    }

    // --- ViewHolder Class ---
    static class OrderViewHolder extends RecyclerView.ViewHolder {
        TextView orderIdText;
        TextView orderDateText;
        TextView customerNameText;
        TextView orderTotalText;
        TextView orderStatusChip; // Changed from Chip to TextView for custom background support

        public OrderViewHolder(@NonNull View itemView) {
            super(itemView);
            // Map to IDs in the new item_order.xml
            orderIdText = itemView.findViewById(R.id.order_id_text);
            orderDateText = itemView.findViewById(R.id.order_date_text);
            customerNameText = itemView.findViewById(R.id.customer_name_text);
            orderTotalText = itemView.findViewById(R.id.order_total_text);
            orderStatusChip = itemView.findViewById(R.id.order_status_chip);
        }

        public void bind(final Order order, final OnOrderClickListener listener, FirebaseFirestore db, SimpleDateFormat dateFormat) {
            Context context = itemView.getContext();

            // 1. Set ID (Formatted to look professional, e.g., #ORD-1234ABCD)
            String rawId = order.getId();
            String displayId = (rawId != null && rawId.length() > 8)
                    ? rawId.substring(0, 8).toUpperCase()
                    : (rawId != null ? rawId : "UNKNOWN");
            orderIdText.setText("#ORD-" + displayId);

            // 2. Set Date
            if (order.getOrderDate() != null) {
                orderDateText.setText(dateFormat.format(order.getOrderDate()));
            } else {
                orderDateText.setText("Date N/A");
            }

            // 3. Set Total Amount with Symbol
            NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));
            orderTotalText.setText(currencyFormat.format(order.getTotalAmount()));

            // 4. Set Status Chip (Background Drawable & Text Color)
            String status = order.getStatus() != null ? order.getStatus() : "Unknown";
            orderStatusChip.setText(status);

            int bgRes = R.drawable.bg_chip_gray; // Default background
            int textColorRes = R.color.text_secondary; // Default text color

            if (status != null) {
                switch (status.toLowerCase()) {
                    case "placed":
                    case "pending":
                        bgRes = R.drawable.bg_chip_blue;
                        textColorRes = R.color.info;
                        break;
                    case "shipped":
                    case "processing":
                        bgRes = R.drawable.bg_chip_orange;
                        textColorRes = R.color.warning;
                        break;
                    case "delivered":
                    case "completed":
                        bgRes = R.drawable.bg_chip_green;
                        textColorRes = R.color.success;
                        break;
                    case "cancelled":
                    case "failed":
                    case "rejected":
                        bgRes = R.drawable.bg_chip_red;
                        textColorRes = R.color.error;
                        break;
                }
            }

            // Apply the custom background and text color
            orderStatusChip.setBackgroundResource(bgRes);
            orderStatusChip.setTextColor(ContextCompat.getColor(context, textColorRes));

            // 5. Fetch Customer Name
            fetchCustomerName(order.getUserId(), db);

            // 6. Click Listener
            itemView.setOnClickListener(v -> listener.onOrderClick(order));
        }

        private void fetchCustomerName(String userId, FirebaseFirestore db) {
            if (userId == null || userId.isEmpty()) {
                customerNameText.setText("Unknown Customer");
                return;
            }

            // Initial state while loading
            customerNameText.setText("Loading...");

            db.collection("users").document(userId).get()
                    .addOnSuccessListener(documentSnapshot -> {
                        if (documentSnapshot.exists()) {
                            String name = documentSnapshot.getString("displayName");
                            customerNameText.setText(name != null ? name : "Customer");
                        } else {
                            customerNameText.setText("Customer (Deleted)");
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.w(TAG, "Failed to fetch user name for ID: " + userId, e);
                        customerNameText.setText("Customer (Error)");
                    });
        }
    }
}