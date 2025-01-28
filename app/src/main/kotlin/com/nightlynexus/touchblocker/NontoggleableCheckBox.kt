package com.nightlynexus.touchblocker

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.widget.CheckBox

// The AppCompat CheckBox has a darker tint. LauncherActivity is not an AppCompatActivity, so the
// other CheckBox is not an AppCompatCheckBox, either.
@SuppressLint("AppCompatCustomView")
private class NontoggleableCheckBox(
  context: Context,
  attrs: AttributeSet
) : CheckBox(context, attrs) {
  override fun toggle() {
    // Do not toggle when the user clicks the CheckBox.
  }
}
