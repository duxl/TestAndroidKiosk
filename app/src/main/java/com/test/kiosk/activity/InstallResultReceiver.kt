package com.test.kiosk.activity

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.util.Log
import android.widget.Toast

class InstallResultReceiver : BroadcastReceiver() {

    val TAG = "InstallResultReceiver"
    override fun onReceive(context: Context?, intent: Intent?) {
        val status = intent?.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)

        val message = intent?.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)

        when (status) {
            PackageInstaller.STATUS_SUCCESS -> {
                Log.i(TAG, "APK 安装成功");
                Toast.makeText(context, "升级成功", Toast.LENGTH_SHORT).show()
            }

            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                Log.i(TAG, "需要用户确认安装");

                // 某些安装场景需要用户确认
                val confirmIntent = if (android.os.Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(
                        Intent.EXTRA_INTENT,
                        Intent::class.java
                    )
                } else {
                    intent.getParcelableExtra(
                        Intent.EXTRA_INTENT
                    )
                }

                if (confirmIntent != null) {
                    confirmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context?.startActivity(confirmIntent);
                }
            }

            else -> {
                Log.e(TAG, "APK 安装失败，status=$status, message=$message")
                Toast.makeText(context, "升级失败：$status", Toast.LENGTH_LONG).show()
            }
        }
    }
}