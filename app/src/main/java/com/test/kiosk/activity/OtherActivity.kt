package com.test.kiosk.activity

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.test.kiosk.Utils
import com.test.kiosk.databinding.ActivityOtherBinding

class OtherActivity : AppCompatActivity() {


    private val TAG = "OtherActivity"

    private val binding by lazy {
        ActivityOtherBinding.inflate(layoutInflater)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnSysSetting.setOnClickListener {
            startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
        }

        binding.btnInstallApk.setOnClickListener {
            val file = Utils.copyApkFromAssets(this, "v2.apk")
            Utils.installSilently(this, file)
        }
    }

    override fun onStart() {
        super.onStart()
        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

        // 监听网络变化
        val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        cm.registerDefaultNetworkCallback(networkCallback)

    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(batteryReceiver)

        // 取消监听网络变化
        val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        // Android 没有 unregisterDefaultNetworkCallback() 这个 API。
        // 无论你是用哪种方式注册的回调，都统一用同一个方法反注册：
        cm.unregisterNetworkCallback(networkCallback)
    }

    val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            Log.d(TAG, "网络已连接")
        }
        override fun onLost(network: Network) {
            Log.d(TAG, "网络断开")
            // Kiosk 场景：这里弹个提示"网络异常"
        }
    }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_BATTERY_CHANGED) {
                val info = Utils.getBatteryInfo(this@OtherActivity)
                // 实时更新 UI / 上报服务器
                Log.d(TAG, "电量: ${info["percent"]}%, 状态: ${info["isCharging"]}")
            }
        }
    }

}