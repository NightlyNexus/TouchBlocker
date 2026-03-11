package com.nightlynexus.touchblocker

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build.VERSION.SDK_INT
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class TouchBlockerTileService : TileService() {
  override fun onStartListening() {
    val tile = qsTile ?: return
    val application = application as TouchBlockerApplication
    val isEnabled = isAccessibilityServiceEnabled(this, TouchBlockerAccessibilityService::class.java)

    tile.state = if (isEnabled && application.floatingViewStatus.added) {
      Tile.STATE_ACTIVE
    } else {
      Tile.STATE_INACTIVE
    }
    tile.updateTile()
  }

  override fun onClick() {
    if (!isAccessibilityServiceEnabled(this, TouchBlockerAccessibilityService::class.java)) {
      val intent = accessibilityServicesSettingsIntent().apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
      if (SDK_INT >= 34) {
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        startActivityAndCollapse(pendingIntent)
      } else {
        @Suppress("DEPRECATION")
        startActivityAndCollapse(intent)
      }
    } else {
      val application = application as TouchBlockerApplication
      val nextAdded = !application.floatingViewStatus.added
      application.floatingViewStatus.setAdded(nextAdded)

      qsTile?.run {
        state = if (nextAdded) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        updateTile()
      }
    }
  }

  companion object {
    fun requestTileUpdate(context: Context) {
      requestListeningState(context, ComponentName(context, TouchBlockerTileService::class.java))
    }
  }
}
