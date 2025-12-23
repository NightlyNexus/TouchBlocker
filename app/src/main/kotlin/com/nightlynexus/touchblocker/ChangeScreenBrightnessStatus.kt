package com.nightlynexus.touchblocker

import android.content.SharedPreferences

internal class ChangeScreenBrightnessStatus(private val sharedPreferences: SharedPreferences) {
  interface Listener {
    fun update(changeScreenBrightness: Boolean)
  }

  private val key = "change_screen_brightness"
  private val listeners = mutableSetOf<Listener>()

  fun setChangeScreenBrightness(changeScreenBrightness: Boolean) {
    sharedPreferences.edit().putBoolean(key, changeScreenBrightness).apply()
    for (listener in listeners) {
      listener.update(changeScreenBrightness)
    }
  }

  fun getChangeScreenBrightness(): Boolean {
    return sharedPreferences.getBoolean(key, false)
  }

  fun addListener(listener: Listener) {
    listeners.add(listener)
  }

  fun removeListener(listener: Listener) {
    listeners.remove(listener)
  }
}
