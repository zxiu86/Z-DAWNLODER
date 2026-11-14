package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.BuildConfig
import com.example.R
import com.example.data.ApkVerifierAndInstaller
import com.example.data.RepoConfigStore
import com.example.data.model.CompatibilityResult
import com.example.data.model.DeviceInfo
import com.example.data.model.DownloadState
import com.example.data.model.InstallFeedback
import com.example.data.model.ReleaseLoadState
import com.example.ui.theme.BgDeepViolet
import com.example.ui.theme.BgMidnight
import com.example.ui.theme.PurpleFocusRing
import com.example.ui.theme.PurplePrimary
import com.example.ui.theme.PurplePrimaryBright
import com.example.ui.theme.PurpleSubtleBorder
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusErrorContainer
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusSuccessContainer
import com.example.ui.theme.StatusWarning
import com.example.ui.theme.StatusWarningContainer
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceCardElevated
import com.example.ui.theme.SurfaceFocused
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.YouTubeRed

@Composable
fun ZDownloaderScreen(
    state: ZDownloaderUiState,
    hasUserRepoOverride: Boolean,
    onRefresh: () -> Unit,
    onDownloadClick: () -> Unit,
    onCancelDownloadClick: () -> Unit,
    onInstallClick: () -> Unit,
    onDeleteApkClick: () -> Unit,
    onOpenUnknownSourcesSettings: () -> Unit,
    onOpenRepoDialog: () -> Unit,
    onCloseRepoDialog: () -> Unit,
    onSaveRepo: (String) -> Unit,
    onResetRepo: () -> Unit,
) {
  val primaryActionFocusRequester = remember { FocusRequester() }
  val scrollState = rememberScrollState()

  LaunchedEffect(state.releaseState) {
    try {
      primaryActionFocusRequester.requestFocus()
    } catch (_: Exception) {
      // Focus requester may not yet be attached on initial composition frame.
    }
  }

  Box(
      modifier =
          Modifier.fillMaxSize()
              .background(
                  Brush.verticalGradient(
                      colors = listOf(BgMidnight, BgDeepViolet, BgMidnight),
                  )
              )
              .windowInsetsPadding(WindowInsets.safeDrawing)
  ) {
    Column(
        modifier =
            Modifier.fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 28.dp, vertical = 22.dp)
                .widthIn(max = 1120.dp)
                .align(Alignment.TopCenter),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
      HeaderBar(
          state = state,
          onRefresh = onRefresh,
          onOpenRepoDialog = onOpenRepoDialog,
      )

      Text(
          text = stringResource(R.string.section_available_apps),
          style = MaterialTheme.typography.titleLarge,
          color = TextPrimary,
          modifier = Modifier.testTag("section_title"),
      )

      when (val release = state.releaseState) {
        ReleaseLoadState.Loading -> LoadingCard()
        ReleaseLoadState.RepoNotConfigured ->
            RepoNotConfiguredCard(
                focusRequester = primaryActionFocusRequester,
                onOpenRepoDialog = onOpenRepoDialog,
            )
        is ReleaseLoadState.Error ->
            ErrorStateCard(
                message = release.message,
                focusRequester = primaryActionFocusRequester,
                onRetry = onRefresh,
                onOpenRepoDialog = onOpenRepoDialog,
            )
        is ReleaseLoadState.Ready ->
            YouTubeAppCard(
                readyState = release,
                downloadState = state.downloadState,
                installFeedback = state.installFeedback,
                primaryFocusRequester = primaryActionFocusRequester,
                onDownloadClick = onDownloadClick,
                onCancelDownloadClick = onCancelDownloadClick,
                onInstallClick = onInstallClick,
                onDeleteApkClick = onDeleteApkClick,
                onOpenUnknownSourcesSettings = onOpenUnknownSourcesSettings,
            )
      }

      DeviceFooterStrip(
          deviceInfo = state.deviceInfo,
          configuredRepo = state.configuredRepo,
          releaseTag = state.releaseTag,
      )
    }

    if (state.isRepoDialogOpen) {
      BackHandler { onCloseRepoDialog() }
      RepoConfigDialog(
          currentRepo = state.configuredRepo,
          hasUserOverride = hasUserRepoOverride,
          onDismiss = onCloseRepoDialog,
          onSave = onSaveRepo,
          onReset = onResetRepo,
      )
    }
  }
}

