package com.nightlynexus.touchblocker

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Point
import android.os.Build.VERSION.SDK_INT
import android.os.PowerManager
import android.view.GestureDetector
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat

class TouchBlockerAccessibilityService : AccessibilityService(),
  FloatingViewStatus.Listener, KeepScreenOnStatus.Listener {
  private val ACTION_START_BLOCKING_TOUCHES =
    "com.nightlynexus.touchblocker.ACTION_START_BLOCKING_TOUCHES"
  private val ACTION_END_BLOCKING_TOUCHES =
    "com.nightlynexus.touchblocker.ACTION_END_BLOCKING_TOUCHES"
  private val ACTION_TOGGLE_BLOCKING_TOUCHES =
    "com.nightlynexus.touchblocker.ACTION_TOGGLE_BLOCKING_TOUCHES"
  private val lockAnimateAlphaDelayMillis = 2000L
  private val lockAnimateAlphaPerSecond = 3f
  private val backgroundToastFadeInDurationMillis = 1000L
  private val backgroundToastFadeOutDelayMillis = 4000L
  private val backgroundToastFadeOutDurationMillis = 2500L

  private lateinit var floatingViewStatus: FloatingViewStatus
  private lateinit var keepScreenOnStatus: KeepScreenOnStatus
  private lateinit var accessibilityPermissionRequestTracker: AccessibilityPermissionRequestTracker
  private lateinit var windowManager: WindowManager
  private lateinit var backgroundView: FloatingBackgroundView
  private lateinit var backgroundViewLayoutParams: WindowManager.LayoutParams
  private lateinit var lockView: FloatingLockView
  private lateinit var lockViewLayoutParams: WindowManager.LayoutParams

  override fun onCreate() {
    val application = application as TouchBlockerApplication
    floatingViewStatus = application.floatingViewStatus
    keepScreenOnStatus = application.keepScreenOnStatus
    accessibilityPermissionRequestTracker = application.accessibilityPermissionRequestTracker
  }

  override fun onFloatingViewAdded() {
    windowManager.addView(backgroundView, backgroundViewLayoutParams)
    windowManager.addView(lockView, lockViewLayoutParams)
  }

  override fun onFloatingViewRemoved() {
    windowManager.removeView(lockView)
    windowManager.removeView(backgroundView)
    lockView.resetAlpha()
    backgroundView.cancelToast()
    backgroundView.setHasShownToast(false)
  }

  override fun onFloatingViewPermissionGranted() {
    // No-op.
  }

  override fun onFloatingViewPermissionRevoked() {
    // No-op.
  }

  override fun onServiceConnected() {
    windowManager = getSystemService(WindowManager::class.java)

    val width: Int
    val height: Int
    val insetLeft: Int
    val insetRight: Int
    val insetTop: Int
    val insetBottom: Int
    if (SDK_INT >= 30) {
      val currentWindowMetrics = windowManager.currentWindowMetrics
      val bounds = currentWindowMetrics.bounds
      width = bounds.width()
      height = bounds.height()
      val insets = currentWindowMetrics.windowInsets.getInsets(
        WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
      )
      insetLeft = insets.left
      insetRight = insets.right
      insetTop = insets.top
      insetBottom = insets.bottom
    } else {
      @Suppress("deprecation") val display = windowManager.defaultDisplay
      val displaySize = Point()
      @Suppress("deprecation") display.getRealSize(displaySize)
      width = displaySize.x
      height = displaySize.y
      // I don't think there's anything good we can do to prevent overlap of system bars on <30.
      insetLeft = 0
      insetRight = 0
      insetTop = 0
      insetBottom = 0
    }

    val powerInteractive = getSystemService(PowerManager::class.java).isInteractive
    val keyguardLocked = getSystemService(KeyguardManager::class.java).isKeyguardLocked
    val screenOn = powerInteractive && !keyguardLocked

    // We need both FLAG_LAYOUT_NO_LIMITS and FLAG_LAYOUT_IN_SCREEN
    // to draw the view over the status bar.
    // We need FLAG_FULLSCREEN to block touches to the status bar.
    backgroundViewLayoutParams = WindowManager.LayoutParams(
      width,
      height,
      WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
      WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
        WindowManager.LayoutParams.FLAG_FULLSCREEN,
      PixelFormat.TRANSLUCENT
    )
    if (keepScreenOnStatus.getKeepScreenOn()) {
      backgroundViewLayoutParams.flags = backgroundViewLayoutParams.flags or
        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
    }
    backgroundView = FloatingBackgroundView(
      this,
      insetLeft,
      insetTop,
      insetRight,
      insetBottom,
      backgroundToastFadeInDurationMillis,
      backgroundToastFadeOutDurationMillis,
      backgroundToastFadeOutDelayMillis,
      screenOn
    ).apply {
      setOnTouchListener(object : View.OnTouchListener {
        private val gestureDetector = GestureDetector(
          this@TouchBlockerAccessibilityService,
          BackgroundViewOnGestureListener()
        )

        override fun onTouch(v: View, event: MotionEvent): Boolean {
          return gestureDetector.onTouchEvent(event)
        }
      })
    }

    val lockWidth = resources.getDimensionPixelSize(R.dimen.lock_width)
    val lockHeight = resources.getDimensionPixelSize(R.dimen.lock_height)
    val maxOutOfScreenBounds = resources.getDimensionPixelSize(R.dimen.max_out_of_screen_bounds)
    val minX = -maxOutOfScreenBounds
    val minY = -maxOutOfScreenBounds
    val maxX = width - lockWidth + maxOutOfScreenBounds
    val maxY = height - lockHeight + maxOutOfScreenBounds
    val maxMoveDistanceForClick = resources.getDimension(R.dimen.max_move_distance_for_click)
    val lockAnimatePixelsPerSecond = resources.getDimensionPixelSize(
      R.dimen.lock_animate_dips_per_second
    )

    // We need both FLAG_LAYOUT_NO_LIMITS and FLAG_LAYOUT_IN_SCREEN
    // to draw the view over the status bar.
    // We need FLAG_FULLSCREEN to block touches to the status bar.
    lockViewLayoutParams = WindowManager.LayoutParams(
      lockWidth,
      lockHeight,
      WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
      WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
        WindowManager.LayoutParams.FLAG_FULLSCREEN or
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
      PixelFormat.TRANSLUCENT
    )
    lockViewLayoutParams.gravity = Gravity.START or Gravity.TOP

    lockViewLayoutParams.x = minX
    lockViewLayoutParams.y = (height - lockHeight) / 2

    val startFadeOutListener = Runnable {
      backgroundView.showToast()
    }

    lockView = FloatingLockView(
      this,
      windowManager,
      lockViewLayoutParams,
      maxMoveDistanceForClick,
      minX,
      minY,
      maxX,
      maxY,
      lockAnimatePixelsPerSecond,
      lockAnimateAlphaPerSecond,
      lockAnimateAlphaDelayMillis,
      screenOn,
      startFadeOutListener
    ).apply {
      setBackgroundResource(R.drawable.lock_background)
      val padding = resources.getDimensionPixelSize(R.dimen.lock_padding)
      setPadding(padding, padding, padding, padding)
      setOnClickListener(LockViewOnClickListener())
    }

    floatingViewStatus.addListener(this)
    floatingViewStatus.setPermissionGranted(true)

    keepScreenOnStatus.addListener(this)

    registerReceiver(screenOffBroadcastReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
    registerReceiver(screenOnBroadcastReceiver, IntentFilter(Intent.ACTION_SCREEN_ON))
    registerReceiver(unlockedBroadcastReceiver, IntentFilter(Intent.ACTION_USER_PRESENT))
    // TODO: Ensure this works when we have NO keyguard. if not, maybe we check the KeyguardManager.

    ContextCompat.registerReceiver(
      this,
      startBlockingTouchesBroadcastReceiver,
      IntentFilter(ACTION_START_BLOCKING_TOUCHES),
      ContextCompat.RECEIVER_EXPORTED
    )
    ContextCompat.registerReceiver(
      this,
      endBlockingTouchesBroadcastReceiver,
      IntentFilter(ACTION_END_BLOCKING_TOUCHES),
      ContextCompat.RECEIVER_EXPORTED
    )
    ContextCompat.registerReceiver(
      this,
      toggleBlockingTouchesBroadcastReceiver,
      IntentFilter(ACTION_TOGGLE_BLOCKING_TOUCHES),
      ContextCompat.RECEIVER_EXPORTED
    )

    if (accessibilityPermissionRequestTracker.recentlyLaunchedAccessibilityPermissionRequest()) {
      startActivity(
        Intent(
          this,
          LauncherActivity::class.java
        ).addFlags(
          Intent.FLAG_ACTIVITY_NEW_TASK or
            Intent.FLAG_ACTIVITY_CLEAR_TOP or
            Intent.FLAG_ACTIVITY_SINGLE_TOP
        )
      )
    }
  }

  override fun update(keepScreenOn: Boolean) {
    backgroundViewLayoutParams.flags = if (keepScreenOn) {
      backgroundViewLayoutParams.flags or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
    } else {
      backgroundViewLayoutParams.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON.inv()
    }
    if (floatingViewStatus.added) {
      windowManager.updateViewLayout(backgroundView, backgroundViewLayoutParams)
    }
  }

  override fun onDestroy() {
    if (lockView.locked) {
      unlock()
    }
    if (floatingViewStatus.added) {
      floatingViewStatus.setAdded(false)
    }
    floatingViewStatus.setPermissionGranted(false)
    floatingViewStatus.removeListener(this)
    keepScreenOnStatus.removeListener(this)
    unregisterReceiver(screenOffBroadcastReceiver)
    unregisterReceiver(screenOnBroadcastReceiver)
    unregisterReceiver(unlockedBroadcastReceiver)
    unregisterReceiver(startBlockingTouchesBroadcastReceiver)
    unregisterReceiver(endBlockingTouchesBroadcastReceiver)
    unregisterReceiver(toggleBlockingTouchesBroadcastReceiver)
  }

  private inner class BackgroundViewOnGestureListener : SimpleOnGestureListener() {
    override fun onDoubleTapEvent(e: MotionEvent): Boolean {
      if (e.actionMasked == MotionEvent.ACTION_UP) {
        lockView.fadeIn()
        return true
      }
      return false
    }
  }

  private inner class LockViewOnClickListener : View.OnClickListener {
    override fun onClick(v: View) {
      if (lockView.locked) {
        unlock()
      } else {
        lock()
      }
    }
  }

  private fun lock() {
    backgroundView.setLocked(true)
    lockView.setLocked(true)
  }

  private fun unlock() {
    backgroundView.setLocked(false)
    lockView.setLocked(false)
  }

  override fun onToggle() {
    if (floatingViewStatus.added) {
      if (lockView.locked) {
        unlock()
        floatingViewStatus.setAdded(false)
      } else {
        lock()
        lockView.resetFadeTimer()
      }
    } else {
      if (lockView.locked) {
        throw IllegalStateException("Not added but locked.")
      } else {
        floatingViewStatus.setAdded(true)
        lock()
        lockView.resetFadeTimer()
      }
    }
  }

  private val screenOffBroadcastReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
      backgroundView.setScreenOn(false)
      lockView.setScreenOn(false)
    }
  }

  private val screenOnBroadcastReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
      if (getSystemService(KeyguardManager::class.java).isKeyguardLocked) {
        return
      }
      backgroundView.setScreenOn(true)
      lockView.setScreenOn(true)
    }
  }

  private val unlockedBroadcastReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
      backgroundView.setScreenOn(true)
      lockView.setScreenOn(true)
    }
  }

  private val startBlockingTouchesBroadcastReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
      if (floatingViewStatus.added) {
        if (lockView.locked) {
          // Already blocking touches.
        } else {
          lock()
          lockView.resetFadeTimer()
        }
      } else {
        if (lockView.locked) {
          throw IllegalStateException("Not added but locked.")
        } else {
          floatingViewStatus.setAdded(true)
          lock()
          lockView.resetFadeTimer()
        }
      }
    }
  }

  private val endBlockingTouchesBroadcastReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
      if (floatingViewStatus.added) {
        if (lockView.locked) {
          unlock()
          floatingViewStatus.setAdded(false)
        } else {
          // Already not blocking touches.
        }
      } else {
        // Already not blocking touches.
      }
    }
  }

  private val toggleBlockingTouchesBroadcastReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
      floatingViewStatus.toggle()
    }
  }

  override fun onAccessibilityEvent(event: AccessibilityEvent) {
    // No-op.
  }

  override fun onInterrupt() {
    // No-op.
  }

  override fun onConfigurationChanged(newConfig: Configuration) {
    val width: Int
    val height: Int
    val insetLeft: Int
    val insetRight: Int
    val insetTop: Int
    val insetBottom: Int
    if (SDK_INT >= 30) {
      val currentWindowMetrics = windowManager.currentWindowMetrics
      val bounds = currentWindowMetrics.bounds
      width = bounds.width()
      height = bounds.height()
      val insets = currentWindowMetrics.windowInsets.getInsets(
        WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
      )
      insetLeft = insets.left
      insetRight = insets.right
      insetTop = insets.top
      insetBottom = insets.bottom
    } else {
      @Suppress("deprecation") val display = windowManager.defaultDisplay
      val displaySize = Point()
      @Suppress("deprecation") display.getRealSize(displaySize)
      width = displaySize.x
      height = displaySize.y
      // I don't think there's anything good we can do to prevent overlap of system bars on <30.
      insetLeft = 0
      insetRight = 0
      insetTop = 0
      insetBottom = 0
    }

    backgroundViewLayoutParams.width = width
    backgroundViewLayoutParams.height = height

    backgroundView.reconfigureToast(insetLeft, insetTop, insetRight, insetBottom)

    val lockWidth = resources.getDimensionPixelSize(R.dimen.lock_width)
    val lockHeight = resources.getDimensionPixelSize(R.dimen.lock_height)
    val maxOutOfScreenBounds = resources.getDimensionPixelSize(R.dimen.max_out_of_screen_bounds)
    val minX = -maxOutOfScreenBounds
    val minY = -maxOutOfScreenBounds
    val maxX = width - lockWidth + maxOutOfScreenBounds
    val maxY = height - lockHeight + maxOutOfScreenBounds

    lockView.reset(minX, (height - lockHeight) / 2, minX, minY, maxX, maxY)

    if (floatingViewStatus.added) {
      windowManager.updateViewLayout(backgroundView, backgroundViewLayoutParams)
      windowManager.updateViewLayout(lockView, lockViewLayoutParams)
    }
  }
}
