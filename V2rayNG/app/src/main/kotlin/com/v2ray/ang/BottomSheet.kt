package com.v2ray.ang

import android.os.Bundle
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

        return view
    }

    companion object {
        const val TAG = "ModalBottomSheet"
    }
}