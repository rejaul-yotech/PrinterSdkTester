package com.yotech.sdktester.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbManager
import android.util.Log
import com.yotech.valtprinter.sdk.ValtPrinterSdk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * App-level receiver to handle USB Plug-and-Play events using the SDK public API.
 */
class AppUsbReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onReceive(context: Context, intent: Intent) {
        val sdk = try { ValtPrinterSdk.get() } catch (e: Exception) { return }

        when (intent.action) {
            UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                Log.d("AppUsbReceiver", "USB Device Attached — triggering auto-connect")
                scope.launch {
                    sdk.autoConnectUsb()
                }
            }

            UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                Log.d("AppUsbReceiver", "USB Device Detached — forcing Idle state")
                // Physical detachment immediately closes the session and returns to Idle
                sdk.disconnect()
            }
        }
    }
}
