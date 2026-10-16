package com.nekoko.pet

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val start = Button(this).apply {
            text = "把奈奈子放到桌面上"
            setOnClickListener {
                if (!Settings.canDrawOverlays(this@MainActivity)) {
                    startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:$packageName")
                        )
                    )
                } else {
                    startForegroundService(Intent(this@MainActivity, PetService::class.java))
                }
            }
        }

        val stop = Button(this).apply {
            text = "让奈奈子回去"
            setOnClickListener {
                stopService(Intent(this@MainActivity, PetService::class.java))
            }
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 300, 60, 60)
            addView(start)
            addView(stop)
        }
        setContentView(root)
    }
}
