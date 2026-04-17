package com.yotech.sdktester

import android.app.Application
import androidx.work.Configuration
import com.yotech.valtprinter.sdk.ValtPrinterSdk

class MyApp : Application(), Configuration.Provider {
    
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()
            
    override fun onCreate() {
        super.onCreate()
        ValtPrinterSdk.init(this)
    }
}