package com.nightlynexus.touchblocker

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.os.SystemClock
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.ImageView
import kotlin.math.roundToInt
import kotlin.math.sqrt

@SuppressLint("AppCompatCustomView", "ViewConstructor")
internal class FloatingLockView(
  context: Context,
  private val windowManager: WindowManager,
  private val layoutParams: WindowManager.LayoutParams,
  private val maxMoveDistanceForClick: Float,
  private var minX: Int,
  private var minY: Int,
  private var maxX: Int,
  private var maxY: Int,
  private val animatePixelsPerSecond: Int,
  private val animateAlphaPerSecond: Float,
  private val lockAnimateAlphaDelayMillis: Long,
  private var screenOn: Boolean,
  private val startFadeOutListener: Runnable
) : ImageView(context) {
  var locked: Boolean
    private set

  init {
    setImageResource(R.drawable.lock_open_24px)
    contentDescription = context.getText(R.string.lock_content_description_unlocked)
    locked = false

    if (!screenOn) {
      visibility = GONE
    }
  }

  fun setLocked(locked: Boolean) {
    if (locked) {
      setImageResource(R.drawable.lock_24px)
      contentDescription = context.getText(R.string.lock_content_description_locked)
      this.locked = true
    } else {
      setImageResource(R.drawable.lock_open_24px)
      contentDescription = context.getText(R.string.lock_content_description_unlocked)
      this.locked = false
    }
  }

  fun setScreenOn(screenOn: Boolean) {
    this.screenOn = screenOn
    if (screenOn) {
      if (alphaAnimating || alpha != 0f) {
        visibility = VISIBLE
      }
    } else {
      visibility = GONE
    }
  }

  private fun setVisible(visible: Boolean) {
    if (visible) {
      if (screenOn) {
        visibility = VISIBLE
      }
    } else {
      visibility = GONE
    }
  }

  fun resetFadeTimer() {
    cancelTimerToFadeOut()
    setTimerToFadeOut()
  }

  fun resetAlpha() {
    alphaAnimating = false
    alpha = 1f
    setVisible(true)
    cancelTimerToFadeOut()
  }

  fun reset(x: Int, y: Int, minX: Int, minY: Int, maxX: Int, maxY: Int) {
    // I think we shouldn't change the fading state on a configuration change.
    /*resetAlpha()
    setTimerToFadeOut()*/
    movementAnimating = false
    layoutParams.x = x
    layoutParams.y = y
    this.minX = minX
    this.minY = minY
    this.maxX = maxX
    this.maxY = maxY
  }

  private var alphaAnimating = false
  private var alphaAnimationFadingIn = false
  private var alphaAnimationStart = 0f
  private var alphaAnimationStartTime = 0L

  fun fadeIn() {
    cancelTimerToFadeOut()
    val alpha = alpha
    if (alpha == 1f) {
      setTimerToFadeOut()
      return
    }
    alphaAnimationFadingIn = true
    setVisible(true)
    alphaAnimationStart = alpha
    alphaAnimating = true
    alphaAnimationStartTime = SystemClock.uptimeMillis()
    invalidate()
  }

  fun fadeOut() {
    val alpha = alpha
    if (alpha == 0f) {
      return
    }
    alphaAnimationFadingIn = false
    setVisible(true)
    alphaAnimationStart = alpha
    alphaAnimating = true
    alphaAnimationStartTime = SystemClock.uptimeMillis()
    invalidate()
  }

  private var touching = false
  private var downX = 0
  private var downY = 0
  private var differenceX = 0f
  private var differenceY = 0f

  override fun onTouchEvent(event: MotionEvent): Boolean {
    when (event.actionMasked) {
      MotionEvent.ACTION_DOWN -> {
        touching = true
        downX = layoutParams.x
        downY = layoutParams.y
        differenceX = event.rawX - layoutParams.x
        differenceY = event.rawY - layoutParams.y

        // Stop animating the movement.
        movementAnimating = false
        // Fade in.
        fadeIn()
        return true
      }

      MotionEvent.ACTION_MOVE -> {
        moveView(
          (event.rawX - differenceX).roundToInt(),
          (event.rawY - differenceY).roundToInt()
        )
        return true
      }

      MotionEvent.ACTION_UP -> {
        touching = false
        if (distance(downX, downY, layoutParams.x, layoutParams.y) <= maxMoveDistanceForClick) {
          // Ripple.
          drawableHotspotChanged(event.x, event.y)
          isPressed = true
          isPressed = false
          performClick()
        }
        ensureBounds()
        setTimerToFadeOut()
        return true
      }

      MotionEvent.ACTION_CANCEL -> {
        touching = false
        ensureBounds()
        setTimerToFadeOut()
        return true
      }
    }
    return false
  }

  private var movementAnimating = false
  private var animationStartX = 0
  private var animationStartY = 0
  private var distanceToDestinationX = 0
  private var distanceToDestinationY = 0
  private var distanceToDestination = 0
  private var movementAnimationStartTime = 0L

  private fun ensureBounds() {
    animationStartX = layoutParams.x
    animationStartY = layoutParams.y
    distanceToDestinationX = if (animationStartX < minX) {
      minX - animationStartX
    } else if (animationStartX > maxX) {
      maxX - animationStartX
    } else {
      0
    }
    distanceToDestinationY = if (animationStartY < minY) {
      minY - animationStartY
    } else if (animationStartY > maxY) {
      maxY - animationStartY
    } else {
      0
    }
    if (distanceToDestinationX == 0 && distanceToDestinationY == 0) {
      return
    }
    distanceToDestination = sqrt((distanceToDestinationX * distanceToDestinationX
      + distanceToDestinationY * distanceToDestinationY).toDouble()).roundToInt()
    movementAnimating = true
    movementAnimationStartTime = SystemClock.uptimeMillis()
    invalidate()
  }

  override fun draw(canvas: Canvas) {
    super.draw(canvas)
    var needsInvalidate = false
    if (movementAnimating) {
      val diffMillis = SystemClock.uptimeMillis() - movementAnimationStartTime
      val diffDistance = (animatePixelsPerSecond * diffMillis / 1_000L).toInt()
      val newX = animationStartX + diffDistance * distanceToDestinationX / distanceToDestination
      val newY = animationStartY + diffDistance * distanceToDestinationY / distanceToDestination
      val coercedX = if (distanceToDestinationX < 0 && newX <= maxX) {
        maxX
      } else if (distanceToDestinationX > 0 && newX >= minX) {
        minX
      } else {
        newX
      }
      val coercedY = if (distanceToDestinationY < 0 && newY <= maxY) {
        maxY
      } else if (distanceToDestinationY > 0 && newY >= minY) {
        minY
      } else {
        newY
      }
      moveView(coercedX, coercedY)
      if (distanceToDestinationX != 0 && (coercedX == minX || coercedX == maxX)) {
        movementAnimating = false
        setTimerToFadeOut()
        check(distanceToDestinationY == 0 || (coercedY == minY || coercedY == maxY))
      } else if (distanceToDestinationY != 0 && (coercedY == minY || coercedY == maxY)) {
        movementAnimating = false
        setTimerToFadeOut()
        check(distanceToDestinationX == 0)
      } else {
        needsInvalidate = true
      }
    }
    if (alphaAnimating) {
      val diffMillis = SystemClock.uptimeMillis() - alphaAnimationStartTime
      val diffDistance = animateAlphaPerSecond * diffMillis / 1_000L
      val coercedAlpha = if (alphaAnimationFadingIn) {
        val newAlpha = alphaAnimationStart + diffDistance
        if (newAlpha >= 1f) {
          alphaAnimating = false
          setTimerToFadeOut()
          1f
        } else {
          newAlpha
        }
      } else {
        val newAlpha = alphaAnimationStart - diffDistance
        if (newAlpha <= 0f) {
          setVisible(false)
          alphaAnimating = false
          0f
        } else {
          newAlpha
        }
      }
      val oldAlpha = alpha
      if (oldAlpha == coercedAlpha) {
        // The delta was too small on this draw pass.
        needsInvalidate = true
      } else {
        alpha = coercedAlpha
        // The delta might be so small that Android does not invalidate from setAlpha.
        needsInvalidate = true
      }
    }
    if (needsInvalidate) {
      invalidate()
    }
  }

  private fun moveView(x: Int, y: Int) {
    layoutParams.x = x
    layoutParams.y = y
    windowManager.updateViewLayout(this, layoutParams)
  }

  private fun cancelTimerToFadeOut() {
    removeCallbacks(fadeOutRunnable)
  }

  private fun setTimerToFadeOut() {
    if (locked && !touching && !movementAnimating && !alphaAnimating) {
      postDelayed(fadeOutRunnable, lockAnimateAlphaDelayMillis)
    }
  }

  private val fadeOutRunnable = Runnable {
    check(locked)
    check(!touching)
    check(!movementAnimating)
    check(!alphaAnimating) {
      "Was animating alpha. Fading in? : $alphaAnimationFadingIn"
    }
    startFadeOutListener.run()
    fadeOut()
  }

  private fun distance(x1: Int, y1: Int, x2: Int, y2: Int): Double {
    val dx = x2 - x1
    val dy = y2 - y1
    return sqrt((dx * dx + dy * dy).toDouble())
  }
}
