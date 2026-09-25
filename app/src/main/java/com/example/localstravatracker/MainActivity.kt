package com.example.localstravatracker

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.localstravatracker.data.Run
import com.example.localstravatracker.service.TrackingService
import com.example.localstravatracker.utils.GpxExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (!granted) {
            Toast.makeText(this, "يلزم تفعيل إذن الموقع لبدء التتبع", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkAndRequestPermissions()

        setContent {
            MaterialTheme {
                MainScreen(
                    onStartService = { startTrackingService(TrackingService.ACTION_START) },
                    onStopService = { startTrackingService(TrackingService.ACTION_STOP) }
                )
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val perms = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val needed = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }

    private fun startTrackingService(action: String) {
        val intent = Intent(this, TrackingService::class.java).apply {
            this.action = action
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(onStartService: () -> Unit, onStopService: () -> Unit) {
    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (selectedTab == 0) "متتبع الجري المباشر" else "سجل الأنشطة والتمارين") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White
                )
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.PlayArrow, contentDescription = "تتبع") },
                    label = { Text("التتبع") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.History, contentDescription = "السجل") },
                    label = { Text("السجل") }
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            if (selectedTab == 0) {
                TrackerView(onStartService, onStopService)
            } else {
                HistoryView()
            }
        }
    }
}

@Composable
fun TrackerView(onStartService: () -> Unit, onStopService: () -> Unit) {
    val isTracking by TrackingService.isTracking.collectAsState()
    val seconds by TrackingService.durationSeconds.collectAsState()
    val distance by TrackingService.distanceMeters.collectAsState()
    val speed by TrackingService.currentSpeedKmh.collectAsState()
    val points by TrackingService.pathPoints.collectAsState()

    var mapView by remember { mutableStateOf<MapView?>(null) }
    val polyline = remember {
        Polyline().apply {
            outlinePaint.color = android.graphics.Color.RED
            outlinePaint.strokeWidth = 10f
        }
    }

    LaunchedEffect(points.size) {
        mapView?.let { map ->
            if (points.isNotEmpty()) {
                val geoPoints = points.map { GeoPoint(it.latitude, it.longitude) }
                polyline.setPoints(geoPoints)
                if (!map.overlays.contains(polyline)) {
                    map.overlays.add(polyline)
                }
                map.controller.animateTo(geoPoints.last())
                map.invalidate()
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    MapView(context).apply {
                        setTileSource(TileSourceFactory.MAPNIK)
                        setMultiTouchControls(true)
                        controller.setZoom(16.5)
                        controller.setCenter(GeoPoint(30.0444, 31.2357))
                        mapView = this
                    }
                }
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                    StatBox("المسافة", String.format("%.2f كم", distance / 1000f))
                    val mins = seconds / 60
                    val secs = seconds % 60
                    StatBox("الوقت", "%02d:%02d".format(mins, secs))
                    StatBox("السرعة", String.format("%.1f كم/س", speed))
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = { if (isTracking) onStopService() else onStartService() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isTracking) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(if (isTracking) Icons.Default.Stop else Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isTracking) "إيقاف وحفظ النشاط" else "بدء النشاط الآن", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun StatBox(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
fun HistoryView() {
    val context = LocalContext.current
    val app = context.applicationContext as TrackerApplication
    val runs by app.database.runDao().getAllRuns().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    if (runs.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("لا توجد أنشطة مسجلة حتى الآن. ابدأ تمرينك الأول!", color = Color.Gray)
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize().padding(12.dp)) {
            items(runs) { run ->
                RunItemCard(
                    run = run,
                    onExportGpx = {
                        scope.launch {
                            val pts = app.database.runDao().getPointsForRun(run.id)
                            val gpx = GpxExporter.generateGpx(run, pts)
                            withContext(Dispatchers.Main) {
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/xml"
                                    putExtra(Intent.EXTRA_SUBJECT, "Run_${run.id}.gpx")
                                    putExtra(Intent.EXTRA_TEXT, gpx)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "مشاركة ملف GPX"))
                            }
                        }
                    },
                    onDelete = {
                        scope.launch { app.database.runDao().deleteRun(run.id) }
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
fun RunItemCard(run: Run, onExportGpx: () -> Unit, onDelete: () -> Unit) {
    val sdf = SimpleDateFormat("dd/MM/yyyy - hh:mm a", Locale.getDefault())
    val dateStr = sdf.format(Date(run.timestamp))

    Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(dateStr, fontWeight = FontWeight.Bold)
                Text(String.format("%.2f كم", run.distanceMeters / 1000f), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                val mins = (run.durationMillis / 1000) / 60
                val secs = (run.durationMillis / 1000) % 60
                Text("المدة: %02d:%02d".format(mins, secs), fontSize = 13.sp)
                Text("متوسط السرعة: %.1f كم/س".format(run.avgSpeedKmh), fontSize = 13.sp)
                Text("حرق: %d سعرة".format(run.caloriesBurned), fontSize = 13.sp)
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onDelete) { Text("حذف", color = Color.Red) }
                Spacer(modifier = Modifier.width(8.dp))
                FilledTonalButton(onClick = onExportGpx) { Text("تصدير GPX") }
            }
        }
    }
}