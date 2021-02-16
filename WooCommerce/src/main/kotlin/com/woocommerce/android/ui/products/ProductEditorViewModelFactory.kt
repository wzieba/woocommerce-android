package com.woocommerce.android.ui.products

import android.os.Bundle
import androidx.lifecycle.AbstractSavedStateViewModelFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.savedstate.SavedStateRegistryOwner
import com.woocommerce.android.viewmodel.SavedStateWithArgs
import javax.inject.Inject
import javax.inject.Named

class ProductEditorViewModelFactory
@Inject constructor(
    private val creators: Map<Class<out ViewModel>,
        @JvmSuppressWildcards ProductEditorViewModelAssistedFactory<out BaseProductEditorViewModel>>,
    @Named("child-saved-state-registry-owner") owner: SavedStateRegistryOwner,
    private val sharedViewModel: ProductDetailViewModel,
    private val defaultArgs: Bundle? = null
) : AbstractSavedStateViewModelFactory(owner, defaultArgs) {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(
        key: String,
        viewModelClass: Class<T>,
        savedState: SavedStateHandle): T {
        return creators[viewModelClass]?.create(SavedStateWithArgs(savedState, defaultArgs), sharedViewModel) as? T
            ?: throw IllegalArgumentException("[$viewModelClass] not found. Did you add it to a module?")
    }
}

interface ProductEditorViewModelAssistedFactory<T : BaseProductEditorViewModel> {
    fun create(savedState: SavedStateWithArgs, sharedViewModel: ProductDetailViewModel): T
}
