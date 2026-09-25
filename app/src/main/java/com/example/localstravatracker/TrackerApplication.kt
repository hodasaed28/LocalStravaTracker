package com.example.localstravatracker

import android.app.Application
import androidx.preference.PreferenceManager
import com.example.localstravatracker.data.AppDatabase
import org.osmdroid.config.Configuration

class TrackerApplication : Application() {
    val database: AppDatabase by lazy { AppDatabase.getDatabase(this) }

    override fun onCreate() {
        super.onCreate()
        Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this))
        Configuration.getInstance().userAgentValue = packageName
    }
}