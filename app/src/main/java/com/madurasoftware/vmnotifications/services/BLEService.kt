package com.madurasoftware.vmnotifications.services

import android.app.Service
import android.bluetooth.*
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import com.madurasoftware.vmnotifications.ui.*
import java.nio.charset.Charset
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue


class BLEService: Service() {

    private val TAG = "BLEService"
    private val mBinder = LocalBinder()//Binder for Activity that binds to this Service
    private var mBluetoothManager: BluetoothManager? = null//BluetoothManager used to get the BluetoothAdapter
    private var mBluetoothGatt: BluetoothGatt? = null//BluetoothGatt controls the Bluetooth communication link
    private var mBluetoothDeviceAddress: String? = null//Address of the connected BLE device
    private val sendQueue: Queue<String>? = ConcurrentLinkedQueue<String>() //To be inited with sendQueue = new ConcurrentLinkedQueue<String>();
    @Volatile
    private var isWriting: Boolean = false
    private var characteristic: BluetoothGattCharacteristic? = null
    private val MAX_MESSAGE_SIZE = 19

    private val targetUUID = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb")


    private val mGattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {//Change in connection state

            if (newState == BluetoothProfile.STATE_CONNECTED) {//See if we are connected
                Log.i(TAG, "onConnectionStateChange connected $newState")
                gatt.discoverServices()
                mBluetoothGatt?.discoverServices()//Discover services on connected BLE device
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {//See if we are not connected
                Log.i(TAG, "onConnectionStateChange disconnected $newState")
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {              //BLE service discovery complete
            if (status == BluetoothGatt.GATT_SUCCESS) {                                 //See if the service discovery was successful
                Log.i(TAG, "onServicesDiscovered success: $status")
                figureCharacteristic(gatt)
            } else {                                                                     //Service discovery failed so log a warning
                Log.i(TAG, "onServicesDiscovered failed: $status")
            }
        }

        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) { //A request to Read has completed
            if (status == BluetoothGatt.GATT_SUCCESS) {
                //See if the read was successful
                Log.i(TAG, "onCharacteristicRead OK: $characteristic")
            } else {
                Log.i(TAG, "onCharacteristicRead: Error$status")
            }

        }

        //For information only. This application sends small packets infrequently and does not need to know what the previous write completed
        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) { //A request to Write has completed
            super.onCharacteristicWrite(gatt, characteristic, status)
            isWriting = false
            _send()
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic?) {
            if (characteristic != null && characteristic.properties == BluetoothGattCharacteristic.PROPERTY_NOTIFY) {
                Log.e(TAG, "onCharacteristicChanged")
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        if (android.os.Debug.isDebuggerConnected()) {
            android.os.Debug.waitForDebugger()
        }
        Log.d(TAG, "onCreate() called")
    }

    override fun onStartCommand(intent: Intent, flags:Int, startId:Int):Int {
        val address = intent.getStringExtra(BluetoothService.CONNECTION)
        if (android.os.Debug.isDebuggerConnected()) {
            android.os.Debug.waitForDebugger()
        }
        Log.d(TAG, "onStartCommand with address $address")
        while (connect(address)) {
            characteristic = figureCharacteristic(mBluetoothGatt!!)
            if (characteristic == null) {
                Thread.sleep(1000)
                continue
            }
            if (!processMessages()) {
                if (mBluetoothGatt != null) {                                                   //Check for existing BluetoothGatt connection
                    mBluetoothGatt!!.close()                                                     //Close BluetoothGatt coonection for proper cleanup
                    mBluetoothGatt = null    //@@                                                  //No longer have a BluetoothGatt connection
                }
                broadcastMessage(CONNECTING_STATUS_DISCONNECTED,address)
                break
            }
        }
        Log.d(TAG, "onStartCommand exiting")
        return Service.START_NOT_STICKY
    }

    private fun figureCharacteristic(gatt: BluetoothGatt): BluetoothGattCharacteristic? {
        for (service in gatt.services) {
            for (characteristic in service.characteristics) {
                if (characteristic.uuid == targetUUID) {
                    Log.d(TAG, "figureCharacteristic found uuid=${characteristic.uuid}")
                    return characteristic
                }
            }
        }
        return null
    }

    // An activity has bound to this service
    override fun onBind(intent: Intent): IBinder? {
        return mBinder                                                                 //Return LocalBinder when an Activity binds to this Service
    }

    // An activity has unbound from this service
    override fun onUnbind(intent: Intent): Boolean {

        if (mBluetoothGatt != null) {                                                   //Check for existing BluetoothGatt connection
            mBluetoothGatt!!.close()                                                     //Close BluetoothGatt coonection for proper cleanup
            mBluetoothGatt = null                                                      //No longer have a BluetoothGatt connection
        }

        return super.onUnbind(intent)
    }

    private fun broadcastMessage(message:Int, info:String) {
        val intent = Intent()
        intent.action = ACTION
        intent.flags = Intent.FLAG_INCLUDE_STOPPED_PACKAGES
        intent.putExtra(BluetoothService.CONNECTION_STATUS,message)
        intent.putExtra(CONNECTION_INFO,info)
        Log.d(TAG, "broadcasting ${BluetoothService.CONNECTION_STATUS} $message $info")
        sendBroadcast(intent)
    }

    private fun getBluetoothAdapter(): BluetoothAdapter {
        Log.d(TAG, "getBluetoothAdapter")
        var bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        while (bluetoothAdapter == null) {
            Thread.sleep(5000)
            bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        }
        Log.d(TAG, "getBluetoothAdapter: success")
        return bluetoothAdapter
    }

    private fun processMessages(): Boolean {
        while (true) {
            val message = NotificationQueue.take()// this will block until a message arrives
            Log.d(TAG, "dequeued message $message")
            if (message.contains("[poison]", false)) {
                return false // poison value found, terminate the loop
            }

            if (!sendMessage(message+'~')) {
                return true // tell caller to retry connection
            }
        }
    }

    private fun _send(): Boolean {
        if (sendQueue!!.isEmpty()) {
            Log.d("TAG", "_send(): EMPTY QUEUE")
            return false
        }
        val sending = sendQueue.poll()
        Log.d(TAG, "_send(): Sending: $sending")
        characteristic!!.value = sending.toByteArray(Charset.forName("UTF-8"))
        isWriting = true // Set the write in progress flag
        mBluetoothGatt!!.writeCharacteristic(characteristic)
        return true
    }

    private fun sendMessage(message: String):Boolean {
        Log.d(TAG, "sendMessage $message")
        var data = message
        while (data.length > MAX_MESSAGE_SIZE) {
            sendQueue!!.add(data.substring(0, MAX_MESSAGE_SIZE))
            data = data.substring(MAX_MESSAGE_SIZE)
        }
        sendQueue!!.add(data)
        if (!isWriting) _send()
        return true //0
    }

    private fun connect(address: String): Boolean {
        broadcastMessage(CONNECTING_STATUS_CONNECTING,address)
        try {
            mBluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            if (mBluetoothManager == null) {
                Log.d(TAG, "Bluetooth Manager is null")
                broadcastMessage(CONNECTING_STATUS_FAILED,address)
                return false
            }
            val bluetoothAdapter = getBluetoothAdapter()
            // Previously connected device.  Try to reconnect.
            if (mBluetoothDeviceAddress != null && address == mBluetoothDeviceAddress && mBluetoothGatt != null) {
                //See if there was previous connection to the device
                Log.i(TAG, "Trying to use an existing mBluetoothGatt for connection.")
                //See if we can connect with the existing BluetoothGatt to connect
                //Success
                //Were not able to connect
                val ret = mBluetoothGatt!!.connect()
                if (ret) {
                    Log.d(TAG, "Connected.")
                    broadcastMessage(CONNECTING_STATUS_CONNECTED,address)
                } else {
                    Log.d(TAG, "failed to connect.")
                    broadcastMessage(CONNECTING_STATUS_FAILED,address)
                }
                return ret
            }
            Log.d(TAG, "getting device")
            val device = bluetoothAdapter.getRemoteDevice(address)
            if (device == null) {
                //Check whether a device was returned
                Log.d(TAG, "failed to connect.")
                broadcastMessage(CONNECTING_STATUS_FAILED, address)
                return false      //Failed to find the device
            }
            //No previous device so get the Bluetooth device by referencing its address
            Log.d(TAG, "getting gatt")
            mBluetoothGatt = device.connectGatt(this, false, mGattCallback)                //Directly connect to the device so autoConnect is false
            mBluetoothDeviceAddress = address                                              //Record the address in case we need to reconnect with the existing BluetoothGatt
            if (mBluetoothGatt != null) {
                Log.d(TAG, "Connected.")
                broadcastMessage(CONNECTING_STATUS_CONNECTED,address)
            } else {
                Log.d(TAG, "failed to connect.")
                broadcastMessage(CONNECTING_STATUS_FAILED,address)
            }
            return true
        } catch (e: Exception) {
            Log.i(TAG, e.message)
        }

        return false
    }
    // A Binder to return to an activity to let it bind to this service
    inner class LocalBinder : Binder(){
        internal fun getService(): BLEService {
            return this@BLEService//Return this instance of BluetoothLeService so clients can call its public methods
        }
    }
}