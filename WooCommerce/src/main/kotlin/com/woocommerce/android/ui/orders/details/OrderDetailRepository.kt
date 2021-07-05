package com.woocommerce.android.ui.orders.details

import com.woocommerce.android.AppConstants
import com.woocommerce.android.analytics.AnalyticsTracker
import com.woocommerce.android.analytics.AnalyticsTracker.Companion.KEY_FEEDBACK_ACTION
import com.woocommerce.android.analytics.AnalyticsTracker.Companion.VALUE_API_FAILED
import com.woocommerce.android.analytics.AnalyticsTracker.Companion.VALUE_API_SUCCESS
import com.woocommerce.android.analytics.AnalyticsTracker.Stat
import com.woocommerce.android.annotations.OpenClassOnDebug
import com.woocommerce.android.model.Order
import com.woocommerce.android.model.Order.OrderStatus
import com.woocommerce.android.model.OrderNote
import com.woocommerce.android.model.OrderShipmentTracking
import com.woocommerce.android.model.Refund
import com.woocommerce.android.model.RequestResult
import com.woocommerce.android.model.ShippingLabel
import com.woocommerce.android.model.WooPlugin
import com.woocommerce.android.model.toAppModel
import com.woocommerce.android.model.toOrderStatus
import com.woocommerce.android.tools.SelectedSite
import com.woocommerce.android.ui.orders.shippinglabels.creation.ShippingLabelCreationFeatures
import com.woocommerce.android.util.ContinuationWrapper
import com.woocommerce.android.util.ContinuationWrapper.ContinuationResult.Cancellation
import com.woocommerce.android.util.ContinuationWrapper.ContinuationResult.Success
import com.woocommerce.android.util.WooLog
import com.woocommerce.android.util.WooLog.T.ORDERS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode.MAIN
import org.wordpress.android.fluxc.Dispatcher
import org.wordpress.android.fluxc.action.WCOrderAction
import org.wordpress.android.fluxc.action.WCProductAction.FETCH_SINGLE_PRODUCT
import org.wordpress.android.fluxc.generated.WCOrderActionBuilder
import org.wordpress.android.fluxc.model.WCOrderShipmentTrackingModel
import org.wordpress.android.fluxc.model.WCOrderStatusModel
import org.wordpress.android.fluxc.model.order.OrderIdentifier
import org.wordpress.android.fluxc.model.order.toIdSet
import org.wordpress.android.fluxc.network.rest.wpcom.wc.shippinglabels.LabelItem
import org.wordpress.android.fluxc.store.WCOrderStore
import org.wordpress.android.fluxc.store.WCOrderStore.AddOrderShipmentTrackingPayload
import org.wordpress.android.fluxc.store.WCOrderStore.DeleteOrderShipmentTrackingPayload
import org.wordpress.android.fluxc.store.WCOrderStore.FetchOrderNotesPayload
import org.wordpress.android.fluxc.store.WCOrderStore.FetchOrderShipmentTrackingsPayload
import org.wordpress.android.fluxc.store.WCOrderStore.OnOrderChanged
import org.wordpress.android.fluxc.store.WCOrderStore.OrderErrorType
import org.wordpress.android.fluxc.store.WCOrderStore.PostOrderNotePayload
import org.wordpress.android.fluxc.store.WCOrderStore.UpdateOrderStatusPayload
import org.wordpress.android.fluxc.store.WCProductStore
import org.wordpress.android.fluxc.store.WCProductStore.OnProductChanged
import org.wordpress.android.fluxc.store.WCRefundStore
import org.wordpress.android.fluxc.store.WCShippingLabelStore
import org.wordpress.android.fluxc.store.WooCommerceStore
import javax.inject.Inject

