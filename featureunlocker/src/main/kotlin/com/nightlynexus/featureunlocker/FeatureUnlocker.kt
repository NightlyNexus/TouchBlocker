package com.nightlynexus.featureunlocker

import android.app.Activity
import android.app.Application
import android.os.Looper
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import java.lang.ref.WeakReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class PlayFeatureUnlocker(
  application: Application,
  private val unlockModifiersProductId: String
) : FeatureUnlocker {
  override var state = FeatureUnlocker.State.Loading
    get() {
      if (!Looper.getMainLooper().isCurrentThread) {
        scope.launch(Dispatchers.Main) {
          throw IllegalStateException(Thread.currentThread().toString())
        }
      }
      return field
    }
    private set(value) {
      if (!Looper.getMainLooper().isCurrentThread) {
        scope.launch(Dispatchers.Main) {
          throw IllegalStateException(Thread.currentThread().toString())
        }
      }
      field = value
      for (i in listeners.indices) {
        listeners[i].stateChanged(value)
      }
      val pendingBuy = pendingBuy
      if (pendingBuy != null) {
        this@PlayFeatureUnlocker.pendingBuy = null
        if (value === FeatureUnlocker.State.Purchasable) {
          val activity = pendingBuy.get()
          if (activity != null) {
            buy(activity)
          }
        }
      }
    }
  private val listeners = mutableListOf<FeatureUnlocker.Listener>()
  private var productDetails: ProductDetails? = null

  // The listener won't be called until the billing client is set.
  private val purchasesUpdatedListener =
    PurchasesUpdatedListener { billingResult, purchases ->
      if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
        val purchase = purchases!!.findPurchaseWithProductId(unlockModifiersProductId)
        if (purchase != null) {
          if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            state = FeatureUnlocker.State.Purchased
            if (!purchase.isAcknowledged) {
              scope.launch(Dispatchers.IO) {
                acknowledgePurchase(purchase)
              }
            }
          } else {
            if (productDetails == null) {
              state = FeatureUnlocker.State.Loading
              scope.launch(Dispatchers.IO) {
                loadProducts()
              }
            } else {
              state = FeatureUnlocker.State.Purchasable
            }
          }
        }
      } else {
        if (productDetails == null) {
          state = FeatureUnlocker.State.Loading
          scope.launch(Dispatchers.IO) {
            loadProducts()
          }
        } else {
          state = FeatureUnlocker.State.Purchasable
        }
      }
    }

  // The listener won't be called until the billing client is set.
  private val billingClientStateListener = object : BillingClientStateListener {
    override fun onBillingSetupFinished(billingResult: BillingResult) {
      if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
        val featureSupportedBillingResult =
          billingClient.isFeatureSupported(BillingClient.FeatureType.PRODUCT_DETAILS)
        if (featureSupportedBillingResult.responseCode == BillingClient.BillingResponseCode.OK) {
          scope.launch(Dispatchers.IO) {
            if (!loadPurchases()) {
              loadProducts()
            }
          }
        } else {
          scope.launch(Dispatchers.Main) {
            state = FeatureUnlocker.State.FailedLoading
          }
        }
      } else {
        scope.launch(Dispatchers.Main) {
          state = FeatureUnlocker.State.FailedLoading
        }
      }
    }

    override fun onBillingServiceDisconnected() {
      billingClient.startConnection(this)
    }
  }

  private val billingClient = BillingClient.newBuilder(application)
    .setListener(purchasesUpdatedListener)
    .enablePendingPurchases(
      PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
    )
    .build()
  private val scope = MainScope()

  override fun addListener(listener: FeatureUnlocker.Listener) {
    listeners += listener
  }

  override fun removeListener(listener: FeatureUnlocker.Listener) {
    listeners -= listener
  }

  override fun startConnection() {
    billingClient.startConnection(billingClientStateListener)
  }

  private suspend fun loadPurchases(): Boolean {
    val purchasesResult = billingClient.queryPurchasesAsync(
      QueryPurchasesParams.newBuilder()
        .setProductType(BillingClient.ProductType.INAPP)
        .build()
    )
    if (purchasesResult.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
      val purchases = purchasesResult.purchasesList
      val purchase = purchases.findPurchaseWithProductId(unlockModifiersProductId)
      return if (purchase == null) {
        false
      } else {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
          withContext(Dispatchers.Main) {
            state = FeatureUnlocker.State.Purchased
          }
          if (!purchase.isAcknowledged) {
            acknowledgePurchase(purchase)
          }
          true
        } else {
          false
        }
      }
    } else {
      withContext(Dispatchers.Main) {
        state = FeatureUnlocker.State.FailedLoading
      }
      return true
    }
  }

  private suspend fun loadProducts() {
    val params = QueryProductDetailsParams.newBuilder()
    params.setProductList(
      listOf(
        QueryProductDetailsParams.Product.newBuilder()
          .setProductId(unlockModifiersProductId)
          .setProductType(BillingClient.ProductType.INAPP)
          .build()
      )
    )
    val productDetailsResult = billingClient.queryProductDetails(params.build())
    if (productDetailsResult.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
      withContext(Dispatchers.Main) {
        val productDetailsList = productDetailsResult.productDetailsList!!
        if (productDetailsList.isEmpty()) {
          // unlockModifiersProductId is not an available product. Check the Google Play dev
          // console.
          state = FeatureUnlocker.State.FailedLoading
        } else {
          productDetails = productDetailsList[0]
          state = FeatureUnlocker.State.Purchasable
        }
      }
    } else {
      withContext(Dispatchers.Main) {
        state = FeatureUnlocker.State.FailedLoading
      }
    }
  }

  private var pendingBuy: WeakReference<Activity>? = null

  override fun buy(activity: Activity) {
    if (pendingBuy != null) {
      throw IllegalStateException("Already pending a buy.")
    }
    when (state) {
      FeatureUnlocker.State.Purchased -> {
        throw IllegalStateException("Already purchased.")
      }

      FeatureUnlocker.State.Purchasable -> {
        // Continue.
      }

      FeatureUnlocker.State.Loading -> {
        pendingBuy = WeakReference(activity)
        return
      }

      FeatureUnlocker.State.FailedLoading -> {
        pendingBuy = WeakReference(activity)
        state = FeatureUnlocker.State.Loading
        if (billingClient.connectionState == BillingClient.ConnectionState.DISCONNECTED) {
          billingClient.startConnection(billingClientStateListener)
        } else {
          val featureSupportedBillingResult =
            billingClient.isFeatureSupported(BillingClient.FeatureType.PRODUCT_DETAILS)
          if (featureSupportedBillingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            scope.launch(Dispatchers.IO) {
              if (!loadPurchases()) {
                loadProducts()
              }
            }
          } else {
            state = FeatureUnlocker.State.FailedLoading
          }
        }
        return
      }
    }
    val billingResult = billingClient.launchBillingFlow(
      activity,
      BillingFlowParams.newBuilder()
        .setProductDetailsParamsList(
          listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder()
              .setProductDetails(productDetails!!)
              .build()
          )
        )
        .build()
    )
    if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
      state = FeatureUnlocker.State.FailedLoading
    }
  }

  private suspend fun acknowledgePurchase(purchase: Purchase) {
    billingClient.acknowledgePurchase(
      AcknowledgePurchaseParams.newBuilder()
        .setPurchaseToken(purchase.purchaseToken)
        .build()
    )
    // Ignore the result.
  }

  private fun List<Purchase>.findPurchaseWithProductId(productId: String): Purchase? {
    for (i in indices) {
      val purchase = this[i]
      val productIds = purchase.products
      for (j in productIds.indices) {
        if (productIds[j] == productId) {
          return purchase
        }
      }
    }
    return null
  }
}

interface FeatureUnlocker {
  companion object {
    fun play(application: Application, unlockModifiersProductId: String): FeatureUnlocker {
      return PlayFeatureUnlocker(application, unlockModifiersProductId)
    }
  }

  enum class State {
    Purchased,
    Purchasable,
    Loading,
    FailedLoading
  }

  interface Listener {
    fun stateChanged(state: State)
  }

  val state: State

  fun addListener(listener: Listener)

  fun removeListener(listener: Listener)

  fun startConnection()

  fun buy(activity: Activity)
}
