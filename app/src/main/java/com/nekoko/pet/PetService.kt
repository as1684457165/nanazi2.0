package com.nekoko.pet

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

class PetService : Service() {

    private lateinit var wm: WindowManager
    private lateinit var view: View
    private lateinit var lp: WindowManager.LayoutParams

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()

        val n = buildNotify()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, n)
        }

        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        view = buildPet()

        lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 120
            y = 400
        }

        var downX = 0f
        var downY = 0f
        var baseX = 0f
        var baseY = 0f

        view.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX
                    downY = e.rawY
                    baseX = lp.x.toFloat()
                    baseY = lp.y.toFloat()
                }
                MotionEvent.ACTION_MOVE -> {
                    lp.x = (baseX + e.rawX - downX).toInt()
                    lp.y = (baseY + e.rawY - downY).toInt()
                    wm.updateViewLayout(view, lp)
                }
                MotionEvent.ACTION_UP -> {
                    val screenW = resources.displayMetrics.widthPixels
                    lp.x = if (e.rawX < screenW / 2) 0 else screenW - view.width
                    wm.updateViewLayout(view, lp)
                }
            }
            true
        }

        wm.addView(view, lp)
    }

    private fun buildPet(): View {
        val ball = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xFFFFD6E7.toInt())
                setStroke(6, 0xFFE58FB0.toInt())
            }
        }
        val label = TextView(this).apply {
            text = "奈奈子"
            setTextColor(0xFFE58FB0.toInt())
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            addView(ball, LinearLayout.LayoutParams(180, 180))
            addView(label)
        }
    }

    private fun buildNotify(): Notification {
        val id = "nekoko_pet"
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(id, "桌宠", NotificationManager.IMPORTANCE_MIN)
        )
        return Notification.Builder(this, id)
            .setContentTitle("奈奈子")
            .setContentText("正在桌面上看着你")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::view.isInitialized) wm.removeView(view)
    }
}
