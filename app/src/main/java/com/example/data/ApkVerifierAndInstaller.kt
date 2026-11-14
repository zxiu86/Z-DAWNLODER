package com.example.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import com.example.data.model.ApkLocalInspection
import com.example.data.model.CompatibilityResult
import com.example.data.model.DeviceInfo
import com.example.data.model.YoutubeMetadata
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.ZipFile

object ApkVerifierAndInstaller {

  fun getApkStorageFile(context: Context): File {
    val dir = File(context.filesDir, "apks")
    if (!dir.exists()) {
      dir.mkdirs()
    }
    return File(dir, "youtube.apk")
  }

  fun readDeviceInfo(): DeviceInfo {
    val abis = Build.SUPPORTED_ABIS?.toList()?.filter { it.isNotBlank() }.orEmpty()
    return DeviceInfo(
        manufacturer = Build.MANUFACTURER.orEmpty().replaceFirstChar { it.uppercase(Locale.US) },
        model = Build.MODEL.orEmpty(),
        androidVersion = Build.VERSION.RELEASE.orEmpty(),
        sdkInt = Build.VERSION.SDK_INT,
        supportedAbis = abis,
    )
  }

  @Suppress("DEPRECATION")
  fun isNetworkAvailable(context: Context): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      val network = cm.activeNetwork ?: return false
      val caps = cm.getNetworkCapabilities(network) ?: return false
      caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    } else {
      val info = cm.activeNetworkInfo
      info != null && info.isConnected
    }
  }

  fun computeSha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(16 * 1024)
    FileInputStream(file).use { input ->
      while (true) {
        val read = input.read(buffer)
        if (read == -1) break
        digest.update(buffer, 0, read)
      }
    }
    return digest.digest().joinToString("") { "%02x".format(Locale.US, it) }
  }

  fun inspectApk(context: Context, file: File): ApkLocalInspection {
    if (!file.exists() || !file.isFile || file.length() < 64L) {
      return ApkLocalInspection(isValidApk = false, errorReason = "ملف APK غير موجود أو فارغ.")
    }

    // Verify ZIP magic bytes ("PK") so HTML error pages or corrupt downloads are caught immediately.
    val header = ByteArray(2)
    FileInputStream(file).use { input ->
      if (input.read(header) != 2 || header[0] != 0x50.toByte() || header[1] != 0x4B.toByte()) {
        return ApkLocalInspection(
            isValidApk = false,
            errorReason = "الملف ليس حزمة APK صالحة (ترويسة ZIP غير صحيحة).",
        )
      }
    }

    val nativeAbis = linkedSetOf<String>()
    var hasManifest = false
    try {
      ZipFile(file).use { zip ->
        val entries = zip.entries()
        while (entries.hasMoreElements()) {
          val entryName = entries.nextElement().name
          if (entryName == "AndroidManifest.xml") {
            hasManifest = true
          } else if (entryName.startsWith("lib/") && entryName.endsWith(".so")) {
            val segments = entryName.split('/')
            if (segments.size >= 3 && segments[1].isNotBlank()) {
              nativeAbis.add(segments[1])
            }
          }
        }
      }
    } catch (e: Exception) {
      return ApkLocalInspection(
          isValidApk = false,
          errorReason = "ملف APK تالف ولا يمكن قراءة محتوياته المضغوطة.",
      )
    }

    if (!hasManifest) {
      return ApkLocalInspection(
          isValidApk = false,
          errorReason = "ملف APK يفتقد AndroidManifest.xml ولا يصلح للتثبيت.",
      )
    }

    val packageInfo = getArchivePackageInfo(context.packageManager, file.absolutePath)
        ?: return ApkLocalInspection(
            isValidApk = false,
            errorReason = "تعذّر على نظام Android تحليل حزمة APK — قد يكون الملف تالفاً.",
        )

    val minSdk =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
          packageInfo.applicationInfo?.minSdkVersion
        } else {
          null
        }

    return ApkLocalInspection(
        isValidApk = true,
        packageName = packageInfo.packageName,
        versionName = packageInfo.versionName,
        versionCode = PackageInfoCompat.getLongVersionCode(packageInfo),
        minSdk = minSdk,
        nativeAbis = nativeAbis.toList(),
    )
  }

  @Suppress("DEPRECATION")
  private fun getArchivePackageInfo(pm: PackageManager, archivePath: String): PackageInfo? =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        pm.getPackageArchiveInfo(archivePath, PackageManager.PackageInfoFlags.of(0))
      } else {
        pm.getPackageArchiveInfo(archivePath, 0)
      }

  fun checkCompatibility(
      device: DeviceInfo,
      metadata: YoutubeMetadata,
      localInspection: ApkLocalInspection? = null,
  ): CompatibilityResult {
    val effectiveMinSdk = localInspection?.minSdk ?: metadata.minSdk
    if (device.sdkInt < effectiveMinSdk) {
      return CompatibilityResult(
          isCompatible = false,
          summary = "غير متوافق (Android قديم)",
          reason =
              "يتطلب التطبيق Android API $effectiveMinSdk على الأقل، بينما يعمل جهازك على API ${device.sdkInt}.",
      )
    }

    val effectiveAbis =
        when {
          localInspection != null && localInspection.nativeAbis.isNotEmpty() ->
              localInspection.nativeAbis
          else -> metadata.supportedAbis
        }

    val normalizedAbis = effectiveAbis.map { it.trim().lowercase(Locale.US) }.filter { it.isNotEmpty() }
    val isUniversal =
        normalizedAbis.isEmpty() ||
            normalizedAbis.any { it == "universal" || it == "noarch" || it == "all" }

    if (!isUniversal) {
      val deviceAbisLower = device.supportedAbis.map { it.lowercase(Locale.US) }
      val matches = deviceAbisLower.any { it in normalizedAbis }
      if (!matches) {
        return CompatibilityResult(
            isCompatible = false,
            summary = "غير متوافق (المعمارية)",
            reason =
                "معالج جهازك يدعم (${device.supportedAbis.joinToString(", ")}) بينما نسخة APK تدعم (${effectiveAbis.joinToString(", ")}).",
        )
      }
    }

    return CompatibilityResult(
        isCompatible = true,
        summary = "متوافق مع جهازك (${device.supportedAbis.firstOrNull() ?: "Android ${device.androidVersion}"})",
        reason = null,
    )
  }

  @Suppress("DEPRECATION")
  fun isPackageInstalled(context: Context, packageName: String): Boolean {
    return try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.packageManager.getPackageInfo(
            packageName,
            PackageManager.PackageInfoFlags.of(0),
        )
      } else {
        context.packageManager.getPackageInfo(packageName, 0)
      }
      true
    } catch (_: Exception) {
      false
    }
  }

  fun canInstallPackages(context: Context): Boolean =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.packageManager.canRequestPackageInstalls()
      } else {
        true
      }

  fun buildInstallIntent(context: Context, apkFile: File): Intent {
    val contentUri: Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
    return Intent(Intent.ACTION_VIEW).apply {
      setDataAndType(contentUri, "application/vnd.android.package-archive")
      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      putExtra(Intent.EXTRA_RETURN_RESULT, true)
    }
  }

  fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "غير محدد"
    val kb = 1024.0
    val mb = kb * 1024.0
    val gb = mb * 1024.0
    return when {
      bytes >= gb -> String.format(Locale.US, "%.2f GB", bytes / gb)
      bytes >= mb -> String.format(Locale.US, "%.1f MB", bytes / mb)
      bytes >= kb -> String.format(Locale.US, "%.0f KB", bytes / kb)
      else -> "$bytes B"
    }
  }
}
