package com.madurasoftware.vmnotifications.ui

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.AsyncTask
import android.os.Handler
import android.os.ParcelUuid
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import com.madurasoftware.vmnotifications.R
import com.madurasoftware.vmnotifications.services.BLEService
import com.madurasoftware.vmnotifications.services.BluetoothService
import com.madurasoftware.vmnotifications.services.DeviceWrapper
import com.madurasoftware.vmnotifications.services.NotificationService
import java.util.*

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

private val TAG = "BluetoothUtils"
private var mScanning: Boolean = false
private val mHandler = Handler()
val SCAN_PERIOD:Long = 1000
internal var devicesDiscovered = TreeSet<DeviceWrapper>()
private var mLastDeviceConnected: DeviceWrapper = DeviceWrapper(DeviceWrapper.DeviceType.NONE,"","")


var bluetoothDevice: BluetoothDevice? = null

val btScanner = BluetoothAdapter.getDefaultAdapter().getBluetoothLeScanner()


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

fun listPairedDevices(activity: Activity, array: ArrayAdapter<DeviceWrapper>):Boolean {
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
    startScanning(array);
    val pairedDevices = BluetoothAdapter.getDefaultAdapter()!!.getBondedDevices()
    for (device in pairedDevices!!) {
        array.add(DeviceWrapper(DeviceWrapper.DeviceType.V4,device.name,device.address))
    }
    return true
}

fun connectToAddress(context: Context, d:DeviceWrapper) {
    val notificationIntent = Intent(context, NotificationService::class.java)
    context.startService(notificationIntent)
    mLastDeviceConnected = d
    if (d.deviceType == DeviceWrapper.DeviceType.BLE) {
        val connectIntent = Intent(context, BLEService::class.java)
        connectIntent.putExtra(BluetoothService.CONNECTION,d.address)
        context.startService(connectIntent)
    } else {
        val connectIntent = Intent(context, BluetoothService::class.java)
        connectIntent.putExtra(BluetoothService.CONNECTION,d.address)
        context.startService(connectIntent)
    }
}

fun sendMessageToLastConnection(context: Context, text:String) {

    when (mLastDeviceConnected.deviceType) {
        DeviceWrapper.DeviceType.BLE->{
            val connectIntent = Intent(context, BLEService::class.java)
            connectIntent.putExtra(BluetoothService.CONNECTION,"")
            connectIntent.putExtra(BluetoothService.MESSAGE,text)
            context.startService(connectIntent)
        }
        DeviceWrapper.DeviceType.V4 -> { val connectIntent = Intent(context, BluetoothService::class.java)
            connectIntent.putExtra(BluetoothService.CONNECTION,"")
            connectIntent.putExtra(BluetoothService.MESSAGE,text)
            context.startService(connectIntent)
        }
        DeviceWrapper.DeviceType.NONE-> {}
    }
}

// Device scan callback.
private val leScanCallback = object : ScanCallback() {
    override fun onScanResult(callbackType: Int, result: ScanResult) {
        val d = DeviceWrapper(DeviceWrapper.DeviceType.BLE,result.device.name,result.device.address)
//        Log.d(TAG, "discovered ${d}")
        if (devicesDiscovered.add(d)) {
            Log.d(TAG, "added ${d}")
        }
    }
}
private fun startScanning(array: ArrayAdapter<DeviceWrapper>) {
    Log.d(TAG, "starting BLE scan")
    devicesDiscovered.clear()
    AsyncTask.execute { btScanner.startScan(leScanCallback) }
    mHandler.postDelayed(Runnable { stopScanning(array) }, SCAN_PERIOD)
}

fun stopScanning(array: ArrayAdapter<DeviceWrapper>) {
    Log.d(TAG,"stopping BLE scan")
    AsyncTask.execute { btScanner.stopScan(leScanCallback) }
    array.addAll(devicesDiscovered)
    array.notifyDataSetChanged()
}