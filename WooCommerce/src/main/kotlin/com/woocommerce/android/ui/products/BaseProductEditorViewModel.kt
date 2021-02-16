package com.woocommerce.android.ui.products

import com.woocommerce.android.util.CoroutineDispatchers
import com.woocommerce.android.viewmodel.SavedStateWithArgs
import com.woocommerce.android.viewmodel.ScopedViewModel

open class BaseProductEditorViewModel(
    savedState: SavedStateWithArgs,
    coroutineDispatchers: CoroutineDispatchers,
    protected val sharedViewModel: ProductDetailViewModel
) :
    ScopedViewModel(savedState, coroutineDispatchers) {

    override fun onCleared() {
        // We can save the changes here to the sharedViewModel
        super.onCleared()
    }
}
