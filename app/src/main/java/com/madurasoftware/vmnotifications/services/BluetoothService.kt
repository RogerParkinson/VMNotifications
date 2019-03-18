package com.madurasoftware.vmnotifications.services

import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.madurasoftware.vmnotifications.ui.*
import java.io.IOException
import java.io.OutputStream
import java.util.*

class BluetoothService : Service() {

    private val TAG = "BluetoothService"
    private val BASE_UUID = "00001101-0000-1000-8000-00805F9B34FB"

    override fun onCreate() {
        super.onCreate()
        if (android.os.Debug.isDebuggerConnected()) {
            android.os.Debug.waitForDebugger()
        }
        Log.d(TAG, "onCreate() called")
    }

    override fun onStartCommand(intent: Intent, flags:Int, startId:Int):Int {
        val info = intent.getStringExtra(CONNECTION)
        Log.d(TAG, "onStartCommand")
        connectToAddress(info)
        return Service.START_NOT_STICKY
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
    }

    private fun broadcastMessage(message:Int, info:String) {
        val intent = Intent()
        intent.action = ACTION
        intent.flags = Intent.FLAG_INCLUDE_STOPPED_PACKAGES
        intent.putExtra(CONNECTION_STATUS,message)
        intent.putExtra(CONNECTION_INFO,info)
        Log.d(TAG, "broadcasting $CONNECTION_STATUS $message $info")
        sendBroadcast(intent)
    }

    companion object {
        const val CONNECTION = "Connection"
        const val CONNECTION_STATUS = "Connection Status"

    }

    @Throws(IOException::class)
    private fun createBluetoothSocket(device: BluetoothDevice, uuid: UUID): BluetoothSocket {
        try {
            val m = device.javaClass.getMethod("createInsecureRfcommSocketToServiceRecord", UUID::class.java)
            return m.invoke(device, uuid) as BluetoothSocket
        } catch (e: Exception) {
            Log.e(TAG, "Could not create Insecure RFComm Connection", e)
            broadcastMessage(CONNECTING_STATUS_FAILED,"")
        }
        return device.createRfcommSocketToServiceRecord(uuid)
    }

    // If the bluetooth adapter returns null it means BT is turned off.
    // We loop and wait for it to come back on again.
    private fun getBluetoothAdapter(): BluetoothAdapter {

        var bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        while (bluetoothAdapter == null) {
            Thread.sleep(5000)
            bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        }
        return bluetoothAdapter!!
    }

    private fun connectToAddress(info:String) {
        val address = info.substring(info.length - 17)
        var btSocket: BluetoothSocket?
        var keepRunning = true

        while (keepRunning) {
            var fail = false
            Log.d(TAG, "Connecting...")
            broadcastMessage(CONNECTING_STATUS_CONNECTING,info)
            val bluetoothAdapter = getBluetoothAdapter()
            val device = bluetoothAdapter.getRemoteDevice(address)

            try {
                btSocket = createBluetoothSocket(
                    device,
                    UUID.fromString(BASE_UUID)
                )
            } catch (e: IOException) {
                Log.e(TAG, "Socket creation failed", e)
                broadcastMessage(CONNECTING_STATUS_FAILED,info)
                return
            }

            // Establish the Bluetooth socket connection.
            try {
                btSocket.connect()
            } catch (e: IOException) {
                try {
                    Log.e(TAG, "Socket connection failed", e)
                    fail = true
                    broadcastMessage(CONNECTING_STATUS_FAILED,info)
                    closeSocket(btSocket)
                } catch (e2: IOException) {
                    Log.e(TAG, "Socket close failed", e2)
                } finally {
                    btSocket = null
                }

            }

            if (!fail) {
                try {
                    val outStream: OutputStream = btSocket.outputStream
                    Log.d(TAG, "Connected")
                    broadcastMessage(CONNECTING_STATUS_CONNECTED,info)
                    while (keepRunning) {
                        val message = NotificationQueue.take()// this will block until a message arrives
                        Log.d(TAG, "dequeued message $message")
                        if (message.contains("[poison]", false)) {
                            keepRunning = false
                            break // poison value found, terminate the loop
                        }
                        val bytes = message.toByteArray()
                        try {
                            outStream.write(bytes)
                        } catch (e2: IOException) {
                            NotificationQueue.add(message)
                            closeSocket(btSocket)
                            Thread.sleep(5000)
                            continue // this should re-conect and retry
                        }
                        Log.d(TAG, "sent message")
                    }
                    Log.d(TAG, "disconnecting")

                    broadcastMessage(CONNECTING_STATUS_DISCONNECTING,info)
                    closeSocket(btSocket) // cleanup the connection
                    btSocket = null
                    broadcastMessage(CONNECTING_STATUS_DISCONNECTED,info)

                } catch (e2: IOException) {
                    Log.e(TAG, "IO Exception", e2)
                    closeSocket(btSocket)
                    btSocket = null
                    keepRunning = false
                }
            }
        }
    }

    private fun closeSocket(btSocket: BluetoothSocket?)  {
        if (btSocket != null) {
            try {
                btSocket.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }
}