package com.nightlynexus.touchblocker

import android.content.Context
import android.os.Build.VERSION.SDK_INT
import android.util.AttributeSet
import android.view.View.MeasureSpec.EXACTLY
import android.view.WindowInsets
import androidx.appcompat.widget.Toolbar

internal class ExtendedToolbar(
  context: Context,
  attributes: AttributeSet
) : Toolbar(context, attributes) {
  private var insetTop = 0
  private var insetLeft = 0
  private var insetRight = 0
  private var initialHeight = 0
  private var initialPaddingTop = 0
  private var initialPaddingLeft = 0
  private var initialPaddingRight = 0
  private var initialMeasure = true

  override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
    if (SDK_INT >= 30) {
      val systemBarsInsets = insets.getInsets(WindowInsets.Type.systemBars())
      insetTop = systemBarsInsets.top
      insetLeft = systemBarsInsets.left
      insetRight = systemBarsInsets.right
    } else {
      insetTop = @Suppress("Deprecation") insets.systemWindowInsetTop
      insetLeft = @Suppress("Deprecation") insets.systemWindowInsetLeft
      insetRight = @Suppress("Deprecation") insets.systemWindowInsetRight
    }
    return super.onApplyWindowInsets(insets)
  }

  override fun onMeasure(
    widthMeasureSpec: Int,
    heightMeasureSpec: Int
  ) {
    if (initialMeasure) {
      super.onMeasure(widthMeasureSpec, heightMeasureSpec)
      initialHeight = measuredHeight
      initialPaddingTop = paddingTop
      initialPaddingLeft = paddingLeft
      initialPaddingRight = paddingRight
      initialMeasure = false
    } else {
      super.onMeasure(
        widthMeasureSpec, MeasureSpec.makeMeasureSpec(initialHeight + insetTop, EXACTLY)
      )
      setPadding(
        initialPaddingLeft + insetLeft,
        initialPaddingTop + insetTop,
        initialPaddingRight + insetRight,
        paddingBottom
      )
    }
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    requestApplyInsets()
  }
}
