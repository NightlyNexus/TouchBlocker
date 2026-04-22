package com.nightlynexus.touchblocker

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Rect
import android.os.Build.VERSION.SDK_INT
import android.view.LayoutInflater
import android.view.View
import android.view.ViewPropertyAnimator
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

@SuppressLint("ViewConstructor")
internal class FloatingBackgroundView(
  context: Context,
  insetLeft: Int,
  insetTop: Int,
  insetRight: Int,
  insetBottom: Int,
  private val backgroundToastFadeInDurationMillis: Long,
  private val backgroundToastFadeOutDurationMillis: Long,
  private val backgroundToastFadeOutDelayMillis: Long,
  private var screenOn: Boolean,
) : FrameLayout(context) {
  private val exclusionRects = listOf(Rect())
  private var backgroundToastView: View
  private var locked = false
  private var hasShownToast = false
  private var backgroundToastViewAlphaAnimator: ViewPropertyAnimator? = null

  init {
    val inflater = LayoutInflater.from(context)
    backgroundToastView = inflater.inflate(R.layout.background_toast, this, false)
    (backgroundToastView.layoutParams as LayoutParams).apply {
      setMargins(
        leftMargin + insetLeft,
        topMargin + insetTop,
        rightMargin + insetRight,
        bottomMargin + insetBottom
      )
    }
    backgroundToastView.alpha = 0f
    backgroundToastView.visibility = View.GONE
    addView(backgroundToastView)

    visibility = GONE

    ViewCompat.setOnApplyWindowInsetsListener(rootView) { _, insets ->
      val systemGestures = insets.getInsets(
        WindowInsetsCompat.Type.systemGestures()
      )
      // Gesture navigation arrived in SDK version 29.
      // Checking the left and right insets is probably good enough to detect gesture navigation.
      // If this check guesses incorrectly, hiding the navigation bar is not a heavy price to pay
      // for being wrong.
      setHideNavigation(SDK_INT >= 29 && (systemGestures.left != 0 || systemGestures.right != 0))
      insets
    }
  }

  fun setLocked(locked: Boolean) {
    this.locked = locked
    visibility = if (locked) {
      if (screenOn) {
        VISIBLE
      } else {
        GONE
      }
    } else {
      cancelToast()
      GONE
    }
  }

  fun setScreenOn(screenOn: Boolean) {
    this.screenOn = screenOn
    visibility = if (screenOn) {
      if (locked) {
        VISIBLE
      } else {
        GONE
      }
    } else {
      cancelToast()
      GONE
    }
  }

  fun setHasShownToast(hasShownToast: Boolean) {
    this.hasShownToast = hasShownToast
  }

  fun showToast() {
    if (hasShownToast) {
      return
    }
    backgroundToastView.visibility = VISIBLE
    backgroundToastViewAlphaAnimator = backgroundToastView.animate()
      .alpha(1f)
      .setDuration(backgroundToastFadeInDurationMillis)
      // We have to set the start delay, or else we will get the
      // backgroundToastFadeOutDelayMillis when coming back here.
      // The View caches the ViewPropertyAnimator instance.
      .setStartDelay(0L)
      .withEndAction {
        hasShownToast = true
        backgroundToastViewAlphaAnimator = backgroundToastView.animate()
          .alpha(0f)
          .setDuration(backgroundToastFadeOutDurationMillis)
          .setStartDelay(backgroundToastFadeOutDelayMillis)
          .withEndAction {
            backgroundToastView.visibility = GONE
          }
          .apply {
            start()
          }
      }
      .apply {
        start()
      }
  }

  fun cancelToast() {
    backgroundToastViewAlphaAnimator?.cancel()
    backgroundToastView.alpha = 0f
    backgroundToastView.visibility = GONE
  }

  fun reconfigureToast(
    insetLeft: Int,
    insetTop: Int,
    insetRight: Int,
    insetBottom: Int
  ) {
    backgroundToastViewAlphaAnimator?.cancel()

    removeView(backgroundToastView)
    val inflater = LayoutInflater.from(context)
    backgroundToastView = inflater.inflate(R.layout.background_toast, this, false)
    (backgroundToastView.layoutParams as LayoutParams).apply {
      setMargins(
        leftMargin + insetLeft,
        topMargin + insetTop,
        rightMargin + insetRight,
        bottomMargin + insetBottom
      )
      backgroundToastView.alpha = 0f
      backgroundToastView.visibility = View.GONE
      addView(backgroundToastView)
    }
  }

  override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
    super.onLayout(changed, left, top, right, bottom)
    if (SDK_INT >= 29) {
      // Prevent left and right edge swipes from peeking the system UI when using gesture
      // navigation.
      exclusionRects[0].set(left, top, right, bottom)
      setSystemGestureExclusionRects(exclusionRects)
    }
  }

  private fun setHideNavigation(hideNavigation: Boolean) {
    systemUiVisibility = if (hideNavigation) {
      systemUiVisibility or
        SYSTEM_UI_FLAG_IMMERSIVE_STICKY or SYSTEM_UI_FLAG_HIDE_NAVIGATION
    } else {
      systemUiVisibility and
        SYSTEM_UI_FLAG_IMMERSIVE_STICKY.inv() and SYSTEM_UI_FLAG_HIDE_NAVIGATION.inv()
    }
  }
}
