package io.kitsuri.pelauncher.Launcher

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.AssetManager
import android.os.Build
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile

/**
 * Minecraft 1.26.60.4 package manager.
 * - Natives: preferred from this APK (jniLibs/arm64-v8a)
 * - Assets: /storage/emulated/0/Idek/Minecraft.apks/ (base.apk + split_install_pack.apk)
 */
class GamePackageManager private constructor(private val context: Context) {

    private val packageContext: Context
    private val assetManager: AssetManager
    private val nativeLibDir: String
    val isBundledMode: Boolean
    val assetsSource: String

    private val knownPackages = arrayOf(
        "com.mojang.minecraftpe",
        "com.mojang.minecraftpe.beta",
        "com.mojang.minecraftpe.preview"
    )

    private val defaultApksFolder = "/storage/emulated/0/Idek/Minecraft.apks"

    private val assetApkNames = arrayOf(
        "base.apk",
        "split_install_pack.apk",
        "split_config.en.apk",
        "split_config.xhdpi.apk",
        "split_config.xxhdpi.apk",
        "split_config.xxxhdpi.apk",
        "split_config.ro.apk"
    )

    private val requiredLibs = arrayOf(
        "libc++_shared.so",
        "libfmod.so",
        "libHttpClient.Android.so",
        "libMediaDecoders_Android.so",
        "libconscrypt_jni.so",
        "libpairipcore.so",
        "libmaesdk.so",
        "libPlayFabMultiplayer.so",
        "libminecraftpe.so"
    )

    private val systemLoadLibs = arrayOf(
        "libpairipcore.so",
        "libmaesdk.so",
        "libPlayFabMultiplayer.so"
    )

    init {
        val bundledDir = File(context.applicationInfo.nativeLibraryDir)
        val bundledMc = File(bundledDir, "libminecraftpe.so")
        val hasBundled = bundledMc.exists() && bundledMc.length() > 1_000_000L

        if (hasBundled) {
            Log.i(TAG, "Bundled natives: 1.26.60.4")
            isBundledMode = true
            packageContext = context.applicationContext
            nativeLibDir = bundledDir.absolutePath
        } else {
            Log.i(TAG, "No bundled libminecraftpe.so – trying installed package")
            isBundledMode = false
            val packageName = detectGamePackage()
                ?: throw IllegalStateException(
                    "No bundled libs and no installed Minecraft."
                )
            packageContext = context.createPackageContext(
                packageName,
                Context.CONTEXT_IGNORE_SECURITY or Context.CONTEXT_INCLUDE_CODE
            )
            nativeLibDir = resolveNativeLibDirFromInstalled()
            extractLibrariesFromInstalled()
        }

        val (assets, source) = createAssetManagerWithSource()
        assetManager = assets
        assetsSource = source
        Log.i(TAG, "nativeLibDir=$nativeLibDir bundled=$isBundledMode assets=$assetsSource")
    }

    private fun detectGamePackage(): String? =
        knownPackages.firstOrNull { isPackageInstalled(it) }

