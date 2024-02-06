package com.v2ray.ang.ui

import android.R
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import com.v2ray.ang.databinding.ActivitySplashBinding


class SplashActivity : BaseActivity() {
    private lateinit var binding: ActivitySplashBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        val window = this.window
        window.statusBarColor = resources.getColor(com.v2ray.ang.R.color.primaryGray)

        Handler().postDelayed(
            Runnable
            {
                val i = Intent(this@SplashActivity, MainActivity::class.java)
                startActivity(i)
                finish()
            }, 2 * 1000
        )
    }
}