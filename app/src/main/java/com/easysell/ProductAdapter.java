package com.easysell;

import android.content.Context;
import android.graphics.Paint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip; // Import Chip

import java.util.List;
import java.util.Locale;

public class ProductAdapter extends RecyclerView.Adapter<ProductAdapter.ProductViewHolder> {

    private final Context context;
    private final List<Product> productList;
    private final OnProductActionClickListener listener; // Listener for button clicks

    /**
     * Interface to handle clicks on buttons within the product card.
     */
    public interface OnProductActionClickListener {
        void onEditClick(Product product);
        void onVisibilityToggleClick(Product product);
        void onItemClick(Product product); // For clicking the card itself
    }

    public ProductAdapter(Context context, List<Product> productList, OnProductActionClickListener listener) {
        this.context = context;
        this.productList = productList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ProductViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_product, parent, false);
        return new ProductViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ProductViewHolder holder, int position) {
        Product product = productList.get(position);
        holder.bind(product, listener);
    }

    @Override
    public int getItemCount() {
        return productList.size();
    }

    static class ProductViewHolder extends RecyclerView.ViewHolder {
        ImageView productImageView;
        TextView productNameTextView;
        TextView productPriceTextView;
        TextView productOriginalPriceTextView;
        Chip saleBadgeChip; // Changed from TextView
        Chip visibilityBadgeChip; // New View
        TextView stockStatusTextView; // New View
        MaterialButton visibilityToggleButton; // New View
        MaterialButton editButton; // New View
        View imageContainer; // To handle clicks on the image/card area

        public ProductViewHolder(@NonNull View itemView) {
            super(itemView);
            productImageView = itemView.findViewById(R.id.product_image_view);
            productNameTextView = itemView.findViewById(R.id.product_name_text_view);
            productPriceTextView = itemView.findViewById(R.id.product_price_text_view);
            productOriginalPriceTextView = itemView.findViewById(R.id.product_original_price_text_view);
            saleBadgeChip = itemView.findViewById(R.id.sale_badge_text_view); // Found by old ID, now a Chip
            visibilityBadgeChip = itemView.findViewById(R.id.visibility_badge); // New ID
            stockStatusTextView = itemView.findViewById(R.id.stock_status_text_view); // New ID
            visibilityToggleButton = itemView.findViewById(R.id.product_visibility_toggle); // New ID
            editButton = itemView.findViewById(R.id.button_edit_product); // New ID
            imageContainer = itemView.findViewById(R.id.image_container); // Container for main click
        }

        void bind(final Product product, final OnProductActionClickListener listener) {
            // Set Product Name
            productNameTextView.setText(product.getTitle());

            // --- Price Display Logic ---
            double originalPrice = product.getPrice();
            double discountedPrice = product.getDiscountedPrice();
            boolean isOnSale = discountedPrice > 0 && discountedPrice < originalPrice;

            saleBadgeChip.setVisibility(isOnSale ? View.VISIBLE : View.GONE);
            productPriceTextView.setText(String.format(Locale.getDefault(), "₹%.0f", isOnSale ? discountedPrice : originalPrice)); // Using %.0f for cleaner display
            productOriginalPriceTextView.setVisibility(isOnSale ? View.VISIBLE : View.GONE);
            if (isOnSale) {
                productOriginalPriceTextView.setText(String.format(Locale.getDefault(), "₹%.0f", originalPrice));
                productOriginalPriceTextView.setPaintFlags(productOriginalPriceTextView.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            } else {
                productOriginalPriceTextView.setPaintFlags(productOriginalPriceTextView.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG)); // Remove strikethrough if not on sale
            }

            // --- Stock Status ---
            // TODO: Add logic here if you have variant stock vs simple stock
            if (product.isInStock()) {
                stockStatusTextView.setText(String.format(Locale.getDefault(), "%d in stock", product.getAvailableQuantity()));
                // Optionally change the dot color here based on stock
            } else {
                stockStatusTextView.setText("Out of stock");
                // Optionally change the dot color here
            }

            // --- Visibility Badge ---
            boolean isVisible = product.isVisibleInCatalogue();
            visibilityBadgeChip.setVisibility(isVisible ? View.VISIBLE : View.GONE); // Only show if visible
            // You might want to add another badge for "Hidden" if needed

            // --- Visibility Toggle Button ---
            if (isVisible) {
                visibilityToggleButton.setText("Hide");
                visibilityToggleButton.setIconResource(R.drawable.ic_visibility_off);
            } else {
                visibilityToggleButton.setText("Show");
                visibilityToggleButton.setIconResource(R.drawable.ic_visibility);
            }

            // --- Media Display Logic ---
            productImageView.setImageResource(R.drawable.ic_launcher_background); // Default placeholder
            if (product.getMedia() != null && !product.getMedia().isEmpty()) {
                String firstImageUrl = null;
                for (MediaItem item : product.getMedia()) {
                    if ("image".equals(item.getType())) {
                        firstImageUrl = item.getUrl();
                        break;
                    }
                }
                if (firstImageUrl != null) {
                    Glide.with(itemView.getContext())
                            .load(firstImageUrl)
                            .placeholder(R.drawable.ic_launcher_background)
                            .error(R.drawable.ic_launcher_foreground)
                            .into(productImageView);
                }
            }

            // --- Click Listeners ---
            editButton.setOnClickListener(v -> listener.onEditClick(product));
            visibilityToggleButton.setOnClickListener(v -> listener.onVisibilityToggleClick(product));
            // Allow clicking anywhere on the card/image to view details
            itemView.setOnClickListener(v -> listener.onItemClick(product));
            imageContainer.setOnClickListener(v -> listener.onItemClick(product)); // Also handle click on image container
        }
    }
}