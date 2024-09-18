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
  private var extraHeight = 0
  private var initialHeight = 0
  private var initialPadding = 0
  private var initialMeasure = true

  override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
    extraHeight = if (SDK_INT >= 30) {
      insets.getInsets(WindowInsets.Type.systemBars()).top
    } else {
      @Suppress("Deprecation") insets.systemWindowInsetTop
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
      initialPadding = paddingTop
      initialMeasure = false
    } else {
      super.onMeasure(
        widthMeasureSpec, MeasureSpec.makeMeasureSpec(initialHeight + extraHeight, EXACTLY)
      )
      setPadding(paddingLeft, initialPadding + extraHeight, paddingRight, paddingBottom)
    }
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    requestApplyInsets()
  }
}
