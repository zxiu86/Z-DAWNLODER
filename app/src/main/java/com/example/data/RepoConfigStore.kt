package com.example.data

import android.content.Context
import com.example.BuildConfig

class RepoConfigStore(context: Context) {
  private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

  fun getConfiguredRepository(): String {
    val overrideRepo = prefs.getString(KEY_REPO_OVERRIDE, null)?.trim().orEmpty()
    if (overrideRepo.isNotEmpty()) {
      return overrideRepo
    }
    return BuildConfig.GITHUB_REPOSITORY.trim()
  }

  fun setRepositoryOverride(ownerRepo: String) {
    val normalized = ownerRepo.trim().removePrefix("https://github.com/").trim('/')
    prefs.edit().putString(KEY_REPO_OVERRIDE, normalized).apply()
  }

  fun clearRepositoryOverride() {
    prefs.edit().remove(KEY_REPO_OVERRIDE).apply()
  }

  fun hasUserOverride(): Boolean = !prefs.getString(KEY_REPO_OVERRIDE, null).isNullOrBlank()

  companion object {
    private const val PREFS_NAME = "zdownloader_config"
    private const val KEY_REPO_OVERRIDE = "github_repo_override"
    private val REPO_REGEX = Regex("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$")

    fun isValidOwnerRepo(value: String): Boolean {
      val cleaned = value.trim().removePrefix("https://github.com/").trim('/')
      return REPO_REGEX.matches(cleaned)
    }

    fun normalizeOwnerRepo(value: String): String =
        value.trim().removePrefix("https://github.com/").trim('/')
  }
}
