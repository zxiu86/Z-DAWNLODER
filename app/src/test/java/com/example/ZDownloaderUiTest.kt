package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import com.example.data.model.ApkLocalInspection
import com.example.data.model.CompatibilityResult
import com.example.data.model.DeviceInfo
import com.example.data.model.DownloadState
import com.example.data.model.GitHubReleaseInfo
import com.example.data.model.ReleaseLoadState
import com.example.data.model.YoutubeMetadata
import com.example.ui.ZDownloaderScreen
import com.example.ui.ZDownloaderUiState
import com.example.ui.theme.ZDownloaderTheme
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w960dp-h540dp-land")
class ZDownloaderUiTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val sampleDevice =
      DeviceInfo(
          manufacturer = "CHRANI",
          model = "TV-7",
          androidVersion = "7.0",
          sdkInt = 24,
          supportedAbis = listOf("armeabi-v7a"),
      )

  private val sampleRelease =
      GitHubReleaseInfo(
          tagName = "0.0.011",
          releaseName = "YouTube 0.0.011",
          publishedAt = "2026-11-14T00:00:00Z",
          apkAssetName = "youtube.apk",
          apkDownloadUrl = "https://github.com/owner/repo/releases/download/0.0.011/youtube.apk",
          apkSizeBytes = 35_000_000L,
          metadataDownloadUrl =
              "https://github.com/owner/repo/releases/download/0.0.011/youtube-metadata.json",
      )

  private val sampleMetadata =
      YoutubeMetadata(
          appName = "YouTube",
          packageName = "com.google.android.youtube.tv",
          versionName = "4.40.303",
          versionCode = 44030300L,
          fileName = "youtube.apk",
          fileSizeBytes = 35_000_000L,
          sha256 = "a".repeat(64),
          minSdk = 24,
          supportedAbis = listOf("armeabi-v7a"),
      )

  @Test
  fun `youtube card displays download button when ready`() {
    val state =
        ZDownloaderUiState(
            configuredRepo = "owner/repo",
            deviceInfo = sampleDevice,
            isNetworkConnected = true,
            releaseState =
                ReleaseLoadState.Ready(
                    release = sampleRelease,
                    metadata = sampleMetadata,
                    compatibility = CompatibilityResult(true, "متوافق مع جهازك (armeabi-v7a)"),
                    isAlreadyInstalledOnDevice = false,
                ),
            downloadState = DownloadState.Idle,
        )

    composeTestRule.setContent {
      ZDownloaderTheme {
        ZDownloaderScreen(
            state = state,
            hasUserRepoOverride = false,
            onRefresh = {},
            onDownloadClick = {},
            onCancelDownloadClick = {},
            onInstallClick = {},
            onDeleteApkClick = {},
            onOpenUnknownSourcesSettings = {},
            onOpenRepoDialog = {},
            onCloseRepoDialog = {},
            onSaveRepo = {},
            onResetRepo = {},
        )
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("youtube_card").assertIsDisplayed()
    composeTestRule.onNodeWithTag("youtube_app_name").assertTextContains("YouTube")
    composeTestRule.onNodeWithTag("download_button").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("download_button").assertTextContains("تنزيل YouTube")
  }

  @Test
  fun `youtube card switches button to install after download verified`() {
    val state =
        ZDownloaderUiState(
            configuredRepo = "owner/repo",
            deviceInfo = sampleDevice,
            isNetworkConnected = true,
            releaseState =
                ReleaseLoadState.Ready(
                    release = sampleRelease,
                    metadata = sampleMetadata,
                    compatibility = CompatibilityResult(true, "متوافق مع جهازك (armeabi-v7a)"),
                    isAlreadyInstalledOnDevice = false,
                ),
            downloadState =
                DownloadState.ReadyToInstall(
                    apkFile = File("/tmp/youtube.apk"),
                    inspection = ApkLocalInspection(isValidApk = true),
                    verifiedSha256 = "a".repeat(64),
                ),
        )

    composeTestRule.setContent {
      ZDownloaderTheme {
        ZDownloaderScreen(
            state = state,
            hasUserRepoOverride = false,
            onRefresh = {},
            onDownloadClick = {},
            onCancelDownloadClick = {},
            onInstallClick = {},
            onDeleteApkClick = {},
            onOpenUnknownSourcesSettings = {},
            onOpenRepoDialog = {},
            onCloseRepoDialog = {},
            onSaveRepo = {},
            onResetRepo = {},
        )
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("install_button").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("install_button").assertTextContains("تثبيت YouTube")
  }

  @Test
  fun `error state renders retry button without crashing`() {
    val state =
        ZDownloaderUiState(
            configuredRepo = "owner/repo",
            deviceInfo = sampleDevice,
            isNetworkConnected = false,
            releaseState = ReleaseLoadState.Error("تعذّر الاتصال بالإنترنت."),
        )

    composeTestRule.setContent {
      ZDownloaderTheme {
        ZDownloaderScreen(
            state = state,
            hasUserRepoOverride = false,
            onRefresh = {},
            onDownloadClick = {},
            onCancelDownloadClick = {},
            onInstallClick = {},
            onDeleteApkClick = {},
            onOpenUnknownSourcesSettings = {},
            onOpenRepoDialog = {},
            onCloseRepoDialog = {},
            onSaveRepo = {},
            onResetRepo = {},
        )
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("error_card").assertIsDisplayed()
    composeTestRule.onNodeWithTag("retry_button").assertIsDisplayed()
  }
}
