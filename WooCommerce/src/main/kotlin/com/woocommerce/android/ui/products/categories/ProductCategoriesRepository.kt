package com.woocommerce.android.ui.products.categories

import com.woocommerce.android.AppConstants
import com.woocommerce.android.analytics.AnalyticsTracker
import com.woocommerce.android.analytics.AnalyticsTracker.Stat
import com.woocommerce.android.annotations.OpenClassOnDebug
import com.woocommerce.android.model.ProductCategory
import com.woocommerce.android.model.RequestResult
import com.woocommerce.android.model.toProductCategory
import com.woocommerce.android.tools.SelectedSite
import com.woocommerce.android.util.ContinuationWrapper
import com.woocommerce.android.util.ContinuationWrapper.ContinuationResult.Cancellation
import com.woocommerce.android.util.ContinuationWrapper.ContinuationResult.Success
import com.woocommerce.android.util.WooLog
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.wordpress.android.fluxc.Dispatcher
import org.wordpress.android.fluxc.action.WCProductAction.ADDED_PRODUCT_CATEGORY
import org.wordpress.android.fluxc.action.WCProductAction.FETCH_PRODUCT_CATEGORIES
import org.wordpress.android.fluxc.generated.WCProductActionBuilder
import org.wordpress.android.fluxc.model.WCProductCategoryModel
import org.wordpress.android.fluxc.store.WCProductStore
import org.wordpress.android.fluxc.store.WCProductStore.OnProductCategoryChanged
import org.wordpress.android.fluxc.store.WCProductStore.ProductErrorType.TERM_EXISTS
import javax.inject.Inject

@OpenClassOnDebug
class ProductCategoriesRepository @Inject constructor(
    private val dispatcher: Dispatcher,
    private val productStore: WCProductStore,
    private val selectedSite: SelectedSite
) {
    companion object {
        private const val PRODUCT_CATEGORIES_PAGE_SIZE = WCProductStore.DEFAULT_PRODUCT_CATEGORY_PAGE_SIZE
    }

    private var loadContinuation = ContinuationWrapper<Boolean>(WooLog.T.PRODUCTS)
    private var addProductCategoryContinuation = ContinuationWrapper<RequestResult>(WooLog.T.PRODUCTS)
    private var offset = 0

    var canLoadMoreProductCategories = true

    init {
        dispatcher.register(this)
    }

    fun onCleanup() {
        dispatcher.unregister(this)
    }

    /**
     * Submits a fetch request to get a list of products categories for the current site
     * and returns the full list of product categories from the database
     */
    suspend fun fetchProductCategories(loadMore: Boolean = false): List<ProductCategory> {
        loadContinuation.callAndWaitUntilTimeout(AppConstants.REQUEST_TIMEOUT) {
            offset = if (loadMore) offset + PRODUCT_CATEGORIES_PAGE_SIZE else 0
            val payload = WCProductStore.FetchProductCategoriesPayload(
                selectedSite.get(),
                pageSize = PRODUCT_CATEGORIES_PAGE_SIZE,
                offset = offset
            )
            dispatcher.dispatch(WCProductActionBuilder.newFetchProductCategoriesAction(payload))
        }

        return getProductCategoriesList()
    }

    /**
     * Returns all product categories for the current site that are in the database
     */
    fun getProductCategoriesList(): List<ProductCategory> {
        return productStore.getProductCategoriesForSite(selectedSite.get())
            .map { it.toProductCategory() }
    }

    fun getProductCategoryByRemoteId(remoteId: Long) =
        productStore.getProductCategoryByRemoteId(selectedSite.get(), remoteId)

    fun getProductCategoryByNameAndParentId(categoryName: String, parentId: Long): ProductCategory? =
        productStore.getProductCategoryByNameAndParentId(selectedSite.get(), categoryName, parentId)
            ?.toProductCategory()

    /**
     * Fires the request to add a new product category
     *
     * @return the result of the action as a [Boolean]
     */
    suspend fun addProductCategory(categoryName: String, parentId: Long): RequestResult {
        val result = addProductCategoryContinuation.callAndWaitUntilTimeout(AppConstants.REQUEST_TIMEOUT) {
            val productCategoryModel = WCProductCategoryModel().apply {
                name = categoryName
                parent = parentId
            }
            val payload = WCProductStore.AddProductCategoryPayload(selectedSite.get(), productCategoryModel)
            dispatcher.dispatch(WCProductActionBuilder.newAddProductCategoryAction(payload))
        }

        return when (result) {
            is Cancellation -> RequestResult.NO_ACTION_NEEDED
            is Success -> result.value
        }
    }

    @SuppressWarnings("unused")
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onProductCategoriesChanged(event: OnProductCategoryChanged) {
        when (event.causeOfChange) {
            FETCH_PRODUCT_CATEGORIES -> {
                if (event.isError) {
                    loadContinuation.continueWith(false)
                    AnalyticsTracker.track(
                        Stat.PRODUCT_CATEGORIES_LOAD_FAILED,
                        this.javaClass.simpleName,
                        event.error.type.toString(),
                        event.error.message
                    )
                } else {
                    canLoadMoreProductCategories = event.canLoadMore
                    AnalyticsTracker.track(Stat.PRODUCT_CATEGORIES_LOADED)
                    loadContinuation.continueWith(true)
                }
            }
            ADDED_PRODUCT_CATEGORY -> {
                if (event.isError) {
                    val requestResultType = if (event.error.type == TERM_EXISTS) {
                        RequestResult.API_ERROR
                    } else RequestResult.ERROR
                    addProductCategoryContinuation.continueWith(requestResultType)
                    AnalyticsTracker.track(
                        Stat.PARENT_CATEGORIES_LOAD_FAILED,
                        this.javaClass.simpleName,
                        event.error.type.toString(),
                        event.error.message
                    )
                } else {
                    AnalyticsTracker.track(Stat.PARENT_CATEGORIES_LOADED)
                    addProductCategoryContinuation.continueWith(RequestResult.SUCCESS)
                }
            }
            else -> {
            }
        }
    }
}
