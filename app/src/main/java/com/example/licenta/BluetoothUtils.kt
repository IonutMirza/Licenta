package com.example.licenta.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import java.io.IOException
import java.util.*

class BluetoothManager(private val context: Context) {

    private val adapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var socket: BluetoothSocket? = null
    private var connectedDevice: BluetoothDevice? = null

    private val foundDevices = mutableListOf<BluetoothDevice>()
    private var scanCallback: ((List<BluetoothDevice>) -> Unit)? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isReceiverRegistered = false

    companion object {
        private val OBD_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb")
        private const val TAG = "BluetoothManager"
    }

    fun hasBluetoothPermissions(): Boolean {
        val connectPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED

        val scanPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.BLUETOOTH_SCAN
        ) == PackageManager.PERMISSION_GRANTED

        val locationPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        return connectPermission && scanPermission && locationPermission
    }

    @SuppressLint("MissingPermission")
    fun startScan(onDevicesFound: (List<BluetoothDevice>) -> Unit) {
        if (!hasBluetoothPermissions()) {
            Toast.makeText(context, "Permisiunile Bluetooth nu sunt acordate.", Toast.LENGTH_SHORT).show()
            onDevicesFound(emptyList())
            return
        }

        if (adapter == null || !adapter.isEnabled) {
            Toast.makeText(context, "Bluetooth nu este activat.", Toast.LENGTH_SHORT).show()
            onDevicesFound(emptyList())
            return
        }

        if (adapter.isDiscovering) {
            adapter.cancelDiscovery()
        }

        foundDevices.clear()
        scanCallback = onDevicesFound

        try {
            if (!isReceiverRegistered) {
                val filter = IntentFilter().apply {
                    addAction(BluetoothDevice.ACTION_FOUND)
                    addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
                }
                context.registerReceiver(bluetoothReceiver, filter)
                isReceiverRegistered = true
            }
            adapter.startDiscovery()
        } catch (e: SecurityException) {
            Log.e(TAG, "Permisiune refuzată la startScan", e)
            mainHandler.post {
                Toast.makeText(context, "Permisiune Bluetooth refuzată.", Toast.LENGTH_SHORT).show()
                onDevicesFound(emptyList())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Eroare la startScan", e)
            mainHandler.post {
                Toast.makeText(context, "Eroare la scanare Bluetooth.", Toast.LENGTH_SHORT).show()
                onDevicesFound(emptyList())
            }
        }
    }

    fun stopScan() {
        try {
            if (adapter?.isDiscovering == true) {
                adapter.cancelDiscovery()
            }
            if (isReceiverRegistered) {
                context.unregisterReceiver(bluetoothReceiver)
                isReceiverRegistered = false
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Permisiune refuzată la stopScan", e)
        } catch (e: Exception) {
            Log.e(TAG, "Eroare la stopScan", e)
        }
        scanCallback = null
    }

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    if (!hasBluetoothPermissions()) {
                        Log.w(TAG, "Permisiuni Bluetooth insuficiente în receiver")
                        return
                    }

                    val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    device?.let {
                        if (!foundDevices.any { it.address == device.address }) {
                            foundDevices.add(device)

                            // Verificare suplimentară de permisiuni pentru device.name și device.address
                            try {
                                val name = device.name ?: "Necunoscut"
                                val address = device.address ?: "Necunoscut"
                                Log.d(TAG, "Dispozitiv găsit: $name - $address")
                            } catch (e: SecurityException) {
                                Log.w(TAG, "Permisiune refuzată la accesarea device.name sau device.address", e)
                            }
                        }
                    }
                }

                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    try {
                        if (isReceiverRegistered) {
                            context.unregisterReceiver(this)
                            isReceiverRegistered = false
                        }
                    } catch (e: IllegalArgumentException) {
                        Log.w(TAG, "Receiver deja dezînregistrat sau context invalid.")
                    }

                    scanCallback?.let { callback ->
                        mainHandler.post { callback(foundDevices) }
                    }
                    scanCallback = null
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun connectToDevice(device: BluetoothDevice, onConnectionResult: (Boolean) -> Unit) {
        if (!hasBluetoothPermissions()) {
            Toast.makeText(context, "Permisiunile Bluetooth nu sunt acordate.", Toast.LENGTH_SHORT).show()
            onConnectionResult(false)
            return
        }

        Thread {
            try {
                adapter?.cancelDiscovery()

                val tmpSocket = device.createRfcommSocketToServiceRecord(OBD_UUID)
                tmpSocket.connect()
                socket = tmpSocket
                connectedDevice = device

                mainHandler.post { onConnectionResult(true) }
            } catch (e: SecurityException) {
                Log.e(TAG, "Permisiune refuzată: BLUETOOTH_CONNECT", e)
                mainHandler.post { onConnectionResult(false) }
            } catch (e: IOException) {
                Log.e(TAG, "Conectarea a eșuat", e)
                try {
                    socket?.close()
                } catch (closeException: IOException) {
                    Log.e(TAG, "Eroare la închiderea socket-ului", closeException)
                }
                socket = null
                connectedDevice = null
                mainHandler.post { onConnectionResult(false) }
            }
        }.start()
    }

    fun getSocket(): BluetoothSocket? = socket

    fun getConnectedDevice(): BluetoothDevice? = connectedDevice

    fun isConnected(): Boolean = socket?.isConnected == true

    fun closeConnection() {
        try {
            socket?.close()
        } catch (e: IOException) {
            Log.e(TAG, "Eroare la închiderea conexiunii", e)
        }
        socket = null
        connectedDevice = null
    }
}
