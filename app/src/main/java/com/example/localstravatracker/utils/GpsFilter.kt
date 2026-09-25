package com.example.localstravatracker.utils

import android.location.Location

object GpsFilter {
    fun isValid(location: Location): Boolean {
        if (location.isFromMockProvider) return false
        if (location.hasAccuracy() && location.accuracy > 25.0f) return false
        return true
    }
}