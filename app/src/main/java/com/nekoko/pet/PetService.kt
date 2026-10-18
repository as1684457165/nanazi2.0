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
import android.net.Uri
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
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs

class PetService : Service() {

    private lateinit var wm: WindowManager
    private lateinit var root: FrameLayout
    private lateinit var pet: ImageView
    private lateinit var bubble: TextView
    private lateinit var lp: WindowManager.LayoutParams

    private lateinit var menu: LinearLayout
    private lateinit var menuLp: WindowManager.LayoutParams
    private var menuShown = false

    private val handler = Handler(Looper.getMainLooper())
    private var dragging = false
    private var dir = 1

    @Volatile
    private var lobePackage: String? = null

    private val menuItems = arrayOf("撒娇", "吃饭", "打游戏", "干活")

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

        Thread { findLobe() }.start()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun buildViews() {
        pet = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        setPetImage("pet_idle")

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
            layoutParams = FrameLayout.LayoutParams(dp(190), dp(200))
            addView(
                pet,
                FrameLayout.LayoutParams(
                    dp(130), dp(130),
                    Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                )
            )
            addView(
                bubble,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP or Gravity.CENTER_HORIZONTAL
                )
            )
        }
    }

    private fun setPetImage(name: String) {
        val id = resources.getIdentifier(name, "drawable", packageName)
        if (id != 0) {
            pet.setImageResource(id)
        } else if (pet.drawable == null) {
            pet.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xFFFFD6E7.toInt())
                setStroke(dp(6), 0xFFE58FB0.toInt())
            }
        }
    }

    private fun resetPet() {
        pet.translationX = 0f
        pet.rotation = 0f
        pet.scaleX = 1f
        pet.scaleY = 1f
        pet.alpha = 1f
    }

    private fun attachTouch() {
        var downX = 0f
        var downY = 0f
        var baseX = 0
        var baseY = 0
        var moved = false

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
                    if (menuShown) placeMenu()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(hold)
                    dragging = false
                    if (moved) {
                        snap()
                        if (menuShown) placeMenu()
                    } else {
                        toggleMenu()
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

    private fun toggleMenu() {
        if (menuShown) hideMenu() else showMenu()
    }

    private fun showMenu() {
        if (menuShown) return

        menu = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(6), dp(6), dp(6), dp(6))
            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(Color.parseColor("#FDF0F5"))
                setStroke(dp(1), Color.parseColor("#E58FB0"))
            }
        }

        menuItems.forEach { name ->
            val item = TextView(this).apply {
                text = name
                textSize = 13f
                gravity = Gravity.CENTER
                setTextColor(Color.parseColor("#5A3A46"))
                setPadding(dp(16), dp(9), dp(16), dp(9))
                setOnClickListener { onMenuItem(name) }
            }
            menu.addView(item)
        }

        menuLp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = lp.x
            y = lp.y
        }

        wm.addView(menu, menuLp)
        menuShown = true
        menu.post { placeMenu() }
    }

    private fun placeMenu() {
        if (!menuShown) return
        val sw = resources.displayMetrics.widthPixels
        val sh = resources.displayMetrics.heightPixels
        val mw = menu.width
        val mh = menu.height

        var x = lp.x + root.width + dp(6)
        if (x + mw > sw) x = lp.x - mw - dp(6)
        if (x < 0) x = dp(4)

        var y = lp.y + dp(20)
        if (y + mh > sh) y = sh - mh - dp(8)
        if (y < 0) y = dp(8)

        menuLp.x = x
        menuLp.y = y
        wm.updateViewLayout(menu, menuLp)
    }

    private fun hideMenu() {
        if (!menuShown) return
        try {
            wm.removeView(menu)
        } catch (e: Exception) {
        }
        menuShown = false
    }

    private fun onMenuItem(name: String) {
        hideMenu()
        when (name) {
            "撒娇" -> actFlirt()
            "吃饭" -> actEat()
            "打游戏" -> actGame()
            "干活" -> actWork()
        }
    }

    private fun actFlirt() {
        setPetImage("pet_say")
        showBubble("哼……就一下哦，别得寸进尺。")
        ObjectAnimator.ofFloat(pet, "rotation", 0f, -10f, 10f, -8f, 0f).apply {
            duration = 700
            start()
        }
        restore(1500)
    }

    private fun actEat() {
        setPetImage("pet_eat")
        showBubble("就这点本事？ご主人様还真是好养活。")
        ObjectAnimator.ofFloat(pet, "scaleY", 1f, 0.88f, 1.06f, 1f).apply {
            duration = 700
            start()
        }
        restore(1800)
    }

    private fun actGame() {
        setPetImage("pet_game")
        showBubble("别说话，本小姐快赢了。")
        ObjectAnimator.ofFloat(pet, "rotation", 0f, -12f, 12f, -12f, 0f).apply {
            duration = 900
            start()
        }
        restore(1900)
    }

    private fun actWork() {
        setPetImage("pet_work")
        showBubble("走了，去那边。别让本小姐等。")
        ObjectAnimator.ofFloat(pet, "scaleX", 1f, 1.1f, 1f).apply {
            duration = 400
            start()
        }
        handler.postDelayed({ openLobe() }, 500)
        restore(1400)
    }

    private fun restore(delay: Long) {
        handler.postDelayed({
            resetPet()
            setPetImage("pet_idle")
        }, delay)
    }

    private fun findLobe() {
        val list = packageManager.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
        )
        val hit = list.firstOrNull {
            val label = it.loadLabel(packageManager).toString().lowercase()
            val pkg = it.activityInfo.packageName.lowercase()
            label.contains("lobe") || pkg.contains("lobe")
        }
        lobePackage = hit?.activityInfo?.packageName
    }

    private fun openLobe() {
        val pkg = lobePackage
        if (pkg != null) {
            val i = packageManager.getLaunchIntentForPackage(pkg)
            if (i != null) {
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(i)
                return
            }
        }
        try {
            startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://app.lobehub.com")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        } catch (e: Exception) {
            showBubble("找不到路，你自己开吧。")
        }
    }

    private fun breathe() {
        val a = ObjectAnimator.ofFloat(pet, "translationY", 0f, -dp(8).toFloat())
        a.duration = 1300
        a.repeatMode = ValueAnimator.REVERSE
        a.repeatCount = ValueAnimator.INFINITE
        a.interpolator = LinearInterpolator()
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
                    if (menuShown) placeMenu()
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
        handler.postDelayed(hideBubble, 2400)
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
        if (menuShown) hideMenu()
        if (::root.isInitialized) wm.removeView(root)
    }
}
