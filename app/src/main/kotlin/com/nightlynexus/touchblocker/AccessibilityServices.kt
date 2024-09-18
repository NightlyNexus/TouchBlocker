package com.nightlynexus.touchblocker

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.text.TextUtils.SimpleStringSplitter

// https://github.com/aosp-mirror/platform_frameworks_base/blob/232000e8b8978943d6385ec5dae2d97c9db10be6/packages/SettingsLib/src/com/android/settingslib/accessibility/AccessibilityUtils.java#L78
internal fun isAccessibilityServiceEnabled(
  context: Context,
  service: Class<out AccessibilityService>
): Boolean {
  val enabledServicesSetting = Settings.Secure.getString(
    context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
  )
  if (enabledServicesSetting == null) {
    return false
  }
  val componentName = ComponentName(context, service)
  val colonSplitter = SimpleStringSplitter(':')
  colonSplitter.setString(enabledServicesSetting)
  for (componentNameString in colonSplitter) {
    val enabledService = ComponentName.unflattenFromString(componentNameString)
    if (enabledService == componentName) {
      return true
    }
  }
  return false
}

internal fun accessibilityServicesSettingsIntent(): Intent {
  return Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
}
