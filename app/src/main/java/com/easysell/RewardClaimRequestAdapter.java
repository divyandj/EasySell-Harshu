package com.easysell;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.PopupMenu;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

public class RewardClaimRequestAdapter extends RecyclerView.Adapter<RewardClaimRequestAdapter.ViewHolder> {

    private List<RewardClaimRequest> requests;
    private final OnRequestActionListener listener;
    private static final String[] TRANSITION_STATUSES = new String[]{"pending", "approved", "fulfilled", "rejected"};

    public interface OnRequestActionListener {
        void onChangeStatus(RewardClaimRequest request, String newStatus);
    }

    public RewardClaimRequestAdapter(List<RewardClaimRequest> requests, OnRequestActionListener listener) {
        this.requests = requests;
        this.listener = listener;
    }

    public void updateList(List<RewardClaimRequest> newRequests) {
        this.requests = newRequests;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_reward_claim_request, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(requests.get(position), listener);
    }

    @Override
    public int getItemCount() {
        return requests.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView buyerName;
        TextView buyerEmail;
        TextView rewardTitle;
        TextView rewardType;
        TextView pointsCost;
        TextView claimStatus;
        TextView requestDate;
        MaterialButton btnPrimary;
        MaterialButton btnSecondary;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            buyerName = itemView.findViewById(R.id.text_buyer_name);
            buyerEmail = itemView.findViewById(R.id.text_buyer_email);
            rewardTitle = itemView.findViewById(R.id.text_reward_title);
            rewardType = itemView.findViewById(R.id.text_reward_type);
            pointsCost = itemView.findViewById(R.id.text_points_cost);
            claimStatus = itemView.findViewById(R.id.text_claim_status);
            requestDate = itemView.findViewById(R.id.text_request_date);
            btnPrimary = itemView.findViewById(R.id.btn_primary_action);
            btnSecondary = itemView.findViewById(R.id.btn_secondary_action);
        }

        public void bind(RewardClaimRequest request, OnRequestActionListener listener) {
            buyerName.setText(request.getBuyerName() != null ? request.getBuyerName() : "Unknown Buyer");
            buyerEmail.setText(request.getBuyerEmail() != null ? request.getBuyerEmail() : "N/A");
            rewardTitle.setText(request.getRewardTitle() != null ? request.getRewardTitle() : "Reward");
            final String currentStatus = normalizeStatus(request.getStatus());

            String type = request.getRewardType() != null ? request.getRewardType() : "custom";
            if ("percent_off".equals(type)) {
                rewardType.setText("Percent Off");
            } else if ("flat_off".equals(type)) {
                rewardType.setText("Flat Off");
            } else if ("free_shipping".equals(type)) {
                rewardType.setText("Free Shipping");
            } else {
                rewardType.setText("Custom");
            }

            pointsCost.setText((request.getPointsCost() != null ? request.getPointsCost() : 0) + " pts");

            if (request.getCreatedAt() != null) {
                SimpleDateFormat sdf = new SimpleDateFormat("MMM dd", Locale.getDefault());
                requestDate.setText(sdf.format(request.getCreatedAt()));
            } else {
                requestDate.setText("N/A");
            }

            claimStatus.setText(formatStatusLabel(currentStatus));
            if ("approved".equals(currentStatus)) {
                claimStatus.setBackgroundResource(R.drawable.bg_chip_blue);
                claimStatus.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.info));
            } else if ("fulfilled".equals(currentStatus)) {
                claimStatus.setBackgroundResource(R.drawable.bg_chip_green);
                claimStatus.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.success));
            } else if ("rejected".equals(currentStatus)) {
                claimStatus.setBackgroundResource(R.drawable.bg_chip_red);
                claimStatus.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.error));
            } else if ("cancelled".equals(currentStatus)) {
                claimStatus.setBackgroundResource(R.drawable.bg_chip_gray);
                claimStatus.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.text_secondary));
            } else {
                claimStatus.setBackgroundResource(R.drawable.bg_chip_orange);
                claimStatus.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.warning));
            }

            btnPrimary.setVisibility(View.VISIBLE);
            btnPrimary.setText("Change Status");
            btnSecondary.setVisibility(View.GONE);
            btnPrimary.setOnClickListener(v -> {
                PopupMenu menu = new PopupMenu(itemView.getContext(), btnPrimary);
                for (String statusOption : TRANSITION_STATUSES) {
                    if (statusOption.equals(currentStatus)) continue;
                    menu.getMenu().add(formatStatusLabel(statusOption));
                }
                menu.setOnMenuItemClickListener(menuItem -> {
                    String label = String.valueOf(menuItem.getTitle());
                    String targetStatus = label.toLowerCase(Locale.ROOT);
                    listener.onChangeStatus(request, targetStatus);
                    return true;
                });
                menu.show();
            });
        }

        private static String normalizeStatus(String status) {
            if (status == null || status.trim().isEmpty()) return "pending";
            return status.trim().toLowerCase(Locale.ROOT);
        }

        private static String formatStatusLabel(String status) {
            if (status == null || status.isEmpty()) return "Pending";
            return Character.toUpperCase(status.charAt(0)) + status.substring(1);
        }
    }
}