    private fun isPackageInstalled(packageName: String): Boolean =
        try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }

    private fun resolveNativeLibDirFromInstalled(): String {
        val appInfo = packageContext.applicationInfo
        return if (appInfo.splitPublicSourceDirs?.isNotEmpty() == true) {
            val cacheLibDir = File(context.cacheDir, "lib/${getDeviceAbi()}")
            cacheLibDir.mkdirs()
            cacheLibDir.absolutePath
        } else {
            appInfo.nativeLibraryDir
        }
    }

    private fun getDeviceAbi(): String =
        Build.SUPPORTED_64_BIT_ABIS.firstOrNull {
            it.contains("arm64") || it.contains("x86_64")
        } ?: Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"

    private fun extractLibrariesFromInstalled() {
        val appInfo = packageContext.applicationInfo
        val sourceDirs = mutableListOf<String>()
        appInfo.sourceDir?.let { sourceDirs.add(it) }
        appInfo.splitSourceDirs?.forEach { sourceDirs.add(it) }
        appInfo.splitPublicSourceDirs?.forEach { sourceDirs.add(it) }
        val cacheLibDir = File(nativeLibDir)
        cacheLibDir.mkdirs()
        for (apkPath in sourceDirs) {
            try {
                ZipFile(apkPath).use { zip ->
                    val entries = zip.entries()
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        val name = entry.name
                        if (!name.startsWith("lib/") || !name.endsWith(".so")) continue
                        val parts = name.split("/")
                        if (parts.size < 3) continue
                        val abi = parts[1]
                        val libName = parts.last()
                        if (!abi.contains("arm64") && !abi.contains(getDeviceAbi())) continue
                        val outFile = File(cacheLibDir, libName)
                        if (outFile.exists() && outFile.length() == entry.size) continue
                        zip.getInputStream(entry).use { input ->
                            FileOutputStream(outFile).use { output -> input.copyTo(output) }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Extract failed $apkPath: ${e.message}")
            }
        }
    }

    private fun createAssetManagerWithSource(): Pair<AssetManager, String> {
        val assets = AssetManager::class.java.newInstance()
        val addAssetPath = AssetManager::class.java
            .getDeclaredMethod("addAssetPath", String::class.java)
            .also { it.isAccessible = true }

        val added = mutableListOf<String>()
        val folder = resolveApksFolder()
        if (folder != null) {
            for (name in assetApkNames) {
                val f = File(folder, name)
                if (f.exists() && f.canRead() && f.length() > 0) {
                    try {
                        addAssetPath.invoke(assets, f.absolutePath)
                        added.add(f.name)
                        Log.i(TAG, "Added asset path: ${f.absolutePath}")
                    } catch (e: Exception) {
                        Log.w(TAG, "addAssetPath failed for ${f.name}: ${e.message}")
                    }
                }
            }
        }

        if (added.isEmpty()) {
            val pkg = detectGamePackage()
            if (pkg != null) {
                try {
                    val ctx = context.createPackageContext(
                        pkg,
                        Context.CONTEXT_IGNORE_SECURITY or Context.CONTEXT_INCLUDE_CODE
                    )
                    val base = ctx.packageResourcePath
                    addAssetPath.invoke(assets, base)
                    added.add("installed:$pkg")
                    val split = base.replace("base.apk", "split_install_pack.apk")
                    if (File(split).exists()) {
                        addAssetPath.invoke(assets, split)
                        added.add("installed:split_install_pack")
                    }
                    ctx.applicationInfo.splitSourceDirs?.forEach { path ->
                        addAssetPath.invoke(assets, path)
                        added.add("installed:${File(path).name}")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Installed package assets failed: ${e.message}")
                }
            }
        }

        try {
            addAssetPath.invoke(assets, context.packageResourcePath)
        } catch (e: Exception) {
            Log.w(TAG, "Launcher assets failed: ${e.message}")
        }

        val source = when {
            added.any { it == "base.apk" || it == "split_install_pack.apk" } ->
                "folder:$folder (${added.joinToString()})"
            added.any { it.startsWith("installed:") } -> added.joinToString()
            else -> "NONE – put base.apk + split_install_pack.apk in $defaultApksFolder"
        }
        return assets to source
    }

    private fun resolveApksFolder(): File? {
        val candidates = listOf(
            File(defaultApksFolder),
            File("/storage/emulated/0/Idek/Minecraft.apks"),
            File("/sdcard/Idek/Minecraft.apks"),
            File(Environment.getExternalStorageDirectory(), "Idek/Minecraft.apks"),
            File(context.getExternalFilesDir(null), "Minecraft.apks")
        )
        for (dir in candidates) {
            if (dir.isDirectory) {
                val base = File(dir, "base.apk")
                val pack = File(dir, "split_install_pack.apk")
                if (base.exists() || pack.exists()) {
                    Log.i(TAG, "Using APKs folder: ${dir.absolutePath}")
                    return dir
                }
            }
        }
        Log.w(TAG, "No Minecraft.apks folder found")
        return null
    }

    fun loadLibrary(name: String): Boolean {
        val libFile = File(nativeLibDir, if (name.startsWith("lib")) name else "lib$name.so")
        val cleanName = name.removePrefix("lib").removeSuffix(".so")
        return try {
            when {
                libFile.exists() -> {
                    System.load(libFile.absolutePath)
                    Log.d(TAG, "Loaded $name from $nativeLibDir")
                }
                systemLoadLibs.contains(libFile.name) -> {
                    System.loadLibrary(cleanName)
                    Log.d(TAG, "Loaded $name as system library")
                }
                else -> {
                    System.loadLibrary(cleanName)
                    Log.d(TAG, "Loaded $name as system library (fallback)")
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load $name: ${e.message}")
            false
        }
    }

    fun loadAllLibraries() {
        requiredLibs.forEach { loadLibrary(it) }
    }

    fun getMinecraftPePath(): String =
        File(nativeLibDir, "libminecraftpe.so").absolutePath

    fun getAssets(): AssetManager = assetManager
    fun getPackageContext(): Context = packageContext
    fun getVersionName(): String? =
        if (isBundledMode) "1.26.60.4"
        else try {
            context.packageManager.getPackageInfo(packageContext.packageName, 0).versionName
        } catch (_: Exception) { null }

    companion object {
        private const val TAG = "GamePackageManager"
        @Volatile private var instance: GamePackageManager? = null
        fun getInstance(context: Context): GamePackageManager =
            instance ?: synchronized(this) {
                instance ?: GamePackageManager(context.applicationContext).also { instance = it }
            }
        fun isInitialized() = instance != null
    }
}
