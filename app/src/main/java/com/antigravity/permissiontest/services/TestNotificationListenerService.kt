package com.antigravity.permissiontest.services

import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class TestNotificationListenerService : NotificationListenerService() {

    companion object {
        private const val TAG = "NotifListenerService"
        var instance: TestNotificationListenerService? = null
            private set
        var isServiceRunning = false
            private set

        val activeList = mutableListOf<StatusBarNotification>()
        var onNotificationsChanged: (() -> Unit)? = null
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        isServiceRunning = true
        Log.d(TAG, "Notification Listener connected successfully")
        refreshActiveList()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        instance = null
        isServiceRunning = false
        Log.d(TAG, "Notification Listener disconnected")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        sbn?.let {
            Log.d(TAG, "Notification posted: ${it.packageName} - ${it.notification.extras.getString(Notification.EXTRA_TITLE)}")
        }
        refreshActiveList()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        refreshActiveList()
    }

    private fun refreshActiveList() {
        try {
            val notifs = activeNotifications
            activeList.clear()
            if (notifs != null) {
                activeList.addAll(notifs)
            }
            onNotificationsChanged?.invoke()
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing notifications", e)
        }
    }

    /**
     * Send inline reply to a notification with RemoteInput (WhatsApp, Telegram, SMS, etc.)
     */
    fun sendDirectReply(sbn: StatusBarNotification, replyText: String): Boolean {
        val actions = sbn.notification.actions ?: return false
        for (action in actions) {
            val remoteInputs = action.remoteInputs ?: continue
            for (remoteInput in remoteInputs) {
                val intent = Intent()
                val bundle = Bundle()
                bundle.putCharSequence(remoteInput.resultKey, replyText)
                RemoteInput.addResultsToIntent(remoteInputs, intent, bundle)
                try {
                    action.actionIntent.send(this, 0, intent)
                    Log.d(TAG, "Direct reply sent successfully to ${sbn.packageName}")
                    return true
                } catch (e: PendingIntent.CanceledException) {
                    Log.e(TAG, "PendingIntent was canceled", e)
                }
            }
        }
        return false
    }

    /**
     * Control notification: dismiss/cancel specific notification by key
     */
    fun dismissNotification(key: String) {
        cancelNotification(key)
        refreshActiveList()
    }

    /**
     * Control notification: dismiss all notifications
     */
    fun dismissAll() {
        cancelAllNotifications()
        refreshActiveList()
    }
}
