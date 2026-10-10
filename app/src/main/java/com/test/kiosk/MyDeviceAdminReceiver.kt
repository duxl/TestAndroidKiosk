package com.test.kiosk

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

class MyDeviceAdminReceiver: DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        // 被激活为设备管理员
    }
}