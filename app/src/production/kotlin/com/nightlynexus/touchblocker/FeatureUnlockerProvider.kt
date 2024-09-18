package com.nightlynexus.touchblocker

import android.app.Application
import com.nightlynexus.featureunlocker.FeatureUnlocker

internal fun provideFeatureUnlocker(application: Application): FeatureUnlocker {
  return FeatureUnlocker.play(application, "unlock_modifiers")
}
