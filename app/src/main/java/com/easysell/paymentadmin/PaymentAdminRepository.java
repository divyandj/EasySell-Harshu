package com.easysell.paymentadmin;

import com.easysell.paymentadmin.model.AdminConfirmRequest;
import com.easysell.paymentadmin.model.AdminConfirmResult;
import com.easysell.paymentadmin.model.ApiEnvelope;
import com.easysell.paymentadmin.model.ApiErrorResponse;
import com.easysell.paymentadmin.model.BucketDto;
import com.easysell.paymentadmin.model.BucketStatusUpdateRequest;
import com.easysell.paymentadmin.model.CreateBucketRequest;
import com.easysell.paymentadmin.model.CursorPage;
import com.easysell.paymentadmin.model.PaymentOrderItem;
import com.easysell.paymentadmin.model.ReopenResult;
import com.easysell.paymentadmin.model.UpdateBucketRequest;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.Timestamp;
import com.google.gson.Gson;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class PaymentAdminRepository {

    private static final String STATUS_UTR_SUBMITTED = "UTR_SUBMITTED";
    private static final String STATUS_UNDER_REVIEW = "PAYMENT_UNDER_REVIEW";
    private static final String STATUS_RECONCILED = "RECONCILED";
    private static final String STATUS_DISPUTED = "DISPUTED";
    private static final String STATUS_CANCELLED_BY_BUYER = "CANCELLED_BY_BUYER";
    private static final String STATUS_EXPIRED = "EXPIRED";
    private static final int SNAPSHOT_FALLBACK_LIMIT = 200;

    public static final String CODE_FIRESTORE_READ_FAILED = "FIRESTORE_READ_FAILED";
    public static final String CODE_REALTIME_FALLBACK_ACTIVE = "REALTIME_FALLBACK_ACTIVE";
    public static final String CODE_REALTIME_FALLBACK_FAILED = "REALTIME_FALLBACK_FAILED";

    public enum RealtimeReadMode {
        FIRESTORE_ONLY,
        FIRESTORE_THEN_API_SNAPSHOT
    }

    public interface ResultCallback<T> {
        void onSuccess(T data);

        void onError(String message, String code);
    }

    private final PaymentAdminApi api;
    private final Gson gson;
    private final FirebaseFirestore firestore;
    private RealtimeReadMode realtimeReadMode = RealtimeReadMode.FIRESTORE_ONLY;

    public PaymentAdminRepository() {
        this.api = PaymentAdminClient.getApi();
        this.gson = new Gson();
        this.firestore = FirebaseFirestore.getInstance();
    }

    public void setRealtimeReadMode(RealtimeReadMode mode) {
        this.realtimeReadMode = mode != null ? mode : RealtimeReadMode.FIRESTORE_ONLY;
    }

    public RealtimeReadMode getRealtimeReadMode() {
        return realtimeReadMode;
    }

    public ListenerRegistration subscribePendingOrders(String storeHandle, ResultCallback<List<PaymentOrderItem>> callback) {
        return subscribeOrdersByStatuses(
                storeHandle,
                Collections.singletonList(STATUS_UTR_SUBMITTED),
                callback,
                new ApiSnapshotLoader<PaymentOrderItem>() {
                    @Override
                    public void load(String scopedStoreHandle, ResultCallback<List<PaymentOrderItem>> fallbackCallback) {
                        fetchPendingOrdersSnapshot(scopedStoreHandle, fallbackCallback);
                    }
                }
        );
    }

    public ListenerRegistration subscribeReviewOrders(String storeHandle, ResultCallback<List<PaymentOrderItem>> callback) {
        return subscribeOrdersByStatuses(
                storeHandle,
                Collections.singletonList(STATUS_UNDER_REVIEW),
                callback,
                new ApiSnapshotLoader<PaymentOrderItem>() {
                    @Override
                    public void load(String scopedStoreHandle, ResultCallback<List<PaymentOrderItem>> fallbackCallback) {
                        fetchReviewOrdersSnapshot(scopedStoreHandle, fallbackCallback);
                    }
                }
        );
    }

    public ListenerRegistration subscribeHistoryOrders(String storeHandle, ResultCallback<List<PaymentOrderItem>> callback) {
        return subscribeOrdersByStatuses(
                storeHandle,
                Arrays.asList(STATUS_RECONCILED, STATUS_DISPUTED, STATUS_CANCELLED_BY_BUYER, STATUS_EXPIRED),
                callback,
                new ApiSnapshotLoader<PaymentOrderItem>() {
                    @Override
                    public void load(String scopedStoreHandle, ResultCallback<List<PaymentOrderItem>> fallbackCallback) {
                        fetchHistoryOrdersSnapshot(scopedStoreHandle, fallbackCallback);
                    }
                }
        );
    }

    public ListenerRegistration subscribeBuckets(String storeHandle, ResultCallback<List<BucketDto>> callback) {
        String scoped = normalizeStoreHandle(storeHandle);
        if (scoped.isEmpty()) {
            callback.onError("Store scope missing.", "STORE_SCOPE_REQUIRED");
            return null;
        }

        Query query = firestore.collection("buckets")
                .whereEqualTo("storeHandle", scoped)
                .orderBy("priority", Query.Direction.ASCENDING)
                .limit(200);

        return query.addSnapshotListener((snapshot, error) -> {
            if (error != null) {
                handleRealtimeReadError(
                        scoped,
                        error,
                        "Unable to load collection accounts.",
                        callback,
                        new ApiSnapshotLoader<BucketDto>() {
                            @Override
                            public void load(String scopedStoreHandle, ResultCallback<List<BucketDto>> fallbackCallback) {
                                fetchBucketsSnapshot(scopedStoreHandle, fallbackCallback);
                            }
                        }
                );
                return;
            }

            callback.onSuccess(mapBuckets(snapshot != null ? snapshot.getDocuments() : Collections.emptyList()));
        });
    }

    public void listPendingOrders(int limit, Long cursor, String storeHandle, ResultCallback<CursorPage<PaymentOrderItem>> callback) {
        withAdminToken(new TokenCallback() {
            @Override
            public void onToken(String authHeader) {
                enqueue(api.listPendingOrders(authHeader, limit, cursor, storeHandle), callback);
            }

            @Override
            public void onError(String message, String code) {
                callback.onError(message, code);
            }
        });
    }

    public void listReviewOrders(int limit, Long cursor, String storeHandle, ResultCallback<CursorPage<PaymentOrderItem>> callback) {
        withAdminToken(new TokenCallback() {
            @Override
            public void onToken(String authHeader) {
                enqueue(api.listReviewOrders(authHeader, limit, cursor, storeHandle), callback);
            }

            @Override
            public void onError(String message, String code) {
                callback.onError(message, code);
            }
        });
    }

    public void listHistoryOrders(int limit, Long cursor, String storeHandle, ResultCallback<CursorPage<PaymentOrderItem>> callback) {
        withAdminToken(new TokenCallback() {
            @Override
            public void onToken(String authHeader) {
                enqueue(api.listHistoryOrders(authHeader, limit, cursor, storeHandle), callback);
            }

            @Override
            public void onError(String message, String code) {
                callback.onError(message, code);
            }
        });
    }

    public void confirmOrder(String orderId, String action, ResultCallback<AdminConfirmResult> callback) {
        withAdminToken(new TokenCallback() {
            @Override
            public void onToken(String authHeader) {
                enqueue(api.confirmOrder(authHeader, orderId, new AdminConfirmRequest(action)), callback);
            }

            @Override
            public void onError(String message, String code) {
                callback.onError(message, code);
            }
        });
    }

    public void reopenOrder(String orderId, ResultCallback<ReopenResult> callback) {
        withAdminToken(new TokenCallback() {
            @Override
            public void onToken(String authHeader) {
                enqueue(api.reopenDisputedOrder(authHeader, orderId, new Object()), callback);
            }

            @Override
            public void onError(String message, String code) {
                callback.onError(message, code);
            }
        });
    }

    public void unresolveOrder(String orderId, ResultCallback<ReopenResult> callback) {
        withAdminToken(new TokenCallback() {
            @Override
            public void onToken(String authHeader) {
                enqueue(api.unresolveReconciledOrder(authHeader, orderId, new Object()), callback);
            }

            @Override
            public void onError(String message, String code) {
                callback.onError(message, code);
            }
        });
    }

    public void listBuckets(String storeHandle, ResultCallback<List<BucketDto>> callback) {
        withAdminToken(new TokenCallback() {
            @Override
            public void onToken(String authHeader) {
                enqueue(api.listBuckets(authHeader, storeHandle), callback);
            }

            @Override
            public void onError(String message, String code) {
                callback.onError(message, code);
            }
        });
    }

    public void createBucket(CreateBucketRequest request, ResultCallback<BucketDto> callback) {
        withAdminToken(new TokenCallback() {
            @Override
            public void onToken(String authHeader) {
                enqueue(api.createBucket(authHeader, request), callback);
            }

            @Override
            public void onError(String message, String code) {
                callback.onError(message, code);
            }
        });
    }

    public void updateBucketStatus(String bucketId, String status, ResultCallback<BucketDto> callback) {
        withAdminToken(new TokenCallback() {
            @Override
            public void onToken(String authHeader) {
                enqueue(api.updateBucketStatus(authHeader, bucketId, new BucketStatusUpdateRequest(status)), callback);
            }

            @Override
            public void onError(String message, String code) {
                callback.onError(message, code);
            }
        });
    }

    public void updateBucket(String bucketId, UpdateBucketRequest request, ResultCallback<BucketDto> callback) {
        withAdminToken(new TokenCallback() {
            @Override
            public void onToken(String authHeader) {
                enqueue(api.updateBucket(authHeader, bucketId, request), callback);
            }

            @Override
            public void onError(String message, String code) {
                callback.onError(message, code);
            }
        });
    }

    private interface TokenCallback {
        void onToken(String authHeader);

        void onError(String message, String code);
    }

    private void withAdminToken(TokenCallback callback) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            callback.onError("Please sign in again.", "UNAUTHORIZED");
            return;
        }

        user.getIdToken(false)
                .addOnSuccessListener(result -> callback.onToken("Bearer " + result.getToken()))
                .addOnFailureListener(error -> callback.onError(
                        error.getMessage() != null ? error.getMessage() : "Unable to get auth token.",
                        "UNAUTHORIZED"
                ));
    }

    private ListenerRegistration subscribeOrdersByStatuses(
            String storeHandle,
            List<String> statuses,
            ResultCallback<List<PaymentOrderItem>> callback,
            ApiSnapshotLoader<PaymentOrderItem> fallbackLoader
    ) {
        String scoped = normalizeStoreHandle(storeHandle);
        if (scoped.isEmpty()) {
            callback.onError("Store scope missing.", "STORE_SCOPE_REQUIRED");
            return null;
        }

        Query query = firestore.collection("orders")
                .whereEqualTo("storeHandle", scoped);

        if (statuses.size() == 1) {
            query = query.whereEqualTo("paymentStatus", statuses.get(0));
        } else {
            query = query.whereIn("paymentStatus", statuses);
        }

        query = query.orderBy("createdAt", Query.Direction.DESCENDING).limit(200);

        return query.addSnapshotListener((snapshot, error) -> {
            if (error != null) {
                handleRealtimeReadError(
                        scoped,
                        error,
                        "Unable to load payment orders.",
                        callback,
                        fallbackLoader
                );
                return;
            }

            callback.onSuccess(mapOrders(snapshot != null ? snapshot.getDocuments() : Collections.emptyList()));
        });
    }

    private interface ApiSnapshotLoader<T> {
        void load(String scopedStoreHandle, ResultCallback<List<T>> callback);
    }

    private <T> void handleRealtimeReadError(
            String scopedStoreHandle,
            Exception error,
            String defaultMessage,
            ResultCallback<List<T>> callback,
            ApiSnapshotLoader<T> fallbackLoader
    ) {
        String firestoreMessage = error != null && error.getMessage() != null
                ? error.getMessage()
                : defaultMessage;

        if (realtimeReadMode == RealtimeReadMode.FIRESTORE_THEN_API_SNAPSHOT && fallbackLoader != null) {
            callback.onError(
                    "Realtime sync unavailable. Switched to one-shot API snapshot mode.",
                    CODE_REALTIME_FALLBACK_ACTIVE
            );

            fallbackLoader.load(scopedStoreHandle, new ResultCallback<List<T>>() {
                @Override
                public void onSuccess(List<T> data) {
                    callback.onSuccess(data != null ? data : Collections.emptyList());
                }

                @Override
                public void onError(String message, String code) {
                    String normalizedCode = (code == null || code.trim().isEmpty())
                            ? CODE_REALTIME_FALLBACK_FAILED
                            : code;
                    callback.onError(
                            message != null ? message : "Unable to load fallback snapshot.",
                            normalizedCode
                    );
                }
            });
            return;
        }

        callback.onError(firestoreMessage, CODE_FIRESTORE_READ_FAILED);
    }

    private void fetchPendingOrdersSnapshot(String storeHandle, ResultCallback<List<PaymentOrderItem>> callback) {
        listPendingOrders(SNAPSHOT_FALLBACK_LIMIT, null, storeHandle, new ResultCallback<CursorPage<PaymentOrderItem>>() {
            @Override
            public void onSuccess(CursorPage<PaymentOrderItem> data) {
                callback.onSuccess(data != null && data.items != null ? data.items : Collections.emptyList());
            }

            @Override
            public void onError(String message, String code) {
                callback.onError(message, code);
            }
        });
    }

    private void fetchReviewOrdersSnapshot(String storeHandle, ResultCallback<List<PaymentOrderItem>> callback) {
        listReviewOrders(SNAPSHOT_FALLBACK_LIMIT, null, storeHandle, new ResultCallback<CursorPage<PaymentOrderItem>>() {
            @Override
            public void onSuccess(CursorPage<PaymentOrderItem> data) {
                callback.onSuccess(data != null && data.items != null ? data.items : Collections.emptyList());
            }

            @Override
            public void onError(String message, String code) {
                callback.onError(message, code);
            }
        });
    }

    private void fetchHistoryOrdersSnapshot(String storeHandle, ResultCallback<List<PaymentOrderItem>> callback) {
        listHistoryOrders(SNAPSHOT_FALLBACK_LIMIT, null, storeHandle, new ResultCallback<CursorPage<PaymentOrderItem>>() {
            @Override
            public void onSuccess(CursorPage<PaymentOrderItem> data) {
                callback.onSuccess(data != null && data.items != null ? data.items : Collections.emptyList());
            }

            @Override
            public void onError(String message, String code) {
                callback.onError(message, code);
            }
        });
    }

    private void fetchBucketsSnapshot(String storeHandle, ResultCallback<List<BucketDto>> callback) {
        listBuckets(storeHandle, new ResultCallback<List<BucketDto>>() {
            @Override
            public void onSuccess(List<BucketDto> data) {
                callback.onSuccess(data != null ? data : Collections.emptyList());
            }

            @Override
            public void onError(String message, String code) {
                callback.onError(message, code);
            }
        });
    }

    private List<PaymentOrderItem> mapOrders(List<DocumentSnapshot> docs) {
        List<PaymentOrderItem> items = new ArrayList<>();
        for (DocumentSnapshot doc : docs) {
            PaymentOrderItem item = new PaymentOrderItem();
            item.orderId = doc.getId();
            item.orderAmount = toDouble(doc.get("orderAmount"));
            if (item.orderAmount <= 0d) {
                item.orderAmount = toDouble(doc.get("totalAmount"));
            }

            item.uniquePayableAmount = toDouble(doc.get("uniquePayableAmount"));
            if (item.uniquePayableAmount <= 0d) {
                item.uniquePayableAmount = toDouble(doc.get("paymentUniquePayableAmount"));
            }

            item.utrNumber = toStringOrNull(doc.get("utrNumber"));
            item.paymentStatus = toStringOrNull(doc.get("paymentStatus"));
            item.createdAt = toMillis(doc.getTimestamp("createdAt"));
            if (item.createdAt == null) {
                item.createdAt = toMillis(doc.getTimestamp("orderDate"));
            }
            item.cancelledAt = toMillis(doc.getTimestamp("cancelledAt"));
            items.add(item);
        }
        return items;
    }

    private List<BucketDto> mapBuckets(List<DocumentSnapshot> docs) {
        List<BucketDto> items = new ArrayList<>();
        for (DocumentSnapshot doc : docs) {
            BucketDto bucket = new BucketDto();
            bucket.bucketId = doc.getId();
            bucket.vendorName = toStringOrNull(doc.get("vendorName"));
            bucket.vendorUpiId = toStringOrNull(doc.get("vendorUpiId"));
            bucket.qrImageUrl = toStringOrNull(doc.get("qrImageUrl"));
            bucket.qrType = toStringOrNull(doc.get("qrType"));
            bucket.priority = (int) toDouble(doc.get("priority"));
            bucket.limitAmount = toDouble(doc.get("limitAmount"));
            bucket.reservedAmount = toDouble(doc.get("reservedAmount"));
            bucket.collectedAmount = toDouble(doc.get("collectedAmount"));
            bucket.status = toStringOrNull(doc.get("status"));

            if (doc.contains("availableAmount")) {
                bucket.availableAmount = toDouble(doc.get("availableAmount"));
            } else {
                bucket.availableAmount = bucket.limitAmount - bucket.reservedAmount - bucket.collectedAmount;
            }

            items.add(bucket);
        }
        return items;
    }

    private String normalizeStoreHandle(String value) {
        return String.valueOf(value == null ? "" : value).trim().toLowerCase(Locale.ROOT);
    }

    private String toStringOrNull(Object value) {
        if (value == null) return null;
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private double toDouble(Object value) {
        if (value == null) return 0d;
        if (value instanceof Number) return ((Number) value).doubleValue();
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return 0d;
        }
    }

    private Long toMillis(Timestamp timestamp) {
        return timestamp != null ? timestamp.toDate().getTime() : null;
    }

    private <T> void enqueue(Call<ApiEnvelope<T>> call, ResultCallback<T> callback) {
        call.enqueue(new Callback<ApiEnvelope<T>>() {
            @Override
            public void onResponse(Call<ApiEnvelope<T>> call, Response<ApiEnvelope<T>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().success) {
                    callback.onSuccess(response.body().data);
                    return;
                }

                ApiErrorResponse apiError = parseApiError(response);
                if (apiError != null) {
                    callback.onError(
                            apiError.message != null ? apiError.message : "Request failed.",
                            apiError.code != null ? apiError.code : "REQUEST_FAILED"
                    );
                    return;
                }

                callback.onError("Request failed.", "REQUEST_FAILED");
            }

            @Override
            public void onFailure(Call<ApiEnvelope<T>> call, Throwable t) {
                callback.onError(t.getMessage() != null ? t.getMessage() : "Network error.", "NETWORK_ERROR");
            }
        });
    }

    private ApiErrorResponse parseApiError(Response<?> response) {
        if (response.errorBody() == null) return null;
        try {
            String raw = response.errorBody().string();
            return gson.fromJson(raw, ApiErrorResponse.class);
        } catch (IOException ignored) {
            return null;
        }
    }
}
