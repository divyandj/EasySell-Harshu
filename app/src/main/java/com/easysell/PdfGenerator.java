package com.easysell;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.pdf.PdfDocument;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.Map;

public class PdfGenerator {

    private static final int PAGE_WIDTH = 2480;
    private static final int PAGE_HEIGHT = 3508;

    // Updated Signature to accept Profile and Header/Footer Images
    public static File generateInvoice(Context context, Order order, Map<String, Bitmap> imageCache,
                                       SellerProfile profile, Bitmap logoBitmap, Bitmap signatureBitmap) {

        View view = LayoutInflater.from(context).inflate(R.layout.layout_invoice_pdf, null);

        // Pass the new data to the population method
        populateInvoiceView(context, view, order, imageCache, profile, logoBitmap, signatureBitmap);

        int widthSpec = View.MeasureSpec.makeMeasureSpec(PAGE_WIDTH, View.MeasureSpec.EXACTLY);
        int heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);

        view.measure(widthSpec, heightSpec);
        view.layout(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());

        PdfDocument document = new PdfDocument();
        int finalHeight = Math.max(view.getMeasuredHeight(), PAGE_HEIGHT);

        PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(PAGE_WIDTH, finalHeight, 1).create();
        PdfDocument.Page page = document.startPage(pageInfo);

        Canvas canvas = page.getCanvas();
        canvas.drawColor(Color.WHITE);
        view.draw(canvas);
        document.finishPage(page);

