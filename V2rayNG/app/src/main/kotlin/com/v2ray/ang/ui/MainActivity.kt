package com.v2ray.ang.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageInfo
import android.content.pm.PackageManager.NameNotFoundException
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.transition.Fade
import android.transition.Transition
import android.transition.TransitionManager
import android.util.Log
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.RatingBar
import android.widget.RatingBar.OnRatingBarChangeListener
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.material.navigation.NavigationView
import com.tbruyelle.rxpermissions.RxPermissions
import com.tencent.mmkv.MMKV
import com.v2ray.ang.AppConfig
import com.v2ray.ang.AppConfig.ANG_PACKAGE
import com.v2ray.ang.BottomSheet
import com.v2ray.ang.R
import com.v2ray.ang.databinding.ActivityMainBinding
import com.v2ray.ang.dto.EConfigType
import com.v2ray.ang.extension.toast
import com.v2ray.ang.helper.SimpleItemTouchHelperCallback
import com.v2ray.ang.helper.SpeedListener
import com.v2ray.ang.network.ApiInterface
import com.v2ray.ang.network.Const
import com.v2ray.ang.network.RetrofitBuilder
import com.v2ray.ang.service.V2RayServiceManager
import com.v2ray.ang.util.AngConfigManager
import com.v2ray.ang.util.MmkvManager
import com.v2ray.ang.util.Utils
import com.v2ray.ang.viewmodel.ConfigResponse
import com.v2ray.ang.viewmodel.MainViewModel
import com.v2ray.ang.viewmodel.SubConfig
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.observers.DisposableSingleObserver
import io.reactivex.schedulers.Schedulers
import ir.samanjafari.easycountdowntimer.CountDownInterface
import ir.samanjafari.easycountdowntimer.EasyCountDownTextview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.drakeet.support.toast.ToastCompat
import org.json.JSONObject
import retrofit2.Response
import rx.Completable
import rx.Observable
import rx.android.schedulers.AndroidSchedulers
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.properties.Delegates


