package com.easysell;

import android.content.Context;
import android.graphics.Paint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.List;
import java.util.Locale;

public class ProductAdapter extends RecyclerView.Adapter<ProductAdapter.ProductViewHolder> {

    private final Context context;
    private final List<Product> productList;

    public ProductAdapter(Context context, List<Product> productList) {
        this.context = context;
        this.productList = productList;
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
        holder.bind(product);
    }

    @Override
    public int getItemCount() {
        return productList.size();
    }

    class ProductViewHolder extends RecyclerView.ViewHolder {
        ImageView productImageView;
        TextView productNameTextView;
        TextView productPriceTextView;
        TextView productOriginalPriceTextView;
        TextView saleBadgeTextView;

        public ProductViewHolder(@NonNull View itemView) {
            super(itemView);
            productImageView = itemView.findViewById(R.id.product_image_view);
            productNameTextView = itemView.findViewById(R.id.product_name_text_view);
            productPriceTextView = itemView.findViewById(R.id.product_price_text_view);
            productOriginalPriceTextView = itemView.findViewById(R.id.product_original_price_text_view);
            saleBadgeTextView = itemView.findViewById(R.id.sale_badge_text_view);
        }

        /**
         * Binds a Product object to the views in the card.
         * @param product The product data to display.
         */
        void bind(Product product) {
            // Set the product title
            productNameTextView.setText(product.getTitle());

            // --- Price Display Logic ---
            double originalPrice = product.getPrice();
            double discountedPrice = product.getDiscountedPrice();

            if (discountedPrice > 0 && discountedPrice < originalPrice) {
                // If there's a valid discount, show the sale UI
                saleBadgeTextView.setVisibility(View.VISIBLE);
                productPriceTextView.setText(String.format(Locale.getDefault(), "₹%.2f", discountedPrice));

                productOriginalPriceTextView.setVisibility(View.VISIBLE);
                productOriginalPriceTextView.setText(String.format(Locale.getDefault(), "₹%.2f", originalPrice));
                // Add the strikethrough effect to the original price
                productOriginalPriceTextView.setPaintFlags(productOriginalPriceTextView.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            } else {
                // Otherwise, show only the original price
                saleBadgeTextView.setVisibility(View.GONE);
                productOriginalPriceTextView.setVisibility(View.GONE);
                productPriceTextView.setText(String.format(Locale.getDefault(), "₹%.2f", originalPrice));
            }

            // --- Media Display Logic ---
            // Set a default placeholder first
            productImageView.setImageResource(R.drawable.ic_launcher_background);

            if (product.getMedia() != null && !product.getMedia().isEmpty()) {
                // Find the first available image in the media list to use as a thumbnail
                String firstImageUrl = null;
                for (MediaItem item : product.getMedia()) {
                    if ("image".equals(item.getType())) {
                        firstImageUrl = item.getUrl();
                        break; // Stop after finding the first image
                    }
                }

                if (firstImageUrl != null) {
                    // Use Glide to load the image from the URL
                    Glide.with(context)
                            .load(firstImageUrl)
                            .placeholder(R.drawable.ic_launcher_background) // Shown while loading
                            .error(R.drawable.ic_launcher_foreground)       // Shown on failure
                            .into(productImageView);
                }
            }
        }
    }
}