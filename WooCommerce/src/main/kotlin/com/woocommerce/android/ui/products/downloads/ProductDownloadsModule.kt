package com.woocommerce.android.ui.products.downloads

import android.os.Bundle
import androidx.lifecycle.ViewModel
import androidx.navigation.fragment.findNavController
import androidx.navigation.navGraphViewModels
import androidx.savedstate.SavedStateRegistryOwner
import com.woocommerce.android.R
import com.woocommerce.android.di.ViewModelAssistedFactory
import com.woocommerce.android.ui.products.BaseProductEditorViewModel
import com.woocommerce.android.ui.products.ProductDetailViewModel
import com.woocommerce.android.ui.products.ProductEditorViewModelAssistedFactory
import com.woocommerce.android.viewmodel.ViewModelFactory
import com.woocommerce.android.viewmodel.ViewModelKey
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoMap
import javax.inject.Named

@Module
abstract class ProductDownloadsModule {
    companion object {
        @Provides
        fun provideDefaultArgs(fragment: ProductDownloadsFragment): Bundle? {
            return fragment.findNavController().getBackStackEntry(R.id.nav_graph_products).arguments
        }

        @Provides
        fun provideSavedStateRegistryOwner(fragment: ProductDownloadsFragment): SavedStateRegistryOwner {
            return fragment.findNavController().getBackStackEntry(R.id.nav_graph_products)
        }

        @Provides
        @Named("child-saved-state-registry-owner")
        fun provideChildSavedStateRegistryOwner(fragment: ProductDownloadsFragment): SavedStateRegistryOwner {
            return fragment
        }

        @Provides
        fun provideSharedViewModel(
            fragment: ProductDownloadsFragment,
            factory: ViewModelFactory
        ): ProductDetailViewModel {
            return fragment.navGraphViewModels<ProductDetailViewModel>(R.id.nav_graph_products) { factory }.value
        }
    }

    @Binds
    @IntoMap
    @ViewModelKey(ProductDetailViewModel::class)
    abstract fun bindSharedViewModelFactory(factory: ProductDetailViewModel.Factory): ViewModelAssistedFactory<out ViewModel>

    @Binds
    @IntoMap
    @ViewModelKey(ProductDownloadsViewModel::class)
    abstract fun bindFactory(factory: ProductDownloadsViewModel.Factory): ProductEditorViewModelAssistedFactory<out BaseProductEditorViewModel>
}
