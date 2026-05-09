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

public class CustomerPaymentHistoryAdapter extends RecyclerView.Adapter<CustomerPaymentHistoryAdapter.VH> {

    public static class PaymentHistoryItem {
        public final String orderId;
        public final String paymentOrderId;
        public final String paymentStatus;
        public final String utrNumber;
        public final double payableAmount;
        public final Long eventTimeMs;

        public PaymentHistoryItem(
                String orderId,
                String paymentOrderId,
                String paymentStatus,
                String utrNumber,
                double payableAmount,
                Long eventTimeMs
        ) {
            this.orderId = orderId;
            this.paymentOrderId = paymentOrderId;
            this.paymentStatus = paymentStatus;
            this.utrNumber = utrNumber;
            this.payableAmount = payableAmount;
            this.eventTimeMs = eventTimeMs;
        }
    }

    private final List<PaymentHistoryItem> items = new ArrayList<>();
    private final NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault());

    public void setItems(List<PaymentHistoryItem> data) {
        items.clear();
        if (data != null) {
            items.addAll(data);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_customer_payment_history, parent, false);
        return new VH(view);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        PaymentHistoryItem item = items.get(position);

        holder.textPaymentReference.setText(buildReference(item));
        holder.textPaymentAmount.setText(currencyFormat.format(item.payableAmount));
        holder.textPaymentUtr.setText("UTR: " + safeOrDash(item.utrNumber));

        if (item.eventTimeMs != null && item.eventTimeMs > 0) {
            holder.textPaymentDate.setText(dateFormat.format(new Date(item.eventTimeMs)));
        } else {
            holder.textPaymentDate.setText("-");
        }

        String label = normalizeStatusLabel(item.paymentStatus);
        holder.chipPaymentStatus.setText(label);
        applyPaymentStatusChip(holder.itemView.getContext(), holder.chipPaymentStatus, item.paymentStatus);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private String buildReference(PaymentHistoryItem item) {
        String orderRef = formatOrderRef(item.orderId);
        String paymentRef = item.paymentOrderId != null ? item.paymentOrderId.trim() : "";
        if (paymentRef.isEmpty()) {
            return orderRef;
        }

        String shortPayment = paymentRef.length() > 10
                ? paymentRef.substring(0, 10).toUpperCase(Locale.ROOT)
                : paymentRef.toUpperCase(Locale.ROOT);
        return "Payment #" + shortPayment + " • " + orderRef;
    }

    private String formatOrderRef(String orderId) {
        String value = orderId != null ? orderId.trim() : "";
        if (value.isEmpty()) {
            return "Order #N/A";
        }

        String shortId = value.length() > 8 ? value.substring(0, 8).toUpperCase(Locale.ROOT) : value.toUpperCase(Locale.ROOT);
        return "Order #" + shortId;
    }

    private String safeOrDash(String value) {
        String normalized = value != null ? value.trim() : "";
        return normalized.isEmpty() ? "-" : normalized;
    }

    private String normalizeStatusLabel(String status) {
        if (status == null || status.trim().isEmpty()) {
            return "NOT AVAILABLE";
        }
        return status.trim().toUpperCase(Locale.ROOT).replace('_', ' ');
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
        TextView textPaymentReference;
        TextView textPaymentDate;
        TextView chipPaymentStatus;
        TextView textPaymentAmount;
        TextView textPaymentUtr;

        VH(@NonNull View itemView) {
            super(itemView);
            textPaymentReference = itemView.findViewById(R.id.textPaymentHistoryReference);
            textPaymentDate = itemView.findViewById(R.id.textPaymentHistoryDate);
            chipPaymentStatus = itemView.findViewById(R.id.chipPaymentHistoryStatus);
            textPaymentAmount = itemView.findViewById(R.id.textPaymentHistoryAmount);
            textPaymentUtr = itemView.findViewById(R.id.textPaymentHistoryUtr);
        }
    }
}
