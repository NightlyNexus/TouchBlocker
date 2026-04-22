package com.nightlynexus.touchblocker

import android.app.Activity
import android.app.AlertDialog
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Intent
import android.os.Build.VERSION.SDK_INT
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.ViewTreeObserver
import android.widget.CompoundButton
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.marginBottom
import androidx.core.view.marginTop
import com.nightlynexus.featureunlocker.FeatureUnlocker
import kotlin.math.roundToInt

class LauncherActivity :
  Activity(),
  FloatingViewStatus.Listener,
  ViewTreeObserver.OnPreDrawListener {
  private lateinit var floatingViewStatus: FloatingViewStatus
  private lateinit var keepScreenOnStatus: KeepScreenOnStatus
  private lateinit var changeScreenBrightnessStatus: ChangeScreenBrightnessStatus
  private lateinit var floatingLockViewSizeStatus: FloatingLockViewSizeStatus
  private lateinit var shouldRequestAddTileServiceStatus: ShouldRequestAddTileServiceStatus
  private lateinit var accessibilityPermissionRequestTracker: AccessibilityPermissionRequestTracker
  private lateinit var featureUnlocker: FeatureUnlocker
  private lateinit var rootView: View
  private lateinit var brandIcon: View
  private lateinit var buttonsContainerView: View
  private lateinit var enableButton: TextView
  private lateinit var keepScreenOnCheckBox: CompoundButton
  private lateinit var changeScreenBrightnessCheckBox: CompoundButton
  private lateinit var assistantCheckBox: CompoundButton
  private lateinit var requestAddTileServiceButton: View
  private lateinit var floatingLockViewSizeSeekBar: SeekBar
  private lateinit var footerView: View
  private var permissionDialog: AlertDialog? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    val application = application as TouchBlockerApplication
    floatingViewStatus = application.floatingViewStatus
    keepScreenOnStatus = application.keepScreenOnStatus
    changeScreenBrightnessStatus = application.changeScreenBrightnessStatus
    floatingLockViewSizeStatus = application.floatingLockViewSizeStatus
    shouldRequestAddTileServiceStatus = application.shouldRequestAddTileServiceStatus
    accessibilityPermissionRequestTracker = application.accessibilityPermissionRequestTracker
    featureUnlocker = application.featureUnlocker

    installSplashScreen()

    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_launcher)
    rootView = findViewById(R.id.root)
    brandIcon = findViewById(R.id.brand_icon)
    enableButton = findViewById(R.id.enable)
    buttonsContainerView = findViewById(R.id.buttons_container)
    keepScreenOnCheckBox = findViewById(R.id.keep_screen_on)
    changeScreenBrightnessCheckBox = findViewById(R.id.change_screen_brightness)
    assistantCheckBox = findViewById(R.id.enable_assistant)
    requestAddTileServiceButton = findViewById(R.id.request_add_tile_service)
    floatingLockViewSizeSeekBar = findViewById(R.id.floating_lock_view_size)
    floatingLockViewSizeSeekBar = findViewById(R.id.floating_lock_view_size)
    footerView = findViewById(R.id.footer)

    rootView.viewTreeObserver.addOnPreDrawListener(this)

    if (floatingViewStatus.added) {
      onFloatingViewAdded()
    } else if (floatingViewStatus.permissionGranted) {
      onFloatingViewRemoved()
    } else {
      onFloatingViewPermissionRevoked()
    }

    keepScreenOnCheckBox.isChecked =
      keepScreenOnStatus.getKeepScreenOn()
    keepScreenOnCheckBox.setOnCheckedChangeListener(
      keepScreenOnCheckBoxListener
    )

    changeScreenBrightnessCheckBox.isChecked =
      changeScreenBrightnessStatus.getChangeScreenBrightness()
    changeScreenBrightnessCheckBox.setOnCheckedChangeListener(
      changeScreenBrightnessCheckBoxListener
    )

    assistantCheckBox.isChecked = isDefaultAssistant()
    assistantCheckBox.setOnClickListener {
      startActivity(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS))
      @StringRes val toastMessageResource = if (assistantCheckBox.isChecked) {
        R.string.enable_assistant_toast_disable
      } else {
        R.string.enable_assistant_toast
      }
      Toast.makeText(this, toastMessageResource, Toast.LENGTH_LONG).show()
    }

    assistantCheckBox.setOnClickListener {
      startActivity(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS))
      @StringRes val toastMessageResource = if (assistantCheckBox.isChecked) {
        R.string.enable_assistant_toast_disable
      } else {
        R.string.enable_assistant_toast
      }
      Toast.makeText(this, toastMessageResource, Toast.LENGTH_LONG).show()
    }

    requestAddTileServiceButton.visibility = if (
      shouldRequestAddTileServiceStatus.getShouldRequest()
    ) {
      View.VISIBLE
    } else {
      View.GONE
    }
    requestAddTileServiceButton.setOnClickListener {
      shouldRequestAddTileServiceStatus.setShouldRequest(false)
      requestAddTileServiceButton.visibility = View.GONE
      requestAddTileService(this, floatingViewStatus.locked)
    }

    floatingLockViewSizeSeekBar.progress =
      progress(floatingLockViewSizeStatus.getSizeMultiplier())
    floatingLockViewSizeSeekBar.setOnSeekBarChangeListener(
      floatingLockViewSizeSeekBarListener
    )

    floatingViewStatus.addListener(this)
    keepScreenOnStatus.addListener(keepScreenOnStatusListener)
    changeScreenBrightnessStatus.addListener(changeScreenBrightnessStatusListener)
    floatingLockViewSizeStatus.addListener(floatingLockViewSizeStatusListener)
    shouldRequestAddTileServiceStatus.addListener(shouldRequestAddTileServiceStatusListener)
  }

  override fun onDestroy() {
    super.onDestroy()
    rootView.viewTreeObserver.removeOnPreDrawListener(this)
    permissionDialog?.dismiss()
    floatingViewStatus.removeListener(this)
    keepScreenOnStatus.removeListener(keepScreenOnStatusListener)
    changeScreenBrightnessStatus.removeListener(changeScreenBrightnessStatusListener)
    floatingLockViewSizeStatus.removeListener(floatingLockViewSizeStatusListener)
    shouldRequestAddTileServiceStatus.removeListener(shouldRequestAddTileServiceStatusListener)
  }

  override fun onPreDraw(): Boolean {
    val rootViewHeight = rootView.height - rootView.paddingTop - rootView.paddingBottom
    val brandIconHeight = brandIcon.minimumHeight +
      brandIcon.marginTop + brandIcon.marginBottom
    val buttonsContainerViewHeight = buttonsContainerView.height +
      buttonsContainerView.marginTop + buttonsContainerView.marginBottom
    val footerViewHeight = footerView.height +
      footerView.marginTop + footerView.marginBottom

    val buttonsContainerViewAndBrandIconHeight = buttonsContainerViewHeight + brandIconHeight
    if (buttonsContainerViewAndBrandIconHeight <= rootViewHeight) {
      brandIcon.visibility = View.VISIBLE
      if (buttonsContainerViewAndBrandIconHeight + footerViewHeight <= rootViewHeight) {
        footerView.visibility = View.VISIBLE
      } else {
        footerView.visibility = View.GONE
      }
    } else {
      brandIcon.visibility = View.GONE
      if (buttonsContainerViewHeight + footerViewHeight <= rootViewHeight) {
        footerView.visibility = View.VISIBLE
      } else {
        footerView.visibility = View.GONE
      }
    }

    ViewCompat.setOnApplyWindowInsetsListener(rootView) { _, insets ->
      val systemBarsAndCutout = insets.getInsets(
        WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
      )
      val paddingVertical = resources.getDimensionPixelSize(R.dimen.root_padding_vertical)

      val paddingTop = systemBarsAndCutout.top + paddingVertical
      val paddingBottom = systemBarsAndCutout.bottom + paddingVertical

      val rootViewPaddingTop: Int
      val buttonsContainerViewPaddingTop: Int
      val rootViewPaddingBottom: Int
      val buttonsContainerViewPaddingBottom: Int
      if (brandIcon.isVisible) {
        rootViewPaddingTop = paddingTop
        buttonsContainerViewPaddingTop = 0
      } else {
        rootViewPaddingTop = 0
        buttonsContainerViewPaddingTop = paddingTop
      }
      if (footerView.isVisible) {
        rootViewPaddingBottom = paddingBottom
        buttonsContainerViewPaddingBottom = 0
      } else {
        rootViewPaddingBottom = 0
        buttonsContainerViewPaddingBottom = paddingBottom
      }
      rootView.setPadding(
        systemBarsAndCutout.left,
        rootViewPaddingTop,
        systemBarsAndCutout.right,
        rootViewPaddingBottom
      )
      buttonsContainerView.setPadding(
        0,
        buttonsContainerViewPaddingTop,
        0,
        buttonsContainerViewPaddingBottom
      )
      insets
    }
    rootView.requestApplyInsets()
    return true
  }

  override fun onFloatingViewAdded() {
    enableButton.setText(R.string.enable_button_remove_floating)
    enableButton.setOnClickListener {
      floatingViewStatus.setAdded(false)
    }
  }

  override fun onFloatingViewRemoved() {
    enableButton.setText(R.string.enable_button_add_floating)
    enableButton.setOnClickListener {
      floatingViewStatus.setAdded(true)
    }
  }

  override fun onFloatingViewLocked() {
    // No-op.
  }

  override fun onFloatingViewUnlocked() {
    // No-op.
  }

  override fun onFloatingViewPermissionGranted() {
    permissionDialog?.dismiss()
    onFloatingViewRemoved()
  }

  override fun onFloatingViewPermissionRevoked() {
    enableButton.setText(R.string.enable_button_accessibility_service)
    enableButton.setOnClickListener {
      showPermissionDialog()
    }
  }

  private fun showPermissionDialog() {
    val permissionDialog = AlertDialog.Builder(this, R.style.DialogPermissionStyle)
      .setView(R.layout.dialog_permission)
      .show()
    permissionDialog.findViewById<View>(R.id.dialog_permission_button_confirm)!!.setOnClickListener {
      permissionDialog.dismiss()
      requestPermission()
    }
    permissionDialog.findViewById<View>(R.id.dialog_permission_button_cancel)!!.setOnClickListener {
      permissionDialog.cancel()
    }
    this.permissionDialog = permissionDialog
  }

  private fun requestPermission() {
    accessibilityPermissionRequestTracker.recordAccessibilityPermissionRequest()
    startActivity(
      accessibilityServicesSettingsIntent().addFlags(
        Intent.FLAG_ACTIVITY_NEW_TASK or
          Intent.FLAG_ACTIVITY_CLEAR_TOP or
          Intent.FLAG_ACTIVITY_SINGLE_TOP
      )
    )
  }

  override fun onToggle() {
    // No-op.
  }

  private val keepScreenOnCheckBoxListener =
    CompoundButton.OnCheckedChangeListener { _, isChecked ->
      if (featureUnlocker.state != FeatureUnlocker.State.Purchased) {
        setKeepScreenOnCheckboxCheckedWithoutCallingListener(false)
        featureUnlocker.buy(this)
      } else {
        keepScreenOnStatus.removeListener(keepScreenOnStatusListener)
        keepScreenOnStatus.setKeepScreenOn(isChecked)
        keepScreenOnStatus.addListener(keepScreenOnStatusListener)
      }
    }

  private val keepScreenOnStatusListener =
    object : KeepScreenOnStatus.Listener {
      override fun update(keepScreenOn: Boolean) {
        setKeepScreenOnCheckboxCheckedWithoutCallingListener(keepScreenOn)
      }
    }

  private fun setKeepScreenOnCheckboxCheckedWithoutCallingListener(checked: Boolean) {
    keepScreenOnCheckBox.setOnCheckedChangeListener(null)
    keepScreenOnCheckBox.isChecked = checked
    keepScreenOnCheckBox.setOnCheckedChangeListener(
      keepScreenOnCheckBoxListener
    )
  }

  private val changeScreenBrightnessCheckBoxListener =
    CompoundButton.OnCheckedChangeListener { _, isChecked ->
      if (featureUnlocker.state != FeatureUnlocker.State.Purchased) {
        setChangeScreenBrightnessCheckboxCheckedWithoutCallingListener(false)
        featureUnlocker.buy(this)
      } else {
        changeScreenBrightnessStatus.removeListener(changeScreenBrightnessStatusListener)
        changeScreenBrightnessStatus.setChangeScreenBrightness(isChecked)
        changeScreenBrightnessStatus.addListener(changeScreenBrightnessStatusListener)
      }
    }

  private val changeScreenBrightnessStatusListener =
    object : ChangeScreenBrightnessStatus.Listener {
      override fun update(changeScreenBrightness: Boolean) {
        setChangeScreenBrightnessCheckboxCheckedWithoutCallingListener(changeScreenBrightness)
      }
    }

  private fun setChangeScreenBrightnessCheckboxCheckedWithoutCallingListener(checked: Boolean) {
    changeScreenBrightnessCheckBox.setOnCheckedChangeListener(null)
    changeScreenBrightnessCheckBox.isChecked = checked
    changeScreenBrightnessCheckBox.setOnCheckedChangeListener(
      changeScreenBrightnessCheckBoxListener
    )
  }

  private val floatingLockViewSizeSeekBarListener =
    object : SeekBar.OnSeekBarChangeListener {
      override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
        floatingLockViewSizeStatus.removeListener(floatingLockViewSizeStatusListener)
        floatingLockViewSizeStatus.setSizeMultiplier(
          sizeMultiplier(progress)
        )
        floatingLockViewSizeStatus.addListener(floatingLockViewSizeStatusListener)
      }

      override fun onStartTrackingTouch(seekBar: SeekBar) {
        // No-op.
      }

      override fun onStopTrackingTouch(seekBar: SeekBar) {
        // No-op.
      }
    }

  private val floatingLockViewSizeStatusListener =
    object : FloatingLockViewSizeStatus.Listener {
      override fun update(sizeMultiplier: Float) {
        setFloatingLockViewSizeSeekBarProgressWithoutCallingListener(
          progress(sizeMultiplier)
        )
      }
    }

  private fun setFloatingLockViewSizeSeekBarProgressWithoutCallingListener(progress: Int) {
    floatingLockViewSizeSeekBar.setOnSeekBarChangeListener(null)
    floatingLockViewSizeSeekBar.progress = progress
    floatingLockViewSizeSeekBar.setOnSeekBarChangeListener(
      floatingLockViewSizeSeekBarListener
    )
  }

  private val shouldRequestAddTileServiceStatusListener =
    object : ShouldRequestAddTileServiceStatus.Listener {
      override fun update(shouldRequest: Boolean) {
        requestAddTileServiceButton.visibility = if (shouldRequest) {
          View.VISIBLE
        } else {
          View.GONE
        }
      }
    }

  override fun onResume() {
    super.onResume()
    // I do not know of a broadcast to know exactly when the default assistant app changed,
    // so I will check in onResume.
    assistantCheckBox.isChecked = isDefaultAssistant()
  }

  private fun sizeMultiplier(progress: Int): Float {
    return sizeMultiplier(
      progress,
      if (SDK_INT >= 26) floatingLockViewSizeSeekBar.min else 0,
      floatingLockViewSizeSeekBar.max,
      FloatingLockViewSizeStatus.sizeMultiplierMin,
      FloatingLockViewSizeStatus.sizeMultiplierMax
    )
  }

  private fun progress(sizeMultiplier: Float): Int {
    return progress(
      sizeMultiplier,
      if (SDK_INT >= 26) floatingLockViewSizeSeekBar.min else 0,
      floatingLockViewSizeSeekBar.max,
      FloatingLockViewSizeStatus.sizeMultiplierMin,
      FloatingLockViewSizeStatus.sizeMultiplierMax
    )
  }

  private fun sizeMultiplier(
    progress: Int,
    progressMin: Int,
    progressMax: Int,
    sizeMultiplierMin: Float,
    sizeMultiplierMax: Float
  ): Float {
    val mid = (progressMin + progressMax) / 2f
    return if (progress <= mid) {
      sizeMultiplierMin +
        (1 - sizeMultiplierMin) * (progress - progressMin) / (mid - progressMin)
    } else {
      1 +
        (sizeMultiplierMax - 1) * (progress - mid) / (progressMax - mid)
    }
  }

  private fun progress(
    sizeMultiplier: Float,
    progressMin: Int,
    progressMax: Int,
    sizeMultiplierMin: Float,
    sizeMultiplierMax: Float
  ): Int {
    val mid = (progressMin + progressMax) / 2f
    return if (sizeMultiplier <= 1) {
      progressMin +
        (sizeMultiplier - sizeMultiplierMin) / (1 - sizeMultiplierMin) * (mid - progressMin)
    } else {
      mid +
        (sizeMultiplier - 1) / (sizeMultiplierMax - 1) * (progressMax - mid)
    }.roundToInt()
  }

  private fun isDefaultAssistant(): Boolean {
    if (SDK_INT >= 29) {
      val roleManager = getSystemService(RoleManager::class.java)
      return roleManager.isRoleHeld(RoleManager.ROLE_ASSISTANT)
    }
    // This only takes a millisecond or two on my Pixel 6.
    val assistantSetting = Settings.Secure.getString(contentResolver, "assistant")
    if (assistantSetting == null) {
      return false
    }
    val assistant = ComponentName.unflattenFromString(assistantSetting)
    val touchBlockerAssistant = ComponentName(this, NoDisplayActivity::class.java)
    return assistant == touchBlockerAssistant
  }
}
