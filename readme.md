# 如何把 Android app 做成系统启动后的第一个页面

这个需求本质是把你的 App 变成 **Launcher（桌面）** 或进入 **Kiosk（单应用锁机）模式**，分三种常见场景，按你的设备类型选：

---


## 场景一：普通手机 / 平板，想让自己 App 开机后直接当桌面

这是最标准的做法 —— 把 App 注册成 **HOME Intent** 的接收者。

### 1. 在 `AndroidManifest.xml` 里给主 Activity 加 intent-filter

```
<activity
    android:name=".MainActivity"
    android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.HOME" />
        <category android:name="android.intent.category.DEFAULT" />
    </intent-filter>
</activity>
```

关键点：`MAIN` + `HOME` + `DEFAULT` 三个 category 缺一不可。

### 2. 首次按 Home 键时选择 "始终用此应用打开"

安装后按 Home 键，系统会弹出选择器，选中你的 App 并勾选 "始终"，以后开机或按 Home 键就直接进你的页面。

> 
> 缺点：状态栏、导航栏还在；用户下拉通知栏、近期任务键仍能退出。

---

## 场景二：定制硬件 / 展厅 / 自助机（Kiosk 锁机模式，推荐）

适合 "开机只能用这一个 App，退不出去" 的设备。Android 官方提供 **Lock Task Mode**：

### 1. 写一个 DeviceAdminReceiver

```
public class MyAdmin extends DeviceAdminReceiver {}
```

### 2. 通过 adb 把你的 App 设为设备 Owner

```
adb shell dpm set-device-owner com.your.package/.MyAdmin
```

### 3. 在 App 里进入锁任务模式

```
// 把你的包加进白名单
DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(DEVICE_POLICY_SERVICE);
ComponentName admin = new ComponentName(this, MyAdmin.class);
dpm.setLockTaskPackages(admin, new String[]{getPackageName()});

// 启动后直接进入
startLockTask();
```

效果：状态栏变黑、Home/Recent 键被屏蔽、无法退出，开机自动进入你的页面。

---

## 场景三：系统级定制（ROM / 嵌入式设备，最彻底）

如果是自己烧系统或做嵌入式主板：

### 1. 把 App 放到系统目录

```
adb root
adb remount
adb push YourApp.apk /system/priv-app/
# 或 /product/priv-app/
```

### 2. 用 platform 签名

App 需要用系统平台密钥签名（`platform.x509.pem`），否则部分系统权限拿不到。

### 3. 修改默认 Launcher

在 `frameworks/base/packages/SettingsProvider/res/values/defaults.xml` 中把默认桌面改成你的包名：

```
<string name="default_home_package">com.your.package</string>
```

或者直接干掉系统 Launcher（把 `Launcher2.apk`/`Trebuchet.apk` 从 priv-app 删除），系统没别的 HOME 应用可选，自然只会启动你的。

---

## 三种方案对比

表格

| 方案 | 适用 | 难度 | 能否被退出 |
| --- | --- | --- | --- |
| 注册 HOME Intent | 普通手机 | 低 | 下拉通知 / 近期任务可退 |
| Lock Task Mode | 企业 / 展厅设备 | 中 | 几乎退不出 |
| 系统 priv-app + 删 Launcher | ROM / 嵌入式 | 高 | 完全退不出 |

---

**常见坑**：

- Android 10 以后普通第三方 App 不能再静默设默认 Launcher，必须用户手动选一次
- 多用户 / 已绑定账号的设备 `dpm set-device-owner` 会失败，需要恢复出厂设置后先设置 owner
- 沉浸式隐藏状态栏用 `WindowInsetsController.hide(WindowInsets.Type.statusBars())`，配合 `setLockTaskEnabled` 才彻底



---

# Android Kiosk（锁任务模式）完整实现详解

## 一、先搞懂原理

**Lock Task Mode** 是 Android 5.0（API 21）引入的企业级 API，专门用来做 "单用途设备"（COSU, Corporate Owned Single Use），比如：

- 展厅展示平板
- 餐厅点餐机
- 医院叫号屏
- 工厂数据采集终端
- 出租车计价 / 广告屏

进入后效果：

- **状态栏变灰**，下拉通知无效
- **Home 键、Recent（多任务）键被屏蔽**
- **锁屏界面不再出现**
- 用户**无法手动退出**你的 App
- 只有被管理员（你的代码）显式调用 `stopLockTask()` 才能退出

