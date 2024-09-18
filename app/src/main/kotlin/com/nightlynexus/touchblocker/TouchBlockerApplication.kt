package com.nightlynexus.touchblocker

import android.app.Application
import com.nightlynexus.featureunlocker.FeatureUnlocker

class TouchBlockerApplication : Application() {
  internal lateinit var floatingViewStatus: FloatingViewStatus
  internal lateinit var keepScreenOnStatus: KeepScreenOnStatus
  internal lateinit var accessibilityPermissionRequestTracker: AccessibilityPermissionRequestTracker
  internal lateinit var featureUnlocker: FeatureUnlocker

  override fun onCreate() {
    super.onCreate()
    floatingViewStatus = FloatingViewStatus(isAccessibilityServiceEnabled(
      this,
      TouchBlockerAccessibilityService::class.java
    ))
    keepScreenOnStatus = KeepScreenOnStatus(
      getSharedPreferences(
        "keep_screen_on_status",
        MODE_PRIVATE
      )
    )
    accessibilityPermissionRequestTracker = AccessibilityPermissionRequestTracker()
    featureUnlocker = provideFeatureUnlocker(this)
    featureUnlocker.addListener(object : FeatureUnlocker.Listener {
      override fun stateChanged(state: FeatureUnlocker.State) {
        if (state !== FeatureUnlocker.State.Purchased) {
          keepScreenOnStatus.setKeepScreenOn(false)
        }
      }
    })
    featureUnlocker.startConnection()
  }
}
