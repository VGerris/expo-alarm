package com.expo.modules.alarm

import android.app.Activity
import android.app.AlarmManager
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import android.widget.LinearLayout
import com.expo.modules.alarm.receivers.AlarmReceiver

class AlarmActivity : Activity() {
    private var alarmIdentifier: String? = null
    private var dismissReceiver: BroadcastReceiver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON
        )
        window.setGravity(Gravity.CENTER)

        // Register receiver for dismiss confirmation
        dismissReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val id = intent?.getStringExtra("identifier")
                if (id == alarmIdentifier) {
                    finish()
                }
            }
        }
        val filter = IntentFilter("com.expo.modules.alarm.DISMISS_CONFIRMED")
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(dismissReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(dismissReceiver, filter)
        }

        val identifier = intent.getStringExtra("identifier") ?: "default"
        val title = intent.getStringExtra("title") ?: "Alarm!"
        val body = intent.getStringExtra("body") ?: "Wake up!"

        val cardLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
            background = android.graphics.drawable.ColorDrawable(0xFF1A1A2E.toInt())
            gravity = android.view.Gravity.CENTER
        }

        val titleText = TextView(this).apply {
            text = title
            textSize = 28f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(0, 0, 0, 8)
            gravity = android.view.Gravity.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }

        val bodyText = TextView(this).apply {
            text = body
            textSize = 16f
            setTextColor(0xFFCCCCCC.toInt())
            setPadding(0, 0, 0, 24)
            gravity = android.view.Gravity.CENTER
        }

        val dismissButton = Button(this).apply {
            text = "Dismiss"
            textSize = 16f
            setTextColor(0xFFFFFFFF.toInt())
            background = android.graphics.drawable.ColorDrawable(0xFF007AFF.toInt())
            setPadding(40, 14, 40, 14)
            setOnClickListener {
                dismissAlarm()
            }
        }

        cardLayout.addView(titleText)
        cardLayout.addView(bodyText)
        cardLayout.addView(dismissButton)

        setContentView(cardLayout)

        // Set window size to match the card
        val screenWidth = resources.displayMetrics.widthPixels
        val cardWidth = (screenWidth * 0.35).toInt()
        window.setLayout(cardWidth, WindowManager.LayoutParams.WRAP_CONTENT)

        alarmIdentifier = identifier
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

        // Dismiss notification
        val notificationManager = androidx.core.app.NotificationManagerCompat.from(this)
        notificationManager.cancel(AlarmService.notificationIdFor(alarmIdentifier ?: "default"))

        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        dismissReceiver?.let {
            unregisterReceiver(it)
        }
    }
}
