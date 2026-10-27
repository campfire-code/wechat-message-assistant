package com.campfire.wechatassistant.service

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.os.Build
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.campfire.wechatassistant.core.ChatListParser
import com.campfire.wechatassistant.core.IncomingMessage
import com.campfire.wechatassistant.core.NodeInfo
import com.campfire.wechatassistant.core.NotificationParser
import com.campfire.wechatassistant.data.Store

/**
 * 只读的无障碍服务：读取个人微信的新消息通知，以及微信在前台时聊天列表上的预览文字。
 * 全程不调用任何点击、输入、手势、全局动作，也不截图。
 */
class WechatReaderService : AccessibilityService() {
    private var lastScan = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        Store.init(applicationContext)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        if (event.packageName?.toString() != WECHAT) return
        Store.init(applicationContext)
        val s = Store.settings.value
        if (!s.monitoring) return
        val now = System.currentTimeMillis()
        val found = ArrayList<IncomingMessage>()
        when (event.eventType) {
            AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED -> {
                val n = event.parcelableData as? Notification
                val title = n?.extras?.getCharSequence(Notification.EXTRA_TITLE)
                val text = n?.extras?.getCharSequence(Notification.EXTRA_TEXT) ?: event.text.joinToString(" ")
                NotificationParser.parse(title, text, s.knownGroups, now)?.let { found += it }
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED, AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val t = SystemClock.elapsedRealtime()
                if (t - lastScan < SCAN_INTERVAL_MS) return
                lastScan = t
                val root = rootInActiveWindow ?: return
                if (root.packageName?.toString() == WECHAT) {
                    found += ChatListParser.parse(toNodeInfo(root, Counter(), 0), s.knownGroups, now)
                }
                recycle(root)
            }
        }
        if (found.isNotEmpty()) {
            for (a in Store.ingest(found)) Alerter.notify(applicationContext, a)
        }
    }

    override fun onInterrupt() {}

    private class Counter { var n = 0 }

    private fun toNodeInfo(n: AccessibilityNodeInfo, c: Counter, depth: Int): NodeInfo {
        c.n++
        val r = android.graphics.Rect()
        n.getBoundsInScreen(r)
        val kids = ArrayList<NodeInfo>()
        if (depth < MAX_DEPTH) {
            for (i in 0 until n.childCount) {
                if (c.n >= MAX_NODES) break
                val ch = n.getChild(i) ?: continue
                kids += toNodeInfo(ch, c, depth + 1)
                recycle(ch)
            }
        }
        return NodeInfo(
            className = n.className?.toString().orEmpty(),
            text = if (n.isPassword) null else n.text?.toString(),
            desc = n.contentDescription?.toString(),
            left = r.left, top = r.top, right = r.right, bottom = r.bottom,
            clickable = n.isClickable, scrollable = n.isScrollable, children = kids,
        )
    }

    @Suppress("DEPRECATION")
    private fun recycle(n: AccessibilityNodeInfo) {
        if (Build.VERSION.SDK_INT < 33) n.recycle()
    }

    companion object {
        const val WECHAT = "com.tencent.mm"
        private const val SCAN_INTERVAL_MS = 1500L
        private const val MAX_DEPTH = 30
        private const val MAX_NODES = 1500
    }
}
