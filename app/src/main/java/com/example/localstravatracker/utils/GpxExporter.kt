package com.example.localstravatracker.utils

import com.example.localstravatracker.data.Run
import com.example.localstravatracker.data.TrackPoint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object GpxExporter {
    fun generateGpx(run: Run, points: List<TrackPoint>): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<gpx version=\"1.1\" creator=\"LocalStravaTracker\">\n")
        sb.append("  <trk>\n")
        sb.append("    <name>Run ${sdf.format(Date(run.timestamp))}</name>\n")
        sb.append("    <trkseg>\n")
        for (p in points) {
            sb.append("      <trkpt lat=\"${p.latitude}\" lon=\"${p.longitude}\">\n")
            sb.append("        <ele>${p.altitude}</ele>\n")
            sb.append("        <time>${sdf.format(Date(p.timestamp))}</time>\n")
            sb.append("      </trkpt>\n")
        }
        sb.append("    </trkseg>\n")
        sb.append("  </trk>\n")
        sb.append("</gpx>")
        return sb.toString()
    }
}