package com.v2ray.ang.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageInfo
import android.content.pm.PackageManager.NameNotFoundException
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.navigation.NavigationView
import com.v2ray.ang.viewmodel.SubConfig
import com.tbruyelle.rxpermissions.RxPermissions
import com.tencent.mmkv.MMKV
import com.v2ray.ang.AppConfig
import com.v2ray.ang.AppConfig.ANG_PACKAGE
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
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.observers.DisposableSingleObserver
import io.reactivex.schedulers.Schedulers
import ir.samanjafari.easycountdowntimer.CountDownInterface
import ir.samanjafari.easycountdowntimer.EasyCountDownTextview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.drakeet.support.toast.ToastCompat
import org.json.JSONObject
import retrofit2.Response
import rx.Observable
import rx.android.schedulers.AndroidSchedulers
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit


class MainActivity : BaseActivity(), SpeedListener,
    NavigationView.OnNavigationItemSelectedListener {
    private lateinit var binding: ActivityMainBinding

    val itemList = ArrayList<SubConfig>()
    private val adapter by lazy { MainRecyclerAdapter2(this, itemList) }
    private val mainStorage by lazy {
        MMKV.mmkvWithID(
            MmkvManager.ID_MAIN,
            MMKV.MULTI_PROCESS_MODE
        )
    }
    private val settingsStorage by lazy {
        MMKV.mmkvWithID(
            MmkvManager.ID_SETTING,
            MMKV.MULTI_PROCESS_MODE
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


    enum class AppStart {
        FIRST_TIME, FIRST_TIME_VERSION, NORMAL
    }

    private val LAST_APP_VERSION = "last_app_version"

    private var leftRewardTime = mainStorage.decodeInt("rewardTime")


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        MmkvManager.removeAllServer()

        val window = this.window
        if (mainViewModel.isRunning.value == true) {
            window.statusBarColor = resources.getColor(com.v2ray.ang.R.color.primaryYellow)
        } else {
            window.statusBarColor = resources.getColor(com.v2ray.ang.R.color.primaryGray)
        }


        when (checkAppStart()) {
            AppStart.NORMAL -> {
                //normalStart()
            }

            AppStart.FIRST_TIME_VERSION -> {
               // normalStart()
            }

            AppStart.FIRST_TIME -> {
               // firstTimeStart()
            }

            else -> {}
        }

        compositeDisposable = CompositeDisposable()
        getApi = RetrofitBuilder.Create(Const.BASE_URL)
        getBaseData()

        binding.fab.setOnClickListener {
            if (mainViewModel.isRunning.value == true) {
                Utils.stopVService(this)
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
        }



        setupViewModel()
        copyAssets()
        migrateLegacy()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            RxPermissions(this).request(Manifest.permission.POST_NOTIFICATIONS).subscribe {
                if (!it) toast(R.string.toast_permission_denied)
            }
        }

    }

    private fun normalStart() {
        val timeIn = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis()).toInt()
        Log.d("TAG : OC IN", timeIn.toString())
        val timeOutPr = mainStorage?.decodeInt("outTime")
        Log.d("TAG : OC OUT", timeOutPr.toString())

        val diffBetweenTwoTime = timeIn - (timeOutPr ?: 0)
        val getRealRewardTime = leftRewardTime - diffBetweenTwoTime
        Log.d("TAG : getRealRewardTime", leftRewardTime.toString())

        startTimer(
            getH(getRealRewardTime.toLong()).toInt(),
            getMin(getRealRewardTime.toLong()).toInt(),
            getSec(getRealRewardTime.toLong()).toInt()
        )
        Toast.makeText(this, getRealRewardTime.toString(), Toast.LENGTH_LONG).show()
    }

    private fun firstTimeStart() {
        startTimer(
            getH(600).toInt(),
            getMin(600).toInt(),
            getSec(600).toInt()
        )
    }

    private fun startTimer(h: Int, m: Int, s: Int) {
        val countDownTextview = findViewById<View>(R.id.tv_timer) as EasyCountDownTextview
        countDownTextview.setTime(0, h, m, s)
        countDownTextview.setOnTick(object : CountDownInterface {
            override fun onTick(time: Long) {
                Log.d("TAG : startFirstTimer", (time / 1000).toString())
                val lol = leftRewardTime - (time / 1000)
                Log.d("TAG : startFirstTimer", lol.toString())
                leftRewardTime -= lol.toInt()
                mainStorage.encode("rewardTime", leftRewardTime)
                Log.d("TAG : TTF", mainStorage.decodeInt("rewardTime").toString())
            }

            override fun onFinish() {
                startBuildingAds()
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
        val disposable: Disposable = getApi.GetConfig()
            .observeOn(io.reactivex.android.schedulers.AndroidSchedulers.mainThread()).subscribeOn(
                Schedulers.io()
            ).subscribeWith(object : DisposableSingleObserver<Response<ConfigResponse>>() {
                override fun onSuccess(t: Response<ConfigResponse>) {
                    if (t.body() != null) {
                        settingsStorage?.encode("Verison", t.body()!!.version)
                        if (t.body()!!.subConfig != null) {
                            if (t.body()!!.subConfig.size > 0) {
                                for (i in 0 until t.body()!!.subConfig.size) {

                                    val obj = JSONObject(t.body()!!.subConfig[i].getConfig()!!)
                                    Log.d("CONFIG", obj.toString())

                                    itemList.add(t.body()!!.subConfig[i])
                                    importCustomizeConfig(
                                        t.body()!!.subConfig[i].getConfig().toString()
                                    )
                                    Log.i("CONFIG", t.body()!!.subConfig[i].getConfig().toString())
                                }

                                initRecyclerview()
                            }
                        }
                    }
                    Log.i("Getconfig", t.body()?.subConfig.toString())
                }

                override fun onError(e: Throwable) {
                    Log.i("GetConfig Throwable: ", e.toString())
                }
            })
        compositeDisposable?.add(disposable)
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
        binding.recyclerView.setHasFixedSize(true)
        binding.recyclerView.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false);
        binding.recyclerView.adapter = adapter
        val callback = SimpleItemTouchHelperCallback(adapter)
        mItemTouchHelper = ItemTouchHelper(callback)
        mItemTouchHelper?.attachToRecyclerView(binding.recyclerView)
    }

    private fun changeTheme(isRun: Boolean) {
        if (isRun) {
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
        } else {
            binding.tvLocation.setTextColor(resources.getColor(R.color.serverlocation))
            binding.imageViewSignal.isVisible = true
            binding.imageView2.isVisible = true
            binding.tvStatus.isVisible = true
            binding.imgMap.background = resources.getDrawable(R.drawable.map)
            binding.layoutParent.setBackgroundColor(resources.getColor(R.color.base_bg))
            binding.tvTap.text = "Tap Start Button to Connecting"
            binding.tvTap.setTextColor(resources.getColor(R.color.colorTapConnect))
            binding.tvKylo.setTextColor(resources.getColor(R.color.white))
            // binding.tvTimer.visibility = View.GONE
            //  stopwatch.reset()
            // stopwatch.stop()
        }
    }

    private fun setupViewModel() {
        mainViewModel.isRunning.observe(this) { isRunning ->
            adapter.isRunning = isRunning
            if (isRunning) {
                binding.tvStatus.text = "Connected"
                changeTheme(isRunning)
            } else {
                binding.tvStatus.text = "Not Connected"
                changeTheme(isRunning)
            }
        }
        mainViewModel.startListenBroadcast()
    }

    private fun copyAssets() {
        val extFolder = Utils.userAssetPath(this)
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val geo = arrayOf("geosite.dat", "geoip.dat")
                assets.list("")
                    ?.filter { geo.contains(it) }
                    ?.filter { !File(extFolder, it).exists() }
                    ?.forEach {
                        val target = File(extFolder, it)
                        assets.open(it).use { input ->
                            FileOutputStream(target).use { output ->
                                input.copyTo(output)
                            }
                        }
                        Log.i(
                            ANG_PACKAGE,
                            "Copied from apk assets folder to ${target.absolutePath}"
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
        V2RayServiceManager.startV2Ray(this)
        changeTheme(true)
        // adapter.click()
        // startChangeSpeedView(up, down)
        // stopwatch.start()
    }

    private fun restartV2Ray() {
        if (mainViewModel.isRunning.value == true) {
            Utils.stopVService(this)
        }
        Observable.timer(500, TimeUnit.MILLISECONDS)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe {
                startV2Ray()
            }
    }

    public override fun onResume() {
        super.onResume()
        mainViewModel.reloadServerList()
    }

    public override fun onPause() {
        super.onPause()
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
                    this,
                    mainViewModel.serverList
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
                }
                .show()
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
            Intent()
                .putExtra("createConfigType", createConfigType)
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
        RxPermissions(this)
            .request(Manifest.permission.CAMERA)
            .subscribe {
                if (it)
                    if (forConfig)
                        scanQRCodeForConfig.launch(Intent(this, ScannerActivity::class.java))
                    else
                        scanQRCodeForUrlToCustomConfig.launch(
                            Intent(
                                this,
                                ScannerActivity::class.java
                            )
                        )
                else
                    toast(R.string.toast_permission_denied)
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

    fun importConfigCustomClipboard()
            : Boolean {
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
    fun importConfigViaSub()
            : Boolean {
        try {
            toast(R.string.title_sub_update)
            MmkvManager.decodeSubscriptions().forEach {
                if (TextUtils.isEmpty(it.first)
                    || TextUtils.isEmpty(it.second.remarks)
                    || TextUtils.isEmpty(it.second.url)
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
                    intent,
                    getString(R.string.title_file_chooser)
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
        RxPermissions(this)
            .request(permission)
            .subscribe {
                if (it) {
                    try {
                        contentResolver.openInputStream(uri).use { input ->
                            importCustomizeConfig(input?.bufferedReader()?.readText())
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                } else
                    toast(R.string.toast_permission_denied)
            }
    }

    /**
     * import customize config
     */
    fun importCustomizeConfig(server: String?) {
        try {
            if (server == null || TextUtils.isEmpty(server)) {
                toast(R.string.toast_none_data)
                return
            }
            mainViewModel.appendCustomConfigServer(server)
            mainViewModel.reloadServerList()
            toast(R.string.toast_success)
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
        val timeStamp = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis()).toInt()
        mainStorage?.encode("outTime", timeStamp)
        super.onStop()
    }

}
