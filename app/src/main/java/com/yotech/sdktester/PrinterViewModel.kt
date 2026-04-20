package com.yotech.sdktester

import android.app.Application
import android.util.Log
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Coffee
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yotech.valtprinter.domain.model.PrintPayload
import com.yotech.valtprinter.domain.model.PrinterDevice
import com.yotech.valtprinter.domain.model.PrinterState
import com.yotech.valtprinter.domain.model.orderdata.BillingData
import com.yotech.valtprinter.domain.model.orderdata.OrderItem
import com.yotech.valtprinter.domain.model.orderdata.SubOrderItem
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
            val payloadRaw = PrintPayload.RawText("Test Receipt\nHello World!\n----------------\n\n\n\n")
//            val payloadKitchen = PrintPayload.Kitchen("Test Kitchen")
            val payload = PrintPayload.Billing(BillingData(
                    // Restaurant Identity (Dishoom Kensington)
                    restaurantName = "Dishoom Kensington",
                    restaurantPhone = "+44 20 7420 9325",
                    logoResId = Icons.Default.Coffee, // R.drawable.ic_dishoom_logo

                    // Address Information (Official UK Format)
                    addressLine1 = "4 Derry Street",
                    addressLine2 = null,               // No flat/suite needed for this building
                    locality = "Kensington",           // The London Borough/Area
                    city = "LONDON",                   // Post Town (Standardized to Uppercase)
                    region = "Greater London",
                    postalCode = "W8 5SE",
                    countryCode = "GB",

                    // Transaction Metadata
                    staffName = "Md. Rejaul Karim",
                    deviceName = "Sunmi V2 Pro",        // Common Android POS hardware
                    orderDeviceName = "Tablet-KDS-01",
                    timestamp = 1743602874000L,        // April 2, 2026
                    orderId = "DSH-9921",
                    orderTag = "Table 14",
                    orderReference = "CHK-55201",
                    orderType = "Dine In",

                    // Financials
                    currencyCode = "GBP",              // British Pound Sterling
                    paymentStatus = "Paid",
                    footerNote = "Optional 12.5% service charge added. Thank you!",

                    subtotal = 44.0,
                    serviceCharge = 5.50,              // 12.5% of 44.0
                    vatPercentage = 20.0,              // Standard UK VAT rate
                    isVatInclusive = true,             // Most UK restaurant menus are VAT inclusive
                    additionalCharge = 0.0,
                    bagFee = 0.0,
                    grandTotal = 49.50,

                    qrCodeContent = "https://www.dishoom.com/kensington/feedback",
                    items = listOf(
                        OrderItem(
                            id = "item_101",
                            name = "Chicken Ruby",
                            category = "Mains",
                            unitPrice = 14.50,
                            quantity = 2,
                            unitLabel = "portion",
                            subItems = listOf(
                                SubOrderItem(
                                    id = "mod_1",
                                    name = "Extra Spicy",
                                    unitPrice = 0.0,
                                    quantity = 1,
                                    unitLabel = ""
                                )
                            )
                        ),
                        OrderItem(
                            id = "item_202",
                            name = "Garlic Naan",
                            category = "Sides",
                            unitPrice = 4.50,
                            quantity = 3,
                            unitLabel = "pcs"
                        ),
                        OrderItem(
                            id = "item_303",
                            name = "Masala Chai",
                            category = "Drinks",
                            unitPrice = 3.50,
                            quantity = 2,
                            unitLabel = "cups"
                        )
                    )
                ))
            val jobId = "TEST-${System.currentTimeMillis()}"
            sdk.submitPrintJob(payload, jobId, true)
        }
    }
}
