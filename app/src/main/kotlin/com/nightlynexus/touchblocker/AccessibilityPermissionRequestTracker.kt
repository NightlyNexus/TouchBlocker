package com.nightlynexus.touchblocker

import android.os.SystemClock

internal class AccessibilityPermissionRequestTracker {
  private val maxDurationMillis = 120_000L
  private var lastAccessibilityPermissionRequestMillis = -1L

  fun recordAccessibilityPermissionRequest() {
    lastAccessibilityPermissionRequestMillis = SystemClock.uptimeMillis()
  }

  fun recentlyLaunchedAccessibilityPermissionRequest(): Boolean {
    val lastAccessibilityPermissionRequestMillis = lastAccessibilityPermissionRequestMillis
    if (lastAccessibilityPermissionRequestMillis == -1L) {
      return false
    }
    val durationMillis = SystemClock.uptimeMillis() - lastAccessibilityPermissionRequestMillis
    return durationMillis <= maxDurationMillis
  }
}
