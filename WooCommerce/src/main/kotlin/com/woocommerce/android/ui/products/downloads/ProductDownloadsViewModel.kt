package com.woocommerce.android.ui.products.downloads

import android.net.Uri
import android.os.Parcelable
import com.squareup.inject.assisted.Assisted
import com.squareup.inject.assisted.AssistedInject
import com.woocommerce.android.R.string
import com.woocommerce.android.media.MediaFilesRepository
import com.woocommerce.android.model.ProductFile
import com.woocommerce.android.ui.products.BaseProductEditorViewModel
import com.woocommerce.android.ui.products.ProductDetailViewModel
import com.woocommerce.android.ui.products.ProductDetailViewModel.ProductDetailViewState
import com.woocommerce.android.ui.products.ProductDetailViewModel.ProductExitEvent.ExitProductDownloads
import com.woocommerce.android.ui.products.ProductEditorViewModelAssistedFactory
import com.woocommerce.android.util.CoroutineDispatchers
import com.woocommerce.android.viewmodel.LiveDataDelegate
import com.woocommerce.android.viewmodel.MultiLiveEvent.Event.ShowSnackbar
import com.woocommerce.android.viewmodel.SavedStateWithArgs
import kotlinx.android.parcel.Parcelize
import kotlinx.coroutines.launch

class ProductDownloadsViewModel @AssistedInject constructor(
    @Assisted savedState: SavedStateWithArgs,
    coroutineDispatchers: CoroutineDispatchers,
    @Assisted sharedViewModel: ProductDetailViewModel,
    private val mediaFilesRepository: MediaFilesRepository
) : BaseProductEditorViewModel(savedState, coroutineDispatchers, sharedViewModel) {
    // view state for the product downloads screen
    val productDownloadsViewStateData = LiveDataDelegate(savedState, ProductDownloadsViewState())
    private var productDownloadsViewState by productDownloadsViewStateData

    fun swapDownloadableFiles(from: Int, to: Int) {
        sharedViewModel.swapDownloadableFiles(from, to)
    }

    fun onProductDownloadClicked(file: ProductFile) {
        sharedViewModel.onProductDownloadClicked(file)
    }

    fun onDoneButtonClicked(exitProductDownloads: ExitProductDownloads) {
        sharedViewModel.onDoneButtonClicked(exitProductDownloads)
    }

    fun onDownloadsSettingsClicked() {
        sharedViewModel.onDownloadsSettingsClicked()
    }

    fun onAddDownloadableFileClicked() {
        sharedViewModel.onAddDownloadableFileClicked()
    }

    fun getProduct(): ProductDetailViewState {
        return sharedViewModel.getProduct()
    }

    fun uploadDownloadableFile(uri: Uri) {
        launch {
            productDownloadsViewState = productDownloadsViewState.copy(isUploadingDownloadableFile = true)
            try {
                val url = mediaFilesRepository.uploadFile(uri)
                sharedViewModel.showAddProductDownload(url)
            } catch (e: Exception) {
                triggerEvent(ShowSnackbar(string.product_downloadable_files_upload_failed))
            } finally {
                productDownloadsViewState = productDownloadsViewState.copy(isUploadingDownloadableFile = false)
            }
        }
    }

    @Parcelize
    data class ProductDownloadsViewState(
        val isUploadingDownloadableFile: Boolean? = null
    ) : Parcelable

    @AssistedInject.Factory
    interface Factory : ProductEditorViewModelAssistedFactory<ProductDownloadsViewModel>
}