---

## 二、前置条件（非常重要，不满足直接失败）

表格

| 条件 | 说明 |
| --- | --- |
| **设备没有任何已登录账号** | 包括 Gmail、企业邮箱、社交账号。有账号就设不了 Device Owner |
| **开启 USB 调试** | 开发者选项里打开 |
| **Android 5.0 以上** | 建议 8.0+，兼容性好 |
| **未被其他 App 设为 Device Owner** | 否则需要先清除 |
| **原生 / 接近原生的系统** | 部分国产定制 ROM（尤其华为、小米、OPPO）对 `dpm` 命令有限制，可能需要系统签名 |

**如果设备已经登录账号**：恢复出厂设置 → 开机后**不要登录任何账号** → 直接开 USB 调试 → 设 owner。

---

## 三、完整代码实现（Kotlin 版）

### 第 1 步：新建 DeviceAdminReceiver

```
// MyDeviceAdminReceiver.kt
class MyDeviceAdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        // 被激活为设备管理员
    }
}
```

这个 Receiver 是 "凭证"，系统通过它识别你的 App 是设备所有者。

---

### 第 2 步：写 device_admin 元数据 XML

在 `res/xml/device_admin.xml` 新建：

```
<?xml version="1.0" encoding="utf-8"?>
<device-admin xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-policies>
        <limit-password />
        <watch-login />
        <reset-password />
        <force-lock />
        <wipe-data />
    </uses-policies>
</device-admin>
```

---

### 第 3 步：AndroidManifest.xml 完整注册

```
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.example.kiosk">

    <!-- 开机自启动 -->
    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
    <!-- 保持屏幕常亮（展厅设备常用） -->
    <uses-permission android:name="android.permission.WAKE_LOCK" />

    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="展厅Kiosk">

        <!-- 主界面 -->
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:launchMode="singleTask"
            android:excludeFromRecents="true"
            android:screenOrientation="landscape">  <!-- 锁定横屏/竖屏 -->
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.HOME" />
                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <!-- 设备管理员 Receiver -->
        <receiver
            android:name=".MyDeviceAdminReceiver"
            android:permission="android.permission.BIND_DEVICE_ADMIN"
            android:exported="true">
            <meta-data
                android:name="android.app.device_admin"
                android:resource="@xml/device_admin" />
            <intent-filter>
                <action android:name="android.app.action.DEVICE_ADMIN_ENABLED" />
            </intent-filter>
        </receiver>

        <!-- 开机自启 Receiver -->
        <receiver
            android:name=".BootReceiver"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.BOOT_COMPLETED" />
            </intent-filter>
        </receiver>

    </application>
</manifest>
```

注意几个关键点：

- `category.HOME` + `category.DEFAULT` 让它成为桌面
- `excludeFromRecents="true"` 让它不出现在最近任务里
- `launchMode="singleTask"` 防止多个实例

---

### 第 4 步：开机自启动 Receiver

```
// BootReceiver.kt
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val i = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(i)
        }
    }
}
```

---

### 第 5 步：MainActivity 核心代码

```
// MainActivity.kt
class MainActivity : AppCompatActivity() {

    private lateinit var dpm: DevicePolicyManager
    private lateinit var admin: ComponentName

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

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
```


---

## 四、用 adb 把 App 设为 Device Owner（最关键一步）

**先把 App 装到设备上**，然后：

```
adb shell dpm set-device-owner com.example.kiosk/.MyDeviceAdminReceiver
```

注意：

- 如果不执行上面的命令，app安装后直接启动会报`SecurityException`
- 包名和 Receiver 类名要和代码里**完全一致**
- 成功会输出：`Success: Device owner set to package ...`
- 失败常见原因：设备已有账号 / 已被设过 owner / ROM 不允许

**如果失败提示 "already set"**，先清除：

```
adb shell dpm remove-active-admin com.example.kiosk/.MyDeviceAdminReceiver
```

或恢复出厂，修改代码后重新运行也需要先清除。

---

## 五、验证流程

1. 安装 App → 运行 → 看到界面全屏，状态栏变黑
2. 按 **Home 键** → 没反应（仍在你的 App）
3. 按 **Recent 键** → 没反应
4. 从屏幕顶部下拉 → 通知栏出不来
5. **重启设备** → 自动进入你的 App

