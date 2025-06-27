package com.example.licenta.pages

import android.bluetooth.BluetoothSocket
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.licenta.bluetooth.BluetoothManager
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.delay

@Composable
fun ObdDataDialog(
    onDismiss: () -> Unit,
    bluetoothManager: BluetoothManager
) {
    var rpm by remember { mutableStateOf("Reading...") }
    var speed by remember { mutableStateOf("Reading...") }
    var fuel by remember { mutableStateOf("Reading...") }

    suspend fun sendCommand(
        command: String,
        inputStream: InputStream,
        outputStream: OutputStream
    ): String {
        try {
            outputStream.write((command + "\r").toByteArray())
            outputStream.flush()
            delay(200)
            val buffer = ByteArray(1024)
            val bytes = inputStream.read(buffer)
            return String(buffer, 0, bytes).replace("\r", "").replace("\n", "").trim()
        } catch (e: Exception) {
            return "Error"
        }
    }

    suspend fun parseRpm(response: String): String {
        val bytes = response.split(" ")
        return if (bytes.size >= 4) {
            val a = bytes[2].toInt(16)
            val b = bytes[3].toInt(16)
            val value = ((a * 256) + b) / 4
            "$value"
        } else "Invalid"
    }

    suspend fun parseSpeed(response: String): String {
        val bytes = response.split(" ")
        return if (bytes.size >= 3) {
            val value = bytes[2].toInt(16)
            "$value"
        } else "Invalid"
    }

    suspend fun parseFuel(response: String): String {
        val bytes = response.split(" ")
        return if (bytes.size >= 3) {
            val value = bytes[2].toInt(16)
            "$value"
        } else "Invalid"
    }

    suspend fun readObdData(socket: BluetoothSocket) {
        val inputStream = socket.inputStream
        val outputStream = socket.outputStream

        sendCommand("ATZ", inputStream, outputStream)
        delay(1000)
        sendCommand("ATE0", inputStream, outputStream)
        sendCommand("ATL0", inputStream, outputStream)
        sendCommand("ATS0", inputStream, outputStream)
        sendCommand("ATH0", inputStream, outputStream)
        sendCommand("ATSP0", inputStream, outputStream)

        while (bluetoothManager.isConnected()) {
            val rpmResp = sendCommand("010C", inputStream, outputStream)
            rpm = parseRpm(rpmResp)

            val speedResp = sendCommand("010D", inputStream, outputStream)
            speed = parseSpeed(speedResp)

            val fuelResp = sendCommand("015E", inputStream, outputStream)
            fuel = parseFuel(fuelResp)

            delay(1000)
        }
    }

    LaunchedEffect(bluetoothManager.isConnected()) {
        val socket = bluetoothManager.getSocket()
        if (socket != null && bluetoothManager.isConnected()) {
            readObdData(socket)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Close")
            }
        },
        title = { Text("Live OBD Data") },
        text = {
            Column(modifier = Modifier.padding(8.dp)) {
                Text("RPM: $rpm")
                Text("Speed: $speed km/h")
                Text("Fuel Consumption: $fuel L/h")
            }
        }
    )
}
