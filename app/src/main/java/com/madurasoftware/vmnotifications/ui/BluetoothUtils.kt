package com.madurasoftware.vmnotifications.ui

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.ArrayAdapter
import com.madurasoftware.vmnotifications.services.BluetoothService
import com.madurasoftware.vmnotifications.services.NotificationService

const val REQUEST_ENABLE_BT = 100
const val MESSAGE_READ = 2 // used in bluetooth handler to identify message update

// used in bluetooth handler to identify message status
const val CONNECTING_STATUS = 3
const val CONNECTING_STATUS_FAILED = -1
const val CONNECTING_STATUS_CONNECTED = 1
const val CONNECTING_STATUS_CONNECTING = 2
const val CONNECTING_STATUS_DISCONNECTING = 3
const val CONNECTING_STATUS_DISCONNECTED = 4
const val ACTION = "com.madurasoftware.vmnotifications.services"
const val CONNECTION_INFO = "ConnectionInfo"

fun isBluetoothSupported(): Boolean {
    if (BluetoothAdapter.getDefaultAdapter() == null) {
        return false
    }
    return true
}

private fun startBluetooth(activity: Activity): Boolean {
    if (!isBluetoothSupported()) {
        activity.finishAffinity()
        return false
    }
    if (!BluetoothAdapter.getDefaultAdapter()!!.isEnabled) {
        val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
        activity.startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
        return false
    } else {
        Log.d(ContentValues.TAG,"Bluetooth already enabled")
    }
    return true
}

fun listPairedDevices(activity: Activity, array: ArrayAdapter<String>):Boolean {
    if (!isBluetoothSupported()) {
        return false
    }
    try {
        if (!startBluetooth(activity)) {
            return false
        }
    } catch (e: Exception) {
        Log.d(ContentValues.TAG, "exception $e")
        return false
    }
    val pairedDevices = BluetoothAdapter.getDefaultAdapter()!!.getBondedDevices()
    for (device in pairedDevices!!) {
        array.add(device.getName() + "\n" + device.getAddress())
    }
    return true
}
fun connectToAddress(context: Context, info:String) {
    val notificationIntent = Intent(context, NotificationService::class.java)
    context.startService(notificationIntent)
    val connectIntent = Intent(context, BluetoothService::class.java)
    connectIntent.putExtra(BluetoothService.CONNECTION,info)
    context.startService(connectIntent)
}
