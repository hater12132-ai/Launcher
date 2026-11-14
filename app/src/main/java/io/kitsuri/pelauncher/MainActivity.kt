package io.kitsuri.pelauncher

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import io.kitsuri.pelauncher.Launcher.GamePackageManager
import io.kitsuri.pelauncher.Launcher.MinecraftActivity

class MainActivity : ComponentActivity() {

    private var statusText by mutableStateOf("Checking…")

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { refreshStatus() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ensureStoragePermission()
        refreshStatus()
        setContent {
            MaterialTheme {
                Surface(modifier = androidx.compose.ui.Modifier.Modifier.fillMaxSize()) {
                    LaunchScreen()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun ensureStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    intent.data = Uri.parse("package:$packageName")
                    startActivity(intent)
                } catch (_: Exception) {
                    startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                }
            }
        } else {
            val need = arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ).filter {
                ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }
            if (need.isNotEmpty()) permissionLauncher.launch(need.toTypedArray())
        }
    }

    private fun refreshStatus() {
        statusText = try {
            val mgr = GamePackageManager.getInstance(applicationContext)
            buildString {
                append(if (mgr.isBundledMode) "Natives: bundled 1.26.60.4" else "Natives: installed ${mgr.getVersionName()}")
                append("\n")
                append("Assets: ${mgr.assetsSource}")
            }
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    @Composable
    private fun LaunchScreen() {
        Column(
            modifier = androidx.compose.ui.Modifier.Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Minimal Launcher", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = androidx.compose.ui.Modifier.Modifier.height(12.dp))
            Text(statusText, style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = androidx.compose.ui.Modifier.Modifier.height(8.dp))
            Text(
                "APKs folder:\n/storage/emulated/0/Idek/Minecraft.apks/",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = androidx.compose.ui.Modifier.Modifier.height(32.dp))
            Button(onClick = {
                try {
                    GamePackageManager.getInstance(applicationContext)
                    startActivity(Intent(this@MainActivity, MinecraftActivity::class.java).apply {
                        putExtra("MODS_ENABLED", false)
                    })
                } catch (e: Exception) {
                    Toast.makeText(this@MainActivity, "Cannot start: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }) {
                Text("Launch Minecraft 1.26.60.4")
            }
            Spacer(modifier = androidx.compose.ui.Modifier.Modifier.height(12.dp))
            Button(onClick = { ensureStoragePermission() }) {
                Text("Grant storage permission")
            }
        }
    }
}
