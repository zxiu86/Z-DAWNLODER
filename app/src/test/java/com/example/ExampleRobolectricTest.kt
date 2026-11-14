package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `app name and package configuration match Z-Downloader`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Z-Downloader", appName)
    assertEquals("com.zxiu86.zdownloader", context.packageName)
    assertEquals("0.0.011", BuildConfig.YOUTUBE_RELEASE_TAG)
  }

  @Test
  fun `MainActivity launches and creates ZDownloaderViewModel without crashing`() {
    val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
    val activity = controller.get()
    assertNotNull(activity)
  }
}
