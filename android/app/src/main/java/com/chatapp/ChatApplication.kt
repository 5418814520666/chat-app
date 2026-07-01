package com.chatapp

import android.app.Application
import android.os.StrictMode
import android.util.Log

class ChatApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectAll()
                .penaltyLog()
                .build()
        )

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("ChatAppCrash", "FATAL: ${throwable.message}", throwable)
            val msg = throwable.message ?: "未知错误"
            try {
                android.os.Looper.prepare()
                android.widget.Toast.makeText(this, "崩溃: $msg", android.widget.Toast.LENGTH_LONG).show()
            } catch (_: Exception) {}
            android.os.Process.killProcess(android.os.Process.myPid())
        }
    }
}
