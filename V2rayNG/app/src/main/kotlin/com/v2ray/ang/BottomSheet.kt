package com.v2ray.ang

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.v2ray.ang.service.V2RayServiceManager
import com.v2ray.ang.ui.MainActivity
import com.v2ray.ang.util.Utils
import rx.Completable
import rx.android.schedulers.AndroidSchedulers
import java.util.concurrent.TimeUnit


class BottomSheet(mainActivity: MainActivity) : BottomSheetDialogFragment() {
    private var rewardedAd: RewardedAd? = null
    private var mActivity = mainActivity


    lateinit var shPref: SharedPreferences

    override fun getTheme(): Int {
        return R.style.NoBackgroundDialogTheme
    }

    lateinit var btnAds: Button
    lateinit var tvTime: TextView
    lateinit var baseReward: String
    lateinit var data: String
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.bottom_sheet, container, false)
        tvTime = view.findViewById(R.id.tv_show_reward)
        val tvDetail: TextView = view.findViewById(R.id.tv_detail)
        btnAds = view.findViewById(R.id.button_ads)

        shPref = mActivity.getSharedPreferences("MyPref", Context.MODE_PRIVATE);


        baseReward = arguments?.getInt("baseReward").toString()

        setDataToView()

        if (baseReward != null) {
            if (baseReward.toInt() > 3600) {
                baseReward = getH(baseReward.toLong()).toInt().toString() + "H"
            } else {
                baseReward = getMin(baseReward.toLong()).toInt().toString() + "M"
            }
            val text = "You can increase "
            val midText = baseReward.toString()
            val endText = " " + resources.getString(R.string.endText)
            val textToghter = text + midText + endText + "\n\n"

            tvDetail.text = colorized(
                textToghter,
                midText,
                resources.getColor(R.color.primaryYellow)
            )
        }


        // tvTime.text = data

        btnAds.setOnClickListener {
            if (mActivity.mainViewModel.isRunning.value != true) {
                itsFromBottomSheet = true
                V2RayServiceManager.startV2Ray(AngApplication.application)
            }
            startGettingAds()
        }


        return view
    }

    fun setDataToView() {
        var rewardPlus = 0
        if (shPref.getBoolean("PlusReward", false)) {
            rewardPlus = shPref.getInt("baseRewardInt", 0)
        }

        val time =
            getH(getCurrentRewardTime().toLong() + rewardPlus).toString() + "H" + " " + getMin(
                getCurrentRewardTime().toLong() + rewardPlus
            ) + "M"

        Log.d("TAGGGGGGGGGG", time)
        if (shPref.getInt("rewardTime", 0) > 0) {
            val text = "Your remaining Time is $time"
            tvTime.text = text
        } else {
            val text = "You Have No Time For Connection"
            tvTime.text = text
            tvTime.setTextColor(resources.getColor(R.color.redColor))
        }
    }

    private fun getCurrentRewardTime(): Int {
        return shPref.getInt("rewardTime", 0)
    }

    private fun startGettingAds() {
        Completable.timer(300, TimeUnit.MILLISECONDS, AndroidSchedulers.mainThread())
            .subscribe {
                Log.d("TAG", V2RayServiceManager.v2rayPoint.isRunning.toString())
                btnAds.isEnabled = false
                btnAds.text = "Waiting loading ads..."
                val adRequest = AdRequest.Builder().build()
                RewardedAd.load(
                    mActivity,
                    "ca-app-pub-3940256099942544/5224354917",
                    adRequest,
                    object : RewardedAdLoadCallback() {
                        override fun onAdFailedToLoad(adError: LoadAdError) {
                            Log.d(TAG, adError.toString())
                            rewardedAd = null
                            btnAds.isEnabled = true
                            btnAds.text = "WATCH ADS"
                            itsFromBottomSheet = false
                            Utils.stopVService(mActivity)
                        }

                        override fun onAdLoaded(ad: RewardedAd) {
                            Log.d(TAG, "Ad was loaded.")
                            rewardedAd = ad
                            ad.show(mActivity) {
                                Log.d(TAG, "User earned the reward.")

                                var rewardTime = shPref.getInt("rewardTime", 0)
                                if (rewardTime > 0) {
                                    shPref.edit().putBoolean("PlusReward", true).apply()
                                } else {
                                    rewardTime = shPref.getInt("baseRewardInt", 0)
                                    shPref.edit().putInt("rewardTime", rewardTime).apply()
                                    shPref.edit().putBoolean("startTimer", true).apply()
                                }

                                Utils.stopVService(mActivity)
                                btnAds.isEnabled = true
                                btnAds.text = "WATCH ADS"
                                itsFromBottomSheet = false
                                setDataToView()
                            }

                        }
                    })
                Log.d("BottomSheet", "IS RUN")


            }
    }

    private fun getMin(ms: Long): Long {
        return (ms / 60 % 60);
    }

    private fun getH(ms: Long): Long {
        return ((ms / (60 * 60)) % 24);
    }

    fun colorized(text: String, word: String, argb: Int): Spannable? {
        val spannable: Spannable = SpannableString(text)
        var substringStart = 0
        var start: Int
        while (text.indexOf(word, substringStart).also { start = it } >= 0) {
            spannable.setSpan(
                ForegroundColorSpan(argb), start, start + word.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            substringStart = start + word.length
        }
        return spannable
    }

    companion object {
        const val TAG = "ModalBottomSheet"
        var itsFromBottomSheet = false
    }

    override fun onDestroyView() {
        super.onDestroyView()
    }
}