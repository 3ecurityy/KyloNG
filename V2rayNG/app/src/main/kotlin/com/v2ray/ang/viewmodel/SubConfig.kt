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

    @SerializedName("sortOrder")
    @Expose
    private val sortOrder: String? = null

    @SerializedName("id")
    @Expose
    private val id: String? = null

    private var isActive: Boolean = false

    fun getId(): String? {
        return id
    }

    fun getIsActive(): Boolean {
        return isActive
    }

    fun setIsActive(value:Boolean){
        this.isActive = value
    }

    fun getSortOrder(): String? {
        return sortOrder
    }

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