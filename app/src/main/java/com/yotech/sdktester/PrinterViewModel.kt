package com.yotech.sdktester

import android.app.Application
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yotech.valtprinter.domain.model.PrintPayload
import com.yotech.valtprinter.domain.model.PrinterDevice
import com.yotech.valtprinter.domain.model.PrinterState
import com.yotech.valtprinter.sdk.ValtPrinterSdk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private val Application.dataStore by preferencesDataStore(name = "printer_prefs")

class PrinterViewModel(application: Application) : AndroidViewModel(application) {

    private val sdk = ValtPrinterSdk.get()

    val discoveredDevices: StateFlow<List<PrinterDevice>> = sdk.discoveredDevices
    val printerState: StateFlow<PrinterState> = sdk.printerState

    private val LAST_PRINTER_MAC = stringPreferencesKey("last_printer_mac")

    private var autoConnectJob: Job? = null

    init {
        // Try to auto-connect to the last remembered printer
        viewModelScope.launch {
            val lastMac = application.dataStore.data.map { preferences ->
                preferences[LAST_PRINTER_MAC]
            }.first()

            if (!lastMac.isNullOrEmpty()) {
                Log.d("PrinterViewModel", "Attempting auto-connect to: $lastMac")
                sdk.startScan()
                
                autoConnectJob = viewModelScope.launch {
                    try {
                        sdk.discoveredDevices.collect { devices ->
                            val match = devices.find { it.address == lastMac }
                            if (match != null) {
                                // Device found, stop scanning and connect
                                sdk.stopScan()
                                connect(match)
                                autoConnectJob?.cancel()
                            }
                        }
                    } catch (e: Exception) {
                        // Job cancelled
                    }
                }
                
                // Timeout auto-scan after 15 seconds
                viewModelScope.launch {
                    delay(15000)
                    autoConnectJob?.cancel()
                    sdk.stopScan()
                }
            }
        }
    }

    fun startScan() {
        // Only scan if not already connected
        if (printerState.value !is PrinterState.Connected) {
            sdk.startScan()
        }
    }

    fun stopScan() {
        sdk.stopScan()
    }

    fun connect(device: PrinterDevice) {
        viewModelScope.launch {
            // Save to DataStore
            getApplication<Application>().dataStore.edit { prefs ->
                prefs[LAST_PRINTER_MAC] = device.address
            }
            // Ask SDK to connect
            sdk.connect(device)
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            getApplication<Application>().dataStore.edit { prefs ->
                prefs.remove(LAST_PRINTER_MAC)
            }
            sdk.disconnect()
        }
    }

    fun printTestReceipt() {
        viewModelScope.launch(Dispatchers.IO) {
            val payload = PrintPayload.RawText("Test Receipt\nHello World!\n----------------\n\n\n\n")
            val jobId = "TEST-${System.currentTimeMillis()}"
            sdk.submitPrintJob(payload, jobId, true)
        }
    }
}
