package com.example.data.model

import java.io.File

data class GitHubReleaseInfo(
    val tagName: String,
    val releaseName: String,
    val publishedAt: String,
    val apkAssetName: String,
    val apkDownloadUrl: String,
    val apkSizeBytes: Long,
    val metadataDownloadUrl: String,
)

data class YoutubeMetadata(
    val appName: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val fileName: String,
    val fileSizeBytes: Long,
    val sha256: String,
    val minSdk: Int,
    val supportedAbis: List<String>,
)

data class DeviceInfo(
    val manufacturer: String,
    val model: String,
    val androidVersion: String,
    val sdkInt: Int,
    val supportedAbis: List<String>,
)

data class CompatibilityResult(
    val isCompatible: Boolean,
    val summary: String,
    val reason: String? = null,
)

data class ApkLocalInspection(
    val isValidApk: Boolean,
    val packageName: String? = null,
    val versionName: String? = null,
    val versionCode: Long? = null,
    val minSdk: Int? = null,
    val nativeAbis: List<String> = emptyList(),
    val errorReason: String? = null,
)

sealed interface ReleaseLoadState {
  data object RepoNotConfigured : ReleaseLoadState

  data object Loading : ReleaseLoadState

  data class Ready(
      val release: GitHubReleaseInfo,
      val metadata: YoutubeMetadata,
      val compatibility: CompatibilityResult,
      val isAlreadyInstalledOnDevice: Boolean,
  ) : ReleaseLoadState

  data class Error(val message: String) : ReleaseLoadState
}

sealed interface DownloadState {
  data object Idle : DownloadState

  data class Downloading(
      val downloadedBytes: Long,
      val totalBytes: Long,
  ) : DownloadState

  data object Verifying : DownloadState

  data class ReadyToInstall(
      val apkFile: File,
      val inspection: ApkLocalInspection,
      val verifiedSha256: String,
  ) : DownloadState

  data class Error(val message: String) : DownloadState
}

sealed interface InstallFeedback {
  data object None : InstallFeedback

  data class Success(val message: String) : InstallFeedback

  data class Warning(val message: String, val requiresUnknownSourcesSettings: Boolean = false) :
      InstallFeedback

  data class Error(val message: String) : InstallFeedback
}
