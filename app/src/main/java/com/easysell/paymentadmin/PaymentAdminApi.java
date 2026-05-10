package com.easysell.paymentadmin;

import com.easysell.paymentadmin.model.AdminConfirmRequest;
import com.easysell.paymentadmin.model.AdminConfirmResult;
import com.easysell.paymentadmin.model.ApiEnvelope;
import com.easysell.paymentadmin.model.BucketDto;
import com.easysell.paymentadmin.model.BucketStatusUpdateRequest;
import com.easysell.paymentadmin.model.CreateBucketRequest;
import com.easysell.paymentadmin.model.CursorPage;
import com.easysell.paymentadmin.model.PaymentOrderItem;
import com.easysell.paymentadmin.model.ReopenResult;
import com.easysell.paymentadmin.model.UpdateBucketRequest;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.PATCH;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface PaymentAdminApi {

    @POST("api/admin/payment/orders/{orderId}/confirm")
    Call<ApiEnvelope<AdminConfirmResult>> confirmOrder(
            @Header("Authorization") String auth,
            @Path("orderId") String orderId,
            @Body AdminConfirmRequest request
    );

    @POST("api/admin/payment/orders/{orderId}/reopen")
    Call<ApiEnvelope<ReopenResult>> reopenDisputedOrder(
            @Header("Authorization") String auth,
            @Path("orderId") String orderId,
            @Body Object emptyBody
    );

    @POST("api/admin/payment/orders/{orderId}/unresolve")
    Call<ApiEnvelope<ReopenResult>> unresolveReconciledOrder(
            @Header("Authorization") String auth,
            @Path("orderId") String orderId,
            @Body Object emptyBody
    );

    @GET("api/admin/payment/orders/pending")
    Call<ApiEnvelope<CursorPage<PaymentOrderItem>>> listPendingOrders(
            @Header("Authorization") String auth,
            @Query("limit") Integer limit,
            @Query("cursor") Long cursor,
            @Query("storeHandle") String storeHandle
    );

    @GET("api/admin/payment/orders/review")
    Call<ApiEnvelope<CursorPage<PaymentOrderItem>>> listReviewOrders(
            @Header("Authorization") String auth,
            @Query("limit") Integer limit,
            @Query("cursor") Long cursor,
            @Query("storeHandle") String storeHandle
    );

    @GET("api/admin/payment/orders/history")
    Call<ApiEnvelope<CursorPage<PaymentOrderItem>>> listHistoryOrders(
            @Header("Authorization") String auth,
            @Query("limit") Integer limit,
            @Query("cursor") Long cursor,
            @Query("storeHandle") String storeHandle
    );

    @GET("api/admin/payment/buckets")
    Call<ApiEnvelope<List<BucketDto>>> listBuckets(
            @Header("Authorization") String auth,
            @Query("storeHandle") String storeHandle
    );

    @POST("api/admin/payment/buckets")
    Call<ApiEnvelope<BucketDto>> createBucket(
            @Header("Authorization") String auth,
            @Body CreateBucketRequest request
    );

    @PATCH("api/admin/payment/buckets/{bucketId}")
    Call<ApiEnvelope<BucketDto>> updateBucket(
            @Header("Authorization") String auth,
            @Path("bucketId") String bucketId,
            @Body UpdateBucketRequest request
    );

    @PATCH("api/admin/payment/buckets/{bucketId}/status")
    Call<ApiEnvelope<BucketDto>> updateBucketStatus(
            @Header("Authorization") String auth,
            @Path("bucketId") String bucketId,
            @Body BucketStatusUpdateRequest request
    );
}
