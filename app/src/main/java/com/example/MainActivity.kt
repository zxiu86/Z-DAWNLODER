package com.example

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.ZDownloaderScreen
import com.example.ui.ZDownloaderViewModel
import com.example.ui.theme.ZDownloaderTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    setContent {
      ZDownloaderTheme {
        val viewModel: ZDownloaderViewModel = viewModel()
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()

        val installLauncher =
            rememberLauncherForActivityResult(
                contract = ActivityResultContracts.StartActivityForResult()
            ) { result ->
              viewModel.onInstallActivityResult(result.resultCode)
            }

        val unknownSourcesLauncher =
            rememberLauncherForActivityResult(
                contract = ActivityResultContracts.StartActivityForResult()
            ) {
              viewModel.onUnknownSourcesSettingsReturned()
            }

        val openUnknownSourcesSettings = {
          val intent =
              if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:$packageName"),
                )
              } else {
                Intent(Settings.ACTION_SECURITY_SETTINGS)
              }
          unknownSourcesLauncher.launch(intent)
        }

        ZDownloaderScreen(
            state = uiState,
            hasUserRepoOverride = viewModel.hasUserRepoOverride(),
            onRefresh = viewModel::refreshRelease,
            onDownloadClick = viewModel::startDownload,
            onCancelDownloadClick = viewModel::cancelDownload,
            onInstallClick = {
              viewModel.prepareAndLaunchInstall(
                  onLaunchInstaller = { installIntent -> installLauncher.launch(installIntent) },
                  onRequestUnknownSources = openUnknownSourcesSettings,
              )
            },
            onDeleteApkClick = viewModel::deleteDownloadedApk,
            onOpenUnknownSourcesSettings = openUnknownSourcesSettings,
            onOpenRepoDialog = viewModel::openRepoDialog,
            onCloseRepoDialog = viewModel::closeRepoDialog,
            onSaveRepo = viewModel::saveRepository,
            onResetRepo = viewModel::resetRepositoryToBuildDefault,
        )
      }
    }
  }
}
