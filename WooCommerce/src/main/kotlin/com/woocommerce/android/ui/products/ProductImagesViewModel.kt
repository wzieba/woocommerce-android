package com.woocommerce.android.ui.products

import android.content.DialogInterface
import android.net.Uri
import android.os.Parcelable
import androidx.lifecycle.SavedStateHandle
import com.woocommerce.android.R.string
import com.woocommerce.android.RequestCodes
import com.woocommerce.android.analytics.AnalyticsTracker
import com.woocommerce.android.analytics.AnalyticsTracker.Stat
import com.woocommerce.android.analytics.AnalyticsTracker.Stat.PRODUCT_DETAIL_IMAGE_TAPPED
import com.woocommerce.android.analytics.AnalyticsTracker.Stat.PRODUCT_IMAGE_SETTINGS_ADD_IMAGES_BUTTON_TAPPED
import com.woocommerce.android.media.ProductImagesService
import com.woocommerce.android.media.ProductImagesService.Companion.OnProductImageUploaded
import com.woocommerce.android.media.ProductImagesService.Companion.OnProductImagesUpdateCompletedEvent
import com.woocommerce.android.media.ProductImagesService.Companion.OnProductImagesUpdateStartedEvent
import com.woocommerce.android.media.ProductImagesServiceWrapper
import com.woocommerce.android.model.Product.Image
import com.woocommerce.android.model.toAppModel
import com.woocommerce.android.tools.NetworkStatus
import com.woocommerce.android.ui.products.ProductImagesViewModel.ProductImagesState.Browsing
import com.woocommerce.android.ui.products.ProductImagesViewModel.ProductImagesState.Dragging
import com.woocommerce.android.util.swap
import com.woocommerce.android.viewmodel.LiveDataDelegate
import com.woocommerce.android.viewmodel.MultiLiveEvent.Event
import com.woocommerce.android.viewmodel.MultiLiveEvent.Event.ExitWithResult
import com.woocommerce.android.viewmodel.MultiLiveEvent.Event.ShowDialog
import com.woocommerce.android.viewmodel.MultiLiveEvent.Event.ShowSnackbar
import com.woocommerce.android.viewmodel.ScopedViewModel
import com.woocommerce.android.viewmodel.navArgs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.parcelize.Parcelize
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import javax.inject.Inject

