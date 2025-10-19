package com.easysell.network;

import java.util.Map;

import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Headers;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface DriveApiService {
    @GET("drive/v3/files")
    Call<ResponseBody> findFolder(
            @Header("Authorization") String authToken,
            @Query("q") String query,
            @Query("fields") String fields,
            @Query("spaces") String spaces
    );

    @POST("drive/v3/files")
    @Headers("Content-Type: application/json")
    Call<ResponseBody> createFolder(
            @Header("Authorization") String authToken,
            @Body RequestBody metadata
    );

    @Multipart
    @POST("upload/drive/v3/files?uploadType=multipart")
    Call<ResponseBody> uploadFileMultipart(
            @Header("Authorization") String authToken,
            @Part("metadata") RequestBody metadata,
            @Part MultipartBody.Part file
    );

    @POST("drive/v3/files/{fileId}/permissions")
    Call<ResponseBody> createPermissions(
            @Header("Authorization") String authToken,
            @Path("fileId") String fileId,
            @Body Map<String, String> permissionBody
    );

    @GET("drive/v3/files/{fileId}?fields=webViewLink")
    Call<ResponseBody> getFileWebViewLink(
            @Header("Authorization") String authToken,
            @Path("fileId") String fileId
    );
}