package com.nightlynexus.touchblocker

import android.app.Application
import com.nightlynexus.featureunlocker.FeatureUnlocker

class TouchBlockerApplication : Application() {
  internal lateinit var floatingViewStatusListener: FloatingViewStatus.Listener
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

    floatingViewStatusListener = object: FloatingViewStatus.Listener {
      private val context = this@TouchBlockerApplication

      override fun onFloatingViewAdded() {
        updateTileService(context)
      }

      override fun onFloatingViewRemoved() {
        updateTileService(context)
      }

      override fun onFloatingViewLocked() {
        updateTileService(context)
      }

      override fun onFloatingViewUnlocked() {
        updateTileService(context)
      }

      override fun onFloatingViewPermissionGranted() {
        updateTileService(context)
      }

      override fun onFloatingViewPermissionRevoked() {
        updateTileService(context)
      }

      override fun onToggle() {
        // No-op.
      }
    }
    floatingViewStatus.addListener(floatingViewStatusListener)
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
