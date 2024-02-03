package com.v2ray.ang.viewmodel

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName

class SubConfig {

    @SerializedName("city")
    @Expose
    private val city: String? = null

    @SerializedName("config")
    @Expose
    private val config: String? = null

    @SerializedName("country")
    @Expose
    private val country: String? = null


    @SerializedName("img")
    @Expose
    private val img: String? = null

    fun getImg(): String? {
        return img
    }


    fun getConfig(): String? {
        return config
    }

    fun getCountry(): String? {
        return country
    }

    fun getCity(): String? {
        return city
    }
}