@OpenClassOnDebug
class OrderDetailRepository @Inject constructor(
    private val dispatcher: Dispatcher,
    private val orderStore: WCOrderStore,
    private val productStore: WCProductStore,
    private val refundStore: WCRefundStore,
    private val shippingLabelStore: WCShippingLabelStore,
    private val selectedSite: SelectedSite,
    private val wooCommerceStore: WooCommerceStore
) {
    private var continuationFetchOrder = ContinuationWrapper<Boolean>(ORDERS)
    private var continuationFetchOrderNotes = ContinuationWrapper<Boolean>(ORDERS)
    private var continuationFetchOrderShipmentTrackingList = ContinuationWrapper<RequestResult>(ORDERS)
    private var continuationUpdateOrderStatus = ContinuationWrapper<Boolean>(ORDERS)
    private var continuationAddOrderNote = ContinuationWrapper<Boolean>(ORDERS)
    private var continuationAddShipmentTracking = ContinuationWrapper<Boolean>(ORDERS)
    private var continuationDeleteShipmentTracking = ContinuationWrapper<Boolean>(ORDERS)

    init {
        dispatcher.register(this)
    }

    fun onCleanup() {
        dispatcher.unregister(this)
    }

    suspend fun fetchOrder(orderIdentifier: OrderIdentifier, useCachedOnFailure: Boolean = true): Order? {
        val remoteOrderId = orderIdentifier.toIdSet().remoteOrderId
        val requestResult = continuationFetchOrder.callAndWaitUntilTimeout(AppConstants.REQUEST_TIMEOUT) {
            val payload = WCOrderStore.FetchSingleOrderPayload(selectedSite.get(), remoteOrderId)
            dispatcher.dispatch(WCOrderActionBuilder.newFetchSingleOrderAction(payload))
        }
        val requestSuccessful = requestResult is Success && requestResult.value
        return if (requestSuccessful || useCachedOnFailure) {
            getOrder(orderIdentifier)
        } else {
            null
        }
    }

    suspend fun fetchOrderNotes(
        localOrderId: Int,
        remoteOrderId: Long
    ): Boolean {
        val result = continuationFetchOrderNotes.callAndWaitUntilTimeout(AppConstants.REQUEST_TIMEOUT) {
            val payload = FetchOrderNotesPayload(localOrderId, remoteOrderId, selectedSite.get())
            dispatcher.dispatch(WCOrderActionBuilder.newFetchOrderNotesAction(payload))
        }
        return when (result) {
            is Cancellation -> false
            is Success -> result.value
        }
    }

    suspend fun fetchOrderShipmentTrackingList(
        localOrderId: Int,
        remoteOrderId: Long
    ): RequestResult {
        val result = continuationFetchOrderShipmentTrackingList.callAndWaitUntilTimeout(AppConstants.REQUEST_TIMEOUT) {
            val payload = FetchOrderShipmentTrackingsPayload(localOrderId, remoteOrderId, selectedSite.get())
            dispatcher.dispatch(WCOrderActionBuilder.newFetchOrderShipmentTrackingsAction(payload))
        }
        return when (result) {
            is Cancellation -> RequestResult.ERROR
            is Success -> result.value
        }
    }

    suspend fun fetchOrderRefunds(remoteOrderId: Long): List<Refund> {
        return withContext(Dispatchers.IO) {
            refundStore.fetchAllRefunds(selectedSite.get(), remoteOrderId)
        }.model?.map { it.toAppModel() } ?: emptyList()
    }

    suspend fun fetchOrderShippingLabels(remoteOrderId: Long): List<ShippingLabel> {
        val result = withContext(Dispatchers.IO) {
            shippingLabelStore.fetchShippingLabelsForOrder(selectedSite.get(), remoteOrderId)
        }

        val action = if (result.isError) {
            VALUE_API_FAILED
        } else VALUE_API_SUCCESS
        AnalyticsTracker.track(Stat.SHIPPING_LABEL_API_REQUEST, mapOf(KEY_FEEDBACK_ACTION to action))

        return result.model?.filter { it.status == LabelItem.STATUS_PURCHASED }?.map { it.toAppModel() } ?: emptyList()
    }

    suspend fun updateOrderStatus(
        localOrderId: Int,
        remoteOrderId: Long,
        newStatus: String
    ): Boolean {
        val result = continuationUpdateOrderStatus.callAndWaitUntilTimeout(AppConstants.REQUEST_TIMEOUT) {
            val payload = UpdateOrderStatusPayload(
                localOrderId, remoteOrderId, selectedSite.get(), newStatus
            )
            dispatcher.dispatch(WCOrderActionBuilder.newUpdateOrderStatusAction(payload))
        }
        return when (result) {
            is Cancellation -> false
            is Success -> result.value
        }
    }

    suspend fun addOrderNote(
        orderIdentifier: OrderIdentifier,
        remoteOrderId: Long,
        noteModel: OrderNote
    ): Boolean {
        val order = orderStore.getOrderByIdentifier(orderIdentifier)
        if (order == null) {
            WooLog.e(ORDERS, "Can't find order with identifier $orderIdentifier")
            return false
        }
        val result = continuationAddOrderNote.callAndWaitUntilTimeout(AppConstants.REQUEST_TIMEOUT) {
            val dataModel = noteModel.toDataModel()
            val payload = PostOrderNotePayload(order.id, remoteOrderId, selectedSite.get(), dataModel)
            dispatcher.dispatch(WCOrderActionBuilder.newPostOrderNoteAction(payload))
        }
        return when (result) {
            is Cancellation -> false
            is Success -> result.value
        }
    }

    suspend fun addOrderShipmentTracking(
        orderIdentifier: OrderIdentifier,
        shipmentTrackingModel: OrderShipmentTracking
    ): Boolean {
        val orderIdSet = orderIdentifier.toIdSet()
        val result = continuationAddShipmentTracking.callAndWaitUntilTimeout(AppConstants.REQUEST_TIMEOUT) {
            val payload = AddOrderShipmentTrackingPayload(
                selectedSite.get(),
                orderIdSet.id,
                orderIdSet.remoteOrderId,
                shipmentTrackingModel.toDataModel(),
                shipmentTrackingModel.isCustomProvider
            )
            dispatcher.dispatch(WCOrderActionBuilder.newAddOrderShipmentTrackingAction(payload))
        }
        return when (result) {
            is Cancellation -> false
            is Success -> result.value
        }
    }

    suspend fun deleteOrderShipmentTracking(
        localOrderId: Int,
        remoteOrderId: Long,
        shipmentTrackingModel: WCOrderShipmentTrackingModel
    ): Boolean {
        val result = continuationDeleteShipmentTracking.callAndWaitUntilTimeout(AppConstants.REQUEST_TIMEOUT) {
            val payload = DeleteOrderShipmentTrackingPayload(
                selectedSite.get(), localOrderId, remoteOrderId, shipmentTrackingModel
            )
            dispatcher.dispatch(WCOrderActionBuilder.newDeleteOrderShipmentTrackingAction(payload))
        }
        return when (result) {
            is Cancellation -> false
            is Success -> result.value
        }
    }

    fun getOrder(orderIdentifier: OrderIdentifier) = orderStore.getOrderByIdentifier(orderIdentifier)?.toAppModel()

    fun getOrderStatus(key: String): OrderStatus {
        return (orderStore.getOrderStatusForSiteAndKey(selectedSite.get(), key) ?: WCOrderStatusModel().apply {
            statusKey = key
            label = key
        }).toOrderStatus()
    }

    fun getOrderStatusOptions() = orderStore.getOrderStatusOptionsForSite(selectedSite.get()).map { it.toOrderStatus() }

    fun getOrderNotes(localOrderId: Int) =
        orderStore.getOrderNotesForOrder(localOrderId).map { it.toAppModel() }

    suspend fun fetchProductsByRemoteIds(remoteIds: List<Long>) =
        productStore.fetchProductListSynced(selectedSite.get(), remoteIds)?.map { it.toAppModel() } ?: emptyList()

    fun hasVirtualProductsOnly(remoteProductIds: List<Long>): Boolean {
        return if (remoteProductIds.isNotEmpty()) {
            productStore.getVirtualProductCountByRemoteIds(
                selectedSite.get(), remoteProductIds
            ) == remoteProductIds.size
        } else false
    }

    fun getProductCountForOrder(remoteProductIds: List<Long>): Int {
        return if (remoteProductIds.isNotEmpty()) {
            productStore.getProductCountByRemoteIds(selectedSite.get(), remoteProductIds)
        } else 0
    }

    fun hasSubscriptionProducts(remoteProductIds: List<Long>): Boolean {
        return if (remoteProductIds.isNotEmpty()) {
            productStore.getProductsByRemoteIds(selectedSite.get(), remoteProductIds)
                .any { it.type == PRODUCT_SUBSCRIPTION_TYPE }
        } else false
    }

    fun getOrderRefunds(remoteOrderId: Long) = refundStore
        .getAllRefunds(selectedSite.get(), remoteOrderId)
        .map { it.toAppModel() }
        .reversed()
        .sortedBy { it.id }

    fun getOrderShipmentTrackingByTrackingNumber(
        localOrderId: Int,
        trackingNumber: String
    ): OrderShipmentTracking? = orderStore.getShipmentTrackingByTrackingNumber(
        selectedSite.get(), localOrderId, trackingNumber
    )?.toAppModel()

    fun getOrderShipmentTrackings(localOrderId: Int) =
        orderStore.getShipmentTrackingsForOrder(selectedSite.get(), localOrderId).map { it.toAppModel() }

    fun getOrderShippingLabels(remoteOrderId: Long) = shippingLabelStore
        .getShippingLabelsForOrder(selectedSite.get(), remoteOrderId)
        .filter { it.status == LabelItem.STATUS_PURCHASED }
        .map { it.toAppModel() }

    fun getWooServicesPluginInfo(): WooPlugin {
        val info = wooCommerceStore.getWooCommerceServicesPluginInfo(selectedSite.get())
        return WooPlugin(info != null, info?.active ?: false, info?.version)
    }

    fun getStoreCountryCode(): String? {
        return wooCommerceStore.getStoreCountryCode(selectedSite.get())
    }

    suspend fun fetchSLCreationEligibility(orderId: Long) {
        val result = shippingLabelStore.fetchShippingLabelCreationEligibility(
            site = selectedSite.get(),
            orderId = orderId,
            canCreatePackage = ShippingLabelCreationFeatures.CAN_CREATE_PACKAGE,
            canCreatePaymentMethod = ShippingLabelCreationFeatures.CAN_CREATE_PAYMENT_METHOD,
            canCreateCustomsForm = ShippingLabelCreationFeatures.CAN_CREATE_CUSTOMS_FORM
        )
        if (result.isError) {
            WooLog.e(
                tag = ORDERS,
                message = "Fetching shipping labels creation eligibility failed for $orderId, " +
                    "error: ${result.error.type} ${result.error.message}")
        } else if (!result.model!!.isEligible) {
            WooLog.d(
                tag = ORDERS,
                message = "Order $orderId is not eligible for shipping labels creation, " +
                    "reason: ${result.model!!.reason}")
        }
    }

    fun isOrderEligibleForSLCreation(orderId: Long): Boolean {
        return shippingLabelStore.isOrderEligibleForShippingLabelCreation(
            site = selectedSite.get(),
            orderId = orderId,
            canCreatePackage = ShippingLabelCreationFeatures.CAN_CREATE_PACKAGE,
            canCreatePaymentMethod = ShippingLabelCreationFeatures.CAN_CREATE_PAYMENT_METHOD,
            canCreateCustomsForm = ShippingLabelCreationFeatures.CAN_CREATE_CUSTOMS_FORM
        )?.isEligible ?: false
    }

    @Suppress("unused")
    @Subscribe(threadMode = MAIN)
    fun onOrderChanged(event: OnOrderChanged) {
        when (event.causeOfChange) {
            WCOrderAction.FETCH_SINGLE_ORDER -> {
                if (event.isError) {
                    continuationFetchOrder.continueWith(false)
                } else {
                    continuationFetchOrder.continueWith(true)
                }
            }
            WCOrderAction.FETCH_ORDER_NOTES -> {
                if (event.isError) {
                    continuationFetchOrderNotes.continueWith(false)
                } else {
                    continuationFetchOrderNotes.continueWith(true)
                }
            }
            WCOrderAction.FETCH_ORDER_SHIPMENT_TRACKINGS -> {
                if (event.isError) {
                    val error = if (event.error.type == OrderErrorType.PLUGIN_NOT_ACTIVE) {
                        RequestResult.API_ERROR
                    } else RequestResult.ERROR
                    continuationFetchOrderShipmentTrackingList.continueWith(error)
                } else {
                    continuationFetchOrderShipmentTrackingList.continueWith(RequestResult.SUCCESS)
                }
            }
            WCOrderAction.UPDATE_ORDER_STATUS -> {
                if (event.isError) {
                    AnalyticsTracker.track(
                        Stat.ORDER_STATUS_CHANGE_FAILED, mapOf(
                        AnalyticsTracker.KEY_ERROR_CONTEXT to this::class.java.simpleName,
                        AnalyticsTracker.KEY_ERROR_TYPE to event.error.type.toString(),
                        AnalyticsTracker.KEY_ERROR_DESC to event.error.message)
                    )
                    continuationUpdateOrderStatus.continueWith(false)
                } else {
                    AnalyticsTracker.track(Stat.ORDER_STATUS_CHANGE_SUCCESS)
                    continuationUpdateOrderStatus.continueWith(true)
                }
            }
            WCOrderAction.POST_ORDER_NOTE -> {
                if (event.isError) {
                    AnalyticsTracker.track(
                        Stat.ORDER_NOTE_ADD_FAILED, mapOf(
                        AnalyticsTracker.KEY_ERROR_CONTEXT to this::class.java.simpleName,
                        AnalyticsTracker.KEY_ERROR_TYPE to event.error.type.toString(),
                        AnalyticsTracker.KEY_ERROR_DESC to event.error.message)
                    )
                    continuationAddOrderNote.continueWith(false)
                } else {
                    AnalyticsTracker.track(Stat.ORDER_NOTE_ADD_SUCCESS)
                    continuationAddOrderNote.continueWith(true)
                }
            }
            WCOrderAction.ADD_ORDER_SHIPMENT_TRACKING -> {
                if (event.isError) {
                    AnalyticsTracker.track(
                        Stat.ORDER_TRACKING_ADD_FAILED, mapOf(
                        AnalyticsTracker.KEY_ERROR_CONTEXT to this::class.java.simpleName,
                        AnalyticsTracker.KEY_ERROR_TYPE to event.error.type.toString(),
                        AnalyticsTracker.KEY_ERROR_DESC to event.error.message)
                    )
                    continuationAddShipmentTracking.continueWith(false)
                } else {
                    AnalyticsTracker.track(Stat.ORDER_TRACKING_ADD_SUCCESS)
                    continuationAddShipmentTracking.continueWith(true)
                }
            }
            WCOrderAction.DELETE_ORDER_SHIPMENT_TRACKING -> {
                if (event.isError) {
                    AnalyticsTracker.track(
                        Stat.ORDER_TRACKING_DELETE_FAILED, mapOf(
                        AnalyticsTracker.KEY_ERROR_CONTEXT to this::class.java.simpleName,
                        AnalyticsTracker.KEY_ERROR_TYPE to event.error.type.toString(),
                        AnalyticsTracker.KEY_ERROR_DESC to event.error.message
                    ))
                    continuationDeleteShipmentTracking.continueWith(false)
                } else {
                    AnalyticsTracker.track(Stat.ORDER_TRACKING_DELETE_SUCCESS)
                    continuationDeleteShipmentTracking.continueWith(true)
                }
            }
            else -> {
            }
        }
    }

    class OnProductImageChanged(val remoteProductId: Long)

    /**
     * This will be triggered if we fetched a product via ProduictImageMap so we could get its image.
     * Here we fire an event that tells the fragment to update that product in the order product list.
     */
    @SuppressWarnings("unused")
    @Subscribe(threadMode = MAIN)
    fun onProductChanged(event: OnProductChanged) {
        if (event.causeOfChange == FETCH_SINGLE_PRODUCT && !event.isError) {
            EventBus.getDefault().post(OnProductImageChanged(event.remoteProductId))
        }
    }

    companion object {
        const val PRODUCT_SUBSCRIPTION_TYPE = "subscription"
    }
}
