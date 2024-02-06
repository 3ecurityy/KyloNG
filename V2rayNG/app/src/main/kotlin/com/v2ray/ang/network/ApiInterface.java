package com.v2ray.ang.network;

import com.google.gson.annotations.SerializedName;
import com.v2ray.ang.viewmodel.ConfigResponse;


import io.reactivex.Single;
import retrofit2.Response;
import retrofit2.http.GET;

public interface ApiInterface {

    // Register api method
    @SerializedName("")
    @GET("/api/servers.json")
    Single<Response<ConfigResponse>> GetConfig();

}
