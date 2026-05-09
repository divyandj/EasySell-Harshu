package com.easysell;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class CustomerOrderHistoryAdapter extends RecyclerView.Adapter<CustomerOrderHistoryAdapter.VH> {

    public static class OrderHistoryItem {
        public final String orderId;
        public final Long orderDateMs;
        public final String fulfillmentStatus;
        public final String paymentStatus;
        public final double totalAmount;
        public final int itemCount;

        public OrderHistoryItem(
                String orderId,
                Long orderDateMs,
                String fulfillmentStatus,
                String paymentStatus,
                double totalAmount,
                int itemCount
        ) {
            this.orderId = orderId;
            this.orderDateMs = orderDateMs;
            this.fulfillmentStatus = fulfillmentStatus;
            this.paymentStatus = paymentStatus;
            this.totalAmount = totalAmount;
            this.itemCount = itemCount;
        }
    }

    private final List<OrderHistoryItem> items = new ArrayList<>();
    private final NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault());

    public void setItems(List<OrderHistoryItem> data) {
        items.clear();
        if (data != null) {
            items.addAll(data);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_customer_order_history, parent, false);
        return new VH(view);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        OrderHistoryItem item = items.get(position);

        holder.textOrderId.setText(formatOrderRef(item.orderId));
        if (item.orderDateMs != null && item.orderDateMs > 0) {
            holder.textOrderDate.setText(dateFormat.format(new Date(item.orderDateMs)));
        } else {
            holder.textOrderDate.setText("-");
        }

        holder.textOrderTotal.setText(currencyFormat.format(item.totalAmount));
        holder.textItemCount.setText(item.itemCount + " item" + (item.itemCount == 1 ? "" : "s"));

        String fulfillmentStatusLabel = normalizeStatusLabel(item.fulfillmentStatus);
        holder.chipFulfillmentStatus.setText(fulfillmentStatusLabel);
        applyFulfillmentStatusChip(holder.itemView.getContext(), holder.chipFulfillmentStatus, item.fulfillmentStatus);

        String paymentStatusLabel = normalizeStatusLabel(item.paymentStatus);
        holder.chipPaymentStatus.setText(paymentStatusLabel);
        applyPaymentStatusChip(holder.itemView.getContext(), holder.chipPaymentStatus, item.paymentStatus);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private String formatOrderRef(String orderId) {
        String value = orderId != null ? orderId.trim() : "";
        if (value.isEmpty()) {
            return "Order #N/A";
        }
        String shortId = value.length() > 8 ? value.substring(0, 8).toUpperCase(Locale.ROOT) : value.toUpperCase(Locale.ROOT);
        return "Order #" + shortId;
    }

    private String normalizeStatusLabel(String status) {
        if (status == null || status.trim().isEmpty()) {
            return "NOT AVAILABLE";
        }
        return status.trim().toUpperCase(Locale.ROOT).replace('_', ' ');
    }

    private void applyFulfillmentStatusChip(Context context, TextView chip, String status) {
        int bgRes = R.drawable.bg_chip_gray;
        int textColorRes = R.color.text_secondary;

        String normalized = status != null ? status.trim().toUpperCase(Locale.ROOT) : "";
        switch (normalized) {
            case "PLACED":
            case "PENDING":
                bgRes = R.drawable.bg_chip_blue;
                textColorRes = R.color.info;
                break;
            case "PROCESSING":
            case "SHIPPED":
                bgRes = R.drawable.bg_chip_orange;
                textColorRes = R.color.warning;
                break;
            case "DELIVERED":
            case "COMPLETED":
                bgRes = R.drawable.bg_chip_green;
                textColorRes = R.color.success;
                break;
            case "CANCELLED":
            case "FAILED":
            case "REJECTED":
                bgRes = R.drawable.bg_chip_red;
                textColorRes = R.color.error;
                break;
            default:
                break;
        }

        chip.setBackgroundResource(bgRes);
        chip.setTextColor(ContextCompat.getColor(context, textColorRes));
    }

    private void applyPaymentStatusChip(Context context, TextView chip, String status) {
        int bgRes = R.drawable.bg_chip_gray;
        int textColorRes = R.color.text_secondary;

        String normalized = status != null ? status.trim().toUpperCase(Locale.ROOT) : "";
        switch (normalized) {
            case "PENDING":
            case "UTR_SUBMITTED":
            case "PAYMENT_UNDER_REVIEW":
                bgRes = R.drawable.bg_chip_orange;
                textColorRes = R.color.warning;
                break;
            case "RECONCILED":
                bgRes = R.drawable.bg_chip_green;
                textColorRes = R.color.success;
                break;
            case "DISPUTED":
                bgRes = R.drawable.bg_chip_red;
                textColorRes = R.color.error;
                break;
            case "EXPIRED":
            case "CANCELLED_BY_BUYER":
            case "NOT_AVAILABLE":
                bgRes = R.drawable.bg_chip_gray;
                textColorRes = R.color.text_secondary;
                break;
            default:
                if (!normalized.isEmpty()) {
                    bgRes = R.drawable.bg_chip_blue;
                    textColorRes = R.color.info;
                }
                break;
        }

        chip.setBackgroundResource(bgRes);
        chip.setTextColor(ContextCompat.getColor(context, textColorRes));
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView textOrderId;
        TextView textOrderDate;
        TextView chipFulfillmentStatus;
        TextView chipPaymentStatus;
        TextView textOrderTotal;
        TextView textItemCount;

        VH(@NonNull View itemView) {
            super(itemView);
            textOrderId = itemView.findViewById(R.id.textHistoryOrderId);
            textOrderDate = itemView.findViewById(R.id.textHistoryOrderDate);
            chipFulfillmentStatus = itemView.findViewById(R.id.chipHistoryFulfillmentStatus);
            chipPaymentStatus = itemView.findViewById(R.id.chipHistoryPaymentStatus);
            textOrderTotal = itemView.findViewById(R.id.textHistoryOrderTotal);
            textItemCount = itemView.findViewById(R.id.textHistoryItemCount);
        }
    }
}