        File file = new File(context.getExternalCacheDir(), "Invoice_" + order.getId() + ".pdf");
        try {
            FileOutputStream fos = new FileOutputStream(file);
            document.writeTo(fos);
            document.close();
            fos.close();
            return file;
        } catch (IOException e) {
            e.printStackTrace();
            document.close();
            return null;
        }
    }

    private static void populateInvoiceView(Context context, View view, Order order, Map<String, Bitmap> imageCache,
                                            SellerProfile profile, Bitmap logoBitmap, Bitmap signatureBitmap) {

        NumberFormat currency = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));
        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
        boolean isBill = "withBill".equalsIgnoreCase(order.getBillingType());

        // --- 1. SELLER DETAILS (Dynamic) ---
        TextView businessHeader = view.findViewById(R.id.pdf_header_business_name);
        TextView sellerName = view.findViewById(R.id.pdf_seller_name);
        TextView sellerAddress = view.findViewById(R.id.pdf_seller_address_details);
        ImageView logoView = view.findViewById(R.id.pdf_seller_logo);
        ImageView signatureView = view.findViewById(R.id.pdf_signature_image);
        TextView footerName = view.findViewById(R.id.pdf_seller_name_footer);

        if (profile != null) {
            String bName = profile.getBusinessName() != null ? profile.getBusinessName() : "Easy Sell Store";
            businessHeader.setText(bName.toUpperCase());
            sellerName.setText(bName);
            footerName.setText("For " + bName);

            StringBuilder addrSb = new StringBuilder();
            if (profile.getAddress() != null) addrSb.append(profile.getAddress()).append("\n");
            if (profile.getPhone() != null) addrSb.append("Contact: ").append(profile.getPhone()).append("\n");
            if (profile.getGstin() != null) addrSb.append("GSTIN: ").append(profile.getGstin());
            sellerAddress.setText(addrSb.toString().trim());

            // Set Images
            if (logoBitmap != null) {
                logoView.setImageBitmap(logoBitmap);
                logoView.setVisibility(View.VISIBLE);
            }
            if (signatureBitmap != null) {
                signatureView.setImageBitmap(signatureBitmap);
                signatureView.setVisibility(View.VISIBLE);
            }
        }

        // --- 2. INVOICE META ---
        TextView label = view.findViewById(R.id.pdf_invoice_label);
        TextView orderId = view.findViewById(R.id.pdf_order_id);
        TextView orderDate = view.findViewById(R.id.pdf_order_date);

        if (isBill) {
            label.setText("TAX INVOICE");
            label.setTextColor(Color.BLACK);
        } else {
            label.setText("ESTIMATE");
            label.setTextColor(Color.DKGRAY);
        }

        orderId.setText("Invoice #: " + (order.getId() != null ? order.getId().toUpperCase() : "N/A"));
        orderDate.setText("Date: " + (order.getOrderDate() != null ? sdf.format(order.getOrderDate()) : "N/A"));

        // --- 3. CUSTOMER DETAILS ---
        TextView custName = view.findViewById(R.id.pdf_customer_name);
        TextView custDetails = view.findViewById(R.id.pdf_customer_details);

        if (order.getShippingAddress() != null) {
            Order.ShippingAddress addr = order.getShippingAddress();
            custName.setText(addr.getName() != null ? addr.getName() : "Guest");
            StringBuilder sb = new StringBuilder();
            if (addr.getAddress() != null) sb.append(addr.getAddress()).append("\n");
            if (addr.getCity() != null) sb.append(addr.getCity());
            if (addr.getPincode() != null) sb.append(" - ").append(addr.getPincode());
            if (addr.getPhone() != null) sb.append("\nPhone: ").append(addr.getPhone());
            custDetails.setText(sb.toString().trim());
        } else {
            custName.setText("Unknown Customer");
            custDetails.setText("");
        }

        // --- 4. ITEMS ---
        LinearLayout container = view.findViewById(R.id.pdf_items_container);
        container.removeAllViews();

        View headerTaxContainer = view.findViewById(R.id.pdf_header_tax_container);
        headerTaxContainer.setVisibility(isBill ? View.VISIBLE : View.GONE);

        if (order.getItems() != null) {
            for (OrderItem item : order.getItems()) {
                addItemRow(context, container, item, currency, isBill, imageCache);
            }
        }

        // --- 5. TOTALS ---
        TextView subtotal = view.findViewById(R.id.pdf_subtotal);
        LinearLayout rewardRow = view.findViewById(R.id.pdf_reward_row);
        TextView rewardDiscount = view.findViewById(R.id.pdf_reward_discount);
        TextView rewardLabel = view.findViewById(R.id.pdf_reward_label);
        TextView tax = view.findViewById(R.id.pdf_tax);
        TextView total = view.findViewById(R.id.pdf_total);
        TextView amountWords = view.findViewById(R.id.pdf_amount_words);
        LinearLayout taxRow = view.findViewById(R.id.pdf_tax_row);

        subtotal.setText(currency.format(order.getOrderSubtotal()));

        double appliedRewardDiscount = order.getRewardDiscount();
        Order.RewardRedeemed redeemedReward = order.getRewardRedeemed();
        if (appliedRewardDiscount > 0) {
            rewardRow.setVisibility(View.VISIBLE);
            rewardDiscount.setText("-" + currency.format(appliedRewardDiscount));
            if (redeemedReward != null && redeemedReward.getTitle() != null && !redeemedReward.getTitle().trim().isEmpty()) {
                StringBuilder rewardText = new StringBuilder(redeemedReward.getTitle().trim());
                if (redeemedReward.getType() != null && !redeemedReward.getType().trim().isEmpty()) {
                    rewardText.append(" • ").append(redeemedReward.getType().replace('_', ' '));
                }
                rewardLabel.setText(rewardText.toString());
                rewardLabel.setVisibility(View.VISIBLE);
            } else {
                rewardLabel.setVisibility(View.GONE);
            }
        } else {
            rewardRow.setVisibility(View.GONE);
        }

        total.setText(currency.format(order.getTotalAmount()));
        amountWords.setText(convertAmountToWords((long) order.getTotalAmount()) + " Only");

        if (isBill || order.getOrderTax() > 0) {
            taxRow.setVisibility(View.VISIBLE);
            tax.setText(currency.format(order.getOrderTax()));
        } else {
            taxRow.setVisibility(View.GONE);
        }
    }

    private static void addItemRow(Context context, LinearLayout container, OrderItem item, NumberFormat currency, boolean showTax, Map<String, Bitmap> imageCache) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setWeightSum(12);

        // 1. Image (Weight 1.5)
        ImageView img = new ImageView(context);
        LinearLayout.LayoutParams imgParams = new LinearLayout.LayoutParams(0, 140, 1.5f);
        img.setLayoutParams(imgParams);
        img.setScaleType(ImageView.ScaleType.FIT_CENTER);
        img.setPadding(10, 10, 10, 10);

        if (item.getImageUrl() != null && imageCache != null && imageCache.containsKey(item.getImageUrl())) {
            img.setImageBitmap(imageCache.get(item.getImageUrl()));
        } else {
            img.setImageResource(R.drawable.ic_image_placeholder);
        }

        // 2. Description (Weight 4)
        TextView desc = new TextView(context);
        String title = item.getTitle() != null ? item.getTitle() : "Item";
        StringBuilder sb = new StringBuilder();
        if (item.getVariant() != null && item.getVariant().getOptions() != null) {
            for (Map.Entry<String, String> entry : item.getVariant().getOptions().entrySet()) {
                if (sb.length() > 0) sb.append(" | ");
                sb.append(entry.getKey()).append(": ").append(entry.getValue());
            }
        }
        if (sb.length() > 0) title += "\n(" + sb.toString() + ")";

        desc.setText(title);
        desc.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 4f));
        desc.setTextColor(Color.BLACK);
        desc.setTextSize(18);
        desc.setPadding(16, 16, 16, 16);

        // 3. Qty (Weight 1)
        TextView qty = new TextView(context);
        qty.setText(String.valueOf(item.getQuantity()));
        qty.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));
        qty.setGravity(Gravity.CENTER);
        qty.setTextColor(Color.BLACK);
        qty.setTextSize(18);
        qty.setPadding(16, 16, 16, 16);

        // 4. Rate (Weight 2)
        TextView rate = new TextView(context);
        double unitPrice = (item.getPriceDetails() != null) ? item.getPriceDetails().getBaseUnitPrice() : 0;
        if (!showTax && item.getPriceDetails() != null) unitPrice = item.getPriceDetails().getFinalUnitPriceWithTax();
        rate.setText(currency.format(unitPrice));
        rate.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 2f));
        rate.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        rate.setTextColor(Color.BLACK);
        rate.setTextSize(18);
        rate.setPadding(16, 16, 16, 16);

        // 5. Tax (Weight 1.5)
        TextView tax = new TextView(context);
        if (showTax) {
            double taxAmt = (item.getPriceDetails() != null) ? item.getPriceDetails().getLineItemTax() : 0;
            tax.setText(currency.format(taxAmt));
            tax.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.5f));
            tax.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
            tax.setTextColor(Color.BLACK);
            tax.setTextSize(18);
            tax.setPadding(16, 16, 16, 16);
            tax.setVisibility(View.VISIBLE);
        } else {
            tax.setVisibility(View.GONE);
        }

        // 6. Total (Weight 2)
        TextView amount = new TextView(context);
        double lineTotal = (item.getPriceDetails() != null) ? item.getPriceDetails().getLineItemTotal() : 0;
        amount.setText(currency.format(lineTotal));
        amount.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 2f));
        amount.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        amount.setTextColor(Color.BLACK);
        amount.setTextSize(18);
        amount.setPadding(16, 16, 16, 16);

        // Add Views & Separators
        row.addView(img);
        row.addView(createVerticalSeparator(context));
        row.addView(desc);
        row.addView(createVerticalSeparator(context));
        row.addView(qty);
        row.addView(createVerticalSeparator(context));
        row.addView(rate);

        if (showTax) {
            row.addView(createVerticalSeparator(context));
            row.addView(tax);
        }

        row.addView(createVerticalSeparator(context));
        row.addView(amount);

        container.addView(row);

        View line = new View(context);
        line.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 2));
        line.setBackgroundColor(Color.parseColor("#999999"));
        container.addView(line);
    }

    private static View createVerticalSeparator(Context context) {
        View v = new View(context);
        v.setLayoutParams(new LinearLayout.LayoutParams(2, LinearLayout.LayoutParams.MATCH_PARENT));
        v.setBackgroundColor(Color.parseColor("#999999"));
        return v;
    }

    private static String convertAmountToWords(long amount) {
        if (amount == 0) return "Zero";
        String[] units = {"", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten", "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen", "Seventeen", "Eighteen", "Nineteen"};
        String[] tens = {"", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety"};
        if (amount < 20) return units[(int) amount];
        if (amount < 100) return tens[(int) (amount / 10)] + ((amount % 10 != 0) ? " " + units[(int) (amount % 10)] : "");
        if (amount < 1000) return units[(int) (amount / 100)] + " Hundred" + ((amount % 100 != 0) ? " " + convertAmountToWords(amount % 100) : "");
        if (amount < 100000) return convertAmountToWords(amount / 1000) + " Thousand" + ((amount % 1000 != 0) ? " " + convertAmountToWords(amount % 1000) : "");
        if (amount < 10000000) return convertAmountToWords(amount / 100000) + " Lakh" + ((amount % 100000 != 0) ? " " + convertAmountToWords(amount % 100000) : "");
        return convertAmountToWords(amount / 10000000) + " Crore" + ((amount % 10000000 != 0) ? " " + convertAmountToWords(amount % 10000000) : "");
    }
}