@Composable
private fun HeaderBar(
    state: ZDownloaderUiState,
    onRefresh: () -> Unit,
    onOpenRepoDialog: () -> Unit,
) {
  BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
    val isCompactWidth = maxWidth < 680.dp

    if (isCompactWidth) {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BrandTitleRow(state)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          TvFocusButton(
              text = stringResource(R.string.action_refresh),
              icon = Icons.Default.Refresh,
              iconContentDescription = stringResource(R.string.cd_refresh_icon),
              onClick = onRefresh,
              isPrimary = false,
              modifier = Modifier.testTag("refresh_button"),
          )
          TvFocusButton(
              text = stringResource(R.string.action_repo_settings),
              icon = Icons.Default.Settings,
              iconContentDescription = stringResource(R.string.cd_settings_icon),
              onClick = onOpenRepoDialog,
              isPrimary = false,
              modifier = Modifier.testTag("repo_settings_button"),
          )
        }
      }
    } else {
      Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        BrandTitleRow(state, modifier = Modifier.weight(1f))
        Spacer(modifier = Modifier.width(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          TvFocusButton(
              text = stringResource(R.string.action_refresh),
              icon = Icons.Default.Refresh,
              iconContentDescription = stringResource(R.string.cd_refresh_icon),
              onClick = onRefresh,
              isPrimary = false,
              modifier = Modifier.testTag("refresh_button"),
          )
          TvFocusButton(
              text = stringResource(R.string.action_repo_settings),
              icon = Icons.Default.Settings,
              iconContentDescription = stringResource(R.string.cd_settings_icon),
              onClick = onOpenRepoDialog,
              isPrimary = false,
              modifier = Modifier.testTag("repo_settings_button"),
          )
        }
      }
    }
  }
}

@Composable
private fun BrandTitleRow(state: ZDownloaderUiState, modifier: Modifier = Modifier) {
  Row(
      modifier = modifier,
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    Image(
        painter = painterResource(id = R.drawable.img_zdownloader_icon),
        contentDescription = stringResource(R.string.cd_app_logo),
        contentScale = ContentScale.Crop,
        modifier =
            Modifier.size(64.dp)
                .clip(RoundedCornerShape(16.dp))
                .border(1.5.dp, PurpleSubtleBorder, RoundedCornerShape(16.dp)),
    )

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineLarge,
            color = TextPrimary,
        )
        StatusPill(
            text = "v${BuildConfig.VERSION_NAME}",
            containerColor = SurfaceCardElevated,
            contentColor = PurpleFocusRing,
        )
        StatusPill(
            text = if (state.isNetworkConnected) "متصل" else "غير متصل",
            containerColor =
                if (state.isNetworkConnected) StatusSuccessContainer else StatusErrorContainer,
            contentColor = if (state.isNetworkConnected) StatusSuccess else StatusError,
        )
      }
      Text(
          text = stringResource(R.string.app_subtitle),
          style = MaterialTheme.typography.bodyMedium,
          color = TextSecondary,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@Composable
private fun YouTubeAppCard(
    readyState: ReleaseLoadState.Ready,
    downloadState: DownloadState,
    installFeedback: InstallFeedback,
    primaryFocusRequester: FocusRequester,
    onDownloadClick: () -> Unit,
    onCancelDownloadClick: () -> Unit,
    onInstallClick: () -> Unit,
    onDeleteApkClick: () -> Unit,
    onOpenUnknownSourcesSettings: () -> Unit,
) {
  val metadata = readyState.metadata
  val displaySize =
      ApkVerifierAndInstaller.formatBytes(
          metadata.fileSizeBytes.takeIf { it > 0L } ?: readyState.release.apkSizeBytes
      )

  Surface(
      shape = MaterialTheme.shapes.large,
      color = SurfaceCard,
      border = BorderStroke(1.5.dp, PurpleSubtleBorder),
      modifier = Modifier.fillMaxWidth().testTag("youtube_card"),
  ) {
    Column(
        modifier = Modifier.padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
      Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(18.dp),
      ) {
        YouTubeLogoBadge(modifier = Modifier.size(width = 96.dp, height = 68.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Text(
                text = metadata.appName,
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary,
                modifier = Modifier.testTag("youtube_app_name"),
            )
            if (readyState.isAlreadyInstalledOnDevice) {
              StatusPill(
                  text = stringResource(R.string.status_installed),
                  containerColor = StatusSuccessContainer,
                  contentColor = StatusSuccess,
              )
            }
          }

          Text(
              text = "${metadata.packageName} • Release ${readyState.release.tagName}",
              style = MaterialTheme.typography.bodyMedium,
              color = TextMuted,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
          )
        }
      }

      // Key Specs Grid
      BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val twoColumns = maxWidth < 680.dp
        if (twoColumns) {
          Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
              SpecTile(
                  label = stringResource(R.string.label_version),
                  value = metadata.versionName,
                  modifier = Modifier.weight(1f),
              )
              SpecTile(
                  label = stringResource(R.string.label_size),
                  value = displaySize,
                  modifier = Modifier.weight(1f),
              )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
              SpecTile(
                  label = stringResource(R.string.label_min_android),
                  value = "API ${metadata.minSdk}+",
                  modifier = Modifier.weight(1f),
              )
              SpecTile(
                  label = stringResource(R.string.label_architecture),
                  value = metadata.supportedAbis.ifEmpty { listOf("universal") }.joinToString(", "),
                  modifier = Modifier.weight(1f),
              )
            }
          }
        } else {
          Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            SpecTile(
                label = stringResource(R.string.label_version),
                value = metadata.versionName,
                modifier = Modifier.weight(1f),
            )
            SpecTile(
                label = stringResource(R.string.label_size),
                value = displaySize,
                modifier = Modifier.weight(1f),
            )
            SpecTile(
                label = stringResource(R.string.label_min_android),
                value = "Android API ${metadata.minSdk}+",
                modifier = Modifier.weight(1f),
            )
            SpecTile(
                label = stringResource(R.string.label_architecture),
                value = metadata.supportedAbis.ifEmpty { listOf("universal") }.joinToString(", "),
                modifier = Modifier.weight(1f),
            )
          }
        }
      }

      CompatibilityBanner(compatibility = readyState.compatibility)

      when (downloadState) {
        is DownloadState.Downloading -> {
          val downloadedFormatted = ApkVerifierAndInstaller.formatBytes(downloadState.downloadedBytes)
          val totalFormatted =
              if (downloadState.totalBytes > 0L) {
                ApkVerifierAndInstaller.formatBytes(downloadState.totalBytes)
              } else {
                "غير محدد"
              }
          val fraction =
              if (downloadState.totalBytes > 0L) {
                (downloadState.downloadedBytes.toFloat() / downloadState.totalBytes.toFloat())
                    .coerceIn(0f, 1f)
              } else {
                null
              }

          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Text(
                  text = "جاري تنزيل youtube.apk...",
                  style = MaterialTheme.typography.titleMedium,
                  color = PurpleFocusRing,
              )
              Text(
                  text =
                      if (fraction != null) {
                        "$downloadedFormatted / $totalFormatted (${(fraction * 100).toInt()}%)"
                      } else {
                        downloadedFormatted
                      },
                  style = MaterialTheme.typography.bodyLarge,
                  color = TextPrimary,
              )
            }

            if (fraction != null) {
              LinearProgressIndicator(
                  progress = { fraction },
                  color = PurplePrimaryBright,
                  trackColor = SurfaceCardElevated,
                  modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(8.dp)),
              )
            } else {
              LinearProgressIndicator(
                  color = PurplePrimaryBright,
                  trackColor = SurfaceCardElevated,
                  modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(8.dp)),
              )
            }
          }
        }
        DownloadState.Verifying -> {
          Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            CircularProgressIndicator(
                color = PurplePrimaryBright,
                modifier = Modifier.size(24.dp),
                strokeWidth = 3.dp,
            )
            Text(
                text = "جاري التحقق من سلامة الحزمة وبصمة SHA-256...",
                style = MaterialTheme.typography.bodyLarge,
                color = PurpleFocusRing,
            )
          }
        }
        is DownloadState.ReadyToInstall -> {
          Surface(
              shape = MaterialTheme.shapes.small,
              color = StatusSuccessContainer,
              border = BorderStroke(1.dp, StatusSuccess.copy(alpha = 0.5f)),
              modifier = Modifier.fillMaxWidth(),
          ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Icon(
                  imageVector = Icons.Default.Check,
                  contentDescription = null,
                  tint = StatusSuccess,
              )
              Text(
                  text =
                      "${stringResource(R.string.status_verified)}: ${downloadState.verifiedSha256.take(16)}...",
                  style = MaterialTheme.typography.bodyMedium,
                  color = StatusSuccess,
              )
            }
          }
        }
        is DownloadState.Error -> {
          Surface(
              shape = MaterialTheme.shapes.small,
              color = StatusErrorContainer,
              border = BorderStroke(1.dp, StatusError.copy(alpha = 0.6f)),
              modifier = Modifier.fillMaxWidth(),
          ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Icon(
                  imageVector = Icons.Default.Warning,
                  contentDescription = null,
                  tint = StatusError,
              )
              Text(
                  text = downloadState.message,
                  style = MaterialTheme.typography.bodyMedium,
                  color = TextPrimary,
              )
            }
          }
        }
        DownloadState.Idle -> Unit
      }

      if (installFeedback !is InstallFeedback.None) {
        InstallFeedbackBanner(
            feedback = installFeedback,
            onOpenUnknownSourcesSettings = onOpenUnknownSourcesSettings,
        )
      }

      // Action Row
      Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(14.dp),
          verticalAlignment = Alignment.CenterVertically,
      ) {
        when (downloadState) {
          is DownloadState.Downloading -> {
            TvFocusButton(
                text = stringResource(R.string.action_cancel_download),
                icon = Icons.Default.Close,
                iconContentDescription = null,
                onClick = onCancelDownloadClick,
                isPrimary = false,
                modifier = Modifier.focusRequester(primaryFocusRequester).testTag("cancel_button"),
            )
          }
          DownloadState.Verifying -> {
            TvFocusButton(
                text = "جاري التحقق...",
                icon = null,
                iconContentDescription = null,
                onClick = {},
                enabled = false,
                isPrimary = true,
                modifier = Modifier.focusRequester(primaryFocusRequester),
            )
          }
          is DownloadState.ReadyToInstall -> {
            val installLabel =
                if (readyState.isAlreadyInstalledOnDevice) {
                  stringResource(R.string.action_reinstall_youtube)
                } else {
                  stringResource(R.string.action_install_youtube)
                }
            TvFocusButton(
                text = installLabel,
                icon = Icons.Default.Check,
                iconContentDescription = null,
                onClick = onInstallClick,
                enabled = readyState.compatibility.isCompatible,
                isPrimary = true,
                modifier = Modifier.focusRequester(primaryFocusRequester).testTag("install_button"),
            )
            TvFocusButton(
                text = stringResource(R.string.action_delete_apk),
                icon = Icons.Default.Delete,
                iconContentDescription = null,
                onClick = onDeleteApkClick,
                isPrimary = false,
                modifier = Modifier.testTag("delete_apk_button"),
            )
          }
          DownloadState.Idle,
          is DownloadState.Error -> {
            TvFocusButton(
                text = stringResource(R.string.action_download_youtube),
                icon = null,
                iconContentDescription = null,
                onClick = onDownloadClick,
                enabled = readyState.compatibility.isCompatible,
                isPrimary = true,
                modifier =
                    Modifier.focusRequester(primaryFocusRequester).testTag("download_button"),
            )
          }
        }
      }
    }
  }
}

