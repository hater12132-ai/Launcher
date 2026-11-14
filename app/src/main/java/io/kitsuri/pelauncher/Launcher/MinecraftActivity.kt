package io.kitsuri.pelauncher.Launcher

import android.content.res.AssetManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import com.mojang.minecraftpe.MainActivity
import org.conscrypt.Conscrypt
import java.security.Security

class MinecraftActivity : MainActivity() {

    private lateinit var gameManager: GamePackageManager

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            Log.d(TAG, "Initializing game manager...")
            gameManager = GamePackageManager.getInstance(applicationContext)
            Log.i(TAG, "Mode: ${if (gameManager.isBundledMode) "BUNDLED 1.26.60.4" else "INSTALLED ${gameManager.getVersionName()}"}")
            Log.i(TAG, "Assets: ${gameManager.assetsSource}")

            try {
                Security.insertProviderAt(Conscrypt.newProvider(), 1)
            } catch (e: Exception) {
                Log.w(TAG, "Conscrypt init failed: ${e.message}")
            }

            Log.d(TAG, "Loading native libraries...")
            gameManager.loadAllLibraries()

            val modsEnabled = intent.getBooleanExtra("MODS_ENABLED", false)
            if (!modsEnabled) {
                Log.d(TAG, "Loading game core (shin)...")
                System.loadLibrary("shin")
                val libPath = gameManager.getMinecraftPePath()
                Log.i(TAG, "Passing libminecraftpe.so to native: $libPath")
                nativeOnLauncherLoaded(libPath)
            }
            Log.i(TAG, "Game initialized, calling super.onCreate()")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize game", e)
            Toast.makeText(this, "Failed to load game: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        super.onCreate(savedInstanceState)
    }

    override fun getAssets(): AssetManager {
        return if (::gameManager.isInitialized) gameManager.getAssets() else super.getAssets()
    }

    private external fun nativeOnLauncherLoaded(libPath: String)

    companion object {
        private const val TAG = "MinecraftActivity"
    }
}
