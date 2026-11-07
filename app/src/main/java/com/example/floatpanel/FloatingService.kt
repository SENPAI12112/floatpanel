package com.example.floatpanel

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.webkit.WebView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs

class FloatingService : Service() {
    private lateinit var wm: WindowManager
    private var panel: View? = null
    private var bubble: View? = null
    private var panelShown = false
    private var bubbleShown = false
    private lateinit var panelLp: WindowManager.LayoutParams
    private lateinit var bubbleLp: WindowManager.LayoutParams

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (panel == null) {
            startForegroundNotification()
            wm = getSystemService(WINDOW_SERVICE) as WindowManager
            buildPanel()
            buildBubble()
            showPanel()
        }
        return START_STICKY
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun startForegroundNotification() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel("float", "Floating panel", NotificationManager.IMPORTANCE_LOW))
        }
        val n = Notification.Builder(this, "float")
            .setContentTitle("Float Panel is running")
            .setContentText("Tap the bubble to open the panel")
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .build()
        startForeground(1, n)
    }

    private fun overlayParams(w: Int, h: Int, focusable: Boolean): WindowManager.LayoutParams {
        val flags = if (focusable) 0 else WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        return WindowManager.LayoutParams(
            w, h, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            flags, PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }
    }

    private fun buildPanel() {
        val dm = resources.displayMetrics
        panelLp = overlayParams((dm.widthPixels * 0.92).toInt(), (dm.heightPixels * 0.6).toInt(), true)
        panelLp.x = (dm.widthPixels * 0.04).toInt()
        panelLp.y = dp(80)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = dp(16).toFloat() }
            clipToOutline = true
        }
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(0xFF14201D.toInt())
            gravity = Gravity.CENTER_VERTICAL
        }
        val title = TextView(this).apply {
            text = "Command Executor  (drag me)"
            setTextColor(Color.WHITE); textSize = 14f
            setPadding(dp(14), dp(12), dp(8), dp(12))
        }
        bar.addView(title, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        bar.addView(barButton("—") { hidePanel(); showBubble() })
        bar.addView(barButton("✕") { stopSelf() })
        root.addView(bar)

        val web = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            loadUrl("file:///android_asset/panel.html")
        }
        root.addView(web, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        makeDraggable(title, panelLp, onMove = { wm.updateViewLayout(root, panelLp) })
        panel = root
    }

    private fun barButton(label: String, onClick: () -> Unit) = TextView(this).apply {
        text = label
        setTextColor(Color.WHITE); textSize = 18f
        gravity = Gravity.CENTER
        setPadding(dp(16), dp(10), dp(16), dp(10))
        setOnClickListener { onClick() }
    }

    private fun buildBubble() {
        bubbleLp = overlayParams(dp(56), dp(56), false)
        bubbleLp.x = dp(12); bubbleLp.y = dp(200)
        val b = TextView(this).apply {
            text = "⌨"
            textSize = 24f; setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0xFF0F766E.toInt()) }
        }
        makeDraggable(b, bubbleLp, onMove = { wm.updateViewLayout(b, bubbleLp) }, onTap = { hideBubble(); showPanel() })
        bubble = b
    }

    private fun makeDraggable(
        handle: View, lp: WindowManager.LayoutParams,
        onTap: (() -> Unit)? = null, onMove: () -> Unit
    ) {
        var sx = 0f; var sy = 0f; var ox = 0; var oy = 0; var moved = false
        handle.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { sx = e.rawX; sy = e.rawY; ox = lp.x; oy = lp.y; moved = false; true }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (e.rawX - sx).toInt(); val dy = (e.rawY - sy).toInt()
                    if (abs(dx) > 10 || abs(dy) > 10) moved = true
                    lp.x = ox + dx; lp.y = oy + dy
                    onMove(); true
                }
                MotionEvent.ACTION_UP -> { if (!moved) onTap?.invoke(); true }
                else -> false
            }
        }
    }

    private fun showPanel() { if (!panelShown) { wm.addView(panel, panelLp); panelShown = true } }
    private fun hidePanel() { if (panelShown) { wm.removeView(panel); panelShown = false } }
    private fun showBubble() { if (!bubbleShown) { wm.addView(bubble, bubbleLp); bubbleShown = true } }
    private fun hideBubble() { if (bubbleShown) { wm.removeView(bubble); bubbleShown = false } }

    override fun onDestroy() {
        hidePanel(); hideBubble()
        super.onDestroy()
    }
}
