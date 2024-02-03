package com.v2ray.ang.viewmodel;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;

public class ConfigResponse {


    @SerializedName("contacts")
    @Expose
    private ArrayList<com.v2ray.ang.viewmodel.SubConfig> SubConfig;

    @SerializedName("version")
    @Expose
    private String version;

    public ArrayList<SubConfig> getSubConfig() {
        return SubConfig;
    }

    public String getVersion() {
        return version;
    }
}
