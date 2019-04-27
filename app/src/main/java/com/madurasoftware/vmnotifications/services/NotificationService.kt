package com.madurasoftware.vmnotifications.services

import android.app.Notification.*
import android.app.Service
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class NotificationService : NotificationListenerService() {

    private val TAG = "NotificationService"

    override fun onCreate() {
        super.onCreate()
        if (android.os.Debug.isDebuggerConnected()) {
            android.os.Debug.waitForDebugger()
        }
        Log.d(TAG, "onCreate() called")
    }

    override fun onStartCommand(intent: Intent, flags:Int, startId:Int):Int {
        return Service.START_NOT_STICKY
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "listener connected")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.d(TAG, "listener disconnected")
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        Log.d(TAG, "onNotificationPosted")
        val map = sbn.notification.extras
        val category = sbn.notification.category ?: ""
        when (category) {
            CATEGORY_EVENT-> {}
            CATEGORY_MESSAGE-> {}
            else -> return
        }
        val title = map.getString(EXTRA_TITLE) ?: ""
        val text = map.getString(EXTRA_TEXT) ?: ""
        val message = "[$category][$title][$text]"

        Log.d(TAG, message)
        NotificationQueue.add(message)
    }

    companion object
}