@Composable
private fun CompatibilityBanner(compatibility: CompatibilityResult) {
  val bg = if (compatibility.isCompatible) StatusSuccessContainer else StatusErrorContainer
  val fg = if (compatibility.isCompatible) StatusSuccess else StatusError

  Surface(
      shape = MaterialTheme.shapes.small,
      color = bg,
      border = BorderStroke(1.dp, fg.copy(alpha = 0.55f)),
      modifier = Modifier.fillMaxWidth().testTag("compatibility_banner"),
  ) {
    Row(
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Icon(
          imageVector = if (compatibility.isCompatible) Icons.Default.Check else Icons.Default.Warning,
          contentDescription = null,
          tint = fg,
      )
      Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = compatibility.summary,
            style = MaterialTheme.typography.titleMedium,
            color = fg,
        )
        if (!compatibility.reason.isNullOrBlank()) {
          Text(
              text = compatibility.reason,
              style = MaterialTheme.typography.bodyMedium,
              color = TextPrimary,
          )
        }
      }
    }
  }
}

@Composable
private fun InstallFeedbackBanner(
    feedback: InstallFeedback,
    onOpenUnknownSourcesSettings: () -> Unit,
) {
  val (bg, fg, msg, showSettingsBtn) =
      when (feedback) {
        is InstallFeedback.Success ->
            Quad(StatusSuccessContainer, StatusSuccess, feedback.message, false)
        is InstallFeedback.Warning ->
            Quad(
                StatusWarningContainer,
                StatusWarning,
                feedback.message,
                feedback.requiresUnknownSourcesSettings,
            )
        is InstallFeedback.Error ->
            Quad(StatusErrorContainer, StatusError, feedback.message, false)
        InstallFeedback.None -> return
      }

  Surface(
      shape = MaterialTheme.shapes.small,
      color = bg,
      border = BorderStroke(1.dp, fg.copy(alpha = 0.6f)),
      modifier = Modifier.fillMaxWidth().testTag("install_feedback_banner"),
  ) {
    Column(
        modifier = Modifier.padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Text(
          text = msg,
          style = MaterialTheme.typography.bodyLarge,
          color = TextPrimary,
      )
      if (showSettingsBtn) {
        TvFocusButton(
            text = stringResource(R.string.action_allow_unknown_sources),
            icon = Icons.Default.Settings,
            iconContentDescription = null,
            onClick = onOpenUnknownSourcesSettings,
            isPrimary = false,
            modifier = Modifier.testTag("unknown_sources_button"),
        )
      }
    }
  }
}

