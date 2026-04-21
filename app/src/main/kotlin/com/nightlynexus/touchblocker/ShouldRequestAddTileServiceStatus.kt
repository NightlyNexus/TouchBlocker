package com.nightlynexus.touchblocker

import android.content.SharedPreferences
import androidx.core.content.edit

internal class ShouldRequestAddTileServiceStatus(
  private val sharedPreferences: SharedPreferences
) {
  interface Listener {
    fun update(shouldRequest: Boolean)
  }

  private val key = "should_request_add_tile_service"
  private val listeners = mutableSetOf<Listener>()

  fun setShouldRequest(shouldRequest: Boolean) {
    sharedPreferences.edit { putBoolean(key, shouldRequest) }
    for (listener in listeners) {
      listener.update(shouldRequest)
    }
  }

  fun getShouldRequest(): Boolean {
    return sharedPreferences.getBoolean(key, true)
  }

  fun addListener(listener: Listener) {
    listeners.add(listener)
  }

  fun removeListener(listener: Listener) {
    listeners.remove(listener)
  }
}
