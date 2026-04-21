package com.nightlynexus.touchblocker

import android.app.Application
import com.nightlynexus.featureunlocker.FeatureUnlocker

class TouchBlockerApplication : Application() {
  internal lateinit var floatingViewStatus: FloatingViewStatus
  internal lateinit var keepScreenOnStatus: KeepScreenOnStatus
  internal lateinit var changeScreenBrightnessStatus: ChangeScreenBrightnessStatus
  internal lateinit var floatingLockViewSizeStatus: FloatingLockViewSizeStatus
  internal lateinit var shouldRequestAddTileServiceStatus: ShouldRequestAddTileServiceStatus
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
    changeScreenBrightnessStatus = ChangeScreenBrightnessStatus(
      getSharedPreferences(
        "change_screen_brightness_status",
        MODE_PRIVATE
      )
    )
    floatingLockViewSizeStatus = FloatingLockViewSizeStatus(
      getSharedPreferences(
        "floating_lock_view_size_status",
        MODE_PRIVATE
      )
    )
    shouldRequestAddTileServiceStatus = ShouldRequestAddTileServiceStatus(
      getSharedPreferences(
        "should_request_add_tile_service_status",
        MODE_PRIVATE
      )
    )

    accessibilityPermissionRequestTracker = AccessibilityPermissionRequestTracker()

    featureUnlocker = provideFeatureUnlocker(this)

    floatingViewStatus.addListener(object: FloatingViewStatus.Listener {
      override fun onFloatingViewAdded() {
        updateTileService(this@TouchBlockerApplication)
      }

      override fun onFloatingViewRemoved() {
        updateTileService(this@TouchBlockerApplication)
      }

      override fun onFloatingViewLocked() {
        // TODO: updateTileService(this@TouchBlockerApplication)
      }

      override fun onFloatingViewUnlocked() {
        // TODO: updateTileService(this@TouchBlockerApplication)
      }

      override fun onFloatingViewPermissionGranted() {
        updateTileService(this@TouchBlockerApplication)
      }

      override fun onFloatingViewPermissionRevoked() {
        updateTileService(this@TouchBlockerApplication)
      }

      override fun onToggle() {
        // No-op.
      }
    })
    updateTileService(this)

    featureUnlocker.addListener(object : FeatureUnlocker.Listener {
      override fun stateChanged(state: FeatureUnlocker.State) {
        if (state !== FeatureUnlocker.State.Purchased) {
          keepScreenOnStatus.setKeepScreenOn(false)
          changeScreenBrightnessStatus.setChangeScreenBrightness(false)
        }
      }
    })
    featureUnlocker.startConnection()
  }
}