---

## 六、常见坑和实战细节

### 1. 状态栏只是 "灰" 不是 "隐藏"

Lock Task 模式下系统只是把状态栏**禁用**，但它还在。想要完全隐藏，必须配合上面的 `hideSystemBars()` 沉浸式代码。

### 2. 通知仍然可能弹出来

如果系统短信、来电等通知弹窗：

```
// 在 deviceAdmin 里屏蔽
dpm.setStatusBarDisabled(admin, true)  // API 23+
```

### 3. 自动系统更新 / 弹窗打断

可以在 Device Owner 模式下禁用：

```
dpm.setSecureSetting(admin, Settings.Global.DEVICE_PROVISIONED, "1")
```

### 4. 防止用户进 Recovery / Fastboot

硬件层面的事，软件做不到。但可以：

- 屏蔽物理按键（音量键组合）
- 用机房锁 / 设备外壳物理固定

### 5. 如果没有 USB 调试权限（已部署的设备）

可以走 **QR Code 扫码配置**或**NFC 配置**方式批量把设备 enroll 成 managed device，企业 MDM 方案都是这么干的。

### 6. 多 App 白名单

如果 Kiosk 里需要临时跳转到某个白名单 App（比如支付 SDK）：

```
dpm.setLockTaskPackages(admin, arrayOf(
    packageName,
    "com.example.paymentsdk"
))
```

只有白名单里的包能在 lockTask 模式下启动。

---

## 七、最小可用项目结构

```
app/
├── manifests/AndroidManifest.xml
├── java/com/example/kiosk/
│   ├── MainActivity.kt
│   ├── MyDeviceAdminReceiver.kt
│   └── BootReceiver.kt
└── res/xml/device_admin.xml
```

---

### Android 官方关于 Kiosk / Lock Task Mode 的核心文档

## 核心文档（必看）

表格

| 文档 | 链接 | 说明 |
| --- | --- | --- |
| **锁定任务模式（中文）** | [https://developer.android.google.cn/work/dpc/dedicated-devices/lock-task-mode?hl=zh-cn](https://developer.android.google.cn/work/dpc/dedicated-devices/lock-task-mode?hl=zh-cn) | 官方主文档，讲清原理、白名单、启停、与屏幕固定的区别 |
| **Lock task mode（英文）** | [https://developer.android.com/work/dpc/dedicated-devices/lock-task-mode](https://developer.android.com/work/dpc/dedicated-devices/lock-task-mode) | 英文版，内容更新最快 |
| **专用设备实战宝典（中文）** | [https://developer.android.google.cn/work/dpc/dedicated-devices/cookbook?hl=zh-cn](https://developer.android.google.cn/work/dpc/dedicated-devices/cookbook?hl=zh-cn) | 官方代码示例合集，含 Kiosk Activity 的 Manifest 写法 |
| **Android Management API — Kiosk mode** | [https://developers.google.com/android/management/policies/dedicated-devices](https://developers.google.com/android/management/policies/dedicated-devices) | 企业级 MDM 方案，用 `installType: KIOSK` 远程配置 |

## 相关参考

表格

| 文档 | 链接 |
| --- | --- |
| `android:lockTaskMode` Manifest 属性 | [https://developer.android.com/guide/topics/manifest/activity-element?hl=zh-cn#enabled](https://developer.android.com/guide/topics/manifest/activity-element?hl=zh-cn#enabled) |
| Android 9 企业版更新（设备 owner 可锁任意 App） | [https://developer.android.google.cn/work/versions/android-9.0](https://developer.android.google.cn/work/versions/android-9.0) |
| Android 5.0 首次引入 Lock Task | [https://developer.android.com/about/versions/lollipop/android-5.0](https://developer.android.com/about/versions/lollipop/android-5.0) |

## 关键 API 官方参考

- [`DevicePolicyManager.setLockTaskPackages()`](https://developer.android.com/reference/android/app/admin/DevicePolicyManager#setLockTaskPackages(android.content.ComponentName,%20java.lang.String%5B%5D)) —— 白名单
- [`Activity.startLockTask()`](https://developer.android.com/reference/android/app/Activity#startLockTask()) —— 进入
- [`Activity.stopLockTask()`](https://developer.android.com/reference/android/app/Activity#stopLockTask()) —— 退出
