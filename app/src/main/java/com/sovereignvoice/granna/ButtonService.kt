package com.sovereignvoice.granna

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.ParcelUuid
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import java.util.UUID

/**
 * Foreground service that:
 *  1. Advertises as a BLE peripheral named "GrannaListener"
 *  2. Waits for the ESP32 button device to connect and send a "PRESS" notification
 *  3. Launches DialerActivity when the button is pressed
 *
 * ESP32 side: connects to this service and writes to the BUTTON_CHAR characteristic.
 */
class ButtonService : Service() {

    companion object {
        private const val TAG = "ButtonService"
        const val CHANNEL_ID = "granna_service"

        // BLE UUIDs — must match the ESP32 firmware
        val SERVICE_UUID: UUID = UUID.fromString("12345678-1234-1234-1234-123456789abc")
        val BUTTON_CHAR_UUID: UUID = UUID.fromString("12345678-1234-1234-1234-123456789abd")
    }

    private var bluetoothManager: BluetoothManager? = null
    private var gattServer: BluetoothGattServer? = null
    private var advertiser: BluetoothLeAdvertiser? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(1, buildNotification(), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
            } else {
                startForeground(1, buildNotification())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground service", e)
        }
        startBleServer()
    }

    private fun startBleServer() {
        bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = bluetoothManager?.adapter

        if (adapter == null || !adapter.isEnabled) {
            Log.w(TAG, "Bluetooth not available or disabled")
            broadcastStatus("Bluetooth Disabled")
            return
        }

        // Stop any existing advertising first to avoid Error 1 (ALREADY_STARTED)
        adapter.bluetoothLeAdvertiser?.stopAdvertising(advertiseCallback)

        // Set up GATT server
        if (gattServer == null) {
            gattServer = bluetoothManager?.openGattServer(this, gattServerCallback)
            val service = BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
            val buttonChar = BluetoothGattCharacteristic(
                BUTTON_CHAR_UUID,
                BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
                BluetoothGattCharacteristic.PERMISSION_WRITE
            )
            val descriptor = BluetoothGattDescriptor(
                UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"),
                BluetoothGattDescriptor.PERMISSION_WRITE or BluetoothGattDescriptor.PERMISSION_READ
            )
            buttonChar.addDescriptor(descriptor)
            service.addCharacteristic(buttonChar)
            gattServer?.addService(service)
        }

        // Start advertising with Device Name
        try {
            adapter.name = "GrannaListener"
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied to set adapter name", e)
        }

        advertiser = adapter.bluetoothLeAdvertiser
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(true)
            .setTimeout(0)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .build()

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false) // UUID in main packet
            .addServiceUuid(ParcelUuid(SERVICE_UUID))
            .build()

        val scanResponse = AdvertiseData.Builder()
            .setIncludeDeviceName(true) // Name in scan response
            .build()

        advertiser?.startAdvertising(settings, data, scanResponse, advertiseCallback)
        Log.i(TAG, "BLE GATT server started, advertising as GrannaListener")
    }

    private val gattServerCallback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            Log.i(TAG, "BLE connection state: $newState from ${device.address}")
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                broadcastStatus("BLE Connected: ${device.address}")
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                broadcastStatus("BLE Disconnected")
            }
        }

        override fun onServiceAdded(status: Int, service: BluetoothGattService?) {
            Log.i(TAG, "onServiceAdded: status=$status, service=${service?.uuid}")
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice, requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean, responseNeeded: Boolean,
            offset: Int, value: ByteArray
        ) {
            if (characteristic.uuid == BUTTON_CHAR_UUID) {
                val msg = String(value).trim()
                Log.i(TAG, "Button event received: '$msg' from ${device.address}")
                if (msg == "PRESS") {
                    onButtonPressed()
                }
            }
            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
            }
        }
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            Log.i(TAG, "BLE advertising started")
            broadcastStatus("Advertising: GrannaListener")
        }
        override fun onStartFailure(errorCode: Int) {
            Log.e(TAG, "BLE advertising failed: $errorCode")
            broadcastStatus("Advertising failed: $errorCode")
        }
    }

    private fun broadcastStatus(status: String) {
        val intent = Intent("com.sovereignvoice.granna.STATUS_UPDATE")
        intent.putExtra("status", status)
        sendBroadcast(intent)
    }

    private fun onButtonPressed() {
        Log.i(TAG, "Button pressed — waking screen and launching DialerActivity")
        
        // Wake the screen
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = pm.newWakeLock(
            PowerManager.FULL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
            "Granna:WakeLock"
        )
        wakeLock.acquire(3000) // Wake for 3 seconds to let Activity take over

        val intent = Intent(this, DialerActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
    }

    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Granna is active")
            .setContentText("Waiting for button press...")
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "Granna Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Keeps Granna running in background" }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        advertiser?.stopAdvertising(advertiseCallback)
        gattServer?.close()
        super.onDestroy()
    }
}
