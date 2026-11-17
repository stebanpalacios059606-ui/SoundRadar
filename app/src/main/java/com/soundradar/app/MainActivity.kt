package com.soundradar.app

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.app.ActivityCompat

class MainActivity : Activity() {
    private val requestCode = 42
    private lateinit var status: TextView
    private lateinit var results: TextView
    private val found = linkedSetOf<String>()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= 33) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    if (device != null) {
                        val name = try { device.name ?: "Nombre oculto" } catch (_: SecurityException) { "Nombre protegido" }
                        val address = try { device.address } catch (_: SecurityException) { "Dirección protegida" }
                        found.add("$name  •  $address")
                        results.text = found.joinToString("\n\n")
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    status.text = "Búsqueda terminada. Dispositivos encontrados: ${found.size}"
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 48, 32, 24)
            setBackgroundColor(0xFF10131B.toInt())
        }
        fun label(value: String, size: Float) = TextView(this).apply {
            text = value
            textSize = size
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(0, 12, 0, 12)
        }
        layout.addView(label("SOUND RADAR", 26f))
        layout.addView(label("Escáner Bluetooth • Solo detección", 14f))
        status = label("Listo para buscar dispositivos cercanos.", 15f)
        layout.addView(status)
        layout.addView(Button(this).apply {
            text = "BUSCAR DISPOSITIVOS"
            setOnClickListener { startScan() }
        })
        layout.addView(label("La app detecta dispositivos visibles. No interfiere, desconecta ni apaga equipos ajenos.", 13f))
        results = label("Aquí aparecerán los dispositivos detectados.", 14f)
        layout.addView(results)
        setContentView(ScrollView(this).apply { addView(layout) })
    }

    private fun startScan() {
        val needed = if (Build.VERSION.SDK_INT >= 31) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        val missing = needed.filter {
            ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), requestCode)
            status.text = "Concede los permisos para continuar."
            return
        }
        val adapter = (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter
        if (adapter == null || !adapter.isEnabled) {
            status.text = "Activa Bluetooth para buscar."
            return
        }
        found.clear()
        results.text = "Buscando..."
        status.text = "Escaneando dispositivos Bluetooth clásicos..."
        try {
            if (adapter.isDiscovering) adapter.cancelDiscovery()
            adapter.startDiscovery()
        } catch (_: SecurityException) {
            status.text = "Falta permiso Bluetooth. Revisa los permisos de la app."
        }
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(receiver, filter)
        }
    }

    override fun onStop() {
        try { unregisterReceiver(receiver) } catch (_: IllegalArgumentException) { }
        super.onStop()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == this.requestCode && grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            startScan()
        } else {
            status.text = "Permisos no concedidos."
        }
    }
}
