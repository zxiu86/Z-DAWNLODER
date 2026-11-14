package com.example

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.example.data.ApkVerifierAndInstaller
import com.example.data.GitHubReleaseClient
import com.example.data.RepoConfigStore
import com.example.data.model.DeviceInfo
import com.example.data.model.YoutubeMetadata
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ZDownloaderCoreTest {

  @Test
  fun `parseReleaseJson extracts youtube apk and metadata download urls`() {
    val json =
        """
        {
          "tag_name": "0.0.011",
          "name": "YouTube TV 0.0.011",
          "published_at": "2026-11-14T00:00:00Z",
          "assets": [
            {
              "name": "youtube.apk",
              "size": 45000000,
              "browser_download_url": "https://github.com/owner/repo/releases/download/0.0.011/youtube.apk"
            },
            {
              "name": "youtube-metadata.json",
              "size": 512,
              "browser_download_url": "https://github.com/owner/repo/releases/download/0.0.011/youtube-metadata.json"
            }
          ]
        }
        """.trimIndent()

    val info = GitHubReleaseClient.parseReleaseJson(json)
    assertEquals("0.0.011", info.tagName)
    assertEquals(45000000L, info.apkSizeBytes)
    assertEquals(
        "https://github.com/owner/repo/releases/download/0.0.011/youtube.apk",
        info.apkDownloadUrl,
    )
    assertEquals(
        "https://github.com/owner/repo/releases/download/0.0.011/youtube-metadata.json",
        info.metadataDownloadUrl,
    )
  }

  @Test
  fun `parseReleaseJson works with youtube apk only and optional asset digest`() {
    val digestHex = "c".repeat(64)
    val json =
        """
        {
          "tag_name": "0.0.011",
          "name": "YouTube TV 0.0.011",
          "published_at": "2026-11-14T00:00:00Z",
          "assets": [
            {
              "name": "youtube.apk",
              "size": 45000000,
              "digest": "sha256:$digestHex",
              "browser_download_url": "https://github.com/owner/repo/releases/download/0.0.011/youtube.apk"
            }
          ]
        }
        """.trimIndent()

    val info = GitHubReleaseClient.parseReleaseJson(json)
    assertEquals("0.0.011", info.tagName)
    assertEquals(45000000L, info.apkSizeBytes)
    assertEquals(digestHex, info.apkDigestSha256)
    assertEquals(null, info.metadataDownloadUrl)
    assertEquals(
        "https://github.com/owner/repo/releases/download/0.0.011/youtube.apk",
        info.apkDownloadUrl,
    )

    val fallbackMeta = GitHubReleaseClient.metadataFromReleaseOnly(info)
    assertEquals(digestHex, fallbackMeta.sha256)
    assertEquals(45000000L, fallbackMeta.fileSizeBytes)
  }

  @Test(expected = IllegalStateException::class)
  fun `parseReleaseJson throws when no apk asset exists in release`() {
    val json =
        """
        {
          "tag_name": "0.0.011",
          "assets": [
            {
              "name": "README.txt",
              "size": 512,
              "browser_download_url": "https://github.com/owner/repo/releases/download/0.0.011/README.txt"
            }
          ]
        }
        """.trimIndent()

    GitHubReleaseClient.parseReleaseJson(json)
  }

  @Test
  fun `parseMetadataJson validates sha256 and supported abis`() {
    val sha = "a".repeat(64)
    val json =
        """
        {
          "app_name": "YouTube",
          "package_name": "com.google.android.youtube.tv",
          "version_name": "4.40.303",
          "version_code": 44030300,
          "file_name": "youtube.apk",
          "file_size": 31457280,
          "sha256": "$sha",
          "min_sdk": 24,
          "supported_abis": ["armeabi-v7a"]
        }
        """.trimIndent()

    val meta = GitHubReleaseClient.parseMetadataJson(json)
    assertEquals("YouTube", meta.appName)
    assertEquals("4.40.303", meta.versionName)
    assertEquals(24, meta.minSdk)
    assertEquals(listOf("armeabi-v7a"), meta.supportedAbis)
    assertEquals(sha, meta.sha256)
  }

  @Test
  fun `compatibility check passes for Android 7 ARMv7 TV and fails on mismatch`() {
    val chraniTv =
        DeviceInfo(
            manufacturer = "CHRANI",
            model = "SmartTV",
            androidVersion = "7.0",
            sdkInt = 24,
            supportedAbis = listOf("armeabi-v7a", "armeabi"),
        )
    val compatibleMeta =
        YoutubeMetadata(
            appName = "YouTube",
            packageName = "com.google.android.youtube.tv",
            versionName = "4.0",
            versionCode = 400,
            fileName = "youtube.apk",
            fileSizeBytes = 1000L,
            sha256 = "b".repeat(64),
            minSdk = 24,
            supportedAbis = listOf("armeabi-v7a"),
        )

    assertTrue(ApkVerifierAndInstaller.checkCompatibility(chraniTv, compatibleMeta).isCompatible)

    val arm64OnlyMeta = compatibleMeta.copy(supportedAbis = listOf("arm64-v8a"))
    val abiMismatch = ApkVerifierAndInstaller.checkCompatibility(chraniTv, arm64OnlyMeta)
    assertFalse(abiMismatch.isCompatible)
    assertTrue(abiMismatch.reason!!.contains("arm64-v8a"))

    val api28Meta = compatibleMeta.copy(minSdk = 28)
    val sdkMismatch = ApkVerifierAndInstaller.checkCompatibility(chraniTv, api28Meta)
    assertFalse(sdkMismatch.isCompatible)
    assertTrue(sdkMismatch.reason!!.contains("28"))
  }

  @Test
  fun `inspectApk rejects HTML file disguised as apk`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val fakeApk = File(context.cacheDir, "fake_html.apk")
    fakeApk.writeText("<html><body>404 Not Found</body></html>")

    val inspection = ApkVerifierAndInstaller.inspectApk(context, fakeApk)
    assertFalse(inspection.isValidApk)
    fakeApk.delete()
  }

  @Test
  fun `inspectApk detects missing AndroidManifest in zip`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val zipFile = File(context.cacheDir, "no_manifest.apk")
    ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
      zos.putNextEntry(ZipEntry("classes.dex"))
      zos.write("dex".toByteArray())
      zos.closeEntry()
    }

    val inspection = ApkVerifierAndInstaller.inspectApk(context, zipFile)
    assertFalse(inspection.isValidApk)
    assertTrue(inspection.errorReason!!.contains("AndroidManifest.xml"))
    zipFile.delete()
  }

  @Test
  fun `buildInstallIntent uses FileProvider content URI and never file URI`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val apkFile = ApkVerifierAndInstaller.getApkStorageFile(context)
    apkFile.writeText("dummy")

    val intent = ApkVerifierAndInstaller.buildInstallIntent(context, apkFile)
    assertEquals(Intent.ACTION_VIEW, intent.action)
    assertEquals("content", intent.data?.scheme)
    assertEquals("${context.packageName}.fileprovider", intent.data?.authority)
    assertTrue((intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0)
    apkFile.delete()
  }

  @Test
  fun `repo config validator enforces owner slash repo format`() {
    assertTrue(RepoConfigStore.isValidOwnerRepo("zxiu86/Z-Downloader"))
    assertTrue(RepoConfigStore.isValidOwnerRepo("https://github.com/zxiu86/Z-Downloader/"))
    assertFalse(RepoConfigStore.isValidOwnerRepo("Z-Downloader"))
    assertFalse(RepoConfigStore.isValidOwnerRepo(""))
  }

  @Test
  fun `downloadApk rejects mismatched SHA-256 and deletes part file`() = runTest {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val payload = "not-a-real-apk-but-deterministic-bytes".toByteArray()
    val client =
        OkHttpClient.Builder()
            .addInterceptor { chain ->
              Response.Builder()
                  .request(chain.request())
                  .protocol(Protocol.HTTP_1_1)
                  .code(200)
                  .message("OK")
                  .header("Content-Type", "application/vnd.android.package-archive")
                  .body(payload.toResponseBody())
                  .build()
            }
            .build()

    val releaseClient = GitHubReleaseClient(httpClient = client)
    val dest = File(context.cacheDir, "sha_mismatch_test.apk")
    val result =
        releaseClient.downloadApk(
            url = "https://github.com/owner/repo/releases/download/0.0.011/youtube.apk",
            destinationFile = dest,
            expectedSizeBytes = payload.size.toLong(),
            expectedSha256 = "0".repeat(64),
            onCallCreated = {},
            onProgress = { _, _ -> },
        )

    assertTrue(result is GitHubReleaseClient.DownloadResult.Failure)
    assertFalse(dest.exists())
    assertFalse(File(dest.parentFile, "${dest.name}.part").exists())
  }
}