private data class Quad(
    val bg: Color,
    val fg: Color,
    val message: String,
    val showSettings: Boolean,
)

@Composable
private fun LoadingCard() {
  Surface(
      shape = MaterialTheme.shapes.large,
      color = SurfaceCard,
      border = BorderStroke(1.5.dp, PurpleSubtleBorder),
      modifier = Modifier.fillMaxWidth().testTag("loading_card"),
  ) {
    Row(
        modifier = Modifier.padding(28.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
      CircularProgressIndicator(
          color = PurplePrimaryBright,
          modifier = Modifier.size(36.dp),
          strokeWidth = 3.5.dp,
      )
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.status_loading),
            style = MaterialTheme.typography.titleLarge,
            color = TextPrimary,
        )
        Text(
            text = "جاري التحقق من youtube.apk وملف youtube-metadata.json بدون حساب GitHub...",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
        )
      }
    }
  }
}

@Composable
private fun RepoNotConfiguredCard(
    focusRequester: FocusRequester,
    onOpenRepoDialog: () -> Unit,
) {
  Surface(
      shape = MaterialTheme.shapes.large,
      color = SurfaceCard,
      border = BorderStroke(1.5.dp, StatusWarning.copy(alpha = 0.6f)),
      modifier = Modifier.fillMaxWidth().testTag("repo_not_configured_card"),
  ) {
    Column(
        modifier = Modifier.padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Text(
          text = "مطلوب تحديد مستودع GitHub العام",
          style = MaterialTheme.typography.headlineMedium,
          color = StatusWarning,
      )
      Text(
          text = stringResource(R.string.repo_missing_error),
          style = MaterialTheme.typography.bodyLarge,
          color = TextSecondary,
      )
      TvFocusButton(
          text = stringResource(R.string.repo_config_title),
          icon = Icons.Default.Settings,
          iconContentDescription = null,
          onClick = onOpenRepoDialog,
          isPrimary = true,
          modifier = Modifier.focusRequester(focusRequester).testTag("configure_repo_cta"),
      )
    }
  }
}

