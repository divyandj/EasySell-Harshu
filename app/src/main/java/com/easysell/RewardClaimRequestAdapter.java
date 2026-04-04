package com.easysell;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

public class RewardClaimRequestAdapter extends RecyclerView.Adapter<RewardClaimRequestAdapter.ViewHolder> {

    private List<RewardClaimRequest> requests;
    private final OnRequestActionListener listener;
    private String currentTabStatus = "pending";

    public interface OnRequestActionListener {
        void onApprove(RewardClaimRequest request);

        void onReject(RewardClaimRequest request);

        void onFulfill(RewardClaimRequest request);
    }

    public RewardClaimRequestAdapter(List<RewardClaimRequest> requests, OnRequestActionListener listener) {
        this.requests = requests;
        this.listener = listener;
    }

    public void updateList(List<RewardClaimRequest> newRequests, String status) {
        this.requests = newRequests;
        this.currentTabStatus = status;
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
        holder.bind(requests.get(position), listener, currentTabStatus);
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
            requestDate = itemView.findViewById(R.id.text_request_date);
            btnPrimary = itemView.findViewById(R.id.btn_primary_action);
            btnSecondary = itemView.findViewById(R.id.btn_secondary_action);
        }

        public void bind(RewardClaimRequest request, OnRequestActionListener listener, String currentStatus) {
            buyerName.setText(request.getBuyerName() != null ? request.getBuyerName() : "Unknown Buyer");
            buyerEmail.setText(request.getBuyerEmail() != null ? request.getBuyerEmail() : "N/A");
            rewardTitle.setText(request.getRewardTitle() != null ? request.getRewardTitle() : "Reward");

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

            if ("pending".equals(currentStatus)) {
                btnPrimary.setVisibility(View.VISIBLE);
                btnSecondary.setVisibility(View.VISIBLE);
                btnPrimary.setText("Approve");
                btnSecondary.setText("Reject");
                btnPrimary.setOnClickListener(v -> listener.onApprove(request));
                btnSecondary.setOnClickListener(v -> listener.onReject(request));
            } else if ("approved".equals(currentStatus)) {
                btnPrimary.setVisibility(View.VISIBLE);
                btnSecondary.setVisibility(View.VISIBLE);
                btnPrimary.setText("Mark Fulfilled");
                btnSecondary.setText("Reject");
                btnPrimary.setOnClickListener(v -> listener.onFulfill(request));
                btnSecondary.setOnClickListener(v -> listener.onReject(request));
            } else {
                btnPrimary.setVisibility(View.GONE);
                btnSecondary.setVisibility(View.GONE);
            }
        }
    }
}
