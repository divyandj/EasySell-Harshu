package com.easysell;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
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
        public CatalogueViewHolder(@NonNull View itemView) {
            super(itemView);
            nameTextView = itemView.findViewById(R.id.catalogue_name_text_view);
        }

        public void bind(final Catalogue catalogue, final OnCatalogueClickListener listener) {
            nameTextView.setText(catalogue.getName());
            itemView.setOnClickListener(v -> listener.onCatalogueClick(catalogue));
        }
    }
}