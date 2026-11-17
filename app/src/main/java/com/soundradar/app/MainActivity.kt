package com.soundradar.app

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
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
    private var leScanner: BluetoothLeScanner? = null

    private val leCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            addDevice(result.device, "BLE")
        }
        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            results.forEach { addDevice(it.device, "BLE") }
        }
        override fun onScanFailed(errorCode: Int) {
            status.text = "Escaneo BLE no disponible (código $errorCode). Sigue la búsqueda clásica."
        }
    }

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
                    if (device != null) addDevice(device, "Clásico")
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    status.text = "Búsqueda clásica terminada. Dispositivos: ${found.size}. El escaneo BLE sigue activo unos segundos."
                    updateResults()
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
        layout.addView(label("Escáner Bluetooth • Clásico + BLE", 14f))
        status = label("Listo. Activa Bluetooth y pulsa buscar.", 15f)
        layout.addView(status)
        layout.addView(Button(this).apply {
            text = "BUSCAR DISPOSITIVOS"
            setOnClickListener { startScan() }
        })
        layout.addView(Button(this).apply {
            text = "DETENER BÚSQUEDA"
            setOnClickListener { stopScanning(); status.text = "Búsqueda detenida." }
        })
        layout.addView(label("Emparejados = guardados en tu teléfono. Cercanos = visibles durante el escaneo. No todos los equipos anuncian su presencia.", 13f))
        results = label("Aquí aparecerán los dispositivos detectados.", 14f)
        layout.addView(results)
        setContentView(ScrollView(this).apply { addView(layout) })
    }

    private fun addDevice(device: BluetoothDevice, source: String) {
        val name = try { device.name ?: "Nombre oculto" } catch (_: SecurityException) { "Nombre protegido" }
        val address = try { device.address ?: "Dirección protegida" } catch (_: SecurityException) { "Dirección protegida" }
        found.add("$name  •  $address  •  $source")
        updateResults()
    }

    private fun updateResults() {
        results.text = if (found.isEmpty()) "Aún no se detectan dispositivos. Acerca la bocina, confirma que esté encendida y en modo de emparejamiento." else found.joinToString("\n\n")
    }

    private fun startScan() {
        val needed = if (Build.VERSION.SDK_INT >= 31) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        val missing = needed.filter { ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), requestCode)
            status.text = "Concede los permisos Bluetooth solicitados y vuelve a buscar."
            return
        }
        val adapter = (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter
        if (adapter == null || !adapter.isEnabled) {
            status.text = "Bluetooth está apagado. Actívalo y vuelve a buscar."
            return
        }
        found.clear()
        results.text = "Preparando búsqueda..."
        try {
            // Include devices already paired with this phone.
            adapter.bondedDevices.forEach { addDevice(it, "Emparejado") }
            if (adapter.isDiscovering) adapter.cancelDiscovery()
            val started = adapter.startDiscovery()
            status.text = if (started) "Buscando equipos clásicos y BLE durante unos segundos..." else "Android no inició la búsqueda clásica. Comprueba permisos y vuelve a intentar."
            leScanner = adapter.bluetoothLeScanner
            leScanner?.startScan(null, ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), leCallback)
            status.append(" Escaneo BLE iniciado.")
        } catch (_: SecurityException) {
            status.text = "Permiso Bluetooth denegado. Abre Ajustes > Aplicaciones > SoundRadar > Permisos."
        } catch (e: Exception) {
            status.text = "No se pudo iniciar el escaneo: ${e.message ?: "error desconocido"}"
        }
    }

    private fun stopScanning() {
        try { leScanner?.stopScan(leCallback) } catch (_: Exception) { }
        try {
            val adapter = (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter
            if (adapter?.isDiscovering == true) adapter.cancelDiscovery()
        } catch (_: Exception) { }
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED)
        else {
            @Suppress("DEPRECATION")
            registerReceiver(receiver, filter)
        }
    }

    override fun onStop() {
        stopScanning()
        try { unregisterReceiver(receiver) } catch (_: IllegalArgumentException) { }
        super.onStop()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == this.requestCode && grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) startScan()
        else status.text = "Permisos no concedidos. Autoriza los permisos Bluetooth para buscar."
    }
}
