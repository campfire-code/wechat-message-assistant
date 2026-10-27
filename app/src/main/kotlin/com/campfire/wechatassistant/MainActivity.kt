package com.campfire.wechatassistant

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.campfire.wechatassistant.data.Store
import com.campfire.wechatassistant.service.WechatReaderService
import com.campfire.wechatassistant.ui.Actions
import com.campfire.wechatassistant.ui.AppTheme
import com.campfire.wechatassistant.ui.MainScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Store.init(applicationContext)
        // 截图用：只有调试版本才认 demo 参数，载入虚构数据（不写入本机存储）
        val debuggable = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        val demo = debuggable && intent.getBooleanExtra("demo", false)
        if (demo) Store.loadDemo(System.currentTimeMillis())
        setContent {
            var serviceOn by remember { mutableStateOf(demo || isServiceEnabled()) }
            val owner = LocalLifecycleOwner.current
            DisposableEffect(owner) {
                val obs = LifecycleEventObserver { _, e ->
                    if (e == Lifecycle.Event.ON_RESUME && !demo) serviceOn = isServiceEnabled()
                }
                owner.lifecycle.addObserver(obs)
                onDispose { owner.lifecycle.removeObserver(obs) }
            }
            val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
            val actions = Actions(
                openAccessibility = { safeStart(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                requestNotifications = {
                    if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                },
                openWechat = {
                    val i = packageManager.getLaunchIntentForPackage(WechatReaderService.WECHAT)
                    if (i == null) Toast.makeText(this, "这台手机没有安装微信", Toast.LENGTH_SHORT).show() else safeStart(i)
                },
            )
            AppTheme { MainScreen(serviceOn, actions) }
        }
    }

    private fun isServiceEnabled(): Boolean {
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        val me = "$packageName/${WechatReaderService::class.java.name}"
        return enabled.split(':').any { it.equals(me, true) || it.equals("$packageName/.service.WechatReaderService", true) }
    }

    private fun safeStart(i: Intent) {
        try {
            startActivity(i)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "无法打开", Toast.LENGTH_SHORT).show()
        }
    }
}