@Composable
private fun ErrorStateCard(
    message: String,
    focusRequester: FocusRequester,
    onRetry: () -> Unit,
    onOpenRepoDialog: () -> Unit,
) {
  Surface(
      shape = MaterialTheme.shapes.large,
      color = SurfaceCard,
      border = BorderStroke(1.5.dp, StatusError.copy(alpha = 0.65f)),
      modifier = Modifier.fillMaxWidth().testTag("error_card"),
  ) {
    Column(
        modifier = Modifier.padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            tint = StatusError,
            modifier = Modifier.size(28.dp),
        )
        Text(
            text = "تعذّر جلب بيانات الإصدار",
            style = MaterialTheme.typography.headlineMedium,
            color = StatusError,
        )
      }
      Text(
          text = message,
          style = MaterialTheme.typography.bodyLarge,
          color = TextPrimary,
      )
      Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        TvFocusButton(
            text = stringResource(R.string.action_retry),
            icon = Icons.Default.Refresh,
            iconContentDescription = null,
            onClick = onRetry,
            isPrimary = true,
            modifier = Modifier.focusRequester(focusRequester).testTag("retry_button"),
        )
        TvFocusButton(
            text = stringResource(R.string.action_repo_settings),
            icon = Icons.Default.Settings,
            iconContentDescription = null,
            onClick = onOpenRepoDialog,
            isPrimary = false,
        )
      }
    }
  }
}

@Composable
private fun DeviceFooterStrip(
    deviceInfo: DeviceInfo,
    configuredRepo: String,
    releaseTag: String,
) {
  Surface(
      shape = MaterialTheme.shapes.medium,
      color = BgDeepViolet.copy(alpha = 0.85f),
      border = BorderStroke(1.dp, PurpleSubtleBorder.copy(alpha = 0.6f)),
      modifier = Modifier.fillMaxWidth().testTag("device_footer"),
  ) {
    Column(
        modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      val abiText =
          if (deviceInfo.supportedAbis.isEmpty()) "غير محدد"
          else deviceInfo.supportedAbis.joinToString(", ")
      Text(
          text =
              "${stringResource(R.string.label_device_info)}: ${deviceInfo.manufacturer} ${deviceInfo.model} • Android ${deviceInfo.androidVersion} (API ${deviceInfo.sdkInt}) • المعالج: $abiText",
          style = MaterialTheme.typography.bodyMedium,
          color = TextSecondary,
      )
      Text(
          text =
              "المصدر: ${configuredRepo.ifBlank { "غير مضبوط" }} • الوسم: $releaseTag • التصفح: أزرار الاتجاهات + OK + Back",
          style = MaterialTheme.typography.labelMedium,
          color = TextMuted,
      )
    }
  }
}

