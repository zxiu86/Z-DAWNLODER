package com.example.data

import com.example.data.model.GitHubReleaseInfo
import com.example.data.model.YoutubeMetadata
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

class GitHubReleaseClient(
    private val httpClient: OkHttpClient = defaultOkHttpClient(),
    private val apiBaseUrl: String = "https://api.github.com",
) {

  sealed interface FetchResult {
    data class Success(val release: GitHubReleaseInfo, val metadata: YoutubeMetadata) : FetchResult

    data class Failure(val message: String) : FetchResult
  }

  sealed interface DownloadResult {
    data class Success(val file: File, val sha256: String, val totalBytes: Long) : DownloadResult

    data object Cancelled : DownloadResult

    data class Failure(val message: String) : DownloadResult
  }

  suspend fun fetchReleaseAndMetadata(ownerRepo: String, tagName: String): FetchResult =
      withContext(Dispatchers.IO) {
        val cleanRepo = RepoConfigStore.normalizeOwnerRepo(ownerRepo)
        if (!RepoConfigStore.isValidOwnerRepo(cleanRepo)) {
          return@withContext FetchResult.Failure(
              "صيغة اسم المستودع غير صالحة. يجب أن تكون بصيغة owner/repo."
          )
        }

        val releaseUrl =
            "${apiBaseUrl.trimEnd('/')}/repos/$cleanRepo/releases/tags/${tagName.trim()}"
        val releaseRequest =
            Request.Builder()
                .url(releaseUrl)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", USER_AGENT)
                .get()
                .build()

        val releaseJson =
            try {
              httpClient.newCall(releaseRequest).execute().use { response ->
                when (response.code) {
                  in 200..299 -> {
                    response.body?.string()
                        ?: return@withContext FetchResult.Failure(
                            "استجابة فارغة من خادم GitHub."
                        )
                  }
                  404 ->
                      return@withContext FetchResult.Failure(
                          "لم يتم العثور على الإصدار ($tagName) في المستودع ($cleanRepo). تأكد من أن المستودع عام وأن الوسم $tagName منشور."
                      )
                  403 ->
                      return@withContext FetchResult.Failure(
                          "تعذّر الوصول إلى GitHub API (HTTP 403) — قد يكون حد الطلبات المؤقت قد انتهى. حاول لاحقاً."
                      )
                  else ->
                      return@withContext FetchResult.Failure(
                          "فشل الاتصال بخادم GitHub (رمز الحالة HTTP ${response.code})."
                      )
                }
              }
            } catch (e: CancellationException) {
              throw e
            } catch (e: Exception) {
              return@withContext FetchResult.Failure(mapNetworkError(e))
            }

        val releaseInfo =
            try {
              parseReleaseJson(releaseJson)
            } catch (e: IllegalStateException) {
              return@withContext FetchResult.Failure(e.message ?: "تعذّر قراءة بيانات الإصدار.")
            } catch (e: Exception) {
              return@withContext FetchResult.Failure("بيانات الإصدار المستلمة من GitHub غير صالحة.")
            }

        val metadataRequest =
            Request.Builder()
                .url(releaseInfo.metadataDownloadUrl)
                .header("Accept", "application/json")
                .header("User-Agent", USER_AGENT)
                .get()
                .build()

        val metadataJson =
            try {
              httpClient.newCall(metadataRequest).execute().use { response ->
                if (!response.isSuccessful) {
                  return@withContext FetchResult.Failure(
                      "تعذّر تنزيل ملف youtube-metadata.json (HTTP ${response.code})."
                  )
                }
                response.body?.string()
                    ?: return@withContext FetchResult.Failure(
                        "ملف youtube-metadata.json فارغ."
                    )
              }
            } catch (e: CancellationException) {
              throw e
            } catch (e: Exception) {
              return@withContext FetchResult.Failure(mapNetworkError(e))
            }

        val metadata =
            try {
              parseMetadataJson(metadataJson, fallbackSizeBytes = releaseInfo.apkSizeBytes)
            } catch (e: Exception) {
              return@withContext FetchResult.Failure(
                  "ملف youtube-metadata.json تالف أو غير مكتمل: ${e.message ?: "خطأ في التنسيق"}"
              )
            }

        FetchResult.Success(releaseInfo, metadata)
      }

  suspend fun downloadApk(
      url: String,
      destinationFile: File,
      expectedSizeBytes: Long,
      expectedSha256: String,
      onCallCreated: (Call) -> Unit,
      onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit,
  ): DownloadResult =
      withContext(Dispatchers.IO) {
        if (!url.startsWith("https://", ignoreCase = true) && !url.startsWith("http://localhost") && !url.startsWith("http://127.0.0.1")) {
          return@withContext DownloadResult.Failure("رابط التنزيل غير آمن أو غير صالح.")
        }

        destinationFile.parentFile?.mkdirs()
        val partFile = File(destinationFile.parentFile, "${destinationFile.name}.part")
        if (partFile.exists()) {
          partFile.delete()
        }

        val request =
            Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/vnd.android.package-archive, application/octet-stream, */*")
                .get()
                .build()

        val call = httpClient.newCall(request)
        onCallCreated(call)

        try {
          call.execute().use { response ->
            when (response.code) {
              in 200..299 -> Unit
              404 ->
                  return@withContext DownloadResult.Failure(
                      "ملف youtube.apk غير موجود على الخادم (HTTP 404)."
                  )
              403 ->
                  return@withContext DownloadResult.Failure(
                      "تم رفض تنزيل الملف من الخادم (HTTP 403)."
                  )
              else ->
                  return@withContext DownloadResult.Failure(
                      "فشل تنزيل youtube.apk (رمز الخطأ HTTP ${response.code})."
                  )
            }

            val contentType = response.header("Content-Type").orEmpty().lowercase(Locale.US)
            if (contentType.contains("text/html")) {
              return@withContext DownloadResult.Failure(
                  "أعاد الخادم صفحة HTML بدلاً من ملف APK حقيقي."
              )
            }

            val body =
                response.body
                    ?: return@withContext DownloadResult.Failure("استجابة التنزيل فارغة.")

            val totalBytes =
                body.contentLength().takeIf { it > 0L }
                    ?: expectedSizeBytes.takeIf { it > 0L }
                    ?: -1L

            val digest = MessageDigest.getInstance("SHA-256")
            var downloadedBytes = 0L
            var lastReportedBytes = 0L
            val buffer = ByteArray(DEFAULT_BUFFER_BYTES)

            body.byteStream().use { input ->
              FileOutputStream(partFile).use { output ->
                while (true) {
                  coroutineContext.ensureActive()
                  val read = input.read(buffer)
                  if (read == -1) break
                  output.write(buffer, 0, read)
                  digest.update(buffer, 0, read)
                  downloadedBytes += read

                  if (downloadedBytes - lastReportedBytes >= PROGRESS_STEP_BYTES ||
                      (totalBytes > 0 && downloadedBytes >= totalBytes)) {
                    lastReportedBytes = downloadedBytes
                    onProgress(downloadedBytes, totalBytes)
                  }
                }
                output.flush()
              }
            }

            if (downloadedBytes <= 0L) {
              partFile.delete()
              return@withContext DownloadResult.Failure("الملف المنزّل فارغ (0 بايت).")
            }

            if (expectedSizeBytes > 0L && downloadedBytes != expectedSizeBytes) {
              partFile.delete()
              return@withContext DownloadResult.Failure(
                  "حجم الملف المنزّل ($downloadedBytes بايت) لا يطابق الحجم المتوقع ($expectedSizeBytes بايت)."
              )
            }

            val actualSha256 = digest.digest().joinToString("") { "%02x".format(Locale.US, it) }
            val cleanExpectedSha = expectedSha256.trim().lowercase(Locale.US)
            if (cleanExpectedSha.isNotEmpty() && actualSha256 != cleanExpectedSha) {
              partFile.delete()
              return@withContext DownloadResult.Failure(
                  "فشل التحقق من بصمة SHA-256! الملف قد يكون تالفاً أو تم تغييره أثناء التنزيل."
              )
            }

            if (destinationFile.exists()) {
              destinationFile.delete()
            }
            if (!partFile.renameTo(destinationFile)) {
              partFile.copyTo(destinationFile, overwrite = true)
              partFile.delete()
            }

            onProgress(downloadedBytes, if (totalBytes > 0L) totalBytes else downloadedBytes)
            DownloadResult.Success(destinationFile, actualSha256, downloadedBytes)
          }
        } catch (e: CancellationException) {
          partFile.delete()
          DownloadResult.Cancelled
        } catch (e: IOException) {
          partFile.delete()
          if (call.isCanceled()) {
            DownloadResult.Cancelled
          } else {
            DownloadResult.Failure(mapNetworkError(e))
          }
        } catch (e: Exception) {
          partFile.delete()
          DownloadResult.Failure(mapNetworkError(e))
        }
      }

  private fun mapNetworkError(e: Exception): String =
      when (e) {
        is UnknownHostException ->
            "تعذّر الاتصال بالإنترنت. تحقق من اتصال الواي فاي أو الشبكة على التلفزيون."
        is SocketTimeoutException ->
            "انتهت مهلة الاتصال بالخادم (Timeout). قد تكون الشبكة بطيئة، حاول مرة أخرى."
        is SSLException ->
            "فشل الاتصال الآمن (SSL/TLS). تحقق من ضبط التاريخ والوقت على التلفزيون."
        else -> "حدث خطأ أثناء الاتصال بالشبكة: ${e.localizedMessage ?: "خطأ غير معروف"}"
      }

  companion object {
    private const val USER_AGENT = "Z-Downloader-AndroidTV/1.0"
    private const val DEFAULT_BUFFER_BYTES = 16 * 1024
    private const val PROGRESS_STEP_BYTES = 64 * 1024L
    const val APK_ASSET_NAME = "youtube.apk"
    const val METADATA_ASSET_NAME = "youtube-metadata.json"

    fun defaultOkHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()

    fun parseReleaseJson(jsonString: String): GitHubReleaseInfo {
      val root = JSONObject(jsonString)
      val tagName = root.optString("tag_name", "").trim()
      val releaseName = root.optString("name", tagName).trim().ifEmpty { tagName }
      val publishedAt = root.optString("published_at", "").trim()
      val assets = root.optJSONArray("assets") ?: JSONArray()

      var apkUrl = ""
      var apkSize = 0L
      var metadataUrl = ""

      for (i in 0 until assets.length()) {
        val asset = assets.optJSONObject(i) ?: continue
        val name = asset.optString("name", "").trim()
        val downloadUrl = asset.optString("browser_download_url", "").trim()
        val size = asset.optLong("size", 0L)

        when (name) {
          APK_ASSET_NAME -> {
            apkUrl = downloadUrl
            apkSize = size
          }
          METADATA_ASSET_NAME -> {
            metadataUrl = downloadUrl
          }
        }
      }

      if (apkUrl.isEmpty()) {
        throw IllegalStateException(
            "ملف $APK_ASSET_NAME غير موجود داخل أصول الإصدار $tagName على GitHub."
        )
      }
      if (metadataUrl.isEmpty()) {
        throw IllegalStateException(
            "ملف $METADATA_ASSET_NAME غير موجود داخل أصول الإصدار $tagName للتحقق من التكامل والبصمة."
        )
      }

      return GitHubReleaseInfo(
          tagName = tagName,
          releaseName = releaseName,
          publishedAt = publishedAt,
          apkAssetName = APK_ASSET_NAME,
          apkDownloadUrl = apkUrl,
          apkSizeBytes = apkSize,
          metadataDownloadUrl = metadataUrl,
      )
    }

    fun parseMetadataJson(jsonString: String, fallbackSizeBytes: Long = 0L): YoutubeMetadata {
      val root = JSONObject(jsonString)
      val sha256 = root.optString("sha256", "").trim().lowercase(Locale.US)
      require(sha256.length == 64 && sha256.all { it in '0'..'9' || it in 'a'..'f' }) {
        "بصمة SHA-256 غير صالحة في ملف البيانات."
      }

      val abis = mutableListOf<String>()
      val abiArray = root.optJSONArray("supported_abis")
      if (abiArray != null) {
        for (i in 0 until abiArray.length()) {
          val abi = abiArray.optString(i, "").trim()
          if (abi.isNotEmpty()) abis.add(abi)
        }
      }

      val sizeBytes = root.optLong("file_size", fallbackSizeBytes)
      return YoutubeMetadata(
          appName = root.optString("app_name", "YouTube").trim().ifEmpty { "YouTube" },
          packageName =
              root.optString("package_name", "com.google.android.youtube.tv")
                  .trim()
                  .ifEmpty { "com.google.android.youtube.tv" },
          versionName = root.optString("version_name", "غير معروف").trim().ifEmpty { "غير معروف" },
          versionCode = root.optLong("version_code", 0L),
          fileName = root.optString("file_name", APK_ASSET_NAME).trim().ifEmpty { APK_ASSET_NAME },
          fileSizeBytes = sizeBytes,
          sha256 = sha256,
          minSdk = root.optInt("min_sdk", 24),
          supportedAbis = abis,
      )
    }
  }
}
