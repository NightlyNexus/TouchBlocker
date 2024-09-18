package com.nightlynexus.touchblocker

internal class FloatingViewStatus(permissionGranted: Boolean) {
  interface Listener {
    fun onFloatingViewAdded()
    fun onFloatingViewRemoved()
    fun onFloatingViewPermissionGranted()
    fun onFloatingViewPermissionRevoked()
    fun onToggle()
  }

  var added = false
    private set

  fun setAdded(added: Boolean) {
    check(this.added != added)
    for (listener in listeners) {
      this.added = added
      if (added) {
        listener.onFloatingViewAdded()
      } else {
        listener.onFloatingViewRemoved()
      }
    }
  }

  var permissionGranted = permissionGranted
    private set

  fun setPermissionGranted(permissionGranted: Boolean) {
    // Allow redundant true setting because onServiceConnected gets called again on device startup.
    check(this.permissionGranted || permissionGranted)
    this.permissionGranted = permissionGranted
    for (listener in listeners) {
      if (permissionGranted) {
        listener.onFloatingViewPermissionGranted()
      } else {
        listener.onFloatingViewPermissionRevoked()
      }
    }
  }

  fun toggle() {
    for (listener in listeners) {
      listener.onToggle()
    }
  }

  fun addListener(listener: Listener) {
    listeners += listener
  }

  fun removeListener(listener: Listener) {
    listeners -= listener
  }

  private val listeners = mutableSetOf<Listener>()
}
