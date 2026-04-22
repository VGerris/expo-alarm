package com.expo.modules.alarm

import android.app.Activity
import android.app.AlarmManager
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import android.widget.LinearLayout

class AlarmActivity : Activity() {
    private var alarmIdentifier: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON
        )

        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 40, 40, 40)
            background = android.graphics.drawable.ColorDrawable(0xFF000000.toInt())
        }

        val titleText = TextView(this).apply {
            text = intent.getStringExtra("title") ?: "Alarm!"
            textSize = 36f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(0, 40, 0, 20)
            gravity = android.view.Gravity.CENTER
        }

        val bodyText = TextView(this).apply {
            text = intent.getStringExtra("body") ?: "Wake up!"
            textSize = 20f
            setTextColor(0xFFCCCCCC.toInt())
            setPadding(0, 0, 0, 40)
            gravity = android.view.Gravity.CENTER
        }

        val dismissButton = Button(this).apply {
            text = "Dismiss"
            textSize = 20f
            setTextColor(0xFFFFFFFF.toInt())
            background = android.graphics.drawable.ColorDrawable(0xFF007AFF.toInt())
            setPadding(40, 20, 40, 20)
            setOnClickListener {
                dismissAlarm()
            }
        }

        rootLayout.addView(titleText)
        rootLayout.addView(bodyText)
        rootLayout.addView(dismissButton)

        setContentView(rootLayout)

        alarmIdentifier = intent.getStringExtra("identifier")
    }

    private fun dismissAlarm() {
        val serviceIntent = Intent(this, AlarmService::class.java).apply {
            action = "STOP_ALARM"
            putExtra("identifier", alarmIdentifier)
        }
        startService(serviceIntent)

        val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
        val cancelIntent = Intent(this, com.expo.modules.alarm.receivers.AlarmReceiver::class.java).apply {
            putExtra("identifier", alarmIdentifier)
        }
        val pendingIntent = android.app.PendingIntent.getBroadcast(
            this,
            alarmIdentifier?.hashCode() ?: 0,
            cancelIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)

        finish()
    }
}