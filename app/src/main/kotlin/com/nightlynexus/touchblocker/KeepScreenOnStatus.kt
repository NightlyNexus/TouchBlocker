package com.nightlynexus.touchblocker

import android.content.SharedPreferences

internal class KeepScreenOnStatus(private val sharedPreferences: SharedPreferences) {
  interface Listener {
    fun update(keepScreenOn: Boolean)
  }

  private val key = "keep_screen_on"
  private val listeners = mutableSetOf<Listener>()

  fun setKeepScreenOn(keepScreenOn: Boolean) {
    sharedPreferences.edit().putBoolean(key, keepScreenOn).apply()
    for (listener in listeners) {
      listener.update(keepScreenOn)
    }
  }

  fun getKeepScreenOn(): Boolean {
    return sharedPreferences.getBoolean(key, false)
  }

  fun addListener(listener: Listener) {
    listeners.add(listener)
  }

  fun removeListener(listener: Listener) {
    listeners.remove(listener)
  }
}
