package com.rayworks.servicedemo

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_UPDATE_CURRENT
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Message
import android.os.Process
import android.os.RemoteCallbackList
import android.os.RemoteException
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat

class RemoteService : Service() {
    /**
     * This is a list of callbacks that have been registered with the
     * service.  Note that this is package scoped (instead of private) so
     * that it can be accessed more efficiently from inner classes.
     */
    val mCallbacks = RemoteCallbackList<IRemoteServiceCallback>()

    var mValue = 0

    lateinit var mNM: NotificationManager

    override fun onCreate() {
        mNM = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // Display a notification about us starting.
        showNotification()
        // While this service is running, it will continually increment a
        // number.  Send the first message that is used to perform the
        // increment.
        mHandler.sendEmptyMessage(REPORT_MSG)
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        Log.i("LocalService", "Received start id $startId: $intent")
        return START_NOT_STICKY
    }

    override fun onDestroy() { // Cancel the persistent notification.
        mNM.cancel(R.string.remote_service_started)
        // Tell the user we stopped.
        Toast.makeText(this, R.string.remote_service_stopped, Toast.LENGTH_SHORT).show()
        // Unregister all callbacks.
        mCallbacks.kill()
        // Remove the next pending message to increment the counter, stopping
        // the increment loop.
        mHandler.removeMessages(REPORT_MSG)
    }

    override fun onBind(intent: Intent): IBinder? {
        // Select the interface to return.  If your service only implements
        // a single interface, you can just return it here without checking
        // the Intent.
        if (IRemoteService::class.java.name == intent.action) {
            return mBinder
        }
        return if (ISecondary::class.java.name == intent.action) {
            mSecondaryBinder
        } else null
    }

    /**
     * The IRemoteInterface is defined through IDL
     */
    private val mBinder: IRemoteService.Stub = object : IRemoteService.Stub() {
        override fun registerCallback(cb: IRemoteServiceCallback?) {
            if (cb != null) mCallbacks.register(cb)
        }

        override fun unregisterCallback(cb: IRemoteServiceCallback?) {
            if (cb != null) mCallbacks.unregister(cb)
        }
    }

    /**
     * A secondary interface to the service.
     */
    private val mSecondaryBinder: ISecondary.Stub = object : ISecondary.Stub() {
        override fun getPid(): Int = Process.myPid()

        override fun basicTypes(
            anInt: Int, aLong: Long, aBoolean: Boolean,
            aFloat: Float, aDouble: Double, aString: String?
        ) {
        }
    }

    override fun onTaskRemoved(rootIntent: Intent) {
        Toast.makeText(this, "Task removed: $rootIntent", Toast.LENGTH_LONG).show()
    }

    private val REPORT_MSG = 1

    /**
     * Our Handler used to execute operations on the main thread.  This is used
     * to schedule increments of our value.
     */
    @SuppressLint("HandlerLeak")
    private val mHandler: Handler =
        object : Handler() {
            override fun handleMessage(msg: Message) {
                when (msg.what) {
                    REPORT_MSG -> {
                        // Up it goes.
                        val value = ++mValue
                        // Broadcast to all clients the new value.
                        val N = mCallbacks.beginBroadcast()
                        var i = 0
                        while (i < N) {
                            try {
                                mCallbacks.getBroadcastItem(i).valueChanged(value)
                            } catch (e: RemoteException) {
                                // The RemoteCallbackList will take care of removing
                                // the dead object for us.
                            }
                            i++
                        }
                        mCallbacks.finishBroadcast()
                        // Repeat every 1 second.
                        sendMessageDelayed(obtainMessage(REPORT_MSG), 1 * 1000.toLong())
                    }
                    else -> super.handleMessage(msg)
                }
            }
        }

    /**
     * Show a notification while this service is running.
     */
    private fun showNotification() { // In this sample, we'll use the same text for the ticker and the expanded notification
        val text = getText(R.string.remote_service_started)
        // The PendingIntent to launch our activity if the user selects this notification
        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, Controller::class.java), FLAG_UPDATE_CURRENT
        )

        val channelId = "default_channel_id"
        val channelDescription = "Default Channel"

        //
        // NB: notification change for Oreo - notification channel
        // https://stackoverflow.com/questions/46990995/on-android-8-1-api-27-notification-does-not-display
        //
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_HIGH
            val notificationChannel = NotificationChannel(channelId, channelDescription, importance)
            notificationChannel.lightColor = Color.GREEN
            notificationChannel.enableVibration(true)
            mNM.createNotificationChannel(notificationChannel)
        }
        // Set the info for the views that show in the notification panel.
        val notification: Notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground) // the status icon
            .setTicker(text) // the status text
            .setWhen(System.currentTimeMillis()) // the time stamp
            .setContentTitle(getText(R.string.remote_service_label)) // the label of the entry
            .setContentText(text) // the contents of the entry
            .setContentIntent(contentIntent) // The intent to send when the entry is clicked
            .build()

        // Send the notification.
        // We use a string id because it is a unique number.  We use it later to cancel.
        mNM.notify(R.string.remote_service_started, notification)
    }
}