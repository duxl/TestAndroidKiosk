package com.test.kiosk

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Context.DEVICE_POLICY_SERVICE
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.net.wifi.WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS
import android.net.wifi.WifiNetworkSpecifier
import android.net.wifi.WifiNetworkSuggestion
import android.os.BatteryManager
import android.os.Build
import android.text.format.Formatter
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission

object Utils {

    /**
     * 获取电池信息
     */
    fun getBatteryInfo(context: ContextWrapper): Map<String, Any> {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val percent = if (level >= 0 && scale > 0) level * 100 / scale else -1

        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
        val chargeType = when (intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)) {
            BatteryManager.BATTERY_PLUGGED_AC -> "充电器"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "无线"
            else -> "未充电"
        }
        val temperature = (intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10.0f // ℃
        val voltage = (intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0) / 1000.0f // V

        return mapOf(
            "percent" to percent,
            "isCharging" to isCharging,
            "chargeType" to chargeType,
            "temperature" to temperature,
            "voltage" to voltage
        )
    }

    /**
     * 获取wifi信息
     */
    @Suppress("DEPRECATION")
    fun getWifiInfo(context: Context): Map<String, Any> {
        val wifiManager = context.applicationContext
            .getSystemService(Context.WIFI_SERVICE) as WifiManager

        val info = wifiManager.connectionInfo
        val ssid = info.ssid?.removePrefix("\"")?.removeSuffix("\"") ?: "<unknown>"
        val rssi = info.rssi
        // RSSI 转成常用的 0~100 信号强度
        val signalLevel = WifiManager.calculateSignalLevel(rssi, 5)  // 0~4
        val ip = Formatter.formatIpAddress(info.ipAddress)

        return mapOf(
            "ssid" to ssid,
            "bssid" to info.bssid,
            "rssi" to rssi,
            "signalLevel" to signalLevel,
            "ip" to ip,
            "linkSpeed" to info.linkSpeed,
            "frequency" to info.frequency
        )
    }

    /**
     * 获取网络信息
     */
    fun getActiveNetworkInfo(context: Context): Map<String, Any> {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val network = cm.activeNetwork ?: return mapOf("online" to false)
        val caps = cm.getNetworkCapabilities(network) ?: return mapOf("online" to false)

        return mapOf(
            "online" to caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
            "isWifi" to caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI),
            "isCellular" to caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR),
            "isVpn" to caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN),
            "validated" to caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        )
    }

    /**
     * 设置wifi
     */
    @RequiresPermission(Manifest.permission.CHANGE_WIFI_STATE)
    @RequiresApi(Build.VERSION_CODES.Q)
    fun setWifi(
        context: Context,
        ssid: String,
        password: String
    ): Boolean {
        val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager

        val suggestion = WifiNetworkSuggestion.Builder()
            .setSsid(ssid)
            .setWpa2Passphrase(password)
            .build()

        val status = wifiManager.addNetworkSuggestions(listOf(suggestion))

        return status == STATUS_NETWORK_SUGGESTIONS_SUCCESS // true 表示成功
    }

}