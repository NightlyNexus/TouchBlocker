package com.nightlynexus.touchblocker

import android.app.Activity
import android.content.Intent
import android.os.Bundle

class NoDisplayActivity : Activity() {
  private lateinit var floatingViewStatus: FloatingViewStatus

  override fun onCreate(savedInstanceState: Bundle?) {
    val application = application as TouchBlockerApplication
    floatingViewStatus = application.floatingViewStatus
    super.onCreate(savedInstanceState)
    if (floatingViewStatus.permissionGranted) {
      floatingViewStatus.toggle()
    } else {
      startActivity(
        accessibilityServicesSettingsIntent().addFlags(
          Intent.FLAG_ACTIVITY_NEW_TASK or
            Intent.FLAG_ACTIVITY_CLEAR_TOP or
            Intent.FLAG_ACTIVITY_SINGLE_TOP
        )
      )
    }
    finish()
  }
}
