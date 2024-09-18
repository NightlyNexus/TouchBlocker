package com.nightlynexus.touchblocker

import android.app.Activity
import android.app.Application
import com.nightlynexus.featureunlocker.FeatureUnlocker

internal fun provideFeatureUnlocker(application: Application): FeatureUnlocker {
  return object : FeatureUnlocker {
    override val state = FeatureUnlocker.State.Purchased

    override fun addListener(listener: FeatureUnlocker.Listener) {
    }

    override fun removeListener(listener: FeatureUnlocker.Listener) {
    }

    override fun startConnection() {
    }

    override fun buy(activity: Activity) {
    }
  }
}