@Composable
private fun SpecTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
  Surface(
      shape = MaterialTheme.shapes.small,
      color = SurfaceCardElevated,
      border = BorderStroke(1.dp, PurpleSubtleBorder.copy(alpha = 0.7f)),
      modifier = modifier,
  ) {
    Column(
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      Text(
          text = label,
          style = MaterialTheme.typography.labelMedium,
          color = TextMuted,
      )
      Text(
          text = value,
          style = MaterialTheme.typography.titleMedium,
          color = TextPrimary,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@Composable
private fun StatusPill(
    text: String,
    containerColor: Color,
    contentColor: Color,
) {
  Surface(
      shape = CircleShape,
      color = containerColor,
      border = BorderStroke(1.dp, contentColor.copy(alpha = 0.45f)),
  ) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = contentColor,
        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
    )
  }
}

@Composable
private fun YouTubeLogoBadge(modifier: Modifier = Modifier) {
  val desc = stringResource(R.string.cd_youtube_logo)
  Box(
      modifier = modifier,
      contentAlignment = Alignment.Center,
  ) {
    Canvas(modifier = Modifier.fillMaxSize()) {
      drawRoundRect(
          color = YouTubeRed,
          cornerRadius = CornerRadius(size.height * 0.28f, size.height * 0.28f),
      )
      val cx = size.width / 2f
      val cy = size.height / 2f
      val triH = size.height * 0.44f
      val triW = triH * 0.88f
      val path =
          Path().apply {
            moveTo(cx - triW * 0.42f, cy - triH * 0.5f)
            lineTo(cx + triW * 0.58f, cy)
            lineTo(cx - triW * 0.42f, cy + triH * 0.5f)
            close()
          }
      drawPath(path = path, color = Color.White)
    }
    // Invisible semantic description anchor without extra resource allocation
    Spacer(modifier = Modifier.size(1.dp).testTag(desc))
  }
}

@Composable
fun TvFocusButton(
    text: String,
    icon: ImageVector?,
    iconContentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isPrimary: Boolean = true,
) {
  var isFocused by remember { mutableStateOf(false) }
  val interactionSource = remember { MutableInteractionSource() }

  val bgColor =
      when {
        !enabled -> SurfaceCardElevated.copy(alpha = 0.5f)
        isFocused && isPrimary -> PurplePrimaryBright
        isFocused -> SurfaceFocused
        isPrimary -> PurplePrimary
        else -> SurfaceCardElevated
      }

  val borderColor =
      when {
        isFocused -> PurpleFocusRing
        isPrimary -> PurplePrimaryBright.copy(alpha = 0.7f)
        else -> PurpleSubtleBorder
      }

  val borderWidth = if (isFocused) 3.dp else 1.5.dp
  val contentColor = if (enabled) TextPrimary else TextMuted

  Surface(
      shape = RoundedCornerShape(14.dp),
      color = bgColor,
      border = BorderStroke(borderWidth, borderColor),
      modifier =
          modifier
              .height(56.dp)
              .clip(RoundedCornerShape(14.dp))
              .onFocusChanged { isFocused = it.isFocused }
              .focusable(enabled = enabled, interactionSource = interactionSource)
              .onPreviewKeyEvent { keyEvent ->
                if (enabled &&
                    keyEvent.type == KeyEventType.KeyUp &&
                    (keyEvent.key == Key.DirectionCenter ||
                        keyEvent.key == Key.Enter ||
                        keyEvent.key == Key.NumPadEnter)) {
                  onClick()
                  true
                } else {
                  false
                }
              }
              .clickable(
                  enabled = enabled,
                  interactionSource = interactionSource,
                  indication = ripple(color = PurpleFocusRing),
                  onClick = onClick,
              ),
  ) {
    Row(
        modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      if (icon != null) {
        Icon(
            imageVector = icon,
            contentDescription = iconContentDescription,
            tint = contentColor,
            modifier = Modifier.size(22.dp),
        )
      }
      Text(
          text = text,
          style = MaterialTheme.typography.labelLarge,
          color = contentColor,
          fontWeight = FontWeight.Bold,
      )
    }
  }
}

@Composable
private fun RepoConfigDialog(
    currentRepo: String,
    hasUserOverride: Boolean,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onReset: () -> Unit,
) {
  var repoInput by remember(currentRepo) { mutableStateOf(currentRepo) }
  val isValid = RepoConfigStore.isValidOwnerRepo(repoInput)

  Dialog(onDismissRequest = onDismiss) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = SurfaceCard,
        border = BorderStroke(2.dp, PurplePrimary),
        modifier = Modifier.fillMaxWidth().widthIn(max = 560.dp),
    ) {
      Column(
          modifier = Modifier.padding(24.dp),
          verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        Text(
            text = stringResource(R.string.repo_config_title),
            style = MaterialTheme.typography.headlineMedium,
            color = TextPrimary,
        )
        Text(
            text = stringResource(R.string.repo_config_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
        )

        OutlinedTextField(
            value = repoInput,
            onValueChange = { repoInput = it },
            singleLine = true,
            label = { Text("owner/repo") },
            colors =
                OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PurpleFocusRing,
                    unfocusedBorderColor = PurpleSubtleBorder,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedLabelColor = PurpleFocusRing,
                    unfocusedLabelColor = TextMuted,
                ),
            modifier = Modifier.fillMaxWidth().testTag("repo_input_field"),
        )

        if (repoInput.isNotBlank() && !isValid) {
          Text(
              text = "يرجى إدخال الاسم بصيغة owner/repo فقط (مثال: username/Z-Downloader).",
              style = MaterialTheme.typography.bodyMedium,
              color = StatusError,
          )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          TvFocusButton(
              text = stringResource(R.string.repo_config_save),
              icon = Icons.Default.Check,
              iconContentDescription = null,
              onClick = { onSave(repoInput) },
              enabled = isValid,
              isPrimary = true,
              modifier = Modifier.testTag("repo_save_button"),
          )
          TvFocusButton(
              text = stringResource(R.string.repo_config_cancel),
              icon = Icons.Default.Close,
              iconContentDescription = null,
              onClick = onDismiss,
              isPrimary = false,
          )
          if (hasUserOverride && BuildConfig.GITHUB_REPOSITORY.isNotBlank()) {
            TvFocusButton(
                text = "الافتراضي",
                icon = Icons.Default.Refresh,
                iconContentDescription = null,
                onClick = onReset,
                isPrimary = false,
            )
          }
        }
      }
    }
  }
}
