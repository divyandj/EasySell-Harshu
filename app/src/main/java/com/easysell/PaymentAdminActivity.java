package com.easysell;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.bumptech.glide.Glide;
import com.cloudinary.android.MediaManager;
import com.cloudinary.android.callback.ErrorInfo;
import com.cloudinary.android.callback.UploadCallback;
import com.easysell.databinding.ActivityPaymentAdminBinding;
import com.easysell.paymentadmin.PaymentAdminRepository;
import com.easysell.paymentadmin.model.AdminConfirmResult;
import com.easysell.paymentadmin.model.BucketDto;
import com.easysell.paymentadmin.model.CreateBucketRequest;
import com.easysell.paymentadmin.model.PaymentOrderItem;
import com.easysell.paymentadmin.model.ReopenResult;
import com.easysell.paymentadmin.model.UpdateBucketRequest;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.android.material.badge.BadgeDrawable;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

public class PaymentAdminActivity extends AppCompatActivity implements PaymentAdminOrderAdapter.ActionListener, PaymentAdminBucketLedgerAdapter.ActionListener {

    private enum QueueMode {
        PENDING,
        REVIEW,
        HISTORY,
        COLLECTION_ACCOUNTS
    }

    private enum HistoryFilter {
        ALL,
        RECONCILED,
        DISPUTED,
        CANCELLED,
        EXPIRED
    }

    private enum SyncState {
        LIVE,
        SNAPSHOT_FALLBACK,
        ERROR
    }

