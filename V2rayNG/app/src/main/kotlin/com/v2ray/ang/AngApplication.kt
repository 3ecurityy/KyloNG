package com.v2ray.ang

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.annotation.NonNull
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.multidex.MultiDexApplication
import androidx.preference.PreferenceManager
import androidx.work.Configuration
import com.tencent.mmkv.MMKV
import com.v2ray.ang.ui.MainActivity
import com.v2ray.ang.util.Utils
import java.util.concurrent.TimeUnit
import kotlin.math.log


class AngApplication() : MultiDexApplication(),
    Configuration.Provider, DefaultLifecycleObserver {


    companion object {
        const val AD_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"
        const val PREF_LAST_VERSION = "pref_last_version"
        lateinit var application: AngApplication
    }

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        application = this
    }

    lateinit var shPref: SharedPreferences

    var firstRun = false
        private set

    @Override
    override fun onCreate() {
        super<MultiDexApplication>.onCreate()
        // ProcessLifecycleOwner.get().lifecycle.addObserver(this)
//        LeakCanary.install(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(this);

        val defaultSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        firstRun = defaultSharedPreferences.getInt(PREF_LAST_VERSION, 0) != BuildConfig.VERSION_CODE
        if (firstRun)
            defaultSharedPreferences.edit().putInt(PREF_LAST_VERSION, BuildConfig.VERSION_CODE)
                .commit()

        shPref = getSharedPreferences("MyPref", Context.MODE_PRIVATE);
        //Logger.init().logLevel(if (BuildConfig.DEBUG) LogLevel.FULL else LogLevel.NONE)
        MMKV.initialize(this)
    }

    override fun getWorkManagerConfiguration(): Configuration {
        return Configuration.Builder()
            .setDefaultProcessName("${BuildConfig.APPLICATION_ID}:bg")
            .build()
    }


    override fun onStart(owner: LifecycleOwner) {
        Log.d("TAG", "onStart")
        super.onStart(owner!!)
    }

    override fun onResume(owner: LifecycleOwner) {
        Log.d("TAG", "onResume")
        super.onResume(owner!!)
    }

    override fun onPause(owner: LifecycleOwner) {
        Log.d("TAG", "onPause")
        super.onPause(owner!!)
    }

    override fun onStop(owner: LifecycleOwner) {
        Log.d("TAG", "onStop")
        if (MainActivity.isStartClik) {
            Utils.stopVService(this)
        }

        val timeStamp = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis())
        //mainStorage?.encode("outTime", timeStamp.toString())
        shPref.edit().putLong("outTime", timeStamp).apply()
        val lastStart = shPref.getLong("outTime", 0)
        Log.d("TAG  OUT", lastStart.toString())

        super.onStop(owner)
    }


    override fun onDestroy(owner: LifecycleOwner) {
        Log.d("TAG", "onDestroy")
        super.onDestroy(owner)
    }


}
