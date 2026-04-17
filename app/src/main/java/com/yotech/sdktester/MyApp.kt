package com.yotech.sdktester

import android.app.Application
import com.yotech.valtprinter.sdk.ValtPrinterSdk

class MyApp: Application() {
    override fun onCreate() {
        super.onCreate()
        ValtPrinterSdk.init(this)
    }
}