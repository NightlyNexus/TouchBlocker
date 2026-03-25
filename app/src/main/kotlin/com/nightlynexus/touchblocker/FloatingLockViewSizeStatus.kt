package com.nightlynexus.touchblocker

import android.content.SharedPreferences
import androidx.core.content.edit

internal class FloatingLockViewSizeStatus(private val sharedPreferences: SharedPreferences) {
  companion object {
    const val sizeMultiplierMin = 0.4f
    const val sizeMultiplierMax = 3f
  }

  interface Listener {
    fun update(sizeMultiplier: Float)
  }

  private val key = "floating_lock_view_size"
  private val listeners = mutableSetOf<Listener>()

  fun setSizeMultiplier(sizeMultiplier: Float) {
    sharedPreferences.edit { putFloat(key, sizeMultiplier) }
    for (listener in listeners) {
      listener.update(sizeMultiplier)
    }
  }

  fun getSizeMultiplier(): Float {
    return sharedPreferences.getFloat(key, 1f)
  }

  fun addListener(listener: Listener) {
    listeners.add(listener)
  }

  fun removeListener(listener: Listener) {
    listeners.remove(listener)
  }
}
