package com.nightlynexus.touchblocker

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build.VERSION.SDK_INT
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi

@RequiresApi(24)
class TouchBlockerTileService : TileService() {
  private lateinit var floatingViewStatus: FloatingViewStatus

  override fun onCreate() {
    val application = application as TouchBlockerApplication
    floatingViewStatus = application.floatingViewStatus
  }

  override fun onStartListening() {
    val qsTile = qsTile
    if (floatingViewStatus.added) {
      qsTile.setGrantPermissionSubtitle(false)
      qsTile.state = Tile.STATE_ACTIVE
    } else {
      qsTile.setGrantPermissionSubtitle(!floatingViewStatus.permissionGranted)
      qsTile.state = Tile.STATE_INACTIVE
    }
    qsTile.updateTile()
  }

  override fun onClick() {
    if (floatingViewStatus.permissionGranted) {
      val qsTile = qsTile
      if (floatingViewStatus.added) {
        floatingViewStatus.setAdded(false)
        qsTile.state = Tile.STATE_INACTIVE
      } else {
        floatingViewStatus.setAdded(true)
        qsTile.state = Tile.STATE_ACTIVE
      }
      qsTile.updateTile()
    } else {
      val intent = Intent(
        this,
        LauncherActivity::class.java
      ).addFlags(
        Intent.FLAG_ACTIVITY_NEW_TASK or
          Intent.FLAG_ACTIVITY_CLEAR_TOP or
          Intent.FLAG_ACTIVITY_SINGLE_TOP
      )
      startActivityAndCollapseCompat(intent)
    }
  }

  private fun Tile.setGrantPermissionSubtitle(show: Boolean) {
    if (SDK_INT >= 29) {
      subtitle = if (show) {
        getText(R.string.tile_subtitle_grant_permission)
      } else {
        null
      }
    }
  }

  private fun startActivityAndCollapseCompat(intent: Intent) {
    if (SDK_INT >= 34) {
      val pendingIntent = PendingIntent.getActivity(
        this,
        0,
        intent,
        PendingIntent.FLAG_IMMUTABLE
      )
      startActivityAndCollapse(pendingIntent)
    } else {
      @SuppressLint("StartActivityAndCollapseDeprecated")
      @Suppress("deprecation")
      startActivityAndCollapse(intent)
    }
  }
}

internal fun updateTileService(context: Context) {
  if (SDK_INT >= 24) {
    TileService.requestListeningState(
      context,
      ComponentName(context, TouchBlockerTileService::class.java)
    )
  }
}
