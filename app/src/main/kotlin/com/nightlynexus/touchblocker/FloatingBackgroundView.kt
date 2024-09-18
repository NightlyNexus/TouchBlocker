package com.nightlynexus.touchblocker

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewPropertyAnimator
import android.widget.FrameLayout

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
}
