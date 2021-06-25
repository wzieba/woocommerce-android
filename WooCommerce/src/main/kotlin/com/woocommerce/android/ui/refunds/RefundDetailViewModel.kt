package com.woocommerce.android.ui.refunds

import android.os.Parcelable
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import com.woocommerce.android.R
import com.woocommerce.android.extensions.calculateTotals
import com.woocommerce.android.extensions.isCashPayment
import com.woocommerce.android.model.Order
import com.woocommerce.android.model.Refund
import com.woocommerce.android.model.toAppModel
import com.woocommerce.android.tools.SelectedSite
import com.woocommerce.android.ui.refunds.RefundProductListAdapter.ProductRefundListItem
import com.woocommerce.android.util.CurrencyFormatter
import com.woocommerce.android.viewmodel.LiveDataDelegate
import com.woocommerce.android.viewmodel.ResourceProvider
import com.woocommerce.android.viewmodel.ScopedViewModel
import com.woocommerce.android.viewmodel.navArgs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.parcelize.Parcelize
import org.wordpress.android.fluxc.model.order.OrderIdentifier
import org.wordpress.android.fluxc.store.WCOrderStore
import org.wordpress.android.fluxc.store.WCRefundStore
import java.math.BigDecimal
import javax.inject.Inject

@HiltViewModel
class RefundDetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    orderStore: WCOrderStore,
    private val selectedSite: SelectedSite,
    private val currencyFormatter: CurrencyFormatter,
    private val resourceProvider: ResourceProvider,
    private val refundStore: WCRefundStore
) : ScopedViewModel(savedState) {
    val viewStateData = LiveDataDelegate(savedState, ViewState())
    private var viewState by viewStateData

    private val _refundItems = MutableLiveData<List<ProductRefundListItem>>()
    val refundItems: LiveData<List<ProductRefundListItem>> = _refundItems

    private lateinit var formatCurrency: (BigDecimal) -> String

    private val navArgs: RefundDetailFragmentArgs by savedState.navArgs()

    init {
        val orderModel = orderStore.getOrderByIdentifier(OrderIdentifier(selectedSite.get().id, navArgs.orderId))
        orderModel?.toAppModel()?.let { order ->
            formatCurrency = currencyFormatter.buildBigDecimalFormatter(order.currency)
            if (navArgs.refundId > 0) {
                refundStore.getRefund(selectedSite.get(), navArgs.orderId, navArgs.refundId)
                    ?.toAppModel()?.let { refund ->
                        displayRefundDetails(refund, order)
                    }
            } else {
                val refunds = refundStore.getAllRefunds(selectedSite.get(), navArgs.orderId).map { it.toAppModel() }
                displayRefundedProducts(order, refunds)
            }
        }
    }

    private fun displayRefundedProducts(order: Order, refunds: List<Refund>) {
        val groupedRefunds = refunds.flatMap { it.items }.groupBy { it.orderItemId }
        val refundedProducts = groupedRefunds.keys.mapNotNull { id ->
            order.items.firstOrNull { it.itemId == id }?.let { item ->
                groupedRefunds[id]?.sumBy { it.quantity }?.let { quantity ->
                    ProductRefundListItem(item, quantity = quantity)
                }
            }
        }

        viewState = viewState.copy(
            currency = order.currency,
            screenTitle = resourceProvider.getString(R.string.orderdetail_refunded_products),
            areItemsVisible = true,
            areDetailsVisible = false
        )

        _refundItems.value = refundedProducts
    }

    private fun displayRefundDetails(refund: Refund, order: Order) {
        if (refund.items.isNotEmpty()) {
            val items = refund.items.map { refundItem ->
                ProductRefundListItem(
                    order.items.first { it.itemId == refundItem.orderItemId },
                    quantity = refundItem.quantity
                )
            }

            val (subtotal, taxes) = items.calculateTotals()
            viewState = viewState.copy(
                    currency = order.currency,
                    areItemsVisible = true,
                    subtotal = formatCurrency(subtotal),
                    taxes = formatCurrency(taxes)
            )

            _refundItems.value = items
        } else {
            viewState = viewState.copy(areItemsVisible = false)
        }

        viewState = viewState.copy(
                screenTitle = "${resourceProvider.getString(R.string.order_refunds_refund)} #${refund.id}",
                refundAmount = formatCurrency(refund.amount),
                refundMethod = resourceProvider.getString(
                        R.string.order_refunds_refunded_via,
                        getRefundMethod(order, refund)),
                refundReason = refund.reason,
                areDetailsVisible = true
        )
    }

    private fun getRefundMethod(order: Order, refund: Refund): String {
        val manualRefund = resourceProvider.getString(R.string.order_refunds_manual_refund)
        return if (order.paymentMethodTitle.isNotBlank() &&
                (refund.automaticGatewayRefund || order.paymentMethod.isCashPayment)) {
            order.paymentMethodTitle
        } else if (order.paymentMethodTitle.isNotBlank()) {
            "$manualRefund - ${order.paymentMethodTitle}"
        } else {
            manualRefund
        }
    }

    @Parcelize
    data class ViewState(
        val screenTitle: String? = null,
        val refundAmount: String? = null,
        val subtotal: String? = null,
        val taxes: String? = null,
        val refundMethod: String? = null,
        val refundReason: String? = null,
        val currency: String? = null,
        val areItemsVisible: Boolean? = null,
        val areDetailsVisible: Boolean? = null
    ) : Parcelable
}
