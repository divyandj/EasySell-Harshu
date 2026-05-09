package com.easysell.paymentadmin;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public final class PaymentAdminClient {

    private static final String DEFAULT_BASE_URL = "https://easysell-backend-aweq.onrender.com/";

    private static volatile PaymentAdminApi api;

    private PaymentAdminClient() {
    }

    public static PaymentAdminApi getApi() {
        if (api == null) {
            synchronized (PaymentAdminClient.class) {
                if (api == null) {
                    HttpLoggingInterceptor logger = new HttpLoggingInterceptor();
                    logger.setLevel(HttpLoggingInterceptor.Level.BODY);

                    OkHttpClient client = new OkHttpClient.Builder()
                            .addInterceptor(logger)
                            .build();

                    Retrofit retrofit = new Retrofit.Builder()
                            .baseUrl(DEFAULT_BASE_URL)
                            .client(client)
                            .addConverterFactory(GsonConverterFactory.create())
                            .build();

                    api = retrofit.create(PaymentAdminApi.class);
                }
            }
        }
        return api;
    }
}
