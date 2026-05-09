package com.easysell;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.PopupMenu;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.bumptech.glide.Glide;
import com.easysell.databinding.ActivityOrderDetailBinding;
// Removed GoogleSignIn import
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldPath;
import com.google.firebase.firestore.FirebaseFirestore;

import java.io.File;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class OrderDetailActivity extends AppCompatActivity {

    private static final String TAG = "OrderDetailActivity";
    private ActivityOrderDetailBinding binding;
    private FirebaseFirestore db;
    private String orderId;
    private String catalogueId;
    private OrderDetailItemAdapter itemAdapter;
    private List<OrderItem> orderItems;
    private DocumentReference orderRef;

    private Order currentOrder;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final String[] statusOptions = { "Placed", "Processing", "Shipped", "Delivered", "Cancelled" };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityOrderDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Order Details");
        }

        db = FirebaseFirestore.getInstance();
        orderId = getIntent().getStringExtra("ORDER_ID");
        catalogueId = getIntent().getStringExtra("CATALOGUE_ID");

        if (orderId == null) {
            Toast.makeText(this, "Error: Missing Order ID", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        setupRecyclerView();
        setupSpinner();
        loadOrderDetails();

        binding.btnUpdateStatus.setOnClickListener(v -> updateOrderStatus());
        binding.btnShareInvoice.setOnClickListener(v -> prepareAndShareInvoice());
    }

    private void setupRecyclerView() {
        orderItems = new ArrayList<>();
        itemAdapter = new OrderDetailItemAdapter(orderItems);
        binding.recyclerOrderItems.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerOrderItems.setAdapter(itemAdapter);
        binding.recyclerOrderItems.setNestedScrollingEnabled(false);
    }

    private void setupSpinner() {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, R.layout.spinner_item, statusOptions);
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        binding.spinnerStatus.setAdapter(adapter);
    }

    private void loadOrderDetails() {
        if (catalogueId != null && !catalogueId.isEmpty()) {
            orderRef = db.collection("catalogues").document(catalogueId)
                    .collection("orders").document(orderId);
            fetchOrder(orderRef);
        } else {
            findOrderAndFetch();
        }
    }

    private void findOrderAndFetch() {
        db.collectionGroup("orders")
                .whereEqualTo(FieldPath.documentId(), orderId)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (!querySnapshot.isEmpty()) {
                        DocumentSnapshot doc = querySnapshot.getDocuments().get(0);
                        orderRef = doc.getReference();
                        Order order = doc.toObject(Order.class);
                        if (order != null) {
                            order.setId(doc.getId());
                            populateUI(order);
                        }
                    } else {
                        Toast.makeText(this, "Order not found.", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> Log.e(TAG, "Error finding order", e));
    }

    private void fetchOrder(DocumentReference ref) {
        ref.get().addOnSuccessListener(documentSnapshot -> {
            if (documentSnapshot.exists()) {
                Order order = documentSnapshot.toObject(Order.class);
                if (order != null) {
                    order.setId(documentSnapshot.getId());
                    populateUI(order);
                }
            } else {
                findOrderAndFetch();
            }
        }).addOnFailureListener(e -> {
            Log.e(TAG, "Fetch failed", e);
            findOrderAndFetch();
        });
    }

    private void populateUI(Order order) {
        this.currentOrder = order;
        String displayId = order.getId().length() > 8 ? order.getId().substring(0, 8).toUpperCase() : order.getId();
        binding.textOrderId.setText("#ORD-" + displayId);

        if (order.getOrderDate() != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault());
            binding.textOrderDate.setText(sdf.format(order.getOrderDate()));
        }

        String currentStatus = order.getStatus() != null ? order.getStatus() : "Pending";
        binding.statusBadge.setText(currentStatus);
        setStatusStyle(currentStatus);

        for (int i = 0; i < statusOptions.length; i++) {
            if (statusOptions[i].equalsIgnoreCase(currentStatus)) {
                binding.spinnerStatus.setSelection(i);
                break;
            }
        }

        if (order.getShippingAddress() != null) {
            Order.ShippingAddress addr = order.getShippingAddress();
            String customerName = addr.getName() != null ? addr.getName() : "Guest";
            binding.textCustomerName.setText(customerName);
            String phone = addr.getPhone() != null ? addr.getPhone() : "N/A";
            binding.textCustomerPhone.setText(phone);
            if (!phone.equals("N/A")) {
                binding.textCustomerPhone.setOnClickListener(v -> showPhoneOptions(v, phone));
                binding.textCustomerPhone.setTextColor(ContextCompat.getColor(this, R.color.primary));
            }
            String fullAddress = String.format("%s, %s - %s",
                    addr.getAddress() != null ? addr.getAddress() : "",
                    addr.getCity() != null ? addr.getCity() : "",
                    addr.getPincode() != null ? addr.getPincode() : "");
            fullAddress = fullAddress.replace(" ,", "").replace(", -", "").replaceAll("^, ", "");
            binding.textShippingAddress.setText(fullAddress);
            if (!fullAddress.isEmpty()) {
                String finalAddress = fullAddress;
                binding.textShippingAddress.setOnClickListener(v -> showAddressOptions(v, finalAddress));
            }

            setupCustomerNameNavigation(order, customerName, phone);
        } else {
            binding.textCustomerName.setText("Unknown Customer");
            binding.textShippingAddress.setText("Address unavailable");
            setupCustomerNameNavigation(order, "Unknown Customer", null);
        }

        if (order.getTransportName() != null && !order.getTransportName().trim().isEmpty()) {
            binding.rowTransport.setVisibility(View.VISIBLE);
            binding.textTransportName.setText(order.getTransportName());
        } else {
            binding.rowTransport.setVisibility(View.GONE);
        }

        boolean isWithBill = "withBill".equalsIgnoreCase(order.getBillingType());
        if (isWithBill) {
            binding.textBillingType.setText("Bill Requested");
            binding.textBillingType.setVisibility(View.VISIBLE);
            binding.textBillingType.setBackgroundResource(R.drawable.bg_chip_blue);
            binding.textBillingType.setTextColor(ContextCompat.getColor(this, R.color.info));
        } else {
            binding.textBillingType.setText("No Bill");
            binding.textBillingType.setVisibility(View.VISIBLE);
            binding.textBillingType.setBackgroundResource(R.drawable.bg_chip_gray);
            binding.textBillingType.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        }

        NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));
        String paymentStatus = order.getResolvedPaymentStatus();
        if (paymentStatus == null || paymentStatus.trim().isEmpty()) {
            binding.textPaymentStatusBadge.setText("NOT AVAILABLE");
        } else {
            binding.textPaymentStatusBadge.setText(formatPaymentStatusLabel(paymentStatus));
        }
        setPaymentStatusStyle(paymentStatus);

        String utrNumber = order.getResolvedUtrNumber();
        binding.textPaymentUtr.setText((utrNumber != null && !utrNumber.trim().isEmpty()) ? utrNumber : "-");
        binding.textPaymentPayableAmount.setText(currencyFormat.format(order.getResolvedPayableAmount()));

        binding.textSubtotal.setText(currencyFormat.format(order.getOrderSubtotal()));

        double rewardDiscount = order.getRewardDiscount();
        Order.RewardRedeemed redeemedReward = order.getRewardRedeemed();
        if (rewardDiscount > 0) {
            binding.rowRewardDiscount.setVisibility(View.VISIBLE);
            binding.textRewardDiscount.setText("- " + currencyFormat.format(rewardDiscount));
            if (redeemedReward != null && redeemedReward.getTitle() != null && !redeemedReward.getTitle().trim().isEmpty()) {
                StringBuilder rewardLabel = new StringBuilder(redeemedReward.getTitle().trim());
                if (redeemedReward.getType() != null && !redeemedReward.getType().trim().isEmpty()) {
                    rewardLabel.append(" • ").append(redeemedReward.getType().replace('_', ' '));
                }
                binding.textRewardLabel.setText(rewardLabel.toString());
                binding.textRewardLabel.setVisibility(View.VISIBLE);
            } else {
                binding.textRewardLabel.setVisibility(View.GONE);
            }
        } else {
            binding.rowRewardDiscount.setVisibility(View.GONE);
        }

        if (isWithBill || order.getOrderTax() > 0) {
            binding.rowTax.setVisibility(View.VISIBLE);
            binding.textTax.setText("+ " + currencyFormat.format(order.getOrderTax()));
        } else {
            binding.rowTax.setVisibility(View.GONE);
        }

        binding.textTotalAmount.setText(currencyFormat.format(order.getTotalAmount()));

        if (order.getItems() != null) {
            itemAdapter.updateItems(order.getItems());
        }
    }

    private void showPhoneOptions(View v, String phoneNumber) {
        PopupMenu popup = new PopupMenu(this, v);
        popup.getMenu().add("Call Customer");
        popup.getMenu().add("Chat on WhatsApp");
        popup.setOnMenuItemClickListener(item -> {
            if (item.getTitle().equals("Call Customer")) {
                Intent intent = new Intent(Intent.ACTION_DIAL);
                intent.setData(Uri.parse("tel:" + phoneNumber));
                startActivity(intent);
            } else if (item.getTitle().equals("Chat on WhatsApp")) {
                String cleanNumber = phoneNumber.replace("+", "").replace(" ", "").replace("-", "");
                if (!cleanNumber.startsWith("91") && cleanNumber.length() == 10)
                    cleanNumber = "91" + cleanNumber;
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(Uri.parse("https://wa.me/" + cleanNumber));
                startActivity(intent);
            }
            return true;
        });
        popup.show();
    }

    private void setupCustomerNameNavigation(Order order, String customerName, String customerPhone) {
        String customerUserId = order != null && order.getUserId() != null ? order.getUserId().trim() : "";
        if (customerUserId.isEmpty()) {
            binding.textCustomerName.setOnClickListener(null);
            binding.textCustomerName.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
            return;
        }

        binding.textCustomerName.setTextColor(ContextCompat.getColor(this, R.color.primary));
        binding.textCustomerName.setOnClickListener(v -> {
            Intent intent = new Intent(this, CustomerDetailsActivity.class);
            intent.putExtra(CustomerDetailsActivity.EXTRA_CUSTOMER_USER_ID, customerUserId);
            intent.putExtra(CustomerDetailsActivity.EXTRA_CUSTOMER_NAME_FALLBACK, customerName);
            intent.putExtra(CustomerDetailsActivity.EXTRA_CUSTOMER_PHONE_FALLBACK, customerPhone);
            startActivity(intent);
        });
    }

    private void showAddressOptions(View v, String address) {
        PopupMenu popup = new PopupMenu(this, v);
        popup.getMenu().add("View on Google Maps");
        popup.getMenu().add("Copy Address");
        popup.setOnMenuItemClickListener(item -> {
            if (item.getTitle().equals("View on Google Maps")) {
                Uri gmmIntentUri = Uri.parse("geo:0,0?q=" + Uri.encode(address));
                Intent mapIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
                mapIntent.setPackage("com.google.android.apps.maps");
                if (mapIntent.resolveActivity(getPackageManager()) != null)
                    startActivity(mapIntent);
                else
                    startActivity(new Intent(Intent.ACTION_VIEW,
                            Uri.parse("https://www.google.com/maps/search/?api=1&query=" + Uri.encode(address))));
            } else if (item.getTitle().equals("Copy Address")) {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("Shipping Address", address);
                clipboard.setPrimaryClip(clip);
                Toast.makeText(this, "Address copied", Toast.LENGTH_SHORT).show();
            }
            return true;
        });
        popup.show();
    }

    // --- UPDATED PDF & SHARE LOGIC ---

    private void prepareAndShareInvoice() {
        if (currentOrder == null) {
            Toast.makeText(this, "Order not loaded.", Toast.LENGTH_SHORT).show();
            return;
        }

        String userId;
        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        } else {
            Toast.makeText(this, "Login required to share.", Toast.LENGTH_SHORT).show();
            return;
        }

        binding.btnShareInvoice.setEnabled(false);
        binding.btnShareInvoice.setText("Fetching Profile & Images...");
        Toast.makeText(this, "Preparing Invoice...", Toast.LENGTH_SHORT).show();

        executor.execute(() -> {
            try {
                // 1. Fetch Seller Profile from Firestore
                DocumentSnapshot userDoc = Tasks.await(db.collection("users").document(userId).get());
                SellerProfile profile = userDoc.exists() ? userDoc.toObject(SellerProfile.class) : new SellerProfile();

                // 2. Download Logo and Signature if available
                Bitmap logoBitmap = null;
                Bitmap signatureBitmap = null;

                if (profile != null) {
                    if (profile.getProfileImageUrl() != null && !profile.getProfileImageUrl().isEmpty()) {
                        try {
                            logoBitmap = Glide.with(getApplicationContext())
                                    .asBitmap()
                                    .load(profile.getProfileImageUrl())
                                    .submit()
                                    .get();
                        } catch (Exception e) {
                            Log.e(TAG, "Failed to load logo", e);
                        }
                    }

                    if (profile.getSignatureImageUrl() != null && !profile.getSignatureImageUrl().isEmpty()) {
                        try {
                            signatureBitmap = Glide.with(getApplicationContext())
                                    .asBitmap()
                                    .load(profile.getSignatureImageUrl())
                                    .submit()
                                    .get();
                        } catch (Exception e) {
                            Log.e(TAG, "Failed to load signature", e);
                        }
                    }
                }

                // 3. Download Product Images
                Map<String, Bitmap> imageCache = new HashMap<>();
                if (currentOrder.getItems() != null) {
                    for (OrderItem item : currentOrder.getItems()) {
                        String url = item.getImageUrl();
                        if (url != null && !url.isEmpty() && !imageCache.containsKey(url)) {
                            try {
                                Bitmap bitmap = Glide.with(getApplicationContext())
                                        .asBitmap()
                                        .load(url)
                                        .submit()
                                        .get();
                                imageCache.put(url, bitmap);
                            } catch (Exception e) {
                                Log.e(TAG, "Failed to load item image: " + url, e);
                            }
                        }
                    }
                }

                // 4. Generate PDF with all data
                // Need final versions for lambda
                SellerProfile finalProfile = profile;
                Bitmap finalLogo = logoBitmap;
                Bitmap finalSign = signatureBitmap;

                mainHandler.post(() -> {
                    File pdfFile = PdfGenerator.generateInvoice(
                            OrderDetailActivity.this,
                            currentOrder,
                            imageCache,
                            finalProfile,
                            finalLogo,
                            finalSign);

                    binding.btnShareInvoice.setEnabled(true);
                    binding.btnShareInvoice.setText("Download & Share Invoice");

                    if (pdfFile != null && pdfFile.exists()) {
                        sharePdfFile(pdfFile);
                    } else {
                        Toast.makeText(OrderDetailActivity.this, "Failed to generate PDF.", Toast.LENGTH_SHORT).show();
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "Error generating invoice data", e);
                mainHandler.post(() -> {
                    binding.btnShareInvoice.setEnabled(true);
                    binding.btnShareInvoice.setText("Download & Share Invoice");
                    Toast.makeText(OrderDetailActivity.this, "Error preparing invoice: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void sharePdfFile(File file) {
        try {
            Uri uri = FileProvider.getUriForFile(
                    this,
                    getApplicationContext().getPackageName() + ".provider",
                    file);

            StringBuilder msg = new StringBuilder();
            msg.append("Invoice for Order #").append(currentOrder.getId().toUpperCase()).append("\n\n");
            NumberFormat currency = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));
            msg.append("Total Amount: ").append(currency.format(currentOrder.getTotalAmount())).append("\n");
            msg.append("Please find the invoice attached.");

            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("Invoice Message", msg.toString());
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, "Message copied! Paste it in WhatsApp.", Toast.LENGTH_LONG).show();

            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("application/pdf");
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.putExtra(Intent.EXTRA_SUBJECT, "Invoice - #" + currentOrder.getId());
            intent.putExtra(Intent.EXTRA_TEXT, msg.toString());
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, "Share Invoice"));
        } catch (Exception e) {
            Log.e(TAG, "Error sharing file", e);
            Toast.makeText(this, "Error sharing file.", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateOrderStatus() {
        if (orderRef == null) {
            Toast.makeText(this, "Order reference not loaded yet.", Toast.LENGTH_SHORT).show();
            return;
        }
        String newStatus = binding.spinnerStatus.getSelectedItem().toString();
        binding.btnUpdateStatus.setEnabled(false);
        binding.btnUpdateStatus.setText("Updating...");

        orderRef.update("status", newStatus)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Status updated!", Toast.LENGTH_SHORT).show();
                    binding.statusBadge.setText(newStatus);
                    setStatusStyle(newStatus);
                    binding.btnUpdateStatus.setEnabled(true);
                    binding.btnUpdateStatus.setText("Update");
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Update failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    binding.btnUpdateStatus.setEnabled(true);
                    binding.btnUpdateStatus.setText("Update");
                });
    }

    private void setStatusStyle(String status) {
        int bgRes = R.drawable.bg_chip_gray;
        int colorRes = R.color.text_secondary;
        if (status != null) {
            switch (status.toLowerCase()) {
                case "placed":
                case "pending":
                    bgRes = R.drawable.bg_chip_blue;
                    colorRes = R.color.info;
                    break;
                case "processing":
                    bgRes = R.drawable.bg_chip_orange;
                    colorRes = R.color.warning;
                    break;
                case "shipped":
                    bgRes = R.drawable.bg_chip_orange;
                    colorRes = R.color.warning;
                    break;
                case "delivered":
                case "completed":
                    bgRes = R.drawable.bg_chip_green;
                    colorRes = R.color.success;
                    break;
                case "cancelled":
                case "failed":
                    bgRes = R.drawable.bg_chip_red;
                    colorRes = R.color.error;
                    break;
            }
        }
        binding.statusBadge.setBackgroundResource(bgRes);
        binding.statusBadge.setTextColor(ContextCompat.getColor(this, colorRes));
    }

    private String formatPaymentStatusLabel(String status) {
        if (status == null || status.trim().isEmpty()) {
            return "NOT AVAILABLE";
        }
        return status.trim().toUpperCase(Locale.ROOT).replace('_', ' ');
    }

    private void setPaymentStatusStyle(String status) {
        int bgRes = R.drawable.bg_chip_gray;
        int colorRes = R.color.text_secondary;

        String normalized = status != null ? status.trim().toUpperCase(Locale.ROOT) : "";
        switch (normalized) {
            case "PENDING":
            case "UTR_SUBMITTED":
            case "PAYMENT_UNDER_REVIEW":
                bgRes = R.drawable.bg_chip_orange;
                colorRes = R.color.warning;
                break;
            case "RECONCILED":
                bgRes = R.drawable.bg_chip_green;
                colorRes = R.color.success;
                break;
            case "DISPUTED":
                bgRes = R.drawable.bg_chip_red;
                colorRes = R.color.error;
                break;
            case "EXPIRED":
            case "CANCELLED_BY_BUYER":
                bgRes = R.drawable.bg_chip_gray;
                colorRes = R.color.text_secondary;
                break;
            default:
                if (!normalized.isEmpty()) {
                    bgRes = R.drawable.bg_chip_blue;
                    colorRes = R.color.info;
                }
                break;
        }

        binding.textPaymentStatusBadge.setBackgroundResource(bgRes);
        binding.textPaymentStatusBadge.setTextColor(ContextCompat.getColor(this, colorRes));
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}