package com.v2ray.ang

import android.content.Context
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.multidex.MultiDexApplication
import androidx.preference.PreferenceManager
import androidx.work.Configuration
import com.google.android.gms.ads.MobileAds
import com.tencent.mmkv.MMKV


class AngApplication() : MultiDexApplication(),
    Configuration.Provider {


    companion object {
        const val AD_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"
        const val PREF_LAST_VERSION = "pref_last_version"
        lateinit var application: AngApplication
    }

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        application = this
    }

    var firstRun = false
        private set

    override fun onCreate() {
        super.onCreate()

//        LeakCanary.install(this)

        val defaultSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        firstRun = defaultSharedPreferences.getInt(PREF_LAST_VERSION, 0) != BuildConfig.VERSION_CODE
        if (firstRun)
            defaultSharedPreferences.edit().putInt(PREF_LAST_VERSION, BuildConfig.VERSION_CODE).commit()

        //Logger.init().logLevel(if (BuildConfig.DEBUG) LogLevel.FULL else LogLevel.NONE)
        MMKV.initialize(this)
    }

    override fun getWorkManagerConfiguration(): Configuration {
        return Configuration.Builder()
            .setDefaultProcessName("${BuildConfig.APPLICATION_ID}:bg")
            .build()
    }


    private fun initializeMobileAdsSdk() {
        if (isMobileAdsInitializeCalled.getAndSet(true)) {
            return
        }

        // Initialize the Mobile Ads SDK.
        MobileAds.initialize(this) { initializationStatus ->
            // Load an ad.
            loadAd()
        }
    }
}
