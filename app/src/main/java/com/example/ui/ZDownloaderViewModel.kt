package com.example.ui

import android.app.Activity
import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.ApkVerifierAndInstaller
import com.example.data.GitHubReleaseClient
import com.example.data.RepoConfigStore
import com.example.data.model.ApkLocalInspection
import com.example.data.model.CompatibilityResult
import com.example.data.model.DeviceInfo
import com.example.data.model.DownloadState
import com.example.data.model.InstallFeedback
import com.example.data.model.ReleaseLoadState
import com.example.data.model.YoutubeMetadata
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call

data class ZDownloaderUiState(
    val configuredRepo: String = "",
    val releaseTag: String = BuildConfig.YOUTUBE_RELEASE_TAG,
    val deviceInfo: DeviceInfo = ApkVerifierAndInstaller.readDeviceInfo(),
    val isNetworkConnected: Boolean = true,
    val releaseState: ReleaseLoadState = ReleaseLoadState.Loading,
    val downloadState: DownloadState = DownloadState.Idle,
    val installFeedback: InstallFeedback = InstallFeedback.None,
    val isRepoDialogOpen: Boolean = false,
)

class ZDownloaderViewModel(
    application: Application,
    private val gitHubClient: GitHubReleaseClient,
    private val repoConfigStore: RepoConfigStore,
) : AndroidViewModel(application) {

  constructor(
      application: Application
  ) : this(
      application = application,
      gitHubClient = GitHubReleaseClient(),
      repoConfigStore = RepoConfigStore(application),
  )

  private val _uiState =
      MutableStateFlow(
          ZDownloaderUiState(
              configuredRepo = repoConfigStore.getConfiguredRepository(),
              deviceInfo = ApkVerifierAndInstaller.readDeviceInfo(),
              isNetworkConnected = ApkVerifierAndInstaller.isNetworkAvailable(application),
          )
      )
  val uiState: StateFlow<ZDownloaderUiState> = _uiState.asStateFlow()

  private var downloadJob: Job? = null
  private var activeCall: Call? = null

  init {
    refreshRelease()
  }

  fun refreshRelease() {
    val context = getApplication<Application>()
    val repo = repoConfigStore.getConfiguredRepository()
    val online = ApkVerifierAndInstaller.isNetworkAvailable(context)

    _uiState.update {
      it.copy(
          configuredRepo = repo,
          isNetworkConnected = online,
          installFeedback = InstallFeedback.None,
      )
    }

    if (repo.isBlank()) {
      _uiState.update { it.copy(releaseState = ReleaseLoadState.RepoNotConfigured) }
      return
    }

    if (!online) {
      _uiState.update {
        it.copy(
            releaseState =
                ReleaseLoadState.Error(
                    "لا يوجد اتصال بالإنترنت. وصّل التلفزيون بشبكة Wi-Fi أو كابل الشبكة ثم اضغط «إعادة المحاولة»."
                )
        )
      }
      return
    }

    _uiState.update { it.copy(releaseState = ReleaseLoadState.Loading) }

    viewModelScope.launch {
      when (
          val result =
              gitHubClient.fetchReleaseAndMetadata(
                  ownerRepo = repo,
                  tagName = BuildConfig.YOUTUBE_RELEASE_TAG,
              )
      ) {
        is GitHubReleaseClient.FetchResult.Failure -> {
          _uiState.update { it.copy(releaseState = ReleaseLoadState.Error(result.message)) }
        }
        is GitHubReleaseClient.FetchResult.Success -> {
          val deviceInfo = _uiState.value.deviceInfo
          val installed =
              ApkVerifierAndInstaller.isPackageInstalled(context, result.metadata.packageName)

          val existingReady = checkExistingVerifiedApk(result.metadata, deviceInfo)
          val compatibility =
              ApkVerifierAndInstaller.checkCompatibility(
                  device = deviceInfo,
                  metadata = result.metadata,
                  localInspection = existingReady?.inspection,
              )

          _uiState.update {
            it.copy(
                releaseState =
                    ReleaseLoadState.Ready(
                        release = result.release,
                        metadata = result.metadata,
                        compatibility = compatibility,
                        isAlreadyInstalledOnDevice = installed,
                    ),
                downloadState =
                    when {
                      it.downloadState is DownloadState.Downloading -> it.downloadState
                      existingReady != null -> existingReady
                      else -> DownloadState.Idle
                    },
            )
          }
        }
      }
    }
  }

  private suspend fun checkExistingVerifiedApk(
      metadata: YoutubeMetadata,
      deviceInfo: DeviceInfo,
  ): DownloadState.ReadyToInstall? =
      withContext(Dispatchers.IO) {
        val context = getApplication<Application>()
        val apkFile = ApkVerifierAndInstaller.getApkStorageFile(context)
        if (!apkFile.exists() || apkFile.length() <= 0L) return@withContext null

        if (metadata.fileSizeBytes > 0L && apkFile.length() != metadata.fileSizeBytes) {
          apkFile.delete()
          return@withContext null
        }

        val sha256 =
            try {
              ApkVerifierAndInstaller.computeSha256(apkFile)
            } catch (_: Exception) {
              return@withContext null
            }

        if (!sha256.equals(metadata.sha256, ignoreCase = true)) {
          apkFile.delete()
          return@withContext null
        }

        val inspection = ApkVerifierAndInstaller.inspectApk(context, apkFile)
        if (!inspection.isValidApk) {
          apkFile.delete()
          return@withContext null
        }

        DownloadState.ReadyToInstall(
            apkFile = apkFile,
            inspection = inspection,
            verifiedSha256 = sha256,
        )
      }

  fun startDownload() {
    val readyState = _uiState.value.releaseState as? ReleaseLoadState.Ready ?: return
    if (_uiState.value.downloadState is DownloadState.Downloading) return

    if (!readyState.compatibility.isCompatible) {
      _uiState.update {
        it.copy(
            installFeedback =
                InstallFeedback.Error(
                    readyState.compatibility.reason
                        ?: "هذا الإصدار غير متوافق مع جهازك ولا يمكن تثبيته."
                )
        )
      }
      return
    }

    val context = getApplication<Application>()
    if (!ApkVerifierAndInstaller.isNetworkAvailable(context)) {
      _uiState.update {
        it.copy(
            isNetworkConnected = false,
            downloadState =
                DownloadState.Error("تعذّر بدء التنزيل لعدم توفر اتصال بالإنترنت."),
        )
      }
      return
    }

    _uiState.update { it.copy(installFeedback = InstallFeedback.None) }

    downloadJob =
        viewModelScope.launch {
          // Do not download twice if valid verified file is already stored locally.
          val existing = checkExistingVerifiedApk(readyState.metadata, _uiState.value.deviceInfo)
          if (existing != null) {
            _uiState.update { it.copy(downloadState = existing) }
            return@launch
          }

          val apkFile = ApkVerifierAndInstaller.getApkStorageFile(context)
          val expectedSize =
              readyState.metadata.fileSizeBytes.takeIf { it > 0L }
                  ?: readyState.release.apkSizeBytes

          _uiState.update {
            it.copy(
                downloadState =
                    DownloadState.Downloading(
                        downloadedBytes = 0L,
                        totalBytes = expectedSize,
                    )
            )
          }

          val result =
              gitHubClient.downloadApk(
                  url = readyState.release.apkDownloadUrl,
                  destinationFile = apkFile,
                  expectedSizeBytes = expectedSize,
                  expectedSha256 = readyState.metadata.sha256,
                  onCallCreated = { call -> activeCall = call },
                  onProgress = { downloaded, total ->
                    _uiState.update {
                      it.copy(downloadState = DownloadState.Downloading(downloaded, total))
                    }
                  },
              )

          activeCall = null

          when (result) {
            is GitHubReleaseClient.DownloadResult.Cancelled -> {
              _uiState.update { it.copy(downloadState = DownloadState.Idle) }
            }
            is GitHubReleaseClient.DownloadResult.Failure -> {
              _uiState.update { it.copy(downloadState = DownloadState.Error(result.message)) }
            }
            is GitHubReleaseClient.DownloadResult.Success -> {
              _uiState.update { it.copy(downloadState = DownloadState.Verifying) }
              val inspection =
                  withContext(Dispatchers.IO) {
                    ApkVerifierAndInstaller.inspectApk(context, result.file)
                  }
              if (!inspection.isValidApk) {
                withContext(Dispatchers.IO) { result.file.delete() }
                _uiState.update {
                  it.copy(
                      downloadState =
                          DownloadState.Error(
                              inspection.errorReason
                                  ?: "ملف APK المنزّل تالف ولا يمكن قراءته."
                          )
                  )
                }
                return@launch
              }

              val finalCompatibility =
                  ApkVerifierAndInstaller.checkCompatibility(
                      device = _uiState.value.deviceInfo,
                      metadata = readyState.metadata,
                      localInspection = inspection,
                  )

              _uiState.update { state ->
                val updatedReleaseState =
                    (state.releaseState as? ReleaseLoadState.Ready)?.copy(
                        compatibility = finalCompatibility
                    ) ?: state.releaseState
                state.copy(
                    releaseState = updatedReleaseState,
                    downloadState =
                        DownloadState.ReadyToInstall(
                            apkFile = result.file,
                            inspection = inspection,
                            verifiedSha256 = result.sha256,
                        ),
                )
              }
            }
          }
        }
  }

  fun cancelDownload() {
    activeCall?.cancel()
    downloadJob?.cancel()
    activeCall = null
    downloadJob = null
    _uiState.update { it.copy(downloadState = DownloadState.Idle) }
  }

  fun deleteDownloadedApk() {
    val context = getApplication<Application>()
    viewModelScope.launch(Dispatchers.IO) {
      val file = ApkVerifierAndInstaller.getApkStorageFile(context)
      if (file.exists()) {
        file.delete()
      }
      _uiState.update {
        it.copy(
            downloadState = DownloadState.Idle,
            installFeedback = InstallFeedback.None,
        )
      }
    }
  }

  fun prepareAndLaunchInstall(
      onLaunchInstaller: (Intent) -> Unit,
      onRequestUnknownSources: () -> Unit,
  ) {
    val readyState = _uiState.value.releaseState as? ReleaseLoadState.Ready ?: return
    val readyApk = _uiState.value.downloadState as? DownloadState.ReadyToInstall ?: return
    val context = getApplication<Application>()

    viewModelScope.launch {
      if (!readyApk.apkFile.exists()) {
        _uiState.update {
          it.copy(
              downloadState = DownloadState.Idle,
              installFeedback =
                  InstallFeedback.Error("لم يتم العثور على ملف youtube.apk المحفوظ. أعد التنزيل."),
          )
        }
        return@launch
      }

      // Re-verify SHA-256 and APK integrity right before handing off to Android package installer.
      val (shaMatch, inspection) =
          withContext(Dispatchers.IO) {
            val currentSha = ApkVerifierAndInstaller.computeSha256(readyApk.apkFile)
            val inspected = ApkVerifierAndInstaller.inspectApk(context, readyApk.apkFile)
            Pair(currentSha.equals(readyState.metadata.sha256, ignoreCase = true), inspected)
          }

      if (!shaMatch) {
        withContext(Dispatchers.IO) { readyApk.apkFile.delete() }
        _uiState.update {
          it.copy(
              downloadState =
                  DownloadState.Error(
                      "فشل التحقق من SHA-256 قبل التثبيت. تم حذف الملف لحماية جهازك."
                  )
          )
        }
        return@launch
      }

      if (!inspection.isValidApk) {
        withContext(Dispatchers.IO) { readyApk.apkFile.delete() }
        _uiState.update {
          it.copy(
              downloadState =
                  DownloadState.Error(
                      inspection.errorReason ?: "ملف APK تالف ولا يصلح للتثبيت."
                  )
          )
        }
        return@launch
      }

      val compat: CompatibilityResult =
          ApkVerifierAndInstaller.checkCompatibility(
              device = _uiState.value.deviceInfo,
              metadata = readyState.metadata,
              localInspection = inspection,
          )
      if (!compat.isCompatible) {
        _uiState.update {
          it.copy(
              installFeedback =
                  InstallFeedback.Error(
                      compat.reason ?: "الملف غير متوافق مع مواصفات هذا الجهاز."
                  )
          )
        }
        return@launch
      }

      if (!ApkVerifierAndInstaller.canInstallPackages(context)) {
        _uiState.update {
          it.copy(
              installFeedback =
                  InstallFeedback.Warning(
                      message =
                          "يتطلب نظام Android تفعيل «التثبيت من مصادر غير معروفة» لتطبيق Z-Downloader أولاً.",
                      requiresUnknownSourcesSettings = true,
                  )
          )
        }
        onRequestUnknownSources()
        return@launch
      }

      try {
        val intent = ApkVerifierAndInstaller.buildInstallIntent(context, readyApk.apkFile)
        _uiState.update { it.copy(installFeedback = InstallFeedback.None) }
        onLaunchInstaller(intent)
      } catch (e: Exception) {
        _uiState.update {
          it.copy(
              installFeedback =
                  InstallFeedback.Error(
                      "تعذّر فتح مثبّت الحزم الرسمي: ${e.localizedMessage ?: "تحقق من إعدادات الأمان"}"
                  )
          )
        }
      }
    }
  }

  fun onUnknownSourcesSettingsReturned() {
    val context = getApplication<Application>()
    if (ApkVerifierAndInstaller.canInstallPackages(context)) {
      _uiState.update {
        it.copy(
            installFeedback =
                InstallFeedback.Success("تم تفعيل إذن التثبيت بنجاح. اضغط «تثبيت YouTube» للمتابعة.")
        )
      }
    } else {
      _uiState.update {
        it.copy(
            installFeedback =
                InstallFeedback.Warning(
                    message = "ما زال إذن التثبيت من مصادر غير معروفة غير مفعّل.",
                    requiresUnknownSourcesSettings = true,
                )
        )
      }
    }
  }

  fun onInstallActivityResult(resultCode: Int) {
    val context = getApplication<Application>()
    val readyState = _uiState.value.releaseState as? ReleaseLoadState.Ready
    val pkgName =
        ( _uiState.value.downloadState as? DownloadState.ReadyToInstall)?.inspection?.packageName
            ?: readyState?.metadata?.packageName
            ?: "com.google.android.youtube.tv"

    val isNowInstalled = ApkVerifierAndInstaller.isPackageInstalled(context, pkgName)

    _uiState.update { state ->
      val updatedRelease =
          (state.releaseState as? ReleaseLoadState.Ready)?.copy(
              isAlreadyInstalledOnDevice = isNowInstalled
          ) ?: state.releaseState

      val feedback =
          when {
            resultCode == Activity.RESULT_OK ->
                InstallFeedback.Success("تم تثبيت YouTube بنجاح! يمكنك تشغيله الآن من قائمة التطبيقات.")
            isNowInstalled ->
                InstallFeedback.Success("تم التحقق: تطبيق YouTube مثبّت وجاهز على جهازك.")
            resultCode == Activity.RESULT_CANCELED ->
                InstallFeedback.Warning(
                    "لم يكتمل التثبيت (تم الإلغاء أو رُفض التثبيت من النظام). تأكد من تفعيل المصادر غير المعروفة وتوافق التوقيع."
                )
            else ->
                InstallFeedback.Error("فشل تثبيت التطبيق (رمز النتيجة: $resultCode).")
          }

      state.copy(releaseState = updatedRelease, installFeedback = feedback)
    }
  }

  fun openRepoDialog() {
    _uiState.update { it.copy(isRepoDialogOpen = true) }
  }

  fun closeRepoDialog() {
    _uiState.update { it.copy(isRepoDialogOpen = false) }
  }

  fun saveRepository(input: String) {
    val normalized = RepoConfigStore.normalizeOwnerRepo(input)
    if (!RepoConfigStore.isValidOwnerRepo(normalized)) {
      return
    }
    repoConfigStore.setRepositoryOverride(normalized)
    _uiState.update { it.copy(isRepoDialogOpen = false, downloadState = DownloadState.Idle) }
    refreshRelease()
  }

  fun resetRepositoryToBuildDefault() {
    repoConfigStore.clearRepositoryOverride()
    _uiState.update { it.copy(isRepoDialogOpen = false, downloadState = DownloadState.Idle) }
    refreshRelease()
  }

  fun hasUserRepoOverride(): Boolean = repoConfigStore.hasUserOverride()

  override fun onCleared() {
    activeCall?.cancel()
    super.onCleared()
  }
}
