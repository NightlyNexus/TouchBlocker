package com.nightlynexus.touchblocker

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.CompoundButton
import android.widget.TextView
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.nightlynexus.featureunlocker.FeatureUnlocker

class LauncherActivity : Activity(), FloatingViewStatus.Listener, KeepScreenOnStatus.Listener {
  private lateinit var floatingViewStatus: FloatingViewStatus
  private lateinit var keepScreenOnStatus: KeepScreenOnStatus
  private lateinit var accessibilityPermissionRequestTracker: AccessibilityPermissionRequestTracker
  private lateinit var featureUnlocker: FeatureUnlocker
  private lateinit var enableButton: TextView
  private lateinit var keepScreenOnCheckbox: CompoundButton

  override fun onCreate(savedInstanceState: Bundle?) {
    val application = application as TouchBlockerApplication
    floatingViewStatus = application.floatingViewStatus
    keepScreenOnStatus = application.keepScreenOnStatus
    accessibilityPermissionRequestTracker = application.accessibilityPermissionRequestTracker
    featureUnlocker = application.featureUnlocker

    installSplashScreen()

    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_launcher)
    enableButton = findViewById(R.id.enable)
    keepScreenOnCheckbox = findViewById(R.id.keep_screen_on)
    if (floatingViewStatus.added) {
      onFloatingViewAdded()
    } else if (floatingViewStatus.permissionGranted) {
      onFloatingViewRemoved()
    } else {
      onFloatingViewPermissionRevoked()
    }
    keepScreenOnCheckbox.isChecked = keepScreenOnStatus.getKeepScreenOn()
    keepScreenOnCheckbox.setOnCheckedChangeListener { _, isChecked ->
      if (keepScreenOnCheckbox.tag != null) {
        return@setOnCheckedChangeListener
      }
      if (featureUnlocker.state != FeatureUnlocker.State.Purchased) {
        setKeepScreenOnCheckboxCheckedWithoutCallingListener(false)
        featureUnlocker.buy(this)
      } else {
        keepScreenOnStatus.setKeepScreenOn(isChecked)
      }
    }
    floatingViewStatus.addListener(this)
  }

  override fun onDestroy() {
    super.onDestroy()
    floatingViewStatus.removeListener(this)
  }

  override fun onFloatingViewAdded() {
    enableButton.setText(R.string.enable_button_remove_floating)
    enableButton.setOnClickListener {
      floatingViewStatus.setAdded(false)
    }
  }

  override fun onFloatingViewRemoved() {
    enableButton.setText(R.string.enable_button_add_floating)
    enableButton.setOnClickListener {
      floatingViewStatus.setAdded(true)
    }
  }

  override fun onFloatingViewPermissionGranted() {
    onFloatingViewRemoved()
  }

  override fun onFloatingViewPermissionRevoked() {
    enableButton.setText(R.string.enable_button_accessibility_service)
    enableButton.setOnClickListener {
      showPermissionDialog()
    }
  }

  private fun showPermissionDialog() {
    val alertDialog = AlertDialog.Builder(this)
      .setView(R.layout.dialog_permission)
      .show()
    alertDialog.findViewById<View>(R.id.dialog_permission_button_confirm)!!.setOnClickListener {
      alertDialog.dismiss()
      requestPermission()
    }
    alertDialog.findViewById<View>(R.id.dialog_permission_button_cancel)!!.setOnClickListener {
      alertDialog.cancel()
    }
  }

  private fun requestPermission() {
    accessibilityPermissionRequestTracker.recordAccessibilityPermissionRequest()
    startActivity(accessibilityServicesSettingsIntent().apply {
      flags = Intent.FLAG_ACTIVITY_NEW_TASK
    })
  }

  override fun onToggle() {
    // No-op.
  }

  override fun update(keepScreenOn: Boolean) {
    setKeepScreenOnCheckboxCheckedWithoutCallingListener(keepScreenOn)
  }

  private fun setKeepScreenOnCheckboxCheckedWithoutCallingListener(checked: Boolean) {
    keepScreenOnCheckbox.tag = true
    keepScreenOnCheckbox.isChecked = checked
    keepScreenOnCheckbox.tag = null
  }
}
