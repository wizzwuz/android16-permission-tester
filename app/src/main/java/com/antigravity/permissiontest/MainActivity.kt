package com.antigravity.permissiontest

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import androidx.core.app.RemoteInput
import android.app.usage.UsageStatsManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.lifecycle.lifecycleScope
import com.antigravity.permissiontest.databinding.ActivityMainBinding
import com.antigravity.permissiontest.services.TestAccessibilityService
import com.antigravity.permissiontest.services.TestNotificationListenerService
import com.antigravity.permissiontest.utils.PermissionHelper
import com.antigravity.permissiontest.utils.RootGranter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val channelId = "perm_test_channel"

    private val postNotifLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        log("POST_NOTIFICATIONS result: $granted")
        updateUI()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        createNotificationChannel()
        setupListeners()

        // Notification listener callbacks
        TestNotificationListenerService.onNotificationsChanged = {
            runOnUiThread {
                updateUI()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateUI()
    }

    private fun setupListeners() {
        // 1-Click Root Granter
        binding.btnGrantAllRoot.setOnClickListener {
            log("Attempting 1-Click Root Grant (su)...")
            lifecycleScope.launch(Dispatchers.IO) {
                val result = RootGranter.grantAllWithRoot(this@MainActivity)
                withContext(Dispatchers.Main) {
                    log(result.second)
                    updateUI()
                    if (result.first) {
                        Toast.makeText(this@MainActivity, "All permissions granted via Root!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@MainActivity, "Root grant failed. Check Root status.", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

        binding.btnCopyAdb.setOnClickListener {
            val cmds = RootGranter.getAdbCommands(this).joinToString("\n")
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("ADB Permission Commands", cmds))
            Toast.makeText(this, "ADB commands copied to clipboard!", Toast.LENGTH_SHORT).show()
            log("Copied ADB commands to clipboard.")
        }

        // Notification Category
        binding.btnOpenNotifSettings.setOnClickListener {
            try {
                startActivity(PermissionHelper.getNotificationListenerIntent())
            } catch (e: Exception) {
                log("Error opening Notif Listener Settings: ${e.message}")
            }
        }

        binding.btnTestPostNotif.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                !PermissionHelper.isPostNotificationGranted(this)) {
                postNotifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                sendTestNotification()
            }
        }

        binding.btnReadNotifs.setOnClickListener {
            val list = TestNotificationListenerService.activeList
            log("Active Notifications count: ${list.size}")
            for ((index, sbn) in list.withIndex()) {
                val title = sbn.notification.extras.getString(android.app.Notification.EXTRA_TITLE) ?: "No Title"
                val text = sbn.notification.extras.getCharSequence(android.app.Notification.EXTRA_TEXT) ?: "No Text"
                log(" [$index] ${sbn.packageName}: \"$title\" - \"$text\"")

                // Test direct reply if it's our test notification
                if (sbn.packageName == packageName && TestNotificationListenerService.instance != null) {
                    val replied = TestNotificationListenerService.instance!!.sendDirectReply(
                        sbn,
                        "Auto-reply response at ${SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())}"
                    )
                    log("  -> Tested Direct Reply via RemoteInput: success=$replied")
                }
            }
        }

        binding.btnDismissAllNotifs.setOnClickListener {
            val service = TestNotificationListenerService.instance
            if (service != null) {
                service.dismissAll()
                log("Triggered dismissAllNotifications()")
            } else {
                log("NotificationListenerService not currently connected. Enable it first!")
            }
        }

        // Install Unknown Apps
        binding.btnOpenInstallSettings.setOnClickListener {
            try {
                startActivity(PermissionHelper.getInstallUnknownAppsIntent(this))
            } catch (e: Exception) {
                log("Error opening Install Unknown Apps settings: ${e.message}")
            }
        }

        // Usage Stats
        binding.btnOpenUsageSettings.setOnClickListener {
            try {
                startActivity(PermissionHelper.getUsageStatsIntent())
            } catch (e: Exception) {
                log("Error opening Usage Stats settings: ${e.message}")
            }
        }

        binding.btnQueryUsage.setOnClickListener {
            queryUsageStats()
        }

        // Accessibility
        binding.btnOpenAccessibilitySettings.setOnClickListener {
            try {
                startActivity(PermissionHelper.getAccessibilitySettingsIntent())
            } catch (e: Exception) {
                log("Error opening Accessibility settings: ${e.message}")
            }
        }

        binding.btnTestGlobalAction.setOnClickListener {
            val service = TestAccessibilityService.instance
            if (service != null) {
                val ok = service.triggerGlobalNotifications()
                log("Accessibility performGlobalAction(NOTIFICATIONS): success=$ok")
            } else {
                log("TestAccessibilityService is NOT enabled or connected.")
            }
        }

        // Write Secure Settings
        binding.btnTestWriteSecure.setOnClickListener {
            testWriteSecureSettings()
        }

        // Restricted Settings
        binding.btnOpenAppInfo.setOnClickListener {
            try {
                startActivity(PermissionHelper.getAppDetailsSettingsIntent(this))
            } catch (e: Exception) {
                log("Error opening App Info: ${e.message}")
            }
        }
    }

    private fun updateUI() {
        // 1. Notification Listener
        val notifEnabled = PermissionHelper.isNotificationListenerEnabled(this)
        val notifConnected = TestNotificationListenerService.isServiceRunning
        val notifCount = TestNotificationListenerService.activeList.size

        if (notifEnabled && notifConnected) {
            binding.statusNotif.text = "CONNECTED"
            binding.statusNotif.setTextColor(Color.parseColor("#2E7D32"))
            binding.tvNotifDetails.text = "Service is active and capturing notifications ($notifCount active)."
        } else if (notifEnabled) {
            binding.statusNotif.text = "ENABLED"
            binding.statusNotif.setTextColor(Color.parseColor("#E65100"))
            binding.tvNotifDetails.text = "Listener enabled in Settings, waiting for Android connection."
        } else {
            binding.statusNotif.text = "DISABLED"
            binding.statusNotif.setTextColor(Color.parseColor("#C62828"))
            binding.tvNotifDetails.text = "Enable in System Notification Access Settings."
        }
        binding.btnReadNotifs.text = "Read Active ($notifCount)"

        // 2. Install Unknown Apps
        val canInstall = PermissionHelper.canInstallUnknownApps(this)
        if (canInstall) {
            binding.statusInstall.text = "ALLOWED"
            binding.statusInstall.setTextColor(Color.parseColor("#2E7D32"))
            binding.tvInstallDetails.text = "canRequestPackageInstalls() = true. Ready to install APKs."
        } else {
            binding.statusInstall.text = "DENIED"
            binding.statusInstall.setTextColor(Color.parseColor("#C62828"))
            binding.tvInstallDetails.text = "canRequestPackageInstalls() = false. Allow in Unknown Apps Settings."
        }

        // 3. Usage Stats
        val usageAllowed = PermissionHelper.isUsageStatsPermissionGranted(this)
        if (usageAllowed) {
            binding.statusUsage.text = "GRANTED"
            binding.statusUsage.setTextColor(Color.parseColor("#2E7D32"))
            binding.tvUsageDetails.text = "PACKAGE_USAGE_STATS is granted. Ready to query app usage."
        } else {
            binding.statusUsage.text = "DENIED"
            binding.statusUsage.setTextColor(Color.parseColor("#C62828"))
            binding.tvUsageDetails.text = "Usage access is not permitted. Enable in Usage Access Settings."
        }

        // 4. Accessibility
        val accEnabled = PermissionHelper.isAccessibilityServiceEnabled(this)
        val accConnected = TestAccessibilityService.isServiceRunning
        if (accEnabled && accConnected) {
            binding.statusAccessibility.text = "CONNECTED"
            binding.statusAccessibility.setTextColor(Color.parseColor("#2E7D32"))
            binding.tvAccessibilityDetails.text = "Accessibility Service active. Ready for gestures & global actions."
        } else if (accEnabled) {
            binding.statusAccessibility.text = "ENABLED"
            binding.statusAccessibility.setTextColor(Color.parseColor("#E65100"))
            binding.tvAccessibilityDetails.text = "Service enabled in Settings, initializing..."
        } else {
            binding.statusAccessibility.text = "DISABLED"
            binding.statusAccessibility.setTextColor(Color.parseColor("#C62828"))
            binding.tvAccessibilityDetails.text = "Service disabled. Enable in Accessibility Settings."
        }

        // 5. Write Secure Settings
        val secureGranted = PermissionHelper.hasWriteSecureSettings(this)
        if (secureGranted) {
            binding.statusSecure.text = "GRANTED"
            binding.statusSecure.setTextColor(Color.parseColor("#2E7D32"))
            binding.tvSecureDetails.text = "WRITE_SECURE_SETTINGS granted. Can modify system secure settings."
        } else {
            binding.statusSecure.text = "DENIED"
            binding.statusSecure.setTextColor(Color.parseColor("#C62828"))
            binding.tvSecureDetails.text = "Not granted. Requires pm grant via ADB or SU."
        }
    }

    private fun sendTestNotification() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val replyLabel = "Reply to permission test"
        val remoteInput = RemoteInput.Builder("key_text_reply")
            .setLabel(replyLabel)
            .build()

        val replyIntent = Intent(this, MainActivity::class.java)
        val replyPendingIntent = PendingIntent.getActivity(
            this,
            101,
            replyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        val replyAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_dialog_info,
            "Reply Here",
            replyPendingIntent
        ).addRemoteInput(remoteInput).build()

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Permission Test Notification")
            .setContentText("Test message with RemoteInput inline reply")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(replyAction)
            .setAutoCancel(true)
            .build()

        manager.notify(2001, notification)
        log("Sent test notification #2001 with RemoteInput reply action.")
    }

    private fun queryUsageStats() {
        val usageManager = getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
        if (usageManager == null) {
            log("UsageStatsManager unavailable")
            return
        }

        val endTime = System.currentTimeMillis()
        val startTime = endTime - (1000 * 60 * 60 * 24) // Past 24 hours

        try {
            val stats = usageManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                startTime,
                endTime
            )

            if (stats.isNullOrEmpty()) {
                log("Usage stats query returned 0 items. Permission may not be granted.")
            } else {
                val sorted = stats.filter { it.totalTimeInForeground > 0 }
                    .sortedByDescending { it.totalTimeInForeground }
                    .take(5)

                log("Top 5 apps in last 24h:")
                for (s in sorted) {
                    val minutes = s.totalTimeInForeground / (1000 * 60)
                    log("  • ${s.packageName}: ${minutes} mins in foreground")
                }
            }
        } catch (e: Exception) {
            log("Query usage stats error: ${e.message}")
        }
    }

    private fun testWriteSecureSettings() {
        try {
            // Try reading
            val androidId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
            log("Read Settings.Secure.ANDROID_ID: $androidId")

            // Try writing a harmless setting: e.g. haptic_feedback_enabled (toggle current)
            val current = Settings.System.getInt(contentResolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, 1)
            val ok = Settings.Secure.putString(contentResolver, "test_perm_timestamp", System.currentTimeMillis().toString())
            log("Write to Settings.Secure: returned $ok")
            updateUI()
        } catch (e: SecurityException) {
            log("SecurityException on Write Secure Settings: ${e.message}")
            log("To fix, run:\n adb shell pm grant $packageName android.permission.WRITE_SECURE_SETTINGS")
        } catch (e: Exception) {
            log("Write secure settings error: ${e.message}")
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Permission Test Channel"
            val desc = "Notifications for testing listener, reading and inline reply"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(channelId, name, importance).apply {
                description = desc
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun log(message: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        val formatted = "[$time] $message\n"
        binding.tvConsole.append(formatted)
    }
}
