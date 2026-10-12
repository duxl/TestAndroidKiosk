package com.test.kiosk.activity

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.test.kiosk.KioskManager
import com.test.kiosk.databinding.ActivitySplashBinding

class SplashActivity : AppCompatActivity() {

    private val binding by lazy {
        ActivitySplashBinding.inflate(layoutInflater)
    }

    private val kioskManager by lazy {
        KioskManager(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        toMainPageIfDeviceOwner()

    }

    private fun toMainPageIfDeviceOwner() {
        if (kioskManager.isDeviceOwner()) {
            Log.d("SplashActivity", "已是设备DeviceOwner")
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }

}