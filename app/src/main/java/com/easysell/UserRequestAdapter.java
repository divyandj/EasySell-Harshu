package com.easysell;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.bumptech.glide.request.RequestOptions;
import com.google.android.material.button.MaterialButton;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

public class UserRequestAdapter extends RecyclerView.Adapter<UserRequestAdapter.ViewHolder> {

    private List<UserRequest> requests;
    private final OnRequestActionListener listener;
    private String currentTabStatus = "pending"; // Default

    public interface OnRequestActionListener {
        void onApprove(UserRequest request);

        void onReject(UserRequest request);
    }

    public UserRequestAdapter(List<UserRequest> requests, OnRequestActionListener listener) {
        this.requests = requests;
        this.listener = listener;
    }

    public void updateList(List<UserRequest> newRequests, String status) {
        this.requests = newRequests;
        this.currentTabStatus = status;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_user_request, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        UserRequest request = requests.get(position);
        holder.bind(request, listener, currentTabStatus);
    }

    @Override
    public int getItemCount() {
        return requests.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView name, type, date, email, phone, gst, businessNameText, addressText;
        MaterialButton btnPrimary, btnSecondary;
        ImageView avatarPlaceholder, cardPhoto;
        LinearLayout layoutBusinessName, layoutAddress;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.text_user_name);
            type = itemView.findViewById(R.id.text_user_type);
            date = itemView.findViewById(R.id.text_request_date);
            email = itemView.findViewById(R.id.text_email);
            phone = itemView.findViewById(R.id.text_phone);
            gst = itemView.findViewById(R.id.text_gst);
            btnPrimary = itemView.findViewById(R.id.btn_primary_action);
            btnSecondary = itemView.findViewById(R.id.btn_secondary_action);
            avatarPlaceholder = itemView.findViewById(R.id.img_avatar_placeholder);
            businessNameText = itemView.findViewById(R.id.text_business_name);
            addressText = itemView.findViewById(R.id.text_address);
            cardPhoto = itemView.findViewById(R.id.img_card_photo);
            layoutBusinessName = itemView.findViewById(R.id.layout_business_name);
            layoutAddress = itemView.findViewById(R.id.layout_address);
        }

        public void bind(UserRequest request, OnRequestActionListener listener, String currentStatus) {
            // --- DATA BINDING ---
            name.setText(request.getDisplayName() != null ? request.getDisplayName() : "Unknown User");
            type.setText("BUYER");
            email.setText(request.getEmail() != null ? request.getEmail() : "N/A");
            phone.setText(request.getPhoneNumber() != null ? request.getPhoneNumber() : "N/A");
            gst.setText(request.getGstPan() != null ? request.getGstPan() : "N/A");

            // --- NEW FIELDS ---
            if (request.getBusinessName() != null && !request.getBusinessName().isEmpty()) {
                layoutBusinessName.setVisibility(View.VISIBLE);
                businessNameText.setText(request.getBusinessName());
            } else {
                layoutBusinessName.setVisibility(View.GONE);
            }

            if (request.getAddress() != null && !request.getAddress().isEmpty()) {
                layoutAddress.setVisibility(View.VISIBLE);
                addressText.setText(request.getAddress());
            } else {
                layoutAddress.setVisibility(View.GONE);
            }

            if (request.getCardPhotoUrl() != null && !request.getCardPhotoUrl().isEmpty()) {
                cardPhoto.setVisibility(View.VISIBLE);
                Glide.with(itemView.getContext())
                        .load(request.getCardPhotoUrl())
                        .apply(new RequestOptions().transform(new RoundedCorners(24)))
                        .into(cardPhoto);
                cardPhoto.setOnClickListener(v -> {
                    android.content.Intent intent = new android.content.Intent(itemView.getContext(), FullscreenPhotoActivity.class);
                    intent.putExtra(FullscreenPhotoActivity.EXTRA_IMAGE_URL, request.getCardPhotoUrl());
                    itemView.getContext().startActivity(intent);
                });
            } else {
                cardPhoto.setVisibility(View.GONE);
            }

            if (request.getCreatedAt() != null) {
                SimpleDateFormat sdf = new SimpleDateFormat("MMM dd", Locale.getDefault());
                date.setText(sdf.format(request.getCreatedAt()));
            } else {
                date.setText("N/A");
            }

            // --- BUTTON LOGIC BASED ON STATUS ---
            if ("pending".equals(currentStatus)) {
                // View: PENDING -> Show Approve (Primary) & Reject (Secondary)
                btnPrimary.setVisibility(View.VISIBLE);
                btnSecondary.setVisibility(View.VISIBLE);

                btnPrimary.setText("Approve");
                btnSecondary.setText("Reject");

                btnPrimary.setOnClickListener(v -> listener.onApprove(request));
                btnSecondary.setOnClickListener(v -> listener.onReject(request));

            } else if ("approved".equals(currentStatus)) {
                // View: APPROVED -> Show Revoke (Secondary). Hide Primary.
                btnPrimary.setVisibility(View.GONE);
                btnSecondary.setVisibility(View.VISIBLE);

                btnSecondary.setText("Revoke Access");

                // Clicking revoke essentially "Rejects" them back
                btnSecondary.setOnClickListener(v -> listener.onReject(request));

            } else if ("rejected".equals(currentStatus)) {
                // View: REJECTED -> Show Restore (Primary). Hide Secondary.
                btnPrimary.setVisibility(View.VISIBLE);
                btnSecondary.setVisibility(View.GONE);

                btnPrimary.setText("Restore Access");

                // Clicking restore "Approves" them again
                btnPrimary.setOnClickListener(v -> listener.onApprove(request));
            }
        }
    }
}