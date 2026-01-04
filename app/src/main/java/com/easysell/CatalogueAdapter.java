package com.easysell;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.List;

public class CatalogueAdapter extends RecyclerView.Adapter<CatalogueAdapter.CatalogueViewHolder> {

    private final List<Catalogue> catalogueList;
    private final OnCatalogueClickListener listener;

    public interface OnCatalogueClickListener {
        void onCatalogueClick(Catalogue catalogue);
    }

    public CatalogueAdapter(List<Catalogue> catalogueList, OnCatalogueClickListener listener) {
        this.catalogueList = catalogueList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public CatalogueViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_catalogue, parent, false);
        return new CatalogueViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CatalogueViewHolder holder, int position) {
        Catalogue catalogue = catalogueList.get(position);
        holder.bind(catalogue, listener);
    }

    @Override
    public int getItemCount() {
        return catalogueList.size();
    }

    static class CatalogueViewHolder extends RecyclerView.ViewHolder {
        TextView nameTextView;
        ImageView coverImageView; // Added ImageView

        public CatalogueViewHolder(@NonNull View itemView) {
            super(itemView);
            nameTextView = itemView.findViewById(R.id.catalogue_name_text_view);
            coverImageView = itemView.findViewById(R.id.catalogue_image_view); // Bind to ID from XML
        }

        public void bind(final Catalogue catalogue, final OnCatalogueClickListener listener) {
            nameTextView.setText(catalogue.getName());

            // --- Image Loading Logic ---
            if (catalogue.getImageUrl() != null && !catalogue.getImageUrl().isEmpty()) {
                // 1. If we have a URL, remove the gray tint so the photo shows in full color
                coverImageView.setImageTintList(null);

                // 2. Remove the padding so the photo fills the card edge-to-edge
                coverImageView.setPadding(0, 0, 0, 0);

                // 3. Load with Glide
                Glide.with(itemView.getContext())
                        .load(catalogue.getImageUrl())
                        .centerCrop()
                        .placeholder(R.drawable.ic_category)
                        .into(coverImageView);
            } else {
                // --- Reset to Default Icon State (Important for RecyclerView recycling) ---
                coverImageView.setImageResource(R.drawable.ic_category);

                // Restore Gray Tint
                coverImageView.setImageTintList(ColorStateList.valueOf(Color.parseColor("#5B5FFF")));

                // Restore Padding (18dp converted to pixels)
                int paddingDp = 18;
                float density = itemView.getContext().getResources().getDisplayMetrics().density;
                int paddingPixel = (int) (paddingDp * density);
                coverImageView.setPadding(paddingPixel, paddingPixel, paddingPixel, paddingPixel);
            }

            itemView.setOnClickListener(v -> listener.onCatalogueClick(catalogue));
        }
    }
}