package com.madurasoftware.vmnotifications.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.widget.Toast
import com.madurasoftware.vmnotifications.services.BluetoothService

class Receiver() : BroadcastReceiver() {

    private var handler: Handler? = null

    fun setHandler(handler: Handler) {
        this.handler = handler
    }

    fun getHandler():Handler {
        return handler!!
    }

    override fun onReceive(context: Context, intent: Intent) {

//        Toast.makeText(context, "Broadcast Intent Detected.",
//            Toast.LENGTH_LONG).show()
        val status = intent.extras.getInt(BluetoothService.CONNECTION_STATUS,-100)
        val info = intent.extras.getString(CONNECTION_INFO,"")
        getHandler().obtainMessage(CONNECTING_STATUS, status, -1, info)
            .sendToTarget()
    }
}