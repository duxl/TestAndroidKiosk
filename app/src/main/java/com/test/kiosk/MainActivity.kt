package com.test.kiosk

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.test.kiosk.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private val binding by lazy {
        ActivityMainBinding.inflate(layoutInflater)
    }
    private lateinit var dpm: DevicePolicyManager
    private lateinit var admin: ComponentName

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }


        dpm = getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
        admin = ComponentName(this, MyDeviceAdminReceiver::class.java)

        // ========== 关键 1：把自己加进 LockTask 白名单 ==========
        // 只有白名单里的包才能进入/退出 lockTask
        val approvedPackages = dpm.getLockTaskPackages(admin).toMutableSet()
        approvedPackages.add(packageName)
        dpm.setLockTaskPackages(admin, approvedPackages.toTypedArray())

        // ========== 关键 2：进入锁任务模式 ==========
        startLockTask()

        // ========== 关键 3：沉浸式隐藏状态栏+导航栏 ==========
        hideSystemBars()

        binding.btnOther.setOnClickListener {
            startActivity(Intent(this, OtherActivity::class.java))
        }

        binding.btnExit.setOnClickListener {
            exitKiosk()
        }
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        // 保持屏幕常亮
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    // ========== 关键 4：屏蔽返回键（可选） ==========
    override fun onBackPressed() {
        // 什么都不做，吞掉返回键
        // 或者：如果在主页就不响应，子页面才响应
    }

    // ========== 关键 5：拦截其他按键（Home/Recent 在 lockTask 下系统已经屏蔽，这里兜底） ==========
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        when (event.keyCode) {
            KeyEvent.KEYCODE_HOME,
            KeyEvent.KEYCODE_APP_SWITCH -> return true  // 吞掉
        }
        return super.dispatchKeyEvent(event)
    }

    // ========== 退出方法（仅供你自己的"隐藏按钮"调用） ==========
    // 比如长按某个角落 5 秒，或输入密码
    fun exitKiosk() {
        stopLockTask()
        finishAffinity()
        // 可选：把自己从 owner 解除
        // dpm.removeActiveAdmin(admin)
    }
}