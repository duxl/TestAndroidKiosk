package com.test.kiosk

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.net.wifi.WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS
import android.net.wifi.WifiNetworkSuggestion
import android.os.BatteryManager
import android.os.Build
import android.text.format.Formatter
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import com.test.kiosk.activity.InstallResultReceiver
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException


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

    /**
     * Owner静默安装apk
     */
    @Throws(IOException::class)
    fun installSilently(
        context: Context,
        apkFile: File
    ) {
        val packageInstaller: PackageInstaller = context.packageManager.packageInstaller

        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)

        val sessionId = packageInstaller.createSession(params)

        packageInstaller.openSession(sessionId).use { session ->
            FileInputStream(apkFile).use { `in` ->
                session.openWrite(
                    "update.apk", 0, apkFile.length()
                ).use { out ->
                    val buffer = ByteArray(64 * 1024)
                    var length: Int

                    while ((`in`.read(buffer).also { length = it }) != -1) {
                        out.write(buffer, 0, length)
                    }
                    session.fsync(out)
                }
            }
        }
        val callbackIntent = Intent(context, InstallResultReceiver::class.java)

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            sessionId,
            callbackIntent,
            PendingIntent.FLAG_UPDATE_CURRENT
                    or PendingIntent.FLAG_MUTABLE
        )

        packageInstaller.openSession(sessionId).use { session ->
            session.commit(pendingIntent.getIntentSender())
        }
    }

    /**
     * 从 assets 复制到 App 的内部存储目录
     */
    @Throws(IOException::class)
    fun copyApkFromAssets(context: Context, assetName: String): File {
        val updateDir = File(context.filesDir, "updates")

        if (!updateDir.exists() && !updateDir.mkdirs()) {
            throw IOException("无法创建 APK 目录")
        }

        val apkFile = File(updateDir, "update.apk")

        context.assets.open(assetName).use { input ->
            FileOutputStream(apkFile).use { output ->
                val buffer = ByteArray(8192)
                var length: Int

                while ((input.read(buffer).also { length = it }) != -1) {
                    output.write(buffer, 0, length)
                }
                output.flush()
            }
        }
        return apkFile
    }
}