package com.test.kiosk

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build


class KioskManager(val context: Context) {
    private val dpm: DevicePolicyManager = (context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager?)!!
    private val admin: ComponentName = ComponentName(context, MyDeviceAdminReceiver::class.java)

    /**
     * 是否为当前用户的设备所有者。
     */
    fun isDeviceOwner(): Boolean {
        return dpm.isDeviceOwnerApp(context.packageName)
    }

    /**
     * 将当前 App 加入锁任务白名单。
     */
    fun configureLockTask() {
        check(isDeviceOwner()) {
            "当前 App 不是 Device Owner，无法配置 Kiosk，使用命令设置Owner：adb shell dpm set-device-owner com.test.kiosk/.MyDeviceAdminReceiver"
        }

        // 加入白名单，让当前 App 进入真正的 Kiosk 模式
        dpm.setLockTaskPackages(admin, arrayOf<String?>(context.packageName))
        // 如果你的 Kiosk 需要启动其他 App，例如专用扫码 App、播放 App，也可以把对应包名加入白名单
        // 加入白名单的应用会受 Kiosk 锁定保护，不加入白名单不受锁保护，但一样可以通过锁保护的app启动
        /*
        dpm.setLockTaskPackages(
            admin,
            arrayOf<String>(
                "com.example.kiosk",
                "com.example.scanner",
                "com.example.player"
            )
        )
         */


        /**
         * 控制 Home、最近任务、通知栏等系统功能
         * Android 9（API 28）及以上版本，可以使用 setLockTaskFeatures() 配置锁任务期间允许使用的系统功能。
         */
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // LOCK_TASK_FEATURE_SYSTEM_INFO    允许系统状态信息区域
            // LOCK_TASK_FEATURE_NONE	        禁用锁任务期间可配置的系统功能
            // LOCK_TASK_FEATURE_HOME	        允许 Home 功能
            // LOCK_TASK_FEATURE_OVERVIEW	    允许最近任务界面
            // LOCK_TASK_FEATURE_NOTIFICATIONS	允许通知栏功能
            // LOCK_TASK_FEATURE_GLOBAL_ACTIONS	允许电源菜单等全局操作
            // LOCK_TASK_FEATURE_KEYGUARD	    允许锁屏界面
            dpm.setLockTaskFeatures(
                admin,
                DevicePolicyManager.LOCK_TASK_FEATURE_SYSTEM_INFO
                        and DevicePolicyManager.LOCK_TASK_FEATURE_NOTIFICATIONS
            )

            // 禁止修改系统设置
            // dpm.addUserRestriction(admin, android.os.UserManager.DISALLOW_CONFIG_DATE_TIME)
        }
    }

    /**
     * 检查指定包名是否允许进入锁任务模式。
     */
    fun isLockTaskPermitted(packageName: String): Boolean {
        return dpm.isLockTaskPermitted(packageName)
    }

    /**
     * 锁定屏幕，可按电源键解锁（需要在xml/device_admin.xml中有对应的force-lock配置）
     */
    fun lockScreen() {
        dpm.lockNow()
    }
}