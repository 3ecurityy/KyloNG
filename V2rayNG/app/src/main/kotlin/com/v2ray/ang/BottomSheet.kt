package com.v2ray.ang

import android.graphics.Color
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
import com.google.android.material.bottomsheet.BottomSheetDialogFragment


class BottomSheet : BottomSheetDialogFragment() {

    override fun getTheme(): Int {
        return R.style.NoBackgroundDialogTheme
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.bottom_sheet, container, false)
        var tvTime: TextView = view.findViewById<TextView>(R.id.tv_show_reward)
        var tvDetail: TextView = view.findViewById<TextView>(R.id.tv_detail)
        var btnAds: Button = view.findViewById<Button>(R.id.button_ads)

        val data = arguments?.getString("key")
        var baseReward = arguments?.getInt("baseReward").toString()
        if (data != null) {
            Log.d("TAG", data)
            if (data.toInt() > 0) {
                val text = "Your remaining Time is $data"
                tvTime.text = text
            } else {
                val text = "You Have No Time For Connection"
                tvTime.text = text
                tvTime.setTextColor(resources.getColor(R.color.redColor))
            }
        }

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

        return view
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
    }
}