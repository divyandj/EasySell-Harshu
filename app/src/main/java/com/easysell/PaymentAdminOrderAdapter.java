package com.easysell;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.easysell.paymentadmin.model.PaymentOrderItem;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class PaymentAdminOrderAdapter extends RecyclerView.Adapter<PaymentAdminOrderAdapter.VH> {

    public interface ActionListener {
        void onReconcile(PaymentOrderItem item);

        void onDispute(PaymentOrderItem item);

        void onReopen(PaymentOrderItem item);
    }

    private final List<PaymentOrderItem> items = new ArrayList<>();
    private final ActionListener listener;

    public PaymentAdminOrderAdapter(ActionListener listener) {
        this.listener = listener;
    }

    public void setItems(List<PaymentOrderItem> newItems) {
        items.clear();
        if (newItems != null) items.addAll(newItems);
        notifyDataSetChanged();
    }

    public void appendItems(List<PaymentOrderItem> moreItems) {
        if (moreItems == null || moreItems.isEmpty()) return;
        int start = items.size();
        items.addAll(moreItems);
        notifyItemRangeInserted(start, moreItems.size());
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_payment_admin_order, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        PaymentOrderItem item = items.get(position);
        h.textOrderId.setText("Order: " + safe(item.orderId));
        String status = safe(item.paymentStatus).toUpperCase(Locale.ROOT);
        h.textStatus.setText(formatStatus(status));
        applyStatusChip(h.textStatus, status);
        h.textAmount.setText("Amount: " + item.orderAmount + " | Collect: " + item.uniquePayableAmount);
        h.textUtr.setText("Payment Reference: " + (item.utrNumber == null || item.utrNumber.isEmpty() ? "-" : item.utrNumber));

        if (item.createdAt != null && item.createdAt > 0) {
            String date = new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(new Date(item.createdAt));
            h.textCreatedAt.setText("Created: " + date);
        } else {
            h.textCreatedAt.setText("Created: -");
        }

        boolean canConfirm = "UTR_SUBMITTED".equals(status) || "PAYMENT_UNDER_REVIEW".equals(status);
        boolean canReopen = "DISPUTED".equals(status);

        h.buttonReconcile.setVisibility(canConfirm ? View.VISIBLE : View.GONE);
        h.buttonDispute.setVisibility(canConfirm ? View.VISIBLE : View.GONE);
        h.buttonReopen.setVisibility(canReopen ? View.VISIBLE : View.GONE);
        h.layoutActions.setVisibility((canConfirm || canReopen) ? View.VISIBLE : View.GONE);

        h.buttonReconcile.setOnClickListener(v -> listener.onReconcile(item));
        h.buttonDispute.setOnClickListener(v -> listener.onDispute(item));
        h.buttonReopen.setOnClickListener(v -> listener.onReopen(item));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String formatStatus(String status) {
        if (status == null || status.trim().isEmpty()) return "UNKNOWN";
        return status.replace('_', ' ');
    }

    private void applyStatusChip(TextView textView, String status) {
        int background;
        int textColor;

        switch (status) {
            case "UTR_SUBMITTED":
            case "PAYMENT_UNDER_REVIEW":
                background = R.drawable.bg_chip_orange;
                textColor = R.color.warning;
                break;
            case "RECONCILED":
                background = R.drawable.bg_chip_green;
                textColor = R.color.success;
                break;
            case "DISPUTED":
                background = R.drawable.bg_chip_red;
                textColor = R.color.error;
                break;
            case "EXPIRED":
            case "CANCELLED_BY_BUYER":
                background = R.drawable.bg_chip_gray;
                textColor = R.color.text_secondary;
                break;
            default:
                background = R.drawable.bg_chip_blue;
                textColor = R.color.info;
                break;
        }

        textView.setBackgroundResource(background);
        textView.setTextColor(ContextCompat.getColor(textView.getContext(), textColor));
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView textOrderId;
        TextView textStatus;
        TextView textAmount;
        TextView textUtr;
        TextView textCreatedAt;
        LinearLayout layoutActions;
        Button buttonReconcile;
        Button buttonDispute;
        Button buttonReopen;

        VH(@NonNull View itemView) {
            super(itemView);
            textOrderId = itemView.findViewById(R.id.textOrderId);
            textStatus = itemView.findViewById(R.id.textStatus);
            textAmount = itemView.findViewById(R.id.textAmount);
            textUtr = itemView.findViewById(R.id.textUtr);
            textCreatedAt = itemView.findViewById(R.id.textCreatedAt);
            layoutActions = itemView.findViewById(R.id.layoutActions);
            buttonReconcile = itemView.findViewById(R.id.buttonReconcile);
            buttonDispute = itemView.findViewById(R.id.buttonDispute);
            buttonReopen = itemView.findViewById(R.id.buttonReopen);
        }
    }
}
