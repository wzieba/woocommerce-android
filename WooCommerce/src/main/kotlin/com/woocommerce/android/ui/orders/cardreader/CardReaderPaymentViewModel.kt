package com.woocommerce.android.ui.orders.cardreader

import androidx.annotation.DimenRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import com.woocommerce.android.R
import com.woocommerce.android.analytics.AnalyticsTracker
import com.woocommerce.android.analytics.AnalyticsTracker.Stat.RECEIPT_EMAIL_FAILED
import com.woocommerce.android.analytics.AnalyticsTracker.Stat.RECEIPT_EMAIL_TAPPED
import com.woocommerce.android.analytics.AnalyticsTracker.Stat.RECEIPT_PRINT_CANCELED
import com.woocommerce.android.analytics.AnalyticsTracker.Stat.RECEIPT_PRINT_FAILED
import com.woocommerce.android.analytics.AnalyticsTracker.Stat.RECEIPT_PRINT_SUCCESS
import com.woocommerce.android.analytics.AnalyticsTracker.Stat.RECEIPT_PRINT_TAPPED
import com.woocommerce.android.analytics.AnalyticsTrackerWrapper
import com.woocommerce.android.cardreader.CardPaymentStatus
import com.woocommerce.android.cardreader.CardPaymentStatus.CapturingPayment
import com.woocommerce.android.cardreader.CardPaymentStatus.CardPaymentStatusErrorType
import com.woocommerce.android.cardreader.CardPaymentStatus.CollectingPayment
import com.woocommerce.android.cardreader.CardPaymentStatus.InitializingPayment
import com.woocommerce.android.cardreader.CardPaymentStatus.PaymentCompleted
import com.woocommerce.android.cardreader.CardPaymentStatus.PaymentFailed
import com.woocommerce.android.cardreader.CardPaymentStatus.ProcessingPayment
import com.woocommerce.android.cardreader.CardPaymentStatus.ShowAdditionalInfo
import com.woocommerce.android.cardreader.CardPaymentStatus.WaitingForInput
import com.woocommerce.android.cardreader.CardReaderManager
import com.woocommerce.android.cardreader.PaymentData
import com.woocommerce.android.model.Order
import com.woocommerce.android.model.UiString
import com.woocommerce.android.model.UiString.UiStringRes
import com.woocommerce.android.model.UiString.UiStringText
import com.woocommerce.android.tools.SelectedSite
import com.woocommerce.android.ui.orders.cardreader.CardReaderPaymentViewModel.CardReaderPaymentEvent.PrintReceipt
import com.woocommerce.android.ui.orders.cardreader.CardReaderPaymentViewModel.CardReaderPaymentEvent.SendReceipt
import com.woocommerce.android.ui.orders.cardreader.CardReaderPaymentViewModel.PrintJobResult.CANCELLED
import com.woocommerce.android.ui.orders.cardreader.CardReaderPaymentViewModel.PrintJobResult.FAILED
import com.woocommerce.android.ui.orders.cardreader.CardReaderPaymentViewModel.PrintJobResult.STARTED
import com.woocommerce.android.ui.orders.cardreader.CardReaderPaymentViewModel.ViewState.CapturingPaymentState
import com.woocommerce.android.ui.orders.cardreader.CardReaderPaymentViewModel.ViewState.CollectPaymentState
import com.woocommerce.android.ui.orders.cardreader.CardReaderPaymentViewModel.ViewState.FailedPaymentState
import com.woocommerce.android.ui.orders.cardreader.CardReaderPaymentViewModel.ViewState.LoadingDataState
import com.woocommerce.android.ui.orders.cardreader.CardReaderPaymentViewModel.ViewState.PaymentSuccessfulState
import com.woocommerce.android.ui.orders.cardreader.CardReaderPaymentViewModel.ViewState.ProcessingPaymentState
import com.woocommerce.android.ui.orders.cardreader.CardReaderPaymentViewModel.ViewState.ReFetchingOrderState
import com.woocommerce.android.ui.orders.details.OrderDetailRepository
import com.woocommerce.android.util.WooLog
import com.woocommerce.android.viewmodel.MultiLiveEvent.Event
import com.woocommerce.android.viewmodel.MultiLiveEvent.Event.Exit
import com.woocommerce.android.viewmodel.MultiLiveEvent.Event.ShowSnackbar
import com.woocommerce.android.viewmodel.ResourceProvider
import com.woocommerce.android.viewmodel.ScopedViewModel
import com.woocommerce.android.viewmodel.navArgs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

private const val ARTIFICIAL_RETRY_DELAY = 500L

@HiltViewModel
class CardReaderPaymentViewModel
@Suppress("LongParameterList")
@Inject constructor(
    savedState: SavedStateHandle,
    private val cardReaderManager: CardReaderManager,
    private val orderRepository: OrderDetailRepository,
    private val resourceProvider: ResourceProvider,
    private val selectedSite: SelectedSite,
    private val paymentCollectibilityChecker: CardReaderPaymentCollectibilityChecker,
    private val tracker: AnalyticsTrackerWrapper
) : ScopedViewModel(savedState) {
    private val arguments: CardReaderPaymentDialogArgs by savedState.navArgs()

    // The app shouldn't store the state as payment flow gets canceled when the vm dies
    private val viewState = MutableLiveData<ViewState>(LoadingDataState)
    val viewStateData: LiveData<ViewState> = viewState

    private var paymentFlowJob: Job? = null
    @VisibleForTesting var refetchOrderJob: Job? = null

    fun start() {
        // TODO cardreader Make sure a reader is connected
        if (paymentFlowJob == null) {
            initPaymentFlow(isRetry = false)
        }
    }

    private fun initPaymentFlow(isRetry: Boolean) {
        paymentFlowJob = launch {
            viewState.postValue((LoadingDataState))
            if (isRetry) {
                delay(ARTIFICIAL_RETRY_DELAY)
            }
            fetchOrder()?.let { order ->
                if (!paymentCollectibilityChecker.isCollectable(order)) {
                    triggerEvent(ShowSnackbar(R.string.card_reader_payment_order_paid_payment_cancelled))
                    triggerEvent(Exit)
                    return@launch
                }
                // TODO cardreader don't hardcode currency symbol ($)
                collectPaymentFlow(
                    cardReaderManager,
                    order.getPaymentDescription(),
                    order.remoteId,
                    order.total,
                    order.currency,
                    order.billingAddress.email,
                    "$${order.total}"
                )
            } ?: run {
                tracker.track(
                    AnalyticsTracker.Stat.CARD_PRESENT_COLLECT_PAYMENT_FAILED,
                    this@CardReaderPaymentViewModel.javaClass.simpleName,
                    null,
                    "Fetching order failed"
                )
                viewState.postValue(
                    FailedPaymentState(
                        errorType = PaymentFlowError.FETCHING_ORDER_FAILED,
                        amountWithCurrencyLabel = null,
                        onPrimaryActionClicked = { initPaymentFlow(isRetry = true) }
                    )
                )
            }
        }
    }

    fun retry(orderId: Long, billingEmail: String, paymentData: PaymentData, amountLabel: String) {
        paymentFlowJob = launch {
            viewState.postValue((LoadingDataState))
            delay(ARTIFICIAL_RETRY_DELAY)
            cardReaderManager.retryCollectPayment(orderId, paymentData).collect { paymentStatus ->
                onPaymentStatusChanged(orderId, billingEmail, paymentStatus, amountLabel)
            }
        }
    }

    private suspend fun collectPaymentFlow(
        cardReaderManager: CardReaderManager,
        paymentDescription: String,
        orderId: Long,
        amount: BigDecimal,
        currency: String,
        billingEmail: String,
        amountLabel: String
    ) {
        cardReaderManager.collectPayment(paymentDescription, orderId, amount, currency, billingEmail.ifEmpty { null })
            .collect { paymentStatus ->
                onPaymentStatusChanged(orderId, billingEmail, paymentStatus, amountLabel)
            }
    }

    private fun onPaymentStatusChanged(
        orderId: Long,
        billingEmail: String,
        paymentStatus: CardPaymentStatus,
        amountLabel: String
    ) {
        when (paymentStatus) {
            InitializingPayment -> viewState.postValue(LoadingDataState)
            CollectingPayment -> viewState.postValue(CollectPaymentState(amountLabel))
            ProcessingPayment -> viewState.postValue(ProcessingPaymentState(amountLabel))
            CapturingPayment -> viewState.postValue(CapturingPaymentState(amountLabel))
            // TODO cardreader store receipt data into a persistent storage
            is PaymentCompleted -> {
                tracker.track(AnalyticsTracker.Stat.CARD_PRESENT_COLLECT_PAYMENT_SUCCESS)
                onPaymentCompleted(paymentStatus, billingEmail, orderId, amountLabel)
            }
            ShowAdditionalInfo -> {
                // TODO cardreader prompt the user to take certain action eg. Remove card
            }
            WaitingForInput -> {
                // TODO cardreader prompt the user to tap/insert a card
            }
            is PaymentFailed -> {
                tracker.track(
                    AnalyticsTracker.Stat.CARD_PRESENT_COLLECT_PAYMENT_FAILED,
                    this@CardReaderPaymentViewModel.javaClass.simpleName,
                    paymentStatus.type.toString(),
                    paymentStatus.errorMessage
                )
                emitFailedPaymentState(orderId, billingEmail, paymentStatus, amountLabel)
            }
        }
    }

    private fun onPaymentCompleted(
        paymentStatus: PaymentCompleted,
        billingEmail: String,
        orderId: Long,
        amountLabel: String
    ) {
        viewState.postValue(PaymentSuccessfulState(
            amountLabel,
            // TODO cardreader this breaks equals of PaymentSuccessfulState - consider if it is ok
            { onPrintReceiptClicked(paymentStatus.receiptUrl, "receipt-order-$orderId") },
            { onSendReceiptClicked(paymentStatus.receiptUrl, billingEmail) }
        ))
        reFetchOrder()
    }

    @VisibleForTesting
    fun reFetchOrder() {
        refetchOrderJob = launch {
            fetchOrder() ?: triggerEvent(ShowSnackbar(R.string.card_reader_refetching_order_failed))
            if (viewState.value == ReFetchingOrderState) {
                triggerEvent(Exit)
            }
        }
    }

    private suspend fun fetchOrder(): Order? {
        return orderRepository.fetchOrder(arguments.orderIdentifier, useCachedOnFailure = false)
    }

    private fun emitFailedPaymentState(orderId: Long, billingEmail: String, error: PaymentFailed, amountLabel: String) {
        WooLog.e(WooLog.T.ORDERS, error.errorMessage)
        val onRetryClicked = error.paymentDataForRetry?.let {
            { retry(orderId, billingEmail, it, amountLabel) }
        } ?: { initPaymentFlow(isRetry = true) }
        viewState.postValue(FailedPaymentState(error.type.mapToUiError(), amountLabel, onRetryClicked))
    }

    private fun onPrintReceiptClicked(receiptUrl: String, documentName: String) {
        launch {
            tracker.track(RECEIPT_PRINT_TAPPED)
            // TODO cardreader show a progress dialog as url loading might take some time
            triggerEvent(PrintReceipt(receiptUrl, documentName))
        }
    }

    private fun onSendReceiptClicked(receiptUrl: String, billingEmail: String) {
        launch {
            tracker.track(RECEIPT_EMAIL_TAPPED)
            triggerEvent(
                SendReceipt(
                    content = UiStringRes(
                        R.string.card_reader_payment_receipt_email_content,
                        listOf(UiStringText(receiptUrl))
                    ),
                    subject = UiStringRes(
                        R.string.card_reader_payment_receipt_email_subject,
                        listOf(UiStringText(selectedSite.get().name.orEmpty()))
                    ),
                    address = billingEmail
                )
            )
        }
    }

    fun onEmailActivityNotFound() {
        tracker.track(RECEIPT_EMAIL_FAILED)
        triggerEvent(ShowSnackbar(R.string.card_reader_payment_email_client_not_found))
    }

    fun onPrintResult(result: PrintJobResult) {
        tracker.track(
            when (result) {
                CANCELLED -> RECEIPT_PRINT_CANCELED
                FAILED -> RECEIPT_PRINT_FAILED
                STARTED -> RECEIPT_PRINT_SUCCESS
            }
        )
    }

    // TODO cardreader cancel payment intent in vm.onCleared if payment not completed with success
    override fun onCleared() {
        super.onCleared()
        orderRepository.onCleanup()
    }

    private fun Order.getPaymentDescription(): String =
        resourceProvider.getString(
            R.string.card_reader_payment_description,
            this.number,
            selectedSite.get().name.orEmpty()
        )

    sealed class CardReaderPaymentEvent : Event() {
        data class PrintReceipt(val receiptUrl: String, val documentName: String) : CardReaderPaymentEvent()
        data class SendReceipt(val content: UiString, val subject: UiString, val address: String) :
            CardReaderPaymentEvent()
    }

    fun onBackPressed() {
        if (refetchOrderJob?.isActive == true) {
            if (viewState.value != ReFetchingOrderState) {
                viewState.value = ReFetchingOrderState
            } else {
                // show "data might be outdated" and exit the flow when the user presses back on FetchingOrder screen
                triggerEvent(ShowSnackbar(R.string.card_reader_refetching_order_failed))
                triggerEvent(Exit)
            }
        } else {
            triggerEvent(Exit)
        }
    }

    @Suppress("LongParameterList")
    sealed class ViewState(
        @StringRes val hintLabel: Int? = null,
        @StringRes val headerLabel: Int? = null,
        @StringRes val paymentStateLabel: Int? = null,
        @DimenRes val paymentStateLabelTopMargin: Int = R.dimen.major_275,
        @DrawableRes val illustration: Int? = null,
        // TODO cardreader add tests
        val isProgressVisible: Boolean = false,
        val primaryActionLabel: Int? = null,
        val secondaryActionLabel: Int? = null
    ) {
        open val onPrimaryActionClicked: (() -> Unit)? = null
        open val onSecondaryActionClicked: (() -> Unit)? = null
        open val amountWithCurrencyLabel: String? = null

        object LoadingDataState : ViewState(
            headerLabel = R.string.card_reader_payment_collect_payment_loading_header,
            hintLabel = R.string.card_reader_payment_collect_payment_loading_hint,
            paymentStateLabel = R.string.card_reader_payment_collect_payment_loading_payment_state,
            isProgressVisible = true
        )

        // TODO cardreader Update FailedPaymentState
        data class FailedPaymentState(
            private val errorType: PaymentFlowError,
            override val amountWithCurrencyLabel: String?,
            override val onPrimaryActionClicked: (() -> Unit)
        ) : ViewState(
            headerLabel = R.string.card_reader_payment_payment_failed_header,
            paymentStateLabel = errorType.message,
            paymentStateLabelTopMargin = R.dimen.major_100,
            primaryActionLabel = R.string.card_reader_payment_failed_retry,
            // TODO cardreader optimize all newly added vector drawables
            illustration = R.drawable.img_products_error
        )

        data class CollectPaymentState(override val amountWithCurrencyLabel: String) : ViewState(
            hintLabel = R.string.card_reader_payment_collect_payment_hint,
            headerLabel = R.string.card_reader_payment_collect_payment_header,
            paymentStateLabel = R.string.card_reader_payment_collect_payment_state,
            illustration = R.drawable.img_card_reader_available
        )

        data class ProcessingPaymentState(override val amountWithCurrencyLabel: String) :
            ViewState(
                hintLabel = R.string.card_reader_payment_processing_payment_hint,
                headerLabel = R.string.card_reader_payment_processing_payment_header,
                paymentStateLabel = R.string.card_reader_payment_processing_payment_state,
                illustration = R.drawable.img_card_reader_available
            )

        data class CapturingPaymentState(override val amountWithCurrencyLabel: String) :
            ViewState(
                hintLabel = R.string.card_reader_payment_capturing_payment_hint,
                headerLabel = R.string.card_reader_payment_capturing_payment_header,
                paymentStateLabel = R.string.card_reader_payment_capturing_payment_state,
                illustration = R.drawable.img_card_reader_available
            )

        data class PaymentSuccessfulState(
            override val amountWithCurrencyLabel: String,
            override val onPrimaryActionClicked: (() -> Unit),
            override val onSecondaryActionClicked: (() -> Unit)
        ) : ViewState(
            headerLabel = R.string.card_reader_payment_completed_payment_header,
            illustration = R.drawable.img_celebration,
            primaryActionLabel = R.string.card_reader_payment_print_receipt,
            secondaryActionLabel = R.string.card_reader_payment_send_receipt
        )

        object ReFetchingOrderState : ViewState(
            headerLabel = R.string.card_reader_payment_fetch_order_loading_header,
            hintLabel = R.string.card_reader_payment_fetch_order_loading_hint,
            paymentStateLabel = R.string.card_reader_payment_fetch_order_loading_payment_state,
            isProgressVisible = true
        )
    }

    enum class PaymentFlowError(val message: Int) {
        FETCHING_ORDER_FAILED(R.string.order_error_fetch_generic),
        NO_NETWORK(R.string.card_reader_payment_failed_no_network_state),
        PAYMENT_DECLINED(R.string.card_reader_payment_failed_card_declined_state),
        GENERIC_ERROR(R.string.card_reader_payment_failed_unexpected_error_state)
    }

    private fun CardPaymentStatusErrorType.mapToUiError(): PaymentFlowError =
        when (this) {
            CardPaymentStatusErrorType.NO_NETWORK -> PaymentFlowError.NO_NETWORK
            CardPaymentStatusErrorType.PAYMENT_DECLINED -> PaymentFlowError.PAYMENT_DECLINED
            CardPaymentStatusErrorType.CARD_READ_TIMED_OUT,
            CardPaymentStatusErrorType.GENERIC_ERROR -> PaymentFlowError.GENERIC_ERROR
        }

    enum class PrintJobResult {
        CANCELLED, STARTED, FAILED
    }
}
