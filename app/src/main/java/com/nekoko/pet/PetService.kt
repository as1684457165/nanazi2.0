package com.nekoko.pet

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import kotlin.math.abs

class PetService : Service() {

    private lateinit var wm: WindowManager
    private lateinit var root: FrameLayout
    private lateinit var pet: ImageView
    private lateinit var bubble: TextView
    private lateinit var lp: WindowManager.LayoutParams

    private val handler = Handler(Looper.getMainLooper())
    private var dragging = false
    private var dir = 1

    private val lines = arrayOf(
        "哼，本小姐才不是担心你。",
        "别误会，可不是专门为了你才做的。",
        "就这点本事？ご主人様还真是好养活。",
        "别戳了，讨厌。",
        "ご主人様，眼睛歇一会儿吧。",
        "……有事？没事本小姐继续站着。",
        "看什么看，本小姐脸上没花。",
        "在呢，别怕。"
    )

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
        buildViews()

        lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(40)
            y = dp(240)
        }

        attachTouch()
        wm.addView(root, lp)

        breathe()
        patrol()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun buildViews() {
        pet = ImageView(this).apply {
            setImageResource(R.drawable.nekoko)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }

        bubble = TextView(this).apply {
            textSize = 11f
            setTextColor(Color.parseColor("#5A3A46"))
            maxWidth = dp(170)
            setPadding(dp(10), dp(6), dp(10), dp(6))
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(Color.parseColor("#FDF0F5"))
                setStroke(dp(1), Color.parseColor("#E58FB0"))
            }
            visibility = View.GONE
        }

        root = FrameLayout(this).apply {
            addView(
                pet,
                FrameLayout.LayoutParams(dp(130), dp(130), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
            )
            addView(
                bubble,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP or Gravity.CENTER_HORIZONTAL
                )
            )
            layoutParams = FrameLayout.LayoutParams(dp(190), dp(200))
        }
    }

    private fun attachTouch() {
        var downX = 0f
        var downY = 0f
        var baseX = 0
        var baseY = 0
        var moved = false
        var lastTap = 0L

        val hold = Runnable {
            showBubble("哼，本小姐不干了。")
            handler.postDelayed({ stopSelf() }, 900)
        }

        root.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX
                    downY = e.rawY
                    baseX = lp.x
                    baseY = lp.y
                    moved = false
                    dragging = true
                    handler.postDelayed(hold, 800)
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    if (abs(dx) > dp(8) || abs(dy) > dp(8)) {
                        moved = true
                        handler.removeCallbacks(hold)
                    }
                    lp.x = (baseX + dx).toInt()
                    lp.y = (baseY + dy).toInt()
                    wm.updateViewLayout(root, lp)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(hold)
                    dragging = false
                    if (moved) {
                        snap()
                    } else {
                        val now = SystemClock.uptimeMillis()
                        if (now - lastTap < 320) {
                            lastTap = 0
                            showBubble("别连戳！")
                            shake()
                        } else {
                            lastTap = now
                            showBubble(lines.random())
                            bounce()
                        }
                    }
                }
            }
            true
        }
    }

    private fun snap() {
        val w = resources.displayMetrics.widthPixels
        lp.x = if (lp.x + root.width / 2 < w / 2) 0 else w - root.width
        wm.updateViewLayout(root, lp)
    }

    private fun breathe() {
        val a = ObjectAnimator.ofFloat(pet, "translationY", 0f, -dp(8).toFloat())
        a.duration = 1300
        a.repeatMode = ValueAnimator.REVERSE
        a.repeatCount = ValueAnimator.INFINITE
        a.interpolator = LinearInterpolator()
        a.start()
    }

    private fun bounce() {
        val a = ObjectAnimator.ofFloat(pet, "scaleX", 1f, 1.15f, 1f)
        val b = ObjectAnimator.ofFloat(pet, "scaleY", 1f, 1.15f, 1f)
        a.duration = 220
        b.duration = 220
        a.start()
        b.start()
    }

    private fun shake() {
        val a = ObjectAnimator.ofFloat(pet, "rotation", 0f, -8f, 8f, -6f, 0f)
        a.duration = 400
        a.start()
    }

    private fun patrol() {
        handler.post(object : Runnable {
            override fun run() {
                if (!dragging && root.width > 0) {
                    val w = resources.displayMetrics.widthPixels
                    lp.x += dir * 2
                    if (lp.x < 0) {
                        lp.x = 0
                        dir = 1
                    }
                    if (lp.x > w - root.width) {
                        lp.x = w - root.width
                        dir = -1
                    }
                    wm.updateViewLayout(root, lp)
                }
                handler.postDelayed(this, 40)
            }
        })
    }

    private val hideBubble = Runnable { bubble.visibility = View.GONE }

    private fun showBubble(text: String) {
        bubble.text = text
        bubble.visibility = View.VISIBLE
        handler.removeCallbacks(hideBubble)
        handler.postDelayed(hideBubble, 2200)
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
        handler.removeCallbacksAndMessages(null)
        if (::root.isInitialized) wm.removeView(root)
    }
}
