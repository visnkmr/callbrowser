package com.example.callbrowser

import android.app.Application
import visnkmr.apps.crasher.Crasher

class CallBrowserApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        // Initialize Crasher for crash reporting
        Crasher(this)
            .setEmail("support@example.com")
            .setDebugMessage("CallBrowser Debug Build")
            .setCrashActivityEnabled(true)
    }
}