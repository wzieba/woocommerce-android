package com.woocommerce.android.ui.orders.shippinglabels

import android.os.Parcelable
import androidx.lifecycle.SavedStateHandle
import com.woocommerce.android.R.string
import com.woocommerce.android.analytics.AnalyticsTracker
import com.woocommerce.android.analytics.AnalyticsTracker.Stat
import com.woocommerce.android.media.FileUtils
import com.woocommerce.android.tools.NetworkStatus
import com.woocommerce.android.ui.orders.OrderNavigationTarget.ViewPrintCustomsForm
import com.woocommerce.android.ui.orders.OrderNavigationTarget.ViewPrintShippingLabelInfo
import com.woocommerce.android.ui.orders.OrderNavigationTarget.ViewShippingLabelFormatOptions
import com.woocommerce.android.ui.orders.OrderNavigationTarget.ViewShippingLabelPaperSizes
import com.woocommerce.android.ui.orders.shippinglabels.ShippingLabelPaperSizeSelectorDialog.ShippingLabelPaperSize
import com.woocommerce.android.util.Base64Decoder
import com.woocommerce.android.util.CoroutineDispatchers
import com.woocommerce.android.viewmodel.LiveDataDelegate
import com.woocommerce.android.viewmodel.MultiLiveEvent.Event.ExitWithResult
import com.woocommerce.android.viewmodel.MultiLiveEvent.Event.ShowSnackbar
import com.woocommerce.android.viewmodel.ScopedViewModel
import com.woocommerce.android.viewmodel.navArgs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize
import java.io.File
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class PrintShippingLabelViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val dispatchers: CoroutineDispatchers,
    private val repository: ShippingLabelRepository,
    private val networkStatus: NetworkStatus,
    private val fileUtils: FileUtils,
    private val base64Decoder: Base64Decoder
) : ScopedViewModel(savedState) {
    private val arguments: PrintShippingLabelFragmentArgs by savedState.navArgs()
    private val label
        get() = repository.getShippingLabelByOrderIdAndLabelId(
            orderId = arguments.orderId,
            shippingLabelId = arguments.shippingLabelId
        )

    val viewStateData = LiveDataDelegate(savedState, PrintShippingLabelViewState(
        isLabelExpired = label?.isAnonymized == true ||
            label?.expiryDate?.let { Date().after(it) } ?: false
    ))
    private var viewState by viewStateData

    fun onPrintShippingLabelInfoSelected() {
        triggerEvent(ViewPrintShippingLabelInfo)
    }

    fun onViewLabelFormatOptionsClicked() {
        triggerEvent(ViewShippingLabelFormatOptions)
    }

    fun onPaperSizeOptionsSelected() {
        triggerEvent(ViewShippingLabelPaperSizes(viewState.paperSize))
    }

    fun onPaperSizeSelected(paperSize: ShippingLabelPaperSize) {
        viewState = viewState.copy(paperSize = paperSize)
    }

    fun onSaveForLaterClicked() {
        triggerEvent(ExitWithResult(Unit))
    }

    fun onPrintShippingLabelClicked() {
        if (networkStatus.isConnected()) {
            AnalyticsTracker.track(Stat.SHIPPING_LABEL_PRINT_REQUESTED)
            viewState = viewState.copy(isProgressDialogShown = true)
            launch {
                val requestResult = repository.printShippingLabel(
                    viewState.paperSize.name.toLowerCase(Locale.US), arguments.shippingLabelId
                )

                viewState = viewState.copy(isProgressDialogShown = false)
                if (requestResult.isError) {
                    triggerEvent(ShowSnackbar(string.shipping_label_preview_error))
                } else {
                    viewState = viewState.copy(previewShippingLabel = requestResult.model)
                }
            }
        } else {
            triggerEvent(ShowSnackbar(string.offline_error))
        }
    }

    fun writeShippingLabelToFile(
        storageDir: File,
        shippingLabelPreview: String
    ) {
        launch(dispatchers.io) {
            val tempFile = fileUtils.createTempTimeStampedFile(
                storageDir = storageDir,
                prefix = "PDF",
                fileExtension = "pdf"
            )
            if (tempFile != null) {
                val content = base64Decoder.decode(shippingLabelPreview, 0)
                fileUtils.writeContentToFile(tempFile, content)?.let {
                    withContext(dispatchers.main) { viewState = viewState.copy(tempFile = it) }
                } ?: handlePreviewError()
            } else {
                handlePreviewError()
            }
        }
    }

    fun onPreviewLabelCompleted() {
        viewState = viewState.copy(tempFile = null, previewShippingLabel = null)
        label?.let {
            if (it.hasCommercialInvoice) {
                triggerEvent(ViewPrintCustomsForm(it.commercialInvoiceUrl!!, arguments.isReprint))
            }
        }
    }

    private suspend fun handlePreviewError() {
        withContext(dispatchers.main) {
            triggerEvent(ShowSnackbar(string.shipping_label_preview_error))
        }
    }

    @Parcelize
    data class PrintShippingLabelViewState(
        val paperSize: ShippingLabelPaperSize = ShippingLabelPaperSize.LABEL,
        val isProgressDialogShown: Boolean? = null,
        val previewShippingLabel: String? = null,
        val isLabelExpired: Boolean = false,
        val tempFile: File? = null
    ) : Parcelable
}
