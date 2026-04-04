package com.easysell;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.bumptech.glide.request.RequestOptions;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class OrderDetailItemAdapter extends RecyclerView.Adapter<OrderDetailItemAdapter.ViewHolder> {

    private List<OrderItem> items;

    public OrderDetailItemAdapter(List<OrderItem> items) {
        this.items = items;
    }

    public void updateItems(List<OrderItem> newItems) {
        this.items = newItems;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_order_product, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        OrderItem item = items.get(position);
        holder.bind(item);
    }

    @Override
    public int getItemCount() {
        return items != null ? items.size() : 0;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView imgProduct;
        TextView textTitle;

        // Bill Rows
        TextView textVariantValue, textPriceBreakdown, textQuantityValue, textLineTotal;
        LinearLayout rowVariant;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            imgProduct = itemView.findViewById(R.id.img_product);
            textTitle = itemView.findViewById(R.id.text_product_title);

            // Bill Row IDs
            textVariantValue = itemView.findViewById(R.id.text_variant_value);
            rowVariant = itemView.findViewById(R.id.row_variant);
            textPriceBreakdown = itemView.findViewById(R.id.text_price_breakdown);
            textQuantityValue = itemView.findViewById(R.id.text_quantity_value);
            textLineTotal = itemView.findViewById(R.id.text_line_total);
        }

        public void bind(OrderItem item) {
            Context context = itemView.getContext();
            NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));

            // 1. Title Logic
            String title = "Unknown Product";
            if (item.getTitle() != null && !item.getTitle().isEmpty()) {
                title = item.getTitle();
            } else if (item.getProductSnapshot() != null && item.getProductSnapshot().getTitle() != null) {
                title = item.getProductSnapshot().getTitle();
            }
            textTitle.setText(title);

            // 2. Variant Row (Normal Font)
            StringBuilder variantBuilder = new StringBuilder();
            if (item.getVariant() != null && item.getVariant().getOptions() != null) {
                for (Map.Entry<String, String> entry : item.getVariant().getOptions().entrySet()) {
                    if (variantBuilder.length() > 0) variantBuilder.append(", ");
                    variantBuilder.append(entry.getKey()).append(": ").append(entry.getValue());
                }
            }

            if (variantBuilder.length() > 0) {
                rowVariant.setVisibility(View.VISIBLE);
                textVariantValue.setText(variantBuilder.toString());
            } else {
                rowVariant.setVisibility(View.GONE);
            }

            // 3. Price Breakdown Row
            // Display as: ₹1,000 + ₹180 (Tax)
            OrderItem.PriceDetails price = item.getPriceDetails();
            if (price != null) {
                double base = price.getBaseUnitPrice(); // e.g. 1000
                // Calculate Unit Tax
                double unitTax = 0;
                if (item.getQuantity() > 0) {
                    unitTax = price.getLineItemTax() / item.getQuantity();
                }

                String breakdown = currencyFormat.format(base);
                if (unitTax > 0) {
                    breakdown += " + " + currencyFormat.format(unitTax) + " (Tax)";
                }
                textPriceBreakdown.setText(breakdown);

                // 4. Line Total
                textLineTotal.setText(currencyFormat.format(price.getLineItemTotal()));
            }

            // 5. Quantity Row
            String priceUnit = (item.getProductSnapshot() != null && item.getProductSnapshot().getPriceUnit() != null)
                    ? item.getProductSnapshot().getPriceUnit() : "Units";
            textQuantityValue.setText(item.getQuantity() + " " + priceUnit);

            // 6. Image
            if (item.getImageUrl() != null && !item.getImageUrl().isEmpty()) {
                Glide.with(context)
                        .load(item.getImageUrl())
                        .apply(new RequestOptions().transform(new CenterCrop(), new RoundedCorners(16)))
                        .placeholder(R.drawable.ic_image_placeholder)
                        .into(imgProduct);
            } else {
                imgProduct.setImageResource(R.drawable.ic_image_placeholder);
            }
        }
    }
}