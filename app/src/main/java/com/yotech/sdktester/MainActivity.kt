package com.yotech.sdktester

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.yotech.sdktester.ui.theme.SDKTesterTheme
import com.yotech.valtprinter.domain.model.PrinterState
import com.yotech.valtprinter.sdk.PrintJobCallback
import com.yotech.valtprinter.sdk.ValtPrinterSdk

class MainActivity : ComponentActivity(), PrintJobCallback {

    private val viewModel: PrinterViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val requestPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val allGranted = permissions.entries.all { it.value }
            if (allGranted) {
                // Permissions granted
            } else {
                Toast.makeText(this, "Bluetooth/Location permissions required", Toast.LENGTH_SHORT).show()
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requestPermissionLauncher.launch(arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT))
        } else {
            requestPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }

        setContent {
            SDKTesterTheme {
                val view = androidx.compose.ui.platform.LocalView.current
                DisposableEffect(view) {
                    ValtPrinterSdk.get().setCaptureView(view)
                    onDispose {
                        ValtPrinterSdk.get().clearCaptureView()
                    }
                }
                Scaffold { innerPadding ->
                    PrinterScreen(
                        viewModel = viewModel,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        ValtPrinterSdk.get().registerCallback(this)
    }

    override fun onPause() {
        super.onPause()
        ValtPrinterSdk.get().unregisterCallback(this)
    }

    override fun onJobSuccess(jobId: String) {
        runOnUiThread { Toast.makeText(this, "Print Success: $jobId", Toast.LENGTH_SHORT).show() }
    }

    override fun onJobFailed(jobId: String, reason: String) {
        runOnUiThread { Toast.makeText(this, "Print Failed: $jobId - $reason", Toast.LENGTH_SHORT).show() }
    }
}

@Composable
fun PrinterScreen(viewModel: PrinterViewModel, modifier: Modifier = Modifier) {
    val printerState by viewModel.printerState.collectAsState()
    val devices by viewModel.discoveredDevices.collectAsState()

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        
        // Status Banner
        val statusText = when (val state = printerState) {
            is PrinterState.Idle -> "Idle"
            is PrinterState.Scanning -> "Scanning..."
            is PrinterState.Connecting -> "Connecting to ${state.deviceName}..."
            is PrinterState.Connected -> "Connected to ${state.device.name ?: state.device.address}"
            is PrinterState.Reconnecting -> "Reconnecting to ${state.deviceName}..."
            is PrinterState.Error -> "Error: ${state.message}"
            is PrinterState.AutoConnecting -> "Auto-Connecting..."
            else -> "Unknown State"
        }
        
        val statusColor = when (printerState) {
            is PrinterState.Connected -> Color(0xFF4CAF50)
            is PrinterState.Error -> Color(0xFFF44336)
            is PrinterState.Idle -> Color(0xFF9E9E9E)
            else -> Color(0xFFFFC107)
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(statusColor)
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "State: $statusText",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(onClick = { viewModel.startScan() }) {
                Text("Scan")
            }
            
            Button(
                onClick = { viewModel.printTestReceipt() },
                enabled = printerState is PrinterState.Connected
            ) {
                Text("Print Test")
            }

            Button(
                onClick = { viewModel.disconnect() },
                enabled = printerState is PrinterState.Connected || printerState is PrinterState.Reconnecting
            ) {
                Text("Disconnect")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "Discovered Devices", style = MaterialTheme.typography.titleMedium)
        HorizontalDivider()

        // Device List
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(devices) { device ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable { viewModel.connect(device) }
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(text = device.name ?: "Unknown Device", style = MaterialTheme.typography.bodyLarge)
                        Text(text = device.address, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}