package com.easysell;

import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.easysell.databinding.ActivityCustomerDetailsBinding;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class CustomerDetailsActivity extends AppCompatActivity {

    public static final String EXTRA_CUSTOMER_USER_ID = "CUSTOMER_USER_ID";
    public static final String EXTRA_CUSTOMER_NAME_FALLBACK = "CUSTOMER_NAME_FALLBACK";
    public static final String EXTRA_CUSTOMER_PHONE_FALLBACK = "CUSTOMER_PHONE_FALLBACK";

    private static final String TAG = "CustomerDetails";

    private ActivityCustomerDetailsBinding binding;
    private FirebaseFirestore db;
    private CustomerOrderHistoryAdapter orderHistoryAdapter;
    private CustomerPaymentHistoryAdapter paymentHistoryAdapter;

    private String customerUserId;
    private String customerNameFallback;
    private String customerPhoneFallback;
    private String currentSellerId;

    private final NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCustomerDetailsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Customer Details");
        }

        db = FirebaseFirestore.getInstance();
        currentSellerId = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid()
                : null;

        customerUserId = safeTrim(getIntent().getStringExtra(EXTRA_CUSTOMER_USER_ID));
        customerNameFallback = safeTrim(getIntent().getStringExtra(EXTRA_CUSTOMER_NAME_FALLBACK));
        customerPhoneFallback = safeTrim(getIntent().getStringExtra(EXTRA_CUSTOMER_PHONE_FALLBACK));

        if (customerUserId.isEmpty()) {
            Toast.makeText(this, "Customer details are unavailable.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        setupLists();
        loadCustomerInsights();
    }

    private void setupLists() {
        orderHistoryAdapter = new CustomerOrderHistoryAdapter();
        paymentHistoryAdapter = new CustomerPaymentHistoryAdapter();

        binding.recyclerOrderHistory.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerOrderHistory.setAdapter(orderHistoryAdapter);
        binding.recyclerOrderHistory.setNestedScrollingEnabled(false);

        binding.recyclerPaymentHistory.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerPaymentHistory.setAdapter(paymentHistoryAdapter);
        binding.recyclerPaymentHistory.setNestedScrollingEnabled(false);
    }

    private void loadCustomerInsights() {
        setLoading(true);

        Query ordersQuery = db.collectionGroup("orders")
                .whereEqualTo("userId", customerUserId);

        if (currentSellerId != null && !currentSellerId.trim().isEmpty()) {
            ordersQuery = ordersQuery.whereEqualTo("sellerId", currentSellerId);
        }

        Tasks.whenAllSuccess(
                        db.collection("users").document(customerUserId).get(),
                        ordersQuery.get()
                )
                .addOnSuccessListener(results -> {
                    DocumentSnapshot profileDoc = (DocumentSnapshot) results.get(0);
                    QuerySnapshot ordersSnapshot = (QuerySnapshot) results.get(1);

                    List<Order> orders = mapOrders(ordersSnapshot);
                    renderCustomerProfile(profileDoc, orders);
                    renderHistory(orders);
                    setLoading(false);
                })
                .addOnFailureListener(error -> {
                    Log.e(TAG, "Failed to load customer insights", error);
                    renderCustomerProfile(null, Collections.emptyList());
                    renderHistory(Collections.emptyList());
                    setLoading(false);
                    Toast.makeText(this, "Could not load full customer details.", Toast.LENGTH_SHORT).show();
                });
    }

    private List<Order> mapOrders(QuerySnapshot snapshot) {
        List<Order> orders = new ArrayList<>();
        if (snapshot == null || snapshot.isEmpty()) {
            return orders;
        }

        for (DocumentSnapshot doc : snapshot.getDocuments()) {
            Order order = doc.toObject(Order.class);
            if (order == null) {
                continue;
            }
            order.setId(doc.getId());
            orders.add(order);
        }

        Collections.sort(orders, new Comparator<Order>() {
            @Override
            public int compare(Order first, Order second) {
                return Long.compare(getOrderDateMs(second), getOrderDateMs(first));
            }
        });

        return orders;
    }

    private void renderCustomerProfile(DocumentSnapshot profileDoc, List<Order> orders) {
        String displayName = firstNonEmpty(
                profileDoc != null ? profileDoc.getString("displayName") : null,
                profileDoc != null ? profileDoc.getString("ownerName") : null,
                customerNameFallback,
                extractLatestCustomerName(orders),
                "Customer"
        );

        String phone = firstNonEmpty(
                profileDoc != null ? profileDoc.getString("contactPhone") : null,
                profileDoc != null ? profileDoc.getString("phone") : null,
                customerPhoneFallback,
                extractLatestCustomerPhone(orders),
                "N/A"
        );

        String email = firstNonEmpty(
                profileDoc != null ? profileDoc.getString("email") : null,
                profileDoc != null ? profileDoc.getString("contactEmail") : null,
                "N/A"
        );

        String whatsapp = firstNonEmpty(
                profileDoc != null ? profileDoc.getString("contactWhatsapp") : null,
                phone,
                "N/A"
        );

        String address = firstNonEmpty(
                profileDoc != null ? profileDoc.getString("contactAddress") : null,
                extractLatestShippingAddress(orders),
                "N/A"
        );

        String storeHandle = firstNonEmpty(
                profileDoc != null ? profileDoc.getString("storeHandle") : null,
                "N/A"
        );

        binding.textCustomerName.setText(displayName);
        binding.textCustomerUserId.setText("ID: " + customerUserId);
        binding.textCustomerEmail.setText(email);
        binding.textCustomerPhone.setText(phone);
        binding.textCustomerWhatsapp.setText(whatsapp);
        binding.textCustomerAddress.setText(address);
        binding.textCustomerStoreHandle.setText(storeHandle);
    }

    private void renderHistory(List<Order> orders) {
        List<CustomerOrderHistoryAdapter.OrderHistoryItem> orderItems = new ArrayList<>();
        List<CustomerPaymentHistoryAdapter.PaymentHistoryItem> paymentItems = new ArrayList<>();

        double totalSpent = 0d;
        int reconciledCount = 0;

        for (Order order : orders) {
            if (order == null) {
                continue;
            }

            int itemCount = order.getItems() != null ? order.getItems().size() : 0;
            orderItems.add(new CustomerOrderHistoryAdapter.OrderHistoryItem(
                    order.getId(),
                    getOrderDateMs(order),
                    firstNonEmpty(order.getStatus(), "NOT_AVAILABLE"),
                    firstNonEmpty(order.getResolvedPaymentStatus(), "NOT_AVAILABLE"),
                    order.getTotalAmount(),
                    itemCount
            ));

            totalSpent += order.getTotalAmount();

            if (order.hasPaymentRecord()) {
                String paymentStatus = firstNonEmpty(order.getResolvedPaymentStatus(), "NOT_AVAILABLE");
                paymentItems.add(new CustomerPaymentHistoryAdapter.PaymentHistoryItem(
                        order.getId(),
                        order.getPaymentOrderId(),
                        paymentStatus,
                        order.getResolvedUtrNumber(),
                        order.getResolvedPayableAmount(),
                        derivePaymentEventTime(order)
                ));

                if ("RECONCILED".equalsIgnoreCase(paymentStatus)) {
                    reconciledCount += 1;
                }
            }
        }

        orderHistoryAdapter.setItems(orderItems);
        paymentHistoryAdapter.setItems(paymentItems);

        binding.textNoOrders.setVisibility(orderItems.isEmpty() ? View.VISIBLE : View.GONE);
        binding.recyclerOrderHistory.setVisibility(orderItems.isEmpty() ? View.GONE : View.VISIBLE);

        binding.textNoPayments.setVisibility(paymentItems.isEmpty() ? View.VISIBLE : View.GONE);
        binding.recyclerPaymentHistory.setVisibility(paymentItems.isEmpty() ? View.GONE : View.VISIBLE);

        binding.textStatTotalOrders.setText(String.valueOf(orderItems.size()));
        binding.textStatTotalSpent.setText(currencyFormat.format(totalSpent));
        binding.textStatPaymentRecords.setText(String.valueOf(paymentItems.size()));
        binding.textStatReconciledPayments.setText(String.valueOf(reconciledCount));

        if (!orders.isEmpty() && getOrderDateMs(orders.get(0)) > 0) {
            binding.textStatLastOrder.setText(dateFormat.format(new Date(getOrderDateMs(orders.get(0)))));
        } else {
            binding.textStatLastOrder.setText("-");
        }
    }

    private void setLoading(boolean loading) {
        binding.progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        binding.scrollContent.setVisibility(loading ? View.GONE : View.VISIBLE);
    }

    private long getOrderDateMs(Order order) {
        if (order == null || order.getOrderDate() == null) {
            return 0L;
        }
        return order.getOrderDate().getTime();
    }

    private Long derivePaymentEventTime(Order order) {
        if (order == null) {
            return null;
        }

        if (order.getPaymentCancelledAtMs() != null && order.getPaymentCancelledAtMs() > 0L) {
            return order.getPaymentCancelledAtMs();
        }

        long orderDate = getOrderDateMs(order);
        if (orderDate > 0L) {
            return orderDate;
        }

        if (order.getPaymentExpiresAtMs() != null && order.getPaymentExpiresAtMs() > 0L) {
            return order.getPaymentExpiresAtMs();
        }

        return null;
    }

    private String extractLatestCustomerName(List<Order> orders) {
        if (orders == null) {
            return null;
        }
        for (Order order : orders) {
            if (order == null || order.getShippingAddress() == null) {
                continue;
            }
            String candidate = order.getShippingAddress().getName();
            if (candidate != null && !candidate.trim().isEmpty()) {
                return candidate.trim();
            }
        }
        return null;
    }

    private String extractLatestCustomerPhone(List<Order> orders) {
        if (orders == null) {
            return null;
        }
        for (Order order : orders) {
            if (order == null || order.getShippingAddress() == null) {
                continue;
            }
            String candidate = order.getShippingAddress().getPhone();
            if (candidate != null && !candidate.trim().isEmpty()) {
                return candidate.trim();
            }
        }
        return null;
    }

    private String extractLatestShippingAddress(List<Order> orders) {
        if (orders == null) {
            return null;
        }
        for (Order order : orders) {
            if (order == null || order.getShippingAddress() == null) {
                continue;
            }

            Order.ShippingAddress address = order.getShippingAddress();
            String formatted = String.format(
                    Locale.getDefault(),
                    "%s, %s - %s",
                    safeTrim(address.getAddress()),
                    safeTrim(address.getCity()),
                    safeTrim(address.getPincode())
            ).replace(" ,", "").replace(", -", "").replaceAll("^, ", "").trim();

            if (!formatted.isEmpty()) {
                return formatted;
            }
        }
        return null;
    }

    private String firstNonEmpty(String... values) {
        if (values == null) {
            return null;
        }

        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return null;
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
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
