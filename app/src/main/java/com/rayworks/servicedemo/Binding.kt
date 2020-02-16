package com.rayworks.servicedemo

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Message
import android.os.Process
import android.os.RemoteException
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class Binding : AppCompatActivity() {
    /** The primary interface we will be calling on the service.  */
    var mService: IRemoteService? = null
    /** Another interface we use on the service.  */
    var mSecondaryService: ISecondary? = null

    var mKillButton: Button? = null
    var mCallbackText: TextView? = null

    private var mIsBound = false

    /**
     * Standard initialization of this activity.  Set up the UI, then wait
     * for the user to poke it before doing anything.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.remote_service_binding)
        // Watch for button clicks.
        var button =
            findViewById<View>(R.id.bind) as Button
        button.setOnClickListener(mBindListener)
        button = findViewById<View>(R.id.unbind) as Button
        button.setOnClickListener(mUnbindListener)
        mKillButton = findViewById<View>(R.id.kill) as Button
        mKillButton!!.setOnClickListener(mKillListener)
        mKillButton!!.isEnabled = false
        mCallbackText = findViewById<View>(R.id.callback) as TextView
        mCallbackText!!.text = "Not attached."
    }

    /**
     * Class for interacting with the main interface of the service.
     */
    private val mConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(
            className: ComponentName,
            service: IBinder
        ) {
            // This is called when the connection with the service has been
            // established, giving us the service object we can use to
            // interact with the service.  We are communicating with our
            // service through an IDL interface, so get a client-side
            // representation of that from the raw service object.
            mService = IRemoteService.Stub.asInterface(service)
            mKillButton!!.isEnabled = true
            mCallbackText!!.text = "Attached."
            // We want to monitor the service for as long as we are
            // connected to it.
            try {
                mService?.registerCallback(mCallback)
            } catch (e: RemoteException) {
                // In this case the service has crashed before we could even
                // do anything with it; we can count on soon being
                // disconnected (and then reconnected if it can be restarted)
                // so there is no need to do anything here.
            }

            // As part of the sample, tell the user what happened.
            Toast.makeText(
                this@Binding, R.string.remote_service_connected,
                Toast.LENGTH_SHORT
            ).show()
        }

        override fun onServiceDisconnected(className: ComponentName) {
            // This is called when the connection with the service has been
            // unexpectedly disconnected -- that is, its process crashed.
            mService = null
            mKillButton!!.isEnabled = false
            mCallbackText!!.text = "Disconnected."
            // As part of the sample, tell the user what happened.
            Toast.makeText(
                this@Binding, R.string.remote_service_disconnected,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    /**
     * Class for interacting with the secondary interface of the service.
     */
    private val mSecondaryConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(
            className: ComponentName,
            service: IBinder
        ) {
            // Connecting to a secondary interface is the same as any
            // other interface.
            mSecondaryService = ISecondary.Stub.asInterface(service)
            mKillButton!!.isEnabled = true
        }

        override fun onServiceDisconnected(className: ComponentName) {
            mSecondaryService = null
            mKillButton!!.isEnabled = false
        }
    }

    private val mBindListener =
        View.OnClickListener {
            // Establish a couple connections with the service, binding
            // by interface names.  This allows other applications to be
            // installed that replace the remote service by implementing
            // the same interface.
            val intent = Intent(this@Binding, RemoteService::class.java)
            intent.action = IRemoteService::class.java.name
            bindService(intent, mConnection, Context.BIND_AUTO_CREATE)
            intent.action = ISecondary::class.java.name
            bindService(intent, mSecondaryConnection, Context.BIND_AUTO_CREATE)
            mIsBound = true
            mCallbackText!!.text = "Binding."
        }

    private val mUnbindListener =
        View.OnClickListener {
            if (mIsBound) {
                // If we have received the service, and hence registered with
                // it, then now is the time to unregister.
                if (mService != null) {
                    try {
                        mService!!.unregisterCallback(mCallback)
                    } catch (e: RemoteException) {
                        // There is nothing special we need to do if the service
                        // has crashed.
                    }
                }
                // Detach our existing connection.
                unbindService(mConnection)
                unbindService(mSecondaryConnection)
                mKillButton!!.isEnabled = false
                mIsBound = false
                mCallbackText!!.text = "Unbinding."
            }
        }

    private val mKillListener =
        View.OnClickListener {
            // To kill the process hosting our service, we need to know its
            // PID.  Conveniently our service has a call that will return
            // to us that information.
            if (mSecondaryService != null) {
                try {
                    val pid = mSecondaryService!!.pid
                    // Note that, though this API allows us to request to
                    // kill any process based on its PID, the kernel will
                    // still impose standard restrictions on which PIDs you
                    // are actually able to kill.  Typically this means only
                    // the process running your application and any additional
                    // processes created by that app as shown here; packages
                    // sharing a common UID will also be able to kill each
                    // other's processes.
                    Process.killProcess(pid)
                    mCallbackText!!.text = "Killed service process."
                } catch (ex: RemoteException) {
                    // Recover gracefully from the process hosting the server dying.
                    // Just for purposes of the sample, put up a notification.
                    Toast.makeText(
                        this@Binding,
                        R.string.remote_call_failed,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    /**
     * This implementation is used to receive callbacks from the remote
     * service.
     */
    private val mCallback: IRemoteServiceCallback = object : IRemoteServiceCallback.Stub() {
        /**
         * This is called by the remote service regularly to tell us about
         * new values.  Note that IPC calls are dispatched through a thread
         * pool running in each process, so the code executing here will
         * NOT be running in our main thread like most other things -- so,
         * to update the UI, we need to use a Handler to hop over there.
         */
        override fun valueChanged(value: Int) {
            mHandler.sendMessage(mHandler.obtainMessage(BUMP_MSG, value, 0))
        }
    }

    private val BUMP_MSG = 1

    @SuppressLint("HandlerLeak")
    private val mHandler: Handler =
        object : Handler() {
            override fun handleMessage(msg: Message) {
                when (msg.what) {
                    BUMP_MSG -> mCallbackText!!.text = "Received from service: " + msg.arg1
                    else -> super.handleMessage(msg)
                }
            }
        }
}