@HiltViewModel
class ProductImagesViewModel @Inject constructor(
    private val networkStatus: NetworkStatus,
    private val productImagesServiceWrapper: ProductImagesServiceWrapper,
    savedState: SavedStateHandle
) : ScopedViewModel(savedState) {
    private val navArgs: ProductImagesFragmentArgs by savedState.navArgs()
    private val originalImages = navArgs.images.toList()

    val isMultiSelectionAllowed = navArgs.requestCode == RequestCodes.PRODUCT_DETAIL_IMAGES

    val viewStateData = LiveDataDelegate(
        savedState,
        ViewState(
            uploadingImageUris = ProductImagesService.getUploadingImageUris(navArgs.remoteId),
            isImageDeletingAllowed = true,
            images = navArgs.images.toList(),
            isWarningVisible = !isMultiSelectionAllowed,
            isDragDropDescriptionVisible = isMultiSelectionAllowed
        )
    ) { old, new ->
        if (old != new) {
            updateButtonStates()
            updateDragAndDropDescriptionStates()
        }
    }
    private var viewState by viewStateData

    val images
        get() = viewState.images ?: emptyList()

    val isImageDeletingAllowed
        get() = viewState.isImageDeletingAllowed ?: true

    init {
        EventBus.getDefault().register(this)

        if (navArgs.showChooser) {
            triggerEvent(ShowImageSourceDialog)
        } else {
            navArgs.selectedImage?.let {
                triggerEvent(ShowImageDetail(it, true))
            }
        }
    }

    fun uploadProductImages(remoteProductId: Long, localUriList: ArrayList<Uri>) {
        if (!networkStatus.isConnected()) {
            triggerEvent(ShowSnackbar(string.network_activity_no_connectivity))
            return
        }
        if (ProductImagesService.isBusy()) {
            triggerEvent(ShowSnackbar(string.product_image_service_busy))
            return
        }
        productImagesServiceWrapper.uploadProductMedia(remoteProductId, localUriList)
    }

    fun onShowStorageChooserButtonClicked() {
        AnalyticsTracker.track(
            Stat.PRODUCT_IMAGE_SETTINGS_ADD_IMAGES_SOURCE_TAPPED,
            mapOf(AnalyticsTracker.KEY_IMAGE_SOURCE to AnalyticsTracker.IMAGE_SOURCE_DEVICE)
        )
        triggerEvent(ShowStorageChooser)
    }

    fun onShowCameraButtonClicked() {
        AnalyticsTracker.track(
            Stat.PRODUCT_IMAGE_SETTINGS_ADD_IMAGES_SOURCE_TAPPED,
            mapOf(AnalyticsTracker.KEY_IMAGE_SOURCE to AnalyticsTracker.IMAGE_SOURCE_CAMERA)
        )
        triggerEvent(ShowCamera)
    }

    fun onShowWPMediaPickerButtonClicked() {
        AnalyticsTracker.track(
            Stat.PRODUCT_IMAGE_SETTINGS_ADD_IMAGES_SOURCE_TAPPED,
            mapOf(AnalyticsTracker.KEY_IMAGE_SOURCE to AnalyticsTracker.IMAGE_SOURCE_WPMEDIA)
        )
        triggerEvent(ShowWPMediaPicker)
    }

    fun onImageRemoved(imageId: Long) {
        viewState = viewState.copy(images = images.filter { it.id != imageId })
    }

    fun onImagesAdded(newImages: List<Image>) {
        viewState = if (isMultiSelectionAllowed) {
            viewState.copy(images = images + newImages)
        } else {
            viewState.copy(images = newImages)
        }
    }

    fun onImageSourceButtonClicked() {
        AnalyticsTracker.track(PRODUCT_IMAGE_SETTINGS_ADD_IMAGES_BUTTON_TAPPED)
        triggerEvent(ShowImageSourceDialog)
    }

    fun onGalleryImageClicked(image: Image) {
        AnalyticsTracker.track(PRODUCT_DETAIL_IMAGE_TAPPED)
        triggerEvent(ShowImageDetail(image))
    }

    fun onValidateButtonClicked() {
        viewState = viewState.copy(productImagesState = Browsing)
    }

    fun onNavigateBackButtonClicked() {
        when (val productImagesState = viewState.productImagesState) {
            is Dragging -> {
                viewState = viewState.copy(
                        productImagesState = Browsing,
                        images = productImagesState.initialState
                )
            }
            Browsing -> {
                onExit()
            }
        }
    }

    private fun onExit() {
        when {
            ProductImagesService.isUploadingForProduct(navArgs.remoteId) -> {
                triggerEvent(ShowDialog(
                    messageId = string.discard_images_message,
                    positiveButtonId = string.discard,
                    positiveBtnAction = DialogInterface.OnClickListener { _, _ ->
                        ProductImagesService.cancel()
                        triggerEvent(ExitWithResult(originalImages))
                    }
                ))
            }
            else -> {
                triggerEvent(ExitWithResult(images))
            }
        }
    }

    private fun updateButtonStates() {
        val numImages = (viewState.images?.size ?: 0) + (viewState.uploadingImageUris?.size ?: 0)
        viewState = viewState.copy(
            chooserButtonButtonTitleRes = when {
                isMultiSelectionAllowed -> string.product_add_photos
                numImages > 0 -> string.product_replace_photo
                else -> string.product_add_photo
            }
        )
    }

    private fun updateDragAndDropDescriptionStates() {
        viewState = viewState.copy(
                isDragDropDescriptionVisible = viewState.productImagesState is Dragging || images.size > 1
        )
    }

    /**
     * The list of product images has started uploading
     */
    @Suppress("unused")
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEventMainThread(event: OnProductImagesUpdateStartedEvent) {
        checkImageUploads(event.id)
    }

    /**
     * The list of product images has finished uploading
     */
    @Suppress("unused")
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEventMainThread(event: OnProductImagesUpdateCompletedEvent) {
        if (event.isCancelled) {
            viewState = viewState.copy(uploadingImageUris = emptyList())
        } else {
            checkImageUploads(event.id)
        }
    }

    /**
     * Checks whether product images are uploading and ensures the view state reflects any currently
     * uploading images
     */
    private fun checkImageUploads(remoteProductId: Long) {
        viewState = if (ProductImagesService.isUploadingForProduct(remoteProductId)) {
            val uris = ProductImagesService.getUploadingImageUris(remoteProductId)
            val images = if (isMultiSelectionAllowed) viewState.images else emptyList()
            viewState.copy(images = images, uploadingImageUris = uris)
        } else {
            viewState.copy(uploadingImageUris = emptyList())
        }
    }

    override fun onCleared() {
        super.onCleared()
        EventBus.getDefault().unregister(this)
    }

    /**
     * A single product image has finished uploading
     */
    @Suppress("unused")
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEventMainThread(event: OnProductImageUploaded) {
        if (event.isError) {
            triggerEvent(ShowSnackbar(string.product_image_service_error_uploading))
        } else {
            event.media?.let { media ->
                viewState = if (isMultiSelectionAllowed) {
                    viewState.copy(images = images + media.toAppModel())
                } else {
                    viewState.copy(images = listOf(media.toAppModel()))
                }
            }
        }
        checkImageUploads(navArgs.remoteId)
    }

    fun onGalleryImageDragStarted() {
        when (viewState.productImagesState) {
            is Dragging -> { /* no-op*/ }
            Browsing -> viewState = viewState.copy(productImagesState = Dragging(images))
        }
    }

    fun onGalleryImageDeleteIconClicked(image: Image) {
        triggerEvent(ShowDeleteImageConfirmation(image))
    }

    fun onDeleteImageConfirmed(image: Image) {
        viewState = viewState.copy(images = images - image)
    }

    fun onGalleryImageMoved(from: Int, to: Int) {
        val canSwap = from >= 0 && from < images.size && to >= 0 && to < images.size
        if (canSwap) {
            val reorderedImages = images.swap(from, to)
            viewState = viewState.copy(images = reorderedImages)
        }
    }

    @Parcelize
    data class ViewState(
        val uploadingImageUris: List<Uri>? = null,
        val isImageDeletingAllowed: Boolean? = null,
        val images: List<Image>? = null,
        val chooserButtonButtonTitleRes: Int? = null,
        val isWarningVisible: Boolean? = null,
        val isDragDropDescriptionVisible: Boolean? = null,
        val productImagesState: ProductImagesState = Browsing
    ) : Parcelable

    object ShowImageSourceDialog : Event()
    object ShowStorageChooser : Event()
    object ShowCamera : Event()
    object ShowWPMediaPicker : Event()
    data class ShowDeleteImageConfirmation(val image: Image) : Event()
    data class ShowImageDetail(val image: Image, val isOpenedDirectly: Boolean = false) : Event()

    sealed class ProductImagesState : Parcelable {
        @Parcelize
        data class Dragging(val initialState: List<Image>) : ProductImagesState()
        @Parcelize
        object Browsing : ProductImagesState()
    }
}