class MainActivity : BaseActivity(), SpeedListener,
    NavigationView.OnNavigationItemSelectedListener, DefaultLifecycleObserver {
    private lateinit var binding: ActivityMainBinding
    private var seconds by Delegates.notNull<Int>()
    private var running: Boolean = false

    private val scope = MainScope()
    private var job: Job? = null

    val itemList = ArrayList<SubConfig>()
    private val adapter by lazy { MainRecyclerAdapter2(this, itemList) }

    private val mainStorage by lazy {
        MMKV.mmkvWithID(
            MmkvManager.ID_MAIN, MMKV.MULTI_PROCESS_MODE
        )
    }
    private val settingsStorage by lazy {
        MMKV.mmkvWithID(
            MmkvManager.ID_SETTING, MMKV.MULTI_PROCESS_MODE
        )
    }
    private val requestVpnPermission =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == RESULT_OK) {
                startV2Ray()
            }
        }
    private var mItemTouchHelper: ItemTouchHelper? = null
    val mainViewModel: MainViewModel by viewModels()

    private var compositeDisposable: CompositeDisposable? = null
    private lateinit var getApi: ApiInterface

    private var mInterstitialAd: InterstitialAd? = null

    private final var TAG = "MainActivity"
    var shConnectionCount = "CONNECTION_COUNT"
    var shUserSetRate = "USER_SET_RATE"

    enum class AppStart {
        FIRST_TIME, FIRST_TIME_VERSION, NORMAL
    }

    companion object {
        var isStartClik = false
    }


    private val LAST_APP_VERSION = "last_app_version"
    var selectedItemUUId = "A"

    //private var leftRewardTime = mainStorage.decodeInt("rewardTime")
    private var leftRewardTime = 0
    private var baseRewardTime = 0

    lateinit var shPref: SharedPreferences
    val bundle = Bundle()
    val modalBottomSheet = BottomSheet(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super<BaseActivity>.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)
        changeStatusBarColor()
        registerMsgReceiver()
        MobileAds.initialize(this)

        binding.parentGasStation.isEnabled = false

        ProcessLifecycleOwner.get().lifecycle.addObserver(this)

        shPref = getSharedPreferences("MyPref", Context.MODE_PRIVATE);

        MmkvManager.removeAllServer()
        mainViewModel.serversCache.clear()
        mainViewModel.serverList.clear()

        selectedItemUUId = shPref.getString("UUID", "A").toString()

        compositeDisposable = CompositeDisposable()
        getApi = RetrofitBuilder.Create(Const.BASE_URL)
        getBaseData()


        binding.parentGasStation.setOnClickListener {
            modalBottomSheet.show(supportFragmentManager, "ModalBottomSheet.TAG")
        }

        if (leftRewardTime < 0) {
            Utils.stopVService(this)
        }


        binding.fab.setOnClickListener {
            Log.d("TAG - getCurrentRewardTime", getCurrentRewardTime().toString())
            if (getCurrentRewardTime() > 0) {
                if (mainViewModel.isRunning.value == true) {
                    //Stop Timer
                    binding.tvTimer2.visibility = View.INVISIBLE

                    //Change Icon ImageView
                    binding.imageView2.visibility = View.VISIBLE
                    binding.imageView2.setImageDrawable(
                        ContextCompat.getDrawable(this, R.drawable.connectiong)
                    )

                    binding.tvConnected.text = resources.getString(R.string.disconnect)
                    binding.fab.isEnabled = false

                    showOnDisConnectedInterstitialAd()
                } else if (settingsStorage?.decodeString(AppConfig.PREF_MODE) ?: "VPN" == "VPN") {
                    val intent = VpnService.prepare(this)
                    if (intent == null) {
                        startV2Ray()
                    } else {
                        requestVpnPermission.launch(intent)
                    }
                } else {
                    startV2Ray()
                }

                val timeStart = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis()).toInt()
                Log.d("TAG", "Time Start $timeStart")
                shPref.edit().putInt("LastStart", timeStart).apply()

            } else {
                modalBottomSheet.show(supportFragmentManager, "ModalBottomSheet.TAG")
            }

        }

        setupViewModel()
        copyAssets()
        migrateLegacy()
        getPermissions()

        mInterstitialAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdClicked() {
                Log.d("TAG", "onAdClicked")
                isStartClik = false
            }

            override fun onAdDismissedFullScreenContent() {
                Log.d("TAG", "onAdDismissedFullScreenContent")
                isStartClik = false
                mInterstitialAd = null
            }

            override fun onAdFailedToShowFullScreenContent(p0: AdError) {
                Log.d("TAG", "onAdFailedToShowFullScreenContent")
                isStartClik = false
                mInterstitialAd = null
            }

            override fun onAdImpression() {
                Log.d("TAG", "onAdImpression")
                isStartClik = false
            }

            override fun onAdShowedFullScreenContent() {
                Log.d("TAG", "onAdShowedFullScreenContent")
            }
        }

        // changeTheme(true)


        //For test DEsign Dialog
        shPref.edit().putInt(shConnectionCount, 10).apply()
        shPref.edit().putBoolean(shUserSetRate, false).apply()
        handleUserRating()


    }


    private fun handleUserRating() {
        if (shPref.getInt(shConnectionCount, 0) > 5 && !shPref.getBoolean(shUserSetRate, false)) {
            val dialogBuilder = AlertDialog.Builder(this)
            val dialogView = layoutInflater.inflate(R.layout.view_dialog_rating, null)
            dialogBuilder.setView(dialogView)
            val rating = dialogView.findViewById<RatingBar>(R.id.myRatingBar)
            rating.onRatingBarChangeListener =
                OnRatingBarChangeListener { ratingBar, value, fromUser ->
                    shPref.edit().putInt(shConnectionCount, 0).apply()
                    if (value >= 4) {
                        shPref.edit().putBoolean(shUserSetRate, true).apply()
                        val intent = Intent(Intent.ACTION_VIEW)
                        intent.setData(Uri.parse("https://play.google.com/store/apps/details?id=com.v2ray.ang"))
                        intent.setPackage("com.android.vending")
                        startActivity(intent)
                    } else {
                        finish()
                    }
                }
            val alertDialog = dialogBuilder.create()
            alertDialog.show()
        }
    }

    //Get Current Real Reward Time
    private fun getCurrentRewardTime(): Int {
        return shPref.getInt("rewardTime", 0)
    }

    private fun updateRewardTime(value: Int) {
        val sumRewardTime: Int
        if (getCurrentRewardTime() > 0) {

        }

    }

    private fun getPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            RxPermissions(this).request(Manifest.permission.POST_NOTIFICATIONS).subscribe {
                if (!it) toast(R.string.toast_permission_denied)
            }
        }
    }

    private fun changeStatusBarColor() {
        val window = this.window
        if (mainViewModel.isRunning.value == true) {
            window.statusBarColor = resources.getColor(R.color.primaryYellow)
        } else {
            window.statusBarColor = resources.getColor(R.color.primaryGray)
        }
    }

    private fun registerMsgReceiver() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            application.registerReceiver(
                mMsgReceiver,
                IntentFilter(AppConfig.BROADCAST_ACTION_ACTIVITY),
                Context.RECEIVER_EXPORTED
            )
        } else {
            application.registerReceiver(
                mMsgReceiver,
                IntentFilter(AppConfig.BROADCAST_ACTION_ACTIVITY)
            )
        }
    }


    private val mMsgReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (!BottomSheet.itsFromBottomSheet)
                when (intent?.getIntExtra("key", 0)) {
                    AppConfig.MSG_STATE_START_SUCCESS -> {
                        showOnConnectedInterstitialAd()

                        val lastCount = shPref.getInt(shConnectionCount, 0)
                        shPref.edit().putInt(shConnectionCount, lastCount + 1).apply()

                        Log.d("TAG", "IS RUN")
                    }
                }
        }
    }

    private fun startConnectionTimer() {
        val lastStart = shPref.getInt("LastStart", 0)
        Log.d("TAG", "lastStart $lastStart")
        val timeIn = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis()).toInt()
        Log.d("TAG", "timeIn $timeIn")
        if (lastStart == 0) {
            //return
        } else {
            seconds = timeIn - lastStart
            Log.d("TAG", "seconds $seconds")
        }

        Log.d("TAG", "SECOND in startConnectionTimer $seconds")
        val timeView = findViewById<View>(R.id.tv_timer2) as TextView
        var seconds = seconds
        job = scope.launch {
            while (true) {
                val hours: Int = seconds / 3600
                val minutes: Int = seconds % 3600 / 60
                val secs: Int = seconds % 60
                val time = String.format(
                    Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, secs
                )
                timeView.text = time
                if (running) {
                    seconds++
                }
                delay(1000)
            }
        }
    }

    private fun normalStart() {
        val timeIn = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis()).toInt()
        Log.d("TAG : OC IN", timeIn.toString())
        // val timeOutPr = mainStorage?.decodeString("outTime").toInt()
        val timeOutPr: Long = shPref.getLong("outTime", 0)
        Log.d("TAG : OC OUT", timeOutPr.toString())

        //Change It for when reward is expire and we want to test it :)
        var diffBetweenTwoTime = 0L
        var getRealRewardTime = 0L
        if (shPref.getBoolean("startTimer", false)) {
            //Dot nothing :)
            getRealRewardTime = 900L
        } else {
            diffBetweenTwoTime = timeIn - timeOutPr
            getRealRewardTime = getCurrentRewardTime() - diffBetweenTwoTime
        }



        bundle.putString("key", getRealRewardTime.toString())

        shPref.edit().putInt("rewardTime", getRealRewardTime.toInt()).apply()

        Log.d("TAG : getRealRewardTime", leftRewardTime.toString())

        startTimer(
            getH(getRealRewardTime).toInt(),
            getMin(getRealRewardTime).toInt(),
            getSec(getRealRewardTime).toInt()
        )
        Toast.makeText(this, getRealRewardTime.toString(), Toast.LENGTH_LONG).show()
    }

    private fun firstTimeStart() {
        leftRewardTime = baseRewardTime
        Log.d("TAG", "baseRewardTime $baseRewardTime")
        Log.d("TAG", "leftRewardTime $leftRewardTime")
        startTimer(
            getH(leftRewardTime.toLong()).toInt(),
            getMin(leftRewardTime.toLong()).toInt(),
            getSec(leftRewardTime.toLong()).toInt()
        )
        bundle.putString("key", leftRewardTime.toString())
    }

    private fun startTimer(h: Int, m: Int, s: Int) {

        var baseRw = shPref.getInt("baseRewardInt", 0);
        val countDownTextview = findViewById<View>(R.id.tv_timer) as EasyCountDownTextview
        countDownTextview.setTime(0, h, m, s)
        countDownTextview.setOnTick(object : CountDownInterface {
            override fun onTick(time: Long) {
                //   Log.d("TAG On Tick Rw", getCurrentRewardTime().toString())
                //  Log.d("TAG On Tick Rw2", shPref.getInt("rewardTime2", 0).toString())
                leftRewardTime = getCurrentRewardTime()

                if (shPref.getBoolean("PlusReward", false)) {
                    //updateRewardTime(rewardPlus)
                    Log.d("TAG On Tick IF ", "IN IF")
                    val sumRewardTime = getCurrentRewardTime() + baseRw
                    leftRewardTime = sumRewardTime
                    Log.d("TAG", "TAG On Tick LOL $sumRewardTime")
                    Log.d("TAG", "TAG On Tick LOL2 $leftRewardTime")
                    Log.d("TAG", "TAG On Tick LOL-A ${getCurrentRewardTime()}")
                    shPref.edit().putInt("rewardTime", leftRewardTime).apply()
                    Log.d("TAG", "TAG On Tick LOL-B ${getCurrentRewardTime()}")
                    shPref.edit().putBoolean("PlusReward", false).apply()

                    // shPref.edit().putInt("rewardTime2", 0).apply()
                    //shPref.edit().putInt("rewardTime", leftRewardTime + rewardPlus).apply()
                    // leftRewardTime += rewardPlus
                }

                val lol = (time / 1000)
                Log.d("TAG", "TAG On Tick LOL-C ${getCurrentRewardTime()}")
                Log.d("TAG", "TAG On Tick LOL-D $lol")
                // leftRewardTime -= lol.toInt()
                Log.d("TAG", "TAG On Tick LOL-F $leftRewardTime")
                shPref.edit().putInt("rewardTime", leftRewardTime - 1).apply()
                Log.d("TAG", "TAG On Tick LOL-B ${getCurrentRewardTime()}")

                val time =
                    getH(getCurrentRewardTime().toLong()).toString() + "H" + " " + getMin(
                        getCurrentRewardTime().toLong()
                    ) + "M"

                shPref.edit().putString("time", time).apply()
                binding.tvTimerReward.text = time
            }

            override fun onFinish() {
                Utils.stopVService(this@MainActivity)
                shPref.edit().putBoolean("rewardExpire", true).apply()
                //V2RayServiceManager.stopV2rayPoint()
            }
        })
        countDownTextview.startTimer()
    }

    private fun getSec(ms: Long): Long {
        return (ms % 60);
    }

    private fun getMin(ms: Long): Long {
        return (ms / 60 % 60);
    }

    private fun getH(ms: Long): Long {
        return ((ms / (60 * 60)) % 24);
    }

    private fun startBuildingAds() {
        Toast.makeText(this, "U Need to Watch Ads to get Reward :)", Toast.LENGTH_SHORT).show()
    }

    private fun getBaseData() {
        //  Log.i("TAG", "GET BASE DATA")
        val disposable: Disposable = getApi.GetConfig()
            .observeOn(io.reactivex.android.schedulers.AndroidSchedulers.mainThread())
            .subscribeOn(Schedulers.io())
            .subscribeWith(object : DisposableSingleObserver<Response<ConfigResponse>>() {
                override fun onSuccess(t: Response<ConfigResponse>) {
                    if (t.body() != null) {
                        settingsStorage?.encode("Verison", t.body()!!.version)
                        if (t.body()!!.subConfig != null) {
                            if (t.body()!!.subConfig.size > 0) {
                                for (i in 0 until t.body()!!.subConfig.size) {
                                    val obj = JSONObject(t.body()!!.subConfig[i].getConfig()!!)
                                    itemList.add(t.body()!!.subConfig[i])
                                    importCustomizeConfig(
                                        t.body()!!.subConfig[i].getConfig().toString()
                                    )
                                }
                                binding.parentLoading.fadeVisibility(View.GONE)
                                initRecyclerview()
                            }
                        }
                    }

                    baseRewardTime = t.body()!!.rewardTime!!.toLong().toInt()
                    Log.d("TAG", "baseRewardTime $baseRewardTime")

                    bundle.putInt("baseReward", baseRewardTime)
                    shPref.edit().putInt("baseRewardInt", baseRewardTime).apply()

                    modalBottomSheet.arguments = bundle
                    binding.fab.isEnabled = true
                    binding.parentGasStation.isEnabled = true

                    when (checkAppStart()) {
                        AppStart.NORMAL -> {
                            normalStart()
                        }

                        AppStart.FIRST_TIME_VERSION -> {
                            normalStart()
                        }

                        AppStart.FIRST_TIME -> {
                            firstTimeStart()
                            shPref.edit().putInt("rewardTime", baseRewardTime).apply()
                            shPref.edit().putBoolean(shUserSetRate, false).apply()
                            shPref.edit().putInt(shConnectionCount, 0).apply()
                            shPref.edit().putBoolean("selectPos0", true).apply()
                        }

                        else -> {}
                    }

                }

                override fun onError(e: Throwable) {
                }
            })
        compositeDisposable?.add(disposable)
    }

    fun View.fadeVisibility(visibility: Int, duration: Long = 400) {
        val transition: Transition = Fade()
        transition.duration = duration
        transition.addTarget(this)
        TransitionManager.beginDelayedTransition(this.parent as ViewGroup, transition)
        this.visibility = visibility
    }

    private fun checkAppStart(): AppStart? {
        val pInfo: PackageInfo
        val sharedPreferences: SharedPreferences =
            PreferenceManager.getDefaultSharedPreferences(this)
        var appStart: AppStart? = AppStart.NORMAL
        try {
            pInfo = packageManager.getPackageInfo(packageName, 0)
            val lastVersionCode = sharedPreferences.getInt(LAST_APP_VERSION, -1)
            val currentVersionCode = pInfo.versionCode
            appStart = checkAppStart(currentVersionCode, lastVersionCode)
            // Update version in preferences
            sharedPreferences.edit().putInt(LAST_APP_VERSION, currentVersionCode).apply()
        } catch (_: NameNotFoundException) {
        }
        return appStart
    }

    private fun checkAppStart(currentVersionCode: Int, lastVersionCode: Int): AppStart {
        return if (lastVersionCode == -1) {
            AppStart.FIRST_TIME
        } else if (lastVersionCode < currentVersionCode) {
            AppStart.FIRST_TIME_VERSION
        } else if (lastVersionCode > currentVersionCode) {
            AppStart.NORMAL
        } else {
            AppStart.NORMAL
        }
    }

    fun importConfigFromServer(config: String): Boolean {
        try {
            val configText = config
            if (TextUtils.isEmpty(configText)) {
                toast(R.string.toast_none_data_clipboard)
                return false
            }
            importCustomizeConfig(configText)
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    fun initRecyclerview() {
        Completable.timer(400, TimeUnit.MILLISECONDS, AndroidSchedulers.mainThread()).subscribe {
            binding.recyclerView.fadeVisibility(View.VISIBLE)
        }

        binding.recyclerView.setHasFixedSize(true)
        binding.recyclerView.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false);
        binding.recyclerView.adapter = adapter
        val callback = SimpleItemTouchHelperCallback(adapter)
        mItemTouchHelper = ItemTouchHelper(callback)
        mItemTouchHelper?.attachToRecyclerView(binding.recyclerView)


        Completable.timer(500, TimeUnit.MILLISECONDS, AndroidSchedulers.mainThread()).subscribe {
            Log.d("TAG UUID MA", selectedItemUUId.toString())
            if (selectedItemUUId.isNullOrEmpty()) {
                Log.d("selectedItd IS NULL", selectedItemUUId.toString())
                //  binding.recyclerView.findViewHolderForAdapterPosition(0)!!.itemView.performClick()
            }
        }


    }

    private fun changeTheme(isRun: Boolean) {
        Log.d("TAG", "IS RUN IS $isRun")
        if (isRun) {
            binding.tvTimer2.visibility = View.VISIBLE
            //binding.tvTimer.visibility = View.VISIBLE
            binding.tvLocation.setTextColor(resources.getColor(R.color.primaryGray))
            binding.imageViewSignal.isVisible = false
            binding.imageView2.isVisible = false
            binding.tvStatus.isVisible = false
            binding.imgMap.background = resources.getDrawable(R.drawable.mapconnect)
            binding.layoutParent.setBackgroundColor(resources.getColor(R.color.primaryYellow))
            binding.tvTap.text = "Tap Stop Button to Disconnect"
            binding.tvTap.setTextColor(resources.getColor(R.color.primaryGray))
            binding.tvKylo.setTextColor(resources.getColor(R.color.primaryGray))
            // binding.tvTimer.visibility = View.VISIBLE
            binding.parentGasStation.background =
                resources.getDrawable(R.drawable.reward_shape_connect)

            binding.tvTimerReward.setTextColor(resources.getColor((R.color.primaryGray)))
            window.statusBarColor = resources.getColor(R.color.primaryYellow)

            binding.fab.background = resources.getDrawable(R.drawable.connect_btn_bg)
            binding.fab.text = "STOP"
            binding.tvConnected.text = "Connected"
            binding.fab.isEnabled = true
            binding.tvWarning.visibility = View.GONE
            binding.animationView.visibility = View.GONE

        } else {
            //binding.tvTimer.visibility = View.GONE
            binding.tvLocation.setTextColor(resources.getColor(R.color.serverlocation))
            binding.imageViewSignal.isVisible = true
            binding.imageView2.isVisible = true
            binding.tvStatus.isVisible = true
            binding.imgMap.background = resources.getDrawable(R.drawable.map)
            binding.layoutParent.setBackgroundColor(resources.getColor(R.color.base_bg))
            binding.tvTap.text = "Tap Start Button to Connecting"
            binding.tvTap.setTextColor(resources.getColor(R.color.colorTapConnect))
            binding.tvKylo.setTextColor(resources.getColor(R.color.white))
            binding.parentGasStation.background = resources.getDrawable(R.drawable.reward_shape)
            binding.tvTimerReward.setTextColor(resources.getColor((R.color.white)))
            window.statusBarColor = resources.getColor(R.color.primaryGray)
            // binding.tvTimer.visibility = View.GONE
            //  stopwatch.reset()
            // stopwatch.stop()
            binding.fab.text = "START"
            binding.fab.background = resources.getDrawable(R.drawable.disconnect_btn_bg)
            binding.imageView2.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ns));

            binding.tvTimer2.visibility = View.GONE
            running = false
            seconds = 0;
            job?.cancel()
            job = null

        }
    }


    private fun setupViewModel() {
        mainViewModel.isRunning.observe(this) { isRunning ->
            adapter.isRunning = isRunning
            if (!BottomSheet.itsFromBottomSheet) {
                if (isRunning) {
                    running = true

                    if (!isStartClik) {
                        changeTheme(isRunning)
                    }

                } else {
                    binding.tvStatus.text = "Not Connected"
                    changeTheme(isRunning)
                }
            }

        }
        mainViewModel.startListenBroadcast()
    }

    private fun copyAssets() {
        val extFolder = Utils.userAssetPath(this)
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val geo = arrayOf("geosite.dat", "geoip.dat")
                assets.list("")?.filter { geo.contains(it) }
                    ?.filter { !File(extFolder, it).exists() }?.forEach {
                        val target = File(extFolder, it)
                        assets.open(it).use { input ->
                            FileOutputStream(target).use { output ->
                                input.copyTo(output)
                            }
                        }
                        Log.i(
                            ANG_PACKAGE, "Copied from apk assets folder to ${target.absolutePath}"
                        )
                    }
            } catch (e: Exception) {
                Log.e(ANG_PACKAGE, "asset copy failed", e)
            }
        }
    }

    private fun migrateLegacy() {
        lifecycleScope.launch(Dispatchers.IO) {
            val result = AngConfigManager.migrateLegacyConfig(this@MainActivity)
            if (result != null) {
                launch(Dispatchers.Main) {
                    if (result) {
                        toast(getString(R.string.migration_success))
                        mainViewModel.reloadServerList()
                    } else {
                        toast(getString(R.string.migration_fail))
                    }
                }
            }
        }
    }

    private fun startV2Ray() {
        if (mainStorage?.decodeString(MmkvManager.KEY_SELECTED_SERVER).isNullOrEmpty()) {
            return
        }
        isStartClik = true
        V2RayServiceManager.startV2Ray(this)
        running = true
        binding.fab.isEnabled = false
        binding.tvWarning.visibility = View.VISIBLE
        binding.animationView.visibility = View.VISIBLE
        binding.animationView.playAnimation()



        binding.imageView2.visibility = View.VISIBLE
        binding.imageView2.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.connectiong))
        binding.tvStatus.text = "Connecting"
        binding.tvTimer2.visibility = View.VISIBLE
    }

    fun showOnConnectedInterstitialAd() {
        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(
            this,
            "ca-app-pub-3940256099942544/1033173712",
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdFailedToLoad(adError: LoadAdError) {
                    Log.d("TAG ADS", adError.toString())
                    mInterstitialAd = null
                }

                override fun onAdLoaded(interstitialAd: InterstitialAd) {
                    Log.d("TAG ADS", "Ad was loaded.")
                    mInterstitialAd = interstitialAd
                    if (mInterstitialAd != null) {
                        mInterstitialAd?.show(this@MainActivity)
                        startConnectionTimer()
                        Observable.timer(500, TimeUnit.MILLISECONDS)
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe {
                                isStartClik = false
                            }
                        changeTheme(true)
                    } else {
                        Log.d("TAG", "The interstitial ad wasn't ready yet.")
                    }
                }
            })
    }

    private fun showOnDisConnectedInterstitialAd() {
        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(this, "ca-app-pub-3940256099942544/1033173712", adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdFailedToLoad(adError: LoadAdError) {
                    Log.d("TAG ADS", adError.toString())
                    mInterstitialAd = null
                }

                override fun onAdLoaded(interstitialAd: InterstitialAd) {

                    Log.d("TAG ADS", "Ad was loaded.")
                    mInterstitialAd = interstitialAd
                    if (mInterstitialAd != null) {
                        mInterstitialAd?.show(this@MainActivity)

                        Observable.timer(100, TimeUnit.MILLISECONDS)
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe {
                                Utils.stopVService(this@MainActivity)
                                changeTheme(false)
                                shPref.edit().putInt("LastStart", 0).apply()
                                isStartClik = true
                            }

                    } else {
                        Log.d("TAG", "The interstitial ad wasn't ready yet.")
                    }
                }
            })
    }

    private fun restartV2Ray() {
        if (mainViewModel.isRunning.value == true) {
            Utils.stopVService(this)
        }
        val service = V2RayServiceManager.serviceControl?.get()?.getService()
        if (service != null) {
            //why me do this???????????????
            //showOnConnectedInterstitialAd()
        }
        Observable.timer(500, TimeUnit.MILLISECONDS).observeOn(AndroidSchedulers.mainThread())
            .subscribe {
                startV2Ray()
            }
    }

    public override fun onResume() {
        Log.d("TAG", "onResume MainAC")
        if (isStartClik) {
            binding.tvWarning.visibility = View.GONE
            binding.fab.isEnabled = true
        }
        super<BaseActivity>.onResume()
        if (leftRewardTime < 0) {
            Utils.stopVService(this)
        }

        if (getCurrentRewardTime() > 0 && shPref.getBoolean("startTimer", false)) {
            normalStart()
            shPref.edit().putBoolean("startTimer", false).apply()
        }

        if (mainViewModel.isRunning.value == true) {
            handleUserRating()
        }

        mainViewModel.reloadServerList()
    }

    public override fun onPause() {
        super<BaseActivity>.onPause()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.import_qrcode -> {
            importQRcode(true)
            true
        }

        R.id.import_clipboard -> {
            importClipboard()
            true
        }

        R.id.import_manually_vmess -> {
            importManually(EConfigType.VMESS.value)
            true
        }

        R.id.import_manually_vless -> {
            importManually(EConfigType.VLESS.value)
            true
        }

        R.id.import_manually_ss -> {
            importManually(EConfigType.SHADOWSOCKS.value)
            true
        }

        R.id.import_manually_socks -> {
            importManually(EConfigType.SOCKS.value)
            true
        }

        R.id.import_manually_trojan -> {
            importManually(EConfigType.TROJAN.value)
            true
        }

        R.id.import_manually_wireguard -> {
            importManually(EConfigType.WIREGUARD.value)
            true
        }

        R.id.import_config_custom_clipboard -> {
            importConfigCustomClipboard()
            true
        }

        R.id.import_config_custom_local -> {
            importConfigCustomLocal()
            true
        }

        R.id.import_config_custom_url -> {
            importConfigCustomUrlClipboard()
            true
        }

        R.id.import_config_custom_url_scan -> {
            importQRcode(false)
            true
        }

//        R.id.sub_setting -> {
//            startActivity<SubSettingActivity>()
//            true
//        }

        R.id.sub_update -> {
            importConfigViaSub()
            true
        }

        R.id.export_all -> {
            if (AngConfigManager.shareNonCustomConfigsToClipboard(
                    this, mainViewModel.serverList
                ) == 0
            ) {
                toast(R.string.toast_success)
            } else {
                toast(R.string.toast_failure)
            }
            true
        }

        R.id.ping_all -> {
            mainViewModel.testAllTcping()
            true
        }

        R.id.real_ping_all -> {
            mainViewModel.testAllRealPing()
            true
        }

        R.id.service_restart -> {
            restartV2Ray()
            true
        }

        R.id.del_all_config -> {
            AlertDialog.Builder(this).setMessage(R.string.del_config_comfirm)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    MmkvManager.removeAllServer()
                    mainViewModel.reloadServerList()
                }.show()
            true
        }

        R.id.del_duplicate_config -> {
            AlertDialog.Builder(this).setMessage(R.string.del_config_comfirm)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    mainViewModel.removeDuplicateServer()
                }.show()
            true
        }

        R.id.del_invalid_config -> {
            AlertDialog.Builder(this).setMessage(R.string.del_config_comfirm)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    MmkvManager.removeInvalidServer()
                    mainViewModel.reloadServerList()
                }.show()
            true
        }

        R.id.sort_by_test_results -> {
            MmkvManager.sortByTestResults()
            mainViewModel.reloadServerList()
            true
        }

        R.id.filter_config -> {
            mainViewModel.filterConfig(this)
            true
        }

        else -> super.onOptionsItemSelected(item)
    }

    private fun importManually(createConfigType: Int) {
        startActivity(
            Intent().putExtra("createConfigType", createConfigType)
                .putExtra("subscriptionId", mainViewModel.subscriptionId)
                .setClass(this, ServerActivity::class.java)
        )
    }

    private fun importQRcode(forConfig: Boolean): Boolean {
//        try {
//            startActivityForResult(Intent("com.google.zxing.client.android.SCAN")
//                    .addCategory(Intent.CATEGORY_DEFAULT)
//                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP), requestCode)
//        } catch (e: Exception) {
        RxPermissions(this).request(Manifest.permission.CAMERA).subscribe {
            if (it) if (forConfig) scanQRCodeForConfig.launch(
                Intent(
                    this,
                    ScannerActivity::class.java
                )
            )
            else scanQRCodeForUrlToCustomConfig.launch(
                Intent(
                    this, ScannerActivity::class.java
                )
            )
            else toast(R.string.toast_permission_denied)
        }
//        }
        return true
    }

    private val scanQRCodeForConfig =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == RESULT_OK) {
                importBatchConfig(it.data?.getStringExtra("SCAN_RESULT"))
            }
        }

    private val scanQRCodeForUrlToCustomConfig =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == RESULT_OK) {
                importConfigCustomUrl(it.data?.getStringExtra("SCAN_RESULT"))
            }
        }

    private fun importClipboard(): Boolean {
        try {
            val clipboard = Utils.getClipboard(this)
            importBatchConfig(clipboard)
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
        return true
    }

    private fun importClipboard(config: String): Boolean {
        try {
            val clipboard = config
            importBatchConfig(clipboard)
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
        return true
    }

    private fun importBatchConfig(server: String?, subid: String = "") {
        val subid2 = if (subid.isNullOrEmpty()) {
            mainViewModel.subscriptionId
        } else {
            subid
        }
        val append = subid.isNullOrEmpty()

        var count = AngConfigManager.importBatchConfig(server, subid2, append)
        if (count <= 0) {
            count = AngConfigManager.importBatchConfig(Utils.decode(server!!), subid2, append)
        }
        if (count > 0) {
            toast(R.string.toast_success)
            mainViewModel.reloadServerList()
        } else {
            toast(R.string.toast_failure)
        }
    }

    private fun importConfigCustomClipboard(): Boolean {
        try {
            val configText = Utils.getClipboard(this)
            if (TextUtils.isEmpty(configText)) {
                toast(R.string.toast_none_data_clipboard)
                return false
            }
            importCustomizeConfig(configText)
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    private fun importConfigCustomLocal(): Boolean {
        try {
            showFileChooser()
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
        return true
    }

    private fun importConfigCustomUrlClipboard(): Boolean {
        try {
            val url = Utils.getClipboard(this)
            if (TextUtils.isEmpty(url)) {
                toast(R.string.toast_none_data_clipboard)
                return false
            }
            return importConfigCustomUrl(url)
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    private fun importConfigCustomUrl(url: String?): Boolean {
        try {
            if (!Utils.isValidUrl(url)) {
                toast(R.string.toast_invalid_url)
                return false
            }
            lifecycleScope.launch(Dispatchers.IO) {
                val configText = try {
                    Utils.getUrlContentWithCustomUserAgent(url)
                } catch (e: Exception) {
                    e.printStackTrace()
                    ""
                }
                launch(Dispatchers.Main) {
                    importCustomizeConfig(configText)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
        return true
    }

    /**
     * import config from sub
     */
    fun importConfigViaSub(): Boolean {
        try {
            toast(R.string.title_sub_update)
            MmkvManager.decodeSubscriptions().forEach {
                if (TextUtils.isEmpty(it.first) || TextUtils.isEmpty(it.second.remarks) || TextUtils.isEmpty(
                        it.second.url
                    )
                ) {
                    return@forEach
                }
                if (!it.second.enabled) {
                    return@forEach
                }
                val url = Utils.idnToASCII(it.second.url)
                if (!Utils.isValidUrl(url)) {
                    return@forEach
                }
                Log.d(ANG_PACKAGE, url)
                lifecycleScope.launch(Dispatchers.IO) {
                    val configText = try {
                        Utils.getUrlContentWithCustomUserAgent(url)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        launch(Dispatchers.Main) {
                            toast("\"" + it.second.remarks + "\" " + getString(R.string.toast_failure))
                        }
                        return@launch
                    }
                    launch(Dispatchers.Main) {
                        importBatchConfig(configText, it.first)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
        return true
    }

    private fun showFileChooser() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "*/*"
        intent.addCategory(Intent.CATEGORY_OPENABLE)

        try {
            chooseFileForCustomConfig.launch(
                Intent.createChooser(
                    intent, getString(R.string.title_file_chooser)
                )
            )
        } catch (ex: ActivityNotFoundException) {
            toast(R.string.toast_require_file_manager)
        }
    }

    private val chooseFileForCustomConfig =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            val uri = it.data?.data
            if (it.resultCode == RESULT_OK && uri != null) {
                readContentFromUri(uri)
            }
        }

    /**
     * read content from uri
     */
    private fun readContentFromUri(uri: Uri) {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        RxPermissions(this).request(permission).subscribe {
            if (it) {
                try {
                    contentResolver.openInputStream(uri).use { input ->
                        importCustomizeConfig(input?.bufferedReader()?.readText())
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            } else toast(R.string.toast_permission_denied)
        }
    }

    /**
     * import customize config
     */
    fun importCustomizeConfig(server: String?) {
        try {
            if (server == null || TextUtils.isEmpty(server)) {
                //toast(R.string.toast_none_data)
                return
            }
            mainViewModel.appendCustomConfigServer(server)
            mainViewModel.reloadServerList()
            //toast(R.string.toast_success)
            //adapter.notifyItemInserted(mainViewModel.serverList.lastIndex)
        } catch (e: Exception) {
            ToastCompat.makeText(
                this,
                "${getString(R.string.toast_malformed_josn)} ${e.cause?.message}",
                Toast.LENGTH_LONG
            ).show()
            e.printStackTrace()
            return
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_BUTTON_B) {
            moveTaskToBack(false)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        //super.onBackPressed()
        onBackPressedDispatcher.onBackPressed()
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        // Handle navigation view item clicks here.
        when (item.itemId) {
            //R.id.server_profile -> activityClass = MainActivity::class.java
            R.id.sub_setting -> {
                startActivity(Intent(this, SubSettingActivity::class.java))
            }

            R.id.settings -> {
                startActivity(
                    Intent(this, SettingsActivity::class.java).putExtra(
                        "isRunning", mainViewModel.isRunning.value == true
                    )
                )
            }

            R.id.user_asset_setting -> {
                startActivity(Intent(this, UserAssetActivity::class.java))
            }

            R.id.feedback -> {
                Utils.openUri(this, AppConfig.v2rayNGIssues)
            }

            R.id.promotion -> {
                Utils.openUri(
                    this, "${Utils.decode(AppConfig.promotionUrl)}?t=${System.currentTimeMillis()}"
                )
            }

            R.id.logcat -> {
                startActivity(Intent(this, LogcatActivity::class.java))
            }

            R.id.privacy_policy -> {
                Utils.openUri(this, AppConfig.v2rayNGPrivacyPolicy)
            }
        }

        return true
    }

    override fun onUpdate(up: String, down: String) {/*   settingsStorage?.encode("UPLOAD", up)
           settingsStorage?.encode("DOWNLOAD", down)
           Log.d("TAGGGGGGGGGGGG", "UP IS : + $up +  DOWN  IS: + $down")
           Log.d("TAGGGGGGGGGGGG SHARED",
               "UP IS : + ${settingsStorage?.decodeString("UPLOAD")} + " + " DOWN  IS: + ${
                   settingsStorage?.decodeString("DOWNLOAD")
               }"
           )*/
    }

    override fun onStop() {
        val timeStamp = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis())
        //mainStorage?.encode("outTime", timeStamp.toString())
        //shPref.edit().putLong("outTime", timeStamp).apply()
        // val lastStart = shPref.getLong("outTime", 0)
        // Log.d("TAG  OUT", lastStart.toString())

        super<BaseActivity>.onStop()
    }


}
