package com.easysell;

import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.easysell.paymentadmin.model.BucketDto;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class PaymentAdminBucketLedgerAdapter extends RecyclerView.Adapter<PaymentAdminBucketLedgerAdapter.VH> {

    public interface ActionListener {
        void onToggleBucketStatus(BucketDto bucket);

        void onEditBucket(BucketDto bucket);
    }

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_BUCKET = 1;

    private final List<RowItem> rows = new ArrayList<>();
    private final ActionListener listener;

    public PaymentAdminBucketLedgerAdapter(ActionListener listener) {
        this.listener = listener;
    }

    public void setData(List<BucketDto> buckets) {
        rows.clear();

        rows.add(RowItem.header("Collection Accounts"));
        if (buckets != null && !buckets.isEmpty()) {
            for (BucketDto bucket : buckets) {
                rows.add(RowItem.bucket(bucket));
            }
        } else {
            rows.add(RowItem.header("No collection accounts yet"));
        }

        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return rows.get(position).type;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_payment_admin_bucket_ledger, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        RowItem row = rows.get(position);

        if (row.type == TYPE_HEADER) {
            h.title.setText(row.headerText);
            h.subtitle.setVisibility(View.GONE);
            h.meta.setVisibility(View.GONE);
            h.health.setVisibility(View.GONE);
            h.healthProgress.setVisibility(View.GONE);
            h.edit.setVisibility(View.GONE);
            h.action.setVisibility(View.GONE);
            return;
        }

        BucketDto bucket = row.bucket;
        h.title.setText(safe(bucket != null ? bucket.vendorName : "") + "  [" + safe(bucket != null ? bucket.bucketId : "") + "]");
        h.subtitle.setVisibility(View.VISIBLE);
        h.subtitle.setText("Payment Handle: " + safe(bucket != null ? bucket.vendorUpiId : "") + " | Priority: " + (bucket != null ? bucket.priority : 0));
        h.meta.setVisibility(View.VISIBLE);
        h.meta.setText(
                "Limit: " + number(bucket != null ? bucket.limitAmount : 0) +
                        " | Reserved: " + number(bucket != null ? bucket.reservedAmount : 0) +
                        " | Collected: " + number(bucket != null ? bucket.collectedAmount : 0) +
                        " | Status: " + safe(bucket != null ? bucket.status : "UNKNOWN")
        );

        double limit = Math.max(0d, bucket != null ? bucket.limitAmount : 0d);
        double reserved = Math.max(0d, bucket != null ? bucket.reservedAmount : 0d);
        double collected = Math.max(0d, bucket != null ? bucket.collectedAmount : 0d);
        double used = Math.max(0d, reserved + collected);
        int utilization = limit > 0d ? (int) Math.min(100d, Math.round((used * 100d) / limit)) : 0;

        h.health.setVisibility(View.VISIBLE);
        h.healthProgress.setVisibility(View.VISIBLE);
        h.health.setText("Health: " + Math.max(0, 100 - utilization) + "% free • " + utilization + "% utilized");
        h.healthProgress.setProgress(utilization);

        int tintRes = utilization >= 90 ? R.color.error : utilization >= 70 ? R.color.warning : R.color.success;
        h.healthProgress.setProgressTintList(ColorStateList.valueOf(
            ContextCompat.getColor(h.healthProgress.getContext(), tintRes)
        ));

        h.edit.setVisibility(View.VISIBLE);
        h.edit.setOnClickListener(v -> {
            if (listener != null && bucket != null) {
                listener.onEditBucket(bucket);
            }
        });

        h.action.setVisibility(View.VISIBLE);

        String current = bucket != null && bucket.status != null ? bucket.status.toUpperCase(Locale.ROOT) : "";
        h.action.setText("ACTIVE".equals(current) ? "Pause" : "Activate");
        h.action.setOnClickListener(v -> {
            if (listener != null && bucket != null) {
                listener.onToggleBucketStatus(bucket);
            }
        });
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    private String safe(String value) {
        return value == null || value.trim().isEmpty() ? "-" : value;
    }

    private String number(double value) {
        return String.format(Locale.US, "%.2f", value);
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView title;
        TextView subtitle;
        TextView meta;
        TextView health;
        ProgressBar healthProgress;
        Button edit;
        Button action;

        VH(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.textTitle);
            subtitle = itemView.findViewById(R.id.textSubtitle);
            meta = itemView.findViewById(R.id.textMeta);
            health = itemView.findViewById(R.id.textHealth);
            healthProgress = itemView.findViewById(R.id.progressHealth);
            edit = itemView.findViewById(R.id.buttonEdit);
            action = itemView.findViewById(R.id.buttonAction);
        }
    }

    private static class RowItem {
        int type;
        String headerText;
        BucketDto bucket;

        static RowItem header(String text) {
            RowItem row = new RowItem();
            row.type = TYPE_HEADER;
            row.headerText = text;
            return row;
        }

        static RowItem bucket(BucketDto value) {
            RowItem row = new RowItem();
            row.type = TYPE_BUCKET;
            row.bucket = value;
            return row;
        }
    }
}