    private static final String EXTRA_ENABLE_READ_FALLBACK = "ENABLE_PAYMENT_READ_FALLBACK";
    private static final Pattern UPI_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9._\\-]{2,}@[a-zA-Z0-9.-]{2,}$");

    private static class TabPaginationState {
        boolean isLoading;
        Long nextCursor;
        final List<PaymentOrderItem> items = new ArrayList<>();

        void reset() {
            nextCursor = null;
            items.clear();
        }

        boolean hasMore() {
            return nextCursor != null;
        }
    }

    private ActivityPaymentAdminBinding binding;
    private PaymentAdminOrderAdapter orderAdapter;
    private PaymentAdminBucketLedgerAdapter bucketLedgerAdapter;
    private PaymentAdminRepository repository;

    private QueueMode currentMode = QueueMode.PENDING;
    private HistoryFilter currentHistoryFilter = HistoryFilter.ALL;
    private final TabPaginationState pendingState = new TabPaginationState();
    private final TabPaginationState reviewState = new TabPaginationState();
    private final TabPaginationState historyState = new TabPaginationState();
    private boolean isCollectionAccountLoading = false;
    private final List<String> historyFilterLabels = new ArrayList<>();
    private String storeHandleScope = "";
    private ListenerRegistration pendingOrdersListener;
    private ListenerRegistration reviewOrdersListener;
    private ListenerRegistration historyOrdersListener;
    private ListenerRegistration bucketListener;
    private boolean readFallbackEnabled = true;
    private SyncState syncState = SyncState.LIVE;
    private String syncStateDetail = "Realtime sync active.";
    private final Handler handler = new Handler(Looper.getMainLooper());

    private Uri selectedQrImageUri;
    private AlertDialog createBucketDialog;
    private EditText createBucketUpiInput;
    private ImageView createBucketQrPreview;
    private TextView createBucketQrStatus;
    private Button createBucketSelectQrButton;
    private Button createBucketClearQrButton;
    private boolean isCreateBucketInProgress = false;

    private Uri selectedEditQrImageUri;
    private String editBucketExistingQrUrl;
    private AlertDialog editBucketDialog;
    private EditText editBucketUpiInput;
    private ImageView editBucketQrPreview;
    private TextView editBucketQrStatus;
    private Button editBucketSelectQrButton;
    private Button editBucketClearQrButton;
    private boolean isEditBucketInProgress = false;

    private interface QrStatusCallback {
        void onStatus(String message);
    }

    private final ActivityResultLauncher<String> qrImagePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri == null) {
                    return;
                }

                selectedQrImageUri = uri;

                if (createBucketQrPreview != null) {
                    createBucketQrPreview.setVisibility(View.VISIBLE);
                    Glide.with(this)
                            .load(uri)
                            .centerCrop()
                            .into(createBucketQrPreview);
                }

                if (createBucketClearQrButton != null) {
                    createBucketClearQrButton.setVisibility(View.VISIBLE);
                }

                setCreateBucketStatus("QR image selected. Scanning for UPI ID...");
                tryExtractUpiIdFromQr(uri, createBucketUpiInput, this::setCreateBucketStatus);
                updateCreateBucketDialogControls();
            }
    );

    private final ActivityResultLauncher<String> editQrImagePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri == null) {
                    return;
                }

                selectedEditQrImageUri = uri;

                if (editBucketQrPreview != null) {
                    editBucketQrPreview.setVisibility(View.VISIBLE);
                    Glide.with(this)
                            .load(uri)
                            .centerCrop()
                            .into(editBucketQrPreview);
                }

                if (editBucketClearQrButton != null) {
                    editBucketClearQrButton.setVisibility(View.VISIBLE);
                }

                setEditBucketStatus("New QR image selected. Scanning for UPI ID...");
                tryExtractUpiIdFromQr(uri, editBucketUpiInput, this::setEditBucketStatus);
                updateEditBucketDialogControls();
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityPaymentAdminBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        storeHandleScope = String.valueOf(getIntent().getStringExtra("STORE_HANDLE") == null
                ? ""
                : getIntent().getStringExtra("STORE_HANDLE")).trim().toLowerCase(Locale.ROOT);
        if (storeHandleScope.isEmpty()) {
            Toast.makeText(this, "Store scope missing. Open Collection Accounts from Home.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        repository = new PaymentAdminRepository();
        readFallbackEnabled = getIntent().getBooleanExtra(EXTRA_ENABLE_READ_FALLBACK, true);
        repository.setRealtimeReadMode(
            readFallbackEnabled
                ? PaymentAdminRepository.RealtimeReadMode.FIRESTORE_THEN_API_SNAPSHOT
                : PaymentAdminRepository.RealtimeReadMode.FIRESTORE_ONLY
        );
        orderAdapter = new PaymentAdminOrderAdapter(this);
        bucketLedgerAdapter = new PaymentAdminBucketLedgerAdapter(this);

        binding.recyclerView.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerView.setAdapter(orderAdapter);

        setupHistoryFilter();
        setupBottomNavigation();
        setupButtons();
        binding.swipeRefresh.setOnRefreshListener(this::loadInitial);
        updateRealtimeCounterChips();
        renderSyncStateBanner();
    }

    private void setupHistoryFilter() {
        historyFilterLabels.add("All");
        historyFilterLabels.add("Reconciled");
        historyFilterLabels.add("Disputed");
        historyFilterLabels.add("Cancelled");
        historyFilterLabels.add("Expired");

        ArrayAdapter<String> filterAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, historyFilterLabels);
        filterAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.spinnerHistoryFilter.setAdapter(filterAdapter);
        binding.spinnerHistoryFilter.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                HistoryFilter selected = mapFilter(position);
                if (selected != currentHistoryFilter) {
                    currentHistoryFilter = selected;
                    applyHistoryFilter();
                }
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
            }
        });
    }

    private void setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_pending) {
                currentMode = QueueMode.PENDING;
            } else if (id == R.id.nav_review) {
                currentMode = QueueMode.REVIEW;
            } else if (id == R.id.nav_history) {
                currentMode = QueueMode.HISTORY;
            } else {
                currentMode = QueueMode.COLLECTION_ACCOUNTS;
            }
            loadInitial();
            return true;
        });

        binding.bottomNavigation.setSelectedItemId(R.id.nav_pending);
    }

    private void setupButtons() {
        binding.buttonRefresh.setOnClickListener(v -> triggerFullRefresh());
        binding.buttonLoadMore.setOnClickListener(v -> loadMore());
        binding.buttonCreateBucket.setOnClickListener(v -> showCreateCollectionAccountDialog());
        binding.buttonRetrySync.setOnClickListener(v -> triggerFullRefresh());
        binding.buttonEmptyAction.setOnClickListener(v -> triggerFullRefresh());
    }

    private void loadInitial() {
        if (currentMode == QueueMode.COLLECTION_ACCOUNTS) {
            loadCollectionAccountsData();
            return;
        }

        boolean forceReload = binding.swipeRefresh.isRefreshing()
                || pendingOrdersListener == null
                || reviewOrdersListener == null
                || historyOrdersListener == null;

        if (forceReload) {
            startRealtimeOrderListeners();
        } else {
            binding.progressBar.setVisibility(View.GONE);
            binding.swipeRefresh.setRefreshing(false);
            updateModeUi();
            renderCurrentOrderModeFromState();
        }
    }

    private void loadMore() {
        binding.buttonLoadMore.setVisibility(View.GONE);
        Toast.makeText(this, "Realtime sync is active. Latest items load automatically.", Toast.LENGTH_SHORT).show();
    }

    private void startRealtimeOrderListeners() {
        stopRealtimeOrderListeners();
        setSyncState(SyncState.LIVE, "Realtime sync active.");

        pendingState.reset();
        reviewState.reset();
        historyState.reset();
        pendingState.isLoading = true;
        reviewState.isLoading = true;
        historyState.isLoading = true;

        binding.progressBar.setVisibility(View.VISIBLE);
        binding.swipeRefresh.setRefreshing(true);
        updateModeUi();

        pendingOrdersListener = repository.subscribePendingOrders(storeHandleScope, new PaymentAdminRepository.ResultCallback<List<PaymentOrderItem>>() {
            @Override
            public void onSuccess(List<PaymentOrderItem> data) {
                updateStateFromRealtime(pendingState, data);
                updateTabBadge(R.id.nav_pending, pendingState.items.size());
                onOrderStreamUpdated(QueueMode.PENDING);
            }

            @Override
            public void onError(String message, String code) {
                pendingState.isLoading = false;
                onOrderStreamError(QueueMode.PENDING, message, code);
            }
        });

        reviewOrdersListener = repository.subscribeReviewOrders(storeHandleScope, new PaymentAdminRepository.ResultCallback<List<PaymentOrderItem>>() {
            @Override
            public void onSuccess(List<PaymentOrderItem> data) {
                updateStateFromRealtime(reviewState, data);
                updateTabBadge(R.id.nav_review, reviewState.items.size());
                onOrderStreamUpdated(QueueMode.REVIEW);
            }

            @Override
            public void onError(String message, String code) {
                reviewState.isLoading = false;
                onOrderStreamError(QueueMode.REVIEW, message, code);
            }
        });

        historyOrdersListener = repository.subscribeHistoryOrders(storeHandleScope, new PaymentAdminRepository.ResultCallback<List<PaymentOrderItem>>() {
            @Override
            public void onSuccess(List<PaymentOrderItem> data) {
                updateStateFromRealtime(historyState, data);
                updateTabBadge(R.id.nav_history, historyState.items.size());
                onOrderStreamUpdated(QueueMode.HISTORY);
            }

            @Override
            public void onError(String message, String code) {
                historyState.isLoading = false;
                onOrderStreamError(QueueMode.HISTORY, message, code);
            }
        });
    }

    private void stopRealtimeOrderListeners() {
        if (pendingOrdersListener != null) {
            pendingOrdersListener.remove();
            pendingOrdersListener = null;
        }
        if (reviewOrdersListener != null) {
            reviewOrdersListener.remove();
            reviewOrdersListener = null;
        }
        if (historyOrdersListener != null) {
            historyOrdersListener.remove();
            historyOrdersListener = null;
        }
    }

    private void updateStateFromRealtime(TabPaginationState state, List<PaymentOrderItem> data) {
        state.isLoading = false;
        state.nextCursor = null;
        state.items.clear();
        if (data != null) {
            state.items.addAll(data);
        }
    }

    private void onOrderStreamUpdated(QueueMode updatedMode) {
        updateRealtimeCounterChips();

        if (currentMode == updatedMode) {
            renderCurrentOrderModeFromState();
        }

        finishOrderLoadingIfSettled();
    }

    private void onOrderStreamError(QueueMode mode, String message, String code) {
        if (PaymentAdminRepository.CODE_REALTIME_FALLBACK_ACTIVE.equals(code)) {
            setSyncState(SyncState.SNAPSHOT_FALLBACK, message);
            if (currentMode == mode) {
                renderCurrentOrderModeFromState();
            }
            finishOrderLoadingIfSettled();
            return;
        }

        setSyncState(SyncState.ERROR, message);

        if (currentMode == mode) {
            handleApiError(code, message);
            renderCurrentOrderModeFromState();
        }

        finishOrderLoadingIfSettled();
    }

    private void renderCurrentOrderModeFromState() {
        updateModeUi();

        if (currentMode == QueueMode.PENDING) {
            orderAdapter.setItems(new ArrayList<>(pendingState.items));
            renderOrderEmptyState(pendingState.items, "No pending payment submissions.");
            return;
        }

        if (currentMode == QueueMode.REVIEW) {
            orderAdapter.setItems(new ArrayList<>(reviewState.items));
            renderOrderEmptyState(reviewState.items, "No orders under payment review.");
            return;
        }

        if (currentMode == QueueMode.HISTORY) {
            applyHistoryFilter();
        }
    }

    private void loadCollectionAccountsData() {
        if (bucketListener != null) {
            bucketListener.remove();
            bucketListener = null;
        }

        setSyncState(SyncState.LIVE, "Realtime sync active.");
        isCollectionAccountLoading = true;
        updateModeUi();
        binding.progressBar.setVisibility(View.VISIBLE);

        bucketListener = repository.subscribeBuckets(storeHandleScope, new PaymentAdminRepository.ResultCallback<List<BucketDto>>() {
            @Override
            public void onSuccess(List<BucketDto> buckets) {
                isCollectionAccountLoading = false;
                binding.progressBar.setVisibility(View.GONE);
                binding.swipeRefresh.setRefreshing(false);

                List<BucketDto> safeBuckets = buckets != null ? buckets : new ArrayList<>();
                bucketLedgerAdapter.setData(safeBuckets);
                renderBucketEmptyState(safeBuckets);
                updateTabBadge(R.id.nav_buckets, countActiveBuckets(safeBuckets));
            }

            @Override
            public void onError(String message, String code) {
                isCollectionAccountLoading = false;
                binding.progressBar.setVisibility(View.GONE);
                binding.swipeRefresh.setRefreshing(false);

                if (PaymentAdminRepository.CODE_REALTIME_FALLBACK_ACTIVE.equals(code)) {
                    setSyncState(SyncState.SNAPSHOT_FALLBACK, message);
                    return;
                }

                setSyncState(SyncState.ERROR, message);
                handleApiError(code, message);
                renderBucketEmptyState(Collections.emptyList());
            }
        });
    }

    private void updateModeUi() {
        boolean isHistory = currentMode == QueueMode.HISTORY;
        boolean isCollectionAccounts = currentMode == QueueMode.COLLECTION_ACCOUNTS;

        binding.spinnerHistoryFilter.setVisibility(isHistory ? View.VISIBLE : View.GONE);
        binding.layoutBucketLedgerControls.setVisibility(isCollectionAccounts ? View.VISIBLE : View.GONE);
        binding.buttonLoadMore.setVisibility(View.GONE);
        binding.swipeRefresh.setEnabled(true);
        hideEmptyState();

        if (isCollectionAccounts) {
            binding.recyclerView.setAdapter(bucketLedgerAdapter);
        } else {
            binding.recyclerView.setAdapter(orderAdapter);
        }
    }

    private int countActiveBuckets(List<BucketDto> buckets) {
        int active = 0;
        for (BucketDto bucket : buckets) {
            if (bucket != null && bucket.status != null && "ACTIVE".equalsIgnoreCase(bucket.status)) {
                active += 1;
            }
        }
        return active;
    }

    private void updateRealtimeCounterChips() {
        binding.textChipPendingCount.setText("Pending " + pendingState.items.size());
        binding.textChipReviewCount.setText("Review " + reviewState.items.size());
        binding.textChipHistoryCount.setText("History " + historyState.items.size());
    }

    private void triggerFullRefresh() {
        binding.swipeRefresh.setRefreshing(true);
        loadInitial();
    }

    private void finishOrderLoadingIfSettled() {
        if (!pendingState.isLoading && !reviewState.isLoading && !historyState.isLoading) {
            binding.progressBar.setVisibility(View.GONE);
            binding.swipeRefresh.setRefreshing(false);
        }
    }

    private void setSyncState(SyncState newState, String detail) {
        syncState = newState;
        if (detail != null && !detail.trim().isEmpty()) {
            syncStateDetail = detail.trim();
        } else if (newState == SyncState.LIVE) {
            syncStateDetail = "Realtime sync active.";
        } else if (newState == SyncState.SNAPSHOT_FALLBACK) {
            syncStateDetail = "Realtime unavailable. Showing API snapshot mode.";
        } else {
            syncStateDetail = "Could not connect to live updates.";
        }
        renderSyncStateBanner();
    }

    private void renderSyncStateBanner() {
        if (syncState == SyncState.LIVE) {
            binding.layoutSyncState.setVisibility(View.GONE);
            return;
        }

        int bgColorRes = syncState == SyncState.ERROR ? R.color.error_bg : R.color.warning_bg;
        int titleColorRes = syncState == SyncState.ERROR ? R.color.error : R.color.warning;
        int subtitleColorRes = R.color.text_secondary;
        String title = syncState == SyncState.ERROR
                ? "Connection issue"
                : "Realtime paused";
        String actionText = syncState == SyncState.ERROR ? "Retry" : "Retry live sync";

        binding.layoutSyncState.setVisibility(View.VISIBLE);
        binding.layoutSyncState.setBackgroundColor(ContextCompat.getColor(this, bgColorRes));
        binding.textSyncStateTitle.setText(title);
        binding.textSyncStateTitle.setTextColor(ContextCompat.getColor(this, titleColorRes));
        binding.textSyncStateSubtitle.setText(syncStateDetail);
        binding.textSyncStateSubtitle.setTextColor(ContextCompat.getColor(this, subtitleColorRes));
        binding.buttonRetrySync.setText(actionText);
    }

    private void renderOrderEmptyState(List<PaymentOrderItem> items, String title) {
        if (items != null && !items.isEmpty()) {
            hideEmptyState();
            return;
        }

        if (syncState == SyncState.SNAPSHOT_FALLBACK) {
            showEmptyState(
                    title,
                    "Live updates are unavailable. This screen is showing a one-shot API snapshot.",
                    "Retry live sync"
            );
            return;
        }

        if (syncState == SyncState.ERROR) {
            showEmptyState(
                    title,
                    "Unable to load data right now. Check your connection and retry.",
                    "Retry"
            );
            return;
        }

        showEmptyState(title, "Pull down to refresh or tap Refresh.", null);
    }

    private void renderBucketEmptyState(List<BucketDto> buckets) {
        if (buckets != null && !buckets.isEmpty()) {
            hideEmptyState();
            return;
        }

        if (syncState == SyncState.SNAPSHOT_FALLBACK) {
            showEmptyState(
                    "No collection accounts found.",
                    "Live updates are unavailable. You are seeing a one-shot API snapshot.",
                    "Retry live sync"
            );
            return;
        }

        if (syncState == SyncState.ERROR) {
            showEmptyState(
                    "Collection accounts unavailable.",
                    "Could not fetch collection accounts. Check your internet and retry.",
                    "Retry"
            );
            return;
        }

        showEmptyState(
                "No collection accounts yet.",
                "Add your first collection account to start routing incoming payments.",
                null
        );
    }

    private void showEmptyState(String title, String subtitle, String actionLabel) {
        binding.layoutEmptyState.setVisibility(View.VISIBLE);
        binding.textEmpty.setText(title);
        binding.textEmptyHint.setText(subtitle);
        binding.textEmptyHint.setVisibility(subtitle == null || subtitle.trim().isEmpty() ? View.GONE : View.VISIBLE);

        if (actionLabel == null || actionLabel.trim().isEmpty()) {
            binding.buttonEmptyAction.setVisibility(View.GONE);
        } else {
            binding.buttonEmptyAction.setVisibility(View.VISIBLE);
            binding.buttonEmptyAction.setText(actionLabel);
        }
    }

    private void hideEmptyState() {
        binding.layoutEmptyState.setVisibility(View.GONE);
    }

    private void applyHistoryFilter() {
        List<PaymentOrderItem> filtered = getFilteredHistoryItems();
        orderAdapter.setItems(filtered);
        String title = currentHistoryFilter == HistoryFilter.ALL
                ? "No historical payment records yet."
                : "No records for this history filter.";
        renderOrderEmptyState(filtered, title);
    }

    private List<PaymentOrderItem> getFilteredHistoryItems() {
        if (historyState.items.isEmpty()) return Collections.emptyList();
        if (currentHistoryFilter == HistoryFilter.ALL) return new ArrayList<>(historyState.items);

        List<PaymentOrderItem> filtered = new ArrayList<>();
        for (PaymentOrderItem item : historyState.items) {
            String status = item != null && item.paymentStatus != null
                    ? item.paymentStatus.toUpperCase(Locale.ROOT)
                    : "";
            if (currentHistoryFilter == HistoryFilter.RECONCILED && "RECONCILED".equals(status)) {
                filtered.add(item);
            } else if (currentHistoryFilter == HistoryFilter.DISPUTED && "DISPUTED".equals(status)) {
                filtered.add(item);
            } else if (currentHistoryFilter == HistoryFilter.CANCELLED && "CANCELLED_BY_BUYER".equals(status)) {
                filtered.add(item);
            } else if (currentHistoryFilter == HistoryFilter.EXPIRED && "EXPIRED".equals(status)) {
                filtered.add(item);
            }
        }
        return filtered;
    }

    private HistoryFilter mapFilter(int position) {
        if (position == 1) return HistoryFilter.RECONCILED;
        if (position == 2) return HistoryFilter.DISPUTED;
        if (position == 3) return HistoryFilter.CANCELLED;
        if (position == 4) return HistoryFilter.EXPIRED;
        return HistoryFilter.ALL;
    }

    private void updateTabBadge(int itemId, int count) {
        if (count <= 0) {
            binding.bottomNavigation.removeBadge(itemId);
            return;
        }
        BadgeDrawable badge = binding.bottomNavigation.getOrCreateBadge(itemId);
        badge.setNumber(count);
        badge.setVisible(true);
    }

    private void showCreateCollectionAccountDialog() {
        if (isCollectionAccountLoading || isCreateBucketInProgress || isEditBucketInProgress) return;

        selectedQrImageUri = null;

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        layout.setPadding(padding, padding, padding, 0);

        EditText vendorName = new EditText(this);
        vendorName.setHint("Vendor name");
        layout.addView(vendorName);

        EditText upiId = new EditText(this);
        upiId.setHint("Vendor UPI ID");
        layout.addView(upiId);
        createBucketUpiInput = upiId;

        Button selectQrImage = new Button(this);
        selectQrImage.setText("Select QR Image");
        layout.addView(selectQrImage);
        createBucketSelectQrButton = selectQrImage;

        Button clearQrImage = new Button(this);
        clearQrImage.setText("Remove Selected QR Image");
        clearQrImage.setVisibility(View.GONE);
        layout.addView(clearQrImage);
        createBucketClearQrButton = clearQrImage;

        ImageView qrPreview = new ImageView(this);
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            (int) (180 * getResources().getDisplayMetrics().density)
        );
        previewParams.topMargin = (int) (8 * getResources().getDisplayMetrics().density);
        qrPreview.setLayoutParams(previewParams);
        qrPreview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        qrPreview.setVisibility(View.GONE);
        layout.addView(qrPreview);
        createBucketQrPreview = qrPreview;

        TextView qrStatus = new TextView(this);
        qrStatus.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        qrStatus.setTextSize(12f);
        qrStatus.setVisibility(View.GONE);
        layout.addView(qrStatus);
        createBucketQrStatus = qrStatus;

        EditText qrType = new EditText(this);
        qrType.setHint("Payment type (UPI/BANK)");
        qrType.setText("UPI");
        layout.addView(qrType);

        EditText priority = new EditText(this);
        priority.setHint("Priority (e.g. 1)");
        priority.setInputType(InputType.TYPE_CLASS_NUMBER);
        layout.addView(priority);

        EditText limitAmount = new EditText(this);
        limitAmount.setHint("Limit amount");
        limitAmount.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        layout.addView(limitAmount);

        selectQrImage.setOnClickListener(v -> {
            if (isCreateBucketInProgress) return;
            qrImagePickerLauncher.launch("image/*");
        });

        clearQrImage.setOnClickListener(v -> {
            if (isCreateBucketInProgress) return;
            clearSelectedQrImage();
        });

        createBucketDialog = new AlertDialog.Builder(this)
                .setTitle("Add Collection Account")
                .setView(layout)
                .setPositiveButton("Create", null)
                .setNegativeButton("Cancel", null)
                .create();

        createBucketDialog.setOnShowListener(dialog -> {
            Button create = createBucketDialog.getButton(AlertDialog.BUTTON_POSITIVE);
            if (create != null) {
                create.setOnClickListener(v -> submitCreateCollectionAccount(vendorName, upiId, qrType, priority, limitAmount));
            }
            updateCreateBucketDialogControls();
        });

        createBucketDialog.setOnDismissListener(dialog -> clearCreateBucketDialogReferences());
        createBucketDialog.show();
    }

    private interface QrUploadCallback {
        void onSuccess(String qrImageUrl);

        void onFailure(String message);
    }

    private void submitCreateCollectionAccount(
            EditText vendorName,
            EditText upiId,
            EditText qrType,
            EditText priority,
            EditText limitAmount
    ) {
        String vendor = vendorName.getText().toString().trim();
        String upi = upiId.getText().toString().trim();
        String limit = limitAmount.getText().toString().trim();
        String pri = priority.getText().toString().trim();
        String qrTypeRaw = qrType.getText().toString().trim();

        if (vendor.isEmpty() || upi.isEmpty() || limit.isEmpty() || pri.isEmpty()) {
            Toast.makeText(this, "Vendor, payment handle, priority and limit are required.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (selectedQrImageUri == null) {
            Toast.makeText(this, "Select a QR image before creating this account.", Toast.LENGTH_SHORT).show();
            return;
        }

        String normalizedQrType = qrTypeRaw.isEmpty() ? "UPI" : qrTypeRaw.toUpperCase(Locale.ROOT);
        if (!"UPI".equals(normalizedQrType) && !"BANK".equals(normalizedQrType)) {
            Toast.makeText(this, "Payment type must be UPI or BANK.", Toast.LENGTH_SHORT).show();
            return;
        }

        int parsedPriority;
        double parsedLimit;
        try {
            parsedPriority = Integer.parseInt(pri);
            parsedLimit = Double.parseDouble(limit);
        } catch (NumberFormatException ex) {
            Toast.makeText(this, "Invalid priority or limit amount.", Toast.LENGTH_SHORT).show();
            return;
        }

        setCreateBucketBusy(true);
        setCreateBucketStatus("Uploading QR image...");
        binding.progressBar.setVisibility(View.VISIBLE);

        uploadImageToCloudinary(selectedQrImageUri, new QrUploadCallback() {
            @Override
            public void onSuccess(String qrImageUrl) {
                CreateBucketRequest req = new CreateBucketRequest();
                req.vendorName = vendor;
                req.vendorUpiId = upi;
                req.qrImageUrl = qrImageUrl;
                req.qrType = normalizedQrType;
                req.priority = parsedPriority;
                req.limitAmount = parsedLimit;

                setCreateBucketStatus("Creating collection account...");

                repository.createBucket(req, new PaymentAdminRepository.ResultCallback<BucketDto>() {
                    @Override
                    public void onSuccess(BucketDto data) {
                        handler.post(() -> {
                            setCreateBucketBusy(false);
                            binding.progressBar.setVisibility(View.GONE);
                            Toast.makeText(PaymentAdminActivity.this, "Collection account created", Toast.LENGTH_SHORT).show();
                            if (createBucketDialog != null && createBucketDialog.isShowing()) {
                                createBucketDialog.dismiss();
                            }
                            loadCollectionAccountsData();
                        });
                    }

                    @Override
                    public void onError(String message, String code) {
                        handler.post(() -> {
                            setCreateBucketBusy(false);
                            binding.progressBar.setVisibility(View.GONE);
                            setCreateBucketStatus("Could not create account. Please retry.");
                            handleApiError(code, message);
                        });
                    }
                });
            }

            @Override
            public void onFailure(String message) {
                handler.post(() -> {
                    setCreateBucketBusy(false);
                    binding.progressBar.setVisibility(View.GONE);
                    setCreateBucketStatus("Upload failed. Please retry.");
                    Toast.makeText(PaymentAdminActivity.this, message, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void showEditCollectionAccountDialog(BucketDto bucket) {
        if (bucket == null || bucket.bucketId == null || bucket.bucketId.trim().isEmpty()) return;
        if (isCollectionAccountLoading || isCreateBucketInProgress || isEditBucketInProgress) return;

        selectedEditQrImageUri = null;
        editBucketExistingQrUrl = bucket.qrImageUrl != null ? bucket.qrImageUrl.trim() : "";

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        layout.setPadding(padding, padding, padding, 0);

        EditText vendorName = new EditText(this);
        vendorName.setHint("Vendor name");
        vendorName.setText(bucket.vendorName != null ? bucket.vendorName : "");
        layout.addView(vendorName);

        EditText upiId = new EditText(this);
        upiId.setHint("Vendor UPI ID");
        upiId.setText(bucket.vendorUpiId != null ? bucket.vendorUpiId : "");
        layout.addView(upiId);
        editBucketUpiInput = upiId;

        Button selectQrImage = new Button(this);
        selectQrImage.setText("Replace QR Image (Optional)");
        layout.addView(selectQrImage);
        editBucketSelectQrButton = selectQrImage;

        Button clearQrImage = new Button(this);
        clearQrImage.setText("Remove New QR Selection");
        clearQrImage.setVisibility(View.GONE);
        layout.addView(clearQrImage);
        editBucketClearQrButton = clearQrImage;

        ImageView qrPreview = new ImageView(this);
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (int) (180 * getResources().getDisplayMetrics().density)
        );
        previewParams.topMargin = (int) (8 * getResources().getDisplayMetrics().density);
        qrPreview.setLayoutParams(previewParams);
        qrPreview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        if (!editBucketExistingQrUrl.isEmpty()) {
            qrPreview.setVisibility(View.VISIBLE);
            Glide.with(this)
                    .load(editBucketExistingQrUrl)
                    .centerCrop()
                    .into(qrPreview);
        } else {
            qrPreview.setVisibility(View.GONE);
        }
        layout.addView(qrPreview);
        editBucketQrPreview = qrPreview;

        TextView qrStatus = new TextView(this);
        qrStatus.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        qrStatus.setTextSize(12f);
        qrStatus.setVisibility(View.GONE);
        layout.addView(qrStatus);
        editBucketQrStatus = qrStatus;

        EditText qrType = new EditText(this);
        qrType.setHint("Payment type (UPI/BANK)");
        String existingQrType = bucket.qrType != null && !bucket.qrType.trim().isEmpty()
                ? bucket.qrType.trim().toUpperCase(Locale.ROOT)
                : "UPI";
        qrType.setText(existingQrType);
        layout.addView(qrType);

        EditText priority = new EditText(this);
        priority.setHint("Priority (e.g. 1)");
        priority.setInputType(InputType.TYPE_CLASS_NUMBER);
        priority.setText(String.valueOf(bucket.priority));
        layout.addView(priority);

        EditText limitAmount = new EditText(this);
        limitAmount.setHint("Limit amount");
        limitAmount.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        limitAmount.setText(String.format(Locale.US, "%.2f", bucket.limitAmount));
        layout.addView(limitAmount);

        if (editBucketExistingQrUrl.isEmpty()) {
            setEditBucketStatus("No existing QR image. Select one before saving.");
        } else {
            setEditBucketStatus("Using current QR image. Select a new one to replace it.");
        }

        selectQrImage.setOnClickListener(v -> {
            if (isEditBucketInProgress) return;
            editQrImagePickerLauncher.launch("image/*");
        });

        clearQrImage.setOnClickListener(v -> {
            if (isEditBucketInProgress) return;
            clearSelectedEditQrImage();
        });

        editBucketDialog = new AlertDialog.Builder(this)
                .setTitle("Edit Collection Account")
                .setView(layout)
                .setPositiveButton("Save", null)
                .setNegativeButton("Cancel", null)
                .create();

        editBucketDialog.setOnShowListener(dialog -> {
            Button save = editBucketDialog.getButton(AlertDialog.BUTTON_POSITIVE);
            if (save != null) {
                save.setOnClickListener(v -> submitEditCollectionAccount(bucket, vendorName, upiId, qrType, priority, limitAmount));
            }
            updateEditBucketDialogControls();
        });

        editBucketDialog.setOnDismissListener(dialog -> clearEditBucketDialogReferences());
        editBucketDialog.show();
    }

    private void submitEditCollectionAccount(
            BucketDto bucket,
            EditText vendorName,
            EditText upiId,
            EditText qrType,
            EditText priority,
            EditText limitAmount
    ) {
        String bucketId = bucket != null && bucket.bucketId != null ? bucket.bucketId.trim() : "";
        if (bucketId.isEmpty()) {
            Toast.makeText(this, "Invalid collection account selected.", Toast.LENGTH_SHORT).show();
            return;
        }

        String vendor = vendorName.getText().toString().trim();
        String upi = upiId.getText().toString().trim();
        String limit = limitAmount.getText().toString().trim();
        String pri = priority.getText().toString().trim();
        String qrTypeRaw = qrType.getText().toString().trim();

        if (vendor.isEmpty() || upi.isEmpty() || limit.isEmpty() || pri.isEmpty()) {
            Toast.makeText(this, "Vendor, payment handle, priority and limit are required.", Toast.LENGTH_SHORT).show();
            return;
        }

        String normalizedQrType = qrTypeRaw.isEmpty() ? "UPI" : qrTypeRaw.toUpperCase(Locale.ROOT);
        if (!"UPI".equals(normalizedQrType) && !"BANK".equals(normalizedQrType)) {
            Toast.makeText(this, "Payment type must be UPI or BANK.", Toast.LENGTH_SHORT).show();
            return;
        }

        int parsedPriority;
        double parsedLimit;
        try {
            parsedPriority = Integer.parseInt(pri);
            parsedLimit = Double.parseDouble(limit);
        } catch (NumberFormatException ex) {
            Toast.makeText(this, "Invalid priority or limit amount.", Toast.LENGTH_SHORT).show();
            return;
        }

        String existingQrUrl = editBucketExistingQrUrl != null ? editBucketExistingQrUrl.trim() : "";
        if (selectedEditQrImageUri == null && existingQrUrl.isEmpty()) {
            Toast.makeText(this, "Select a QR image before saving this account.", Toast.LENGTH_SHORT).show();
            return;
        }

        setEditBucketBusy(true);
        binding.progressBar.setVisibility(View.VISIBLE);

        if (selectedEditQrImageUri != null) {
            setEditBucketStatus("Uploading new QR image...");
            uploadImageToCloudinary(selectedEditQrImageUri, new QrUploadCallback() {
                @Override
                public void onSuccess(String qrImageUrl) {
                    persistEditedBucket(bucketId, vendor, upi, qrImageUrl, normalizedQrType, parsedPriority, parsedLimit);
                }

                @Override
                public void onFailure(String message) {
                    handler.post(() -> {
                        setEditBucketBusy(false);
                        binding.progressBar.setVisibility(View.GONE);
                        setEditBucketStatus("Upload failed. Please retry.");
                        Toast.makeText(PaymentAdminActivity.this, message, Toast.LENGTH_LONG).show();
                    });
                }
            });
            return;
        }

        setEditBucketStatus("Saving collection account...");
        persistEditedBucket(bucketId, vendor, upi, existingQrUrl, normalizedQrType, parsedPriority, parsedLimit);
    }

    private void persistEditedBucket(
            String bucketId,
            String vendorName,
            String vendorUpiId,
            String qrImageUrl,
            String qrType,
            int priority,
            double limitAmount
    ) {
        UpdateBucketRequest request = new UpdateBucketRequest();
        request.vendorName = vendorName;
        request.vendorUpiId = vendorUpiId;
        request.qrImageUrl = qrImageUrl;
        request.qrType = qrType;
        request.priority = priority;
        request.limitAmount = limitAmount;

        repository.updateBucket(bucketId, request, new PaymentAdminRepository.ResultCallback<BucketDto>() {
            @Override
            public void onSuccess(BucketDto data) {
                handler.post(() -> {
                    setEditBucketBusy(false);
                    binding.progressBar.setVisibility(View.GONE);
                    Toast.makeText(PaymentAdminActivity.this, "Collection account updated", Toast.LENGTH_SHORT).show();
                    if (editBucketDialog != null && editBucketDialog.isShowing()) {
                        editBucketDialog.dismiss();
                    }
                    loadCollectionAccountsData();
                });
            }

            @Override
            public void onError(String message, String code) {
                handler.post(() -> {
                    setEditBucketBusy(false);
                    binding.progressBar.setVisibility(View.GONE);
                    setEditBucketStatus("Could not update account. Please retry.");
                    handleApiError(code, message);
                });
            }
        });
    }

    private void uploadImageToCloudinary(Uri imageUri, QrUploadCallback callback) {
        MediaManager.get().upload(imageUri)
                .unsigned("easysell_preset")
                .option("resource_type", "image")
                .callback(new UploadCallback() {
                    @Override
                    public void onStart(String requestId) {
                    }

                    @Override
                    public void onProgress(String requestId, long bytes, long totalBytes) {
                    }

                    @Override
                    public void onSuccess(String requestId, Map resultData) {
                        Object value = resultData.get("secure_url");
                        String uploadedUrl = value != null ? String.valueOf(value).trim() : "";
                        if (uploadedUrl.isEmpty()) {
                            callback.onFailure("Upload completed without a valid image URL.");
                            return;
                        }
                        callback.onSuccess(uploadedUrl);
                    }

                    @Override
                    public void onError(String requestId, ErrorInfo error) {
                        String message = error != null && error.getDescription() != null
                                ? error.getDescription()
                                : "Image upload failed.";
                        callback.onFailure(message);
                    }

                    @Override
                    public void onReschedule(String requestId, ErrorInfo error) {
                    }
                })
                .dispatch();
    }

    private void tryExtractUpiIdFromQr(Uri uri, EditText upiInput, QrStatusCallback statusCallback) {
        InputImage image;
        try {
            image = InputImage.fromFilePath(this, uri);
        } catch (IOException e) {
            if (statusCallback != null) {
                statusCallback.onStatus("Could not read QR image. Enter UPI ID manually.");
            }
            return;
        }

        BarcodeScanner scanner = BarcodeScanning.getClient();
        scanner.process(image)
                .addOnSuccessListener(barcodes -> {
                    String extractedUpiId = extractUpiIdFromBarcodes(barcodes);
                    if (extractedUpiId == null) {
                        if (statusCallback != null) {
                            statusCallback.onStatus("Could not detect a UPI ID from QR. Enter UPI ID manually.");
                        }
                        return;
                    }

                    if (upiInput == null) {
                        return;
                    }

                    String existingUpi = upiInput.getText().toString().trim();
                    if (existingUpi.isEmpty()) {
                        upiInput.setText(extractedUpiId);
                        if (statusCallback != null) {
                            statusCallback.onStatus("UPI detected and auto-filled from QR.");
                        }
                        return;
                    }

                    if (!existingUpi.equalsIgnoreCase(extractedUpiId)) {
                        if (statusCallback != null) {
                            statusCallback.onStatus("UPI detected from QR, keeping your current manual UPI value.");
                        }
                    } else {
                        if (statusCallback != null) {
                            statusCallback.onStatus("UPI detected from QR.");
                        }
                    }
                })
                .addOnFailureListener(error -> {
                    if (statusCallback != null) {
                        statusCallback.onStatus("Could not scan QR. Enter UPI ID manually.");
                    }
                })
                .addOnCompleteListener(task -> scanner.close());
    }

    private String extractUpiIdFromBarcodes(List<Barcode> barcodes) {
        if (barcodes == null || barcodes.isEmpty()) return null;

        for (Barcode barcode : barcodes) {
            String extracted = extractUpiIdFromRawValue(barcode != null ? barcode.getRawValue() : null);
            if (extracted != null) {
                return extracted;
            }
        }

        return null;
    }

    private String extractUpiIdFromRawValue(String rawValue) {
        if (rawValue == null) return null;

        String text = rawValue.trim();
        if (text.isEmpty()) return null;

        if (text.startsWith("upi://pay")) {
            String pa = Uri.parse(text).getQueryParameter("pa");
            if (isValidUpiId(pa)) {
                return pa.trim();
            }
        }

        String[] queryTokens = text.split("[?&]");
        for (String token : queryTokens) {
            if (token == null || !token.startsWith("pa=")) continue;
            String candidate = Uri.decode(token.substring(3));
            if (isValidUpiId(candidate)) {
                return candidate.trim();
            }
        }

        if (isValidUpiId(text)) {
            return text;
        }

        return null;
    }

    private boolean isValidUpiId(String value) {
        if (value == null) return false;
        String candidate = value.trim();
        return !candidate.isEmpty() && UPI_ID_PATTERN.matcher(candidate).matches();
    }

    private void clearSelectedQrImage() {
        selectedQrImageUri = null;
        if (createBucketQrPreview != null) {
            createBucketQrPreview.setImageDrawable(null);
            createBucketQrPreview.setVisibility(View.GONE);
        }
        if (createBucketClearQrButton != null) {
            createBucketClearQrButton.setVisibility(View.GONE);
        }
        setCreateBucketStatus("No QR image selected.");
        updateCreateBucketDialogControls();
    }

    private void setCreateBucketStatus(String message) {
        if (createBucketQrStatus == null) return;
        String text = message != null ? message.trim() : "";
        if (text.isEmpty()) {
            createBucketQrStatus.setVisibility(View.GONE);
            createBucketQrStatus.setText("");
            return;
        }
        createBucketQrStatus.setVisibility(View.VISIBLE);
        createBucketQrStatus.setText(text);
    }

    private void setCreateBucketBusy(boolean busy) {
        isCreateBucketInProgress = busy;
        updateCreateBucketDialogControls();
    }

    private void updateCreateBucketDialogControls() {
        if (createBucketDialog != null) {
            Button positive = createBucketDialog.getButton(AlertDialog.BUTTON_POSITIVE);
            if (positive != null) {
                positive.setEnabled(!isCreateBucketInProgress);
            }

            Button negative = createBucketDialog.getButton(AlertDialog.BUTTON_NEGATIVE);
            if (negative != null) {
                negative.setEnabled(!isCreateBucketInProgress);
            }

            createBucketDialog.setCancelable(!isCreateBucketInProgress);
            createBucketDialog.setCanceledOnTouchOutside(!isCreateBucketInProgress);
        }

        if (createBucketSelectQrButton != null) {
            createBucketSelectQrButton.setEnabled(!isCreateBucketInProgress);
        }

        if (createBucketClearQrButton != null) {
            createBucketClearQrButton.setEnabled(!isCreateBucketInProgress);
            createBucketClearQrButton.setVisibility(selectedQrImageUri == null ? View.GONE : View.VISIBLE);
        }

        if (createBucketUpiInput != null) {
            createBucketUpiInput.setEnabled(!isCreateBucketInProgress);
        }
    }

    private void clearCreateBucketDialogReferences() {
        createBucketDialog = null;
        createBucketUpiInput = null;
        createBucketQrPreview = null;
        createBucketQrStatus = null;
        createBucketSelectQrButton = null;
        createBucketClearQrButton = null;
        selectedQrImageUri = null;
        isCreateBucketInProgress = false;
    }

    private void clearSelectedEditQrImage() {
        selectedEditQrImageUri = null;

        if (editBucketQrPreview != null) {
            if (editBucketExistingQrUrl != null && !editBucketExistingQrUrl.trim().isEmpty()) {
                editBucketQrPreview.setVisibility(View.VISIBLE);
                Glide.with(this)
                        .load(editBucketExistingQrUrl)
                        .centerCrop()
                        .into(editBucketQrPreview);
            } else {
                editBucketQrPreview.setImageDrawable(null);
                editBucketQrPreview.setVisibility(View.GONE);
            }
        }

        if (editBucketClearQrButton != null) {
            editBucketClearQrButton.setVisibility(View.GONE);
        }

        if (editBucketExistingQrUrl != null && !editBucketExistingQrUrl.trim().isEmpty()) {
            setEditBucketStatus("Reverted to existing QR image.");
        } else {
            setEditBucketStatus("No QR image selected.");
        }

        updateEditBucketDialogControls();
    }

    private void setEditBucketStatus(String message) {
        if (editBucketQrStatus == null) return;
        String text = message != null ? message.trim() : "";
        if (text.isEmpty()) {
            editBucketQrStatus.setVisibility(View.GONE);
            editBucketQrStatus.setText("");
            return;
        }
        editBucketQrStatus.setVisibility(View.VISIBLE);
        editBucketQrStatus.setText(text);
    }

    private void setEditBucketBusy(boolean busy) {
        isEditBucketInProgress = busy;
        updateEditBucketDialogControls();
    }

    private void updateEditBucketDialogControls() {
        if (editBucketDialog != null) {
            Button positive = editBucketDialog.getButton(AlertDialog.BUTTON_POSITIVE);
            if (positive != null) {
                positive.setEnabled(!isEditBucketInProgress);
            }

            Button negative = editBucketDialog.getButton(AlertDialog.BUTTON_NEGATIVE);
            if (negative != null) {
                negative.setEnabled(!isEditBucketInProgress);
            }

            editBucketDialog.setCancelable(!isEditBucketInProgress);
            editBucketDialog.setCanceledOnTouchOutside(!isEditBucketInProgress);
        }

        if (editBucketSelectQrButton != null) {
            editBucketSelectQrButton.setEnabled(!isEditBucketInProgress);
        }

        if (editBucketClearQrButton != null) {
            editBucketClearQrButton.setEnabled(!isEditBucketInProgress);
            editBucketClearQrButton.setVisibility(selectedEditQrImageUri == null ? View.GONE : View.VISIBLE);
        }

        if (editBucketUpiInput != null) {
            editBucketUpiInput.setEnabled(!isEditBucketInProgress);
        }
    }

    private void clearEditBucketDialogReferences() {
        editBucketDialog = null;
        editBucketUpiInput = null;
        editBucketQrPreview = null;
        editBucketQrStatus = null;
        editBucketSelectQrButton = null;
        editBucketClearQrButton = null;
        selectedEditQrImageUri = null;
        editBucketExistingQrUrl = null;
        isEditBucketInProgress = false;
    }

    @Override
    public void onEditBucket(BucketDto bucket) {
        showEditCollectionAccountDialog(bucket);
    }

    @Override
    public void onToggleBucketStatus(BucketDto bucket) {
        if (bucket == null || bucket.bucketId == null) return;
        String current = bucket.status != null ? bucket.status.toUpperCase(Locale.ROOT) : "";
        String target = "ACTIVE".equals(current) ? "PAUSED" : "ACTIVE";

        binding.progressBar.setVisibility(View.VISIBLE);
        repository.updateBucketStatus(bucket.bucketId, target, new PaymentAdminRepository.ResultCallback<BucketDto>() {
            @Override
            public void onSuccess(BucketDto data) {
                binding.progressBar.setVisibility(View.GONE);
                Toast.makeText(PaymentAdminActivity.this, "Collection account status updated: " + target, Toast.LENGTH_SHORT).show();
                loadCollectionAccountsData();
            }

            @Override
            public void onError(String message, String code) {
                binding.progressBar.setVisibility(View.GONE);
                handleApiError(code, message);
            }
        });
    }

    @Override
    public void onReconcile(PaymentOrderItem item) {
        confirm(item, "RECONCILE");
    }

    @Override
    public void onDispute(PaymentOrderItem item) {
        confirm(item, "DISPUTE");
    }

    @Override
    public void onReopen(PaymentOrderItem item) {
        if (item == null || item.orderId == null) return;
        binding.progressBar.setVisibility(View.VISIBLE);
        repository.reopenOrder(item.orderId, new PaymentAdminRepository.ResultCallback<ReopenResult>() {
            @Override
            public void onSuccess(ReopenResult data) {
                binding.progressBar.setVisibility(View.GONE);
                Toast.makeText(PaymentAdminActivity.this, "Order reopened", Toast.LENGTH_SHORT).show();
                loadInitial();
            }

            @Override
            public void onError(String message, String code) {
                binding.progressBar.setVisibility(View.GONE);
                handleApiError(code, message);
            }
        });
    }

    @Override
    public void onUnresolve(PaymentOrderItem item) {
        if (item == null || item.orderId == null) return;
        binding.progressBar.setVisibility(View.VISIBLE);
        repository.unresolveOrder(item.orderId, new PaymentAdminRepository.ResultCallback<ReopenResult>() {
            @Override
            public void onSuccess(ReopenResult data) {
                binding.progressBar.setVisibility(View.GONE);
                Toast.makeText(PaymentAdminActivity.this, "Order moved back to review", Toast.LENGTH_SHORT).show();
                loadInitial();
            }

            @Override
            public void onError(String message, String code) {
                binding.progressBar.setVisibility(View.GONE);
                handleApiError(code, message);
            }
        });
    }

    private void confirm(PaymentOrderItem item, String action) {
        if (item == null || item.orderId == null) return;
        binding.progressBar.setVisibility(View.VISIBLE);
        repository.confirmOrder(item.orderId, action, new PaymentAdminRepository.ResultCallback<AdminConfirmResult>() {
            @Override
            public void onSuccess(AdminConfirmResult data) {
                binding.progressBar.setVisibility(View.GONE);
                Toast.makeText(PaymentAdminActivity.this, "Order updated: " + action, Toast.LENGTH_SHORT).show();
                loadInitial();
            }

            @Override
            public void onError(String message, String code) {
                binding.progressBar.setVisibility(View.GONE);
                handleApiError(code, message);
            }
        });
    }

    private void handleApiError(String code, String fallbackMessage) {
        String friendly = mapErrorCode(code, fallbackMessage);
        Toast.makeText(this, friendly, Toast.LENGTH_LONG).show();

        if ("UNAUTHORIZED".equals(code) || "FORBIDDEN".equals(code)) {
            FirebaseAuth.getInstance().signOut();
            Intent intent = new Intent(this, SignInActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        }
    }

    private String mapErrorCode(String code, String fallbackMessage) {
        if (code == null || code.trim().isEmpty()) {
            return fallbackMessage != null ? fallbackMessage : "Request failed.";
        }

        if ("UTR_CORRECTION_ALREADY_USED".equals(code)) {
            return "UTR was already corrected once. No further correction is allowed.";
        }
        if ("REOPEN_REQUIRED".equals(code)) {
            return "Order must be reopened before this action.";
        }
        if ("ORDER_NOT_CONFIRMABLE".equals(code)) {
            return "Order is not in a confirmable state.";
        }
        if ("ORDER_NOT_RECONCILED".equals(code)) {
            return "Only reconciled orders can be moved back to review.";
        }
        if ("ORDER_EXPIRED".equals(code)) {
            return "Order has expired and cannot be processed.";
        }
        if ("INSUFFICIENT_BUCKET_BALANCE".equals(code)) {
            return "Unable to rollback this order because bucket totals are out of sync.";
        }
        if ("SUFFIX_RESERVATION_CONFLICT".equals(code)) {
            return "This payment suffix is already reserved by another order. Please check recent payments.";
        }
        if ("VENDOR_ACTIVE_BUCKET_EXISTS".equals(code)) {
            return "An active collection account already exists for this payment handle.";
        }
        if ("BUCKET_NOT_FOUND".equals(code)) {
            return "Collection account not found. Refresh and try again.";
        }
        if ("INVALID_INPUT".equals(code)) {
            return (fallbackMessage != null && !fallbackMessage.trim().isEmpty())
                    ? fallbackMessage
                    : "Submitted details are invalid.";
        }
        if ("UNAUTHORIZED".equals(code)) {
            return "Session expired. Please sign in again.";
        }
        if ("FORBIDDEN".equals(code)) {
            return "You do not have admin access for this operation.";
        }
        if ("STORE_SCOPE_REQUIRED".equals(code)) {
            return "Your store scope is missing. Update profile store handle and try again.";
        }
        if ("STORE_SCOPE_MISMATCH".equals(code)) {
            return "You can only access payment setup for your own store.";
        }
        if (PaymentAdminRepository.CODE_FIRESTORE_READ_FAILED.equals(code)) {
            return "Realtime feed is unavailable right now. Please retry.";
        }
        if (PaymentAdminRepository.CODE_REALTIME_FALLBACK_FAILED.equals(code)) {
            return "Realtime feed failed and snapshot fallback also failed. Check network and retry.";
        }

        return (fallbackMessage != null && !fallbackMessage.trim().isEmpty())
                ? fallbackMessage
                : (code + ": Request failed.");
    }

    @Override
    protected void onDestroy() {
        stopRealtimeOrderListeners();
        if (bucketListener != null) {
            bucketListener.remove();
            bucketListener = null;
        }
        if (createBucketDialog != null && createBucketDialog.isShowing()) {
            createBucketDialog.dismiss();
        }
        if (editBucketDialog != null && editBucketDialog.isShowing()) {
            editBucketDialog.dismiss();
        }
        clearCreateBucketDialogReferences();
        clearEditBucketDialogReferences();
        super.onDestroy();
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
