package com.woocommerce.android.ui.products

import androidx.lifecycle.SavedStateHandle
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.clearInvocations
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.spy
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.woocommerce.android.AppPrefs
import com.woocommerce.android.R
import com.woocommerce.android.R.drawable
import com.woocommerce.android.R.string
import com.woocommerce.android.initSavedStateHandle
import com.woocommerce.android.media.MediaFilesRepository
import com.woocommerce.android.media.ProductImagesServiceWrapper
import com.woocommerce.android.tools.NetworkStatus
import com.woocommerce.android.tools.SelectedSite
import com.woocommerce.android.ui.products.ProductDetailViewModel.ProductDetailViewState
import com.woocommerce.android.ui.products.ProductStatus.DRAFT
import com.woocommerce.android.ui.products.categories.ProductCategoriesRepository
import com.woocommerce.android.ui.products.models.ProductProperty.ComplexProperty
import com.woocommerce.android.ui.products.models.ProductProperty.Editable
import com.woocommerce.android.ui.products.models.ProductProperty.PropertyGroup
import com.woocommerce.android.ui.products.models.ProductProperty.RatingBar
import com.woocommerce.android.ui.products.models.ProductPropertyCard
import com.woocommerce.android.ui.products.models.ProductPropertyCard.Type.PRIMARY
import com.woocommerce.android.ui.products.models.ProductPropertyCard.Type.SECONDARY
import com.woocommerce.android.ui.products.models.SiteParameters
import com.woocommerce.android.ui.products.tags.ProductTagsRepository
import com.woocommerce.android.ui.products.variations.VariationRepository
import com.woocommerce.android.util.CurrencyFormatter
import com.woocommerce.android.util.ProductUtils
import com.woocommerce.android.viewmodel.BaseUnitTest
import com.woocommerce.android.viewmodel.MultiLiveEvent.Event.ShowDialog
import com.woocommerce.android.viewmodel.MultiLiveEvent.Event.ShowSnackbar
import com.woocommerce.android.viewmodel.ResourceProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.wordpress.android.fluxc.store.WooCommerceStore
import java.math.BigDecimal

@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
class ProductDetailViewModel_AddFlowTest : BaseUnitTest() {
    companion object {
        private const val PRODUCT_REMOTE_ID = 1L
    }

    private val wooCommerceStore: WooCommerceStore = mock()
    private val selectedSite: SelectedSite = mock()
    private val networkStatus: NetworkStatus = mock()
    private val productRepository: ProductDetailRepository = mock()
    private val productCategoriesRepository: ProductCategoriesRepository = mock()
    private val productTagsRepository: ProductTagsRepository = mock()
    private val mediaFilesRepository: MediaFilesRepository = mock()
    private val variationRepository: VariationRepository = mock()
    private val resources: ResourceProvider = mock {
        on(it.getString(any())).thenAnswer { i -> i.arguments[0].toString() }
        on(it.getString(any(), any())).thenAnswer { i -> i.arguments[0].toString() }
    }
    private val productImagesServiceWrapper: ProductImagesServiceWrapper = mock()
    private val currencyFormatter: CurrencyFormatter = mock {
        on(it.formatCurrency(any<BigDecimal>(), any(), any())).thenAnswer { i -> "${i.arguments[1]}${i.arguments[0]}" }
    }

    private val savedState: SavedStateHandle =
        ProductDetailFragmentArgs(remoteProductId = PRODUCT_REMOTE_ID, isAddProduct = true).initSavedStateHandle()

    private val siteParams = SiteParameters(
        currencyCode = "USD",
        currencySymbol = "$",
        currencyPosition = null,
        weightUnit = "kg",
        dimensionUnit = "cm",
        gmtOffset = 0f
    )
    private val parameterRepository: ParameterRepository = mock {
        on(it.getParameters(any(), any<SavedStateHandle>())).thenReturn(siteParams)
    }

    private val prefs: AppPrefs = mock {
        on(it.getSelectedProductType()).then { "simple" }
    }

    private val productUtils = ProductUtils()

    private val product = ProductTestUtils.generateProduct(PRODUCT_REMOTE_ID)
    private lateinit var viewModel: ProductDetailViewModel

    private val defaultPricingGroup: Map<String, String> =
        mapOf("" to resources.getString(R.string.product_price_empty))

    private val expectedCards = listOf(
        ProductPropertyCard(
            type = PRIMARY,
            properties = listOf(
                Editable(R.string.product_detail_title_hint, ""),
                ComplexProperty(
                    R.string.product_description,
                    resources.getString(R.string.product_description_empty),
                    showTitle = false)
            )
        ),
        ProductPropertyCard(
            type = SECONDARY,
            properties = listOf(
                PropertyGroup(
                    R.string.product_price,
                    defaultPricingGroup,
                    R.drawable.ic_gridicons_money,
                    showTitle = false
                ),
                RatingBar(
                    string.product_reviews,
                    resources.getString(string.product_ratings_count_zero),
                    0F,
                    drawable.ic_reviews
                ),
                PropertyGroup(
                    string.product_inventory,
                    mapOf(
                        Pair(
                            resources.getString(string.product_stock_status),
                            resources.getString(string.product_stock_status_instock)
                        )
                    ),
                    drawable.ic_gridicons_list_checkmark,
                    true
                ),
                ComplexProperty(
                    R.string.product_type,
                    resources.getString(R.string.product_detail_product_type_hint),
                    R.drawable.ic_gridicons_product,
                    true
                )
            )
        )
    )

    @Before
    fun setup() {
        doReturn(true).whenever(networkStatus).isConnected()

        viewModel = spy(
            ProductDetailViewModel(
                savedState,
                coroutinesTestRule.testDispatchers,
                parameterRepository,
                productRepository,
                networkStatus,
                currencyFormatter,
                resources,
                productCategoriesRepository,
                productTagsRepository,
                mediaFilesRepository,
                variationRepository,
                prefs
            )
        )

        clearInvocations(
            viewModel,
            selectedSite,
            productRepository,
            networkStatus,
            currencyFormatter,
            wooCommerceStore,
            productImagesServiceWrapper,
            resources,
            productCategoriesRepository,
            productTagsRepository
        )
    }

    @Test
    fun `Displays the product detail properties correctly`() = coroutinesTestRule.testDispatcher.runBlockingTest {
        viewModel.productDetailViewStateData.observeForever { _, _ -> }

        var cards: List<ProductPropertyCard>? = null
        viewModel.productDetailCards.observeForever {
            cards = it.map { card -> productUtils.stripCallbacks(card) }
        }

        viewModel.start()

        assertThat(cards).isEqualTo(expectedCards)
    }

    @Test
    fun `Display success message on add product success`() = coroutinesTestRule.testDispatcher.runBlockingTest {
        // given
        doReturn(product).whenever(productRepository).getProduct(any())
        doReturn(Pair(true, 1L)).whenever(productRepository).addProduct(any())

        var successSnackbarShown = false
        viewModel.event.observeForever {
            if (it is ShowSnackbar && it.message == R.string.product_detail_publish_product_success) {
                successSnackbarShown = true
            }
        }

        var productData: ProductDetailViewState? = null

        // when
        viewModel.productDetailViewStateData.observeForever { _, new -> productData = new }

        viewModel.start()

        viewModel.onUpdateButtonClicked()

        // then
        verify(productRepository, times(1)).getProduct(1L)

        assertThat(successSnackbarShown).isTrue()
        assertThat(productData?.isProgressDialogShown).isFalse()
        assertThat(productData?.isProductUpdated).isFalse()
        assertThat(productData?.productDraft).isEqualTo(product)
    }

    @Test
    fun `Display error message on add product failed`() = coroutinesTestRule.testDispatcher.runBlockingTest {
        // given
        doReturn(Pair(false, 0L)).whenever(productRepository).addProduct(any())

        var successSnackbarShown = false
        viewModel.event.observeForever {
            if (it is ShowSnackbar && it.message == R.string.product_detail_publish_product_error) {
                successSnackbarShown = true
            }
        }

        var productData: ProductDetailViewState? = null

        // when
        viewModel.productDetailViewStateData.observeForever { _, new -> productData = new }

        viewModel.start()

        viewModel.onUpdateButtonClicked()

        // then
        assertThat(successSnackbarShown).isTrue
        assertThat(productData?.isProgressDialogShown).isFalse
    }

    @Test
    fun `Display error message on add product for NO network`() = coroutinesTestRule.testDispatcher.runBlockingTest {
        // given
        doReturn(false).whenever(networkStatus).isConnected()

        var successSnackbarShown = false
        viewModel.event.observeForever {
            if (it is ShowSnackbar && it.message == R.string.offline_error) {
                successSnackbarShown = true
            }
        }

        var productData: ProductDetailViewState? = null

        // when
        viewModel.productDetailViewStateData.observeForever { _, new -> productData = new }

        viewModel.start()

        viewModel.onUpdateButtonClicked()

        // then
        assertThat(successSnackbarShown).isTrue()
        assertThat(productData?.isProgressDialogShown).isFalse()
    }

    @Test
    fun `Display correct message on updating a freshly added product`() =
        coroutinesTestRule.testDispatcher.runBlockingTest {
            // given
            doReturn(product).whenever(productRepository).getProduct(any())
            doReturn(Pair(true, 1L)).whenever(productRepository).addProduct(any())

            var successSnackbarShown = false
            viewModel.event.observeForever {
                if (it is ShowSnackbar && it.message == R.string.product_detail_publish_product_success) {
                    successSnackbarShown = true
                }
            }

            var productData: ProductDetailViewState? = null

            // when
            viewModel.productDetailViewStateData.observeForever { _, new -> productData = new }

            viewModel.start()

            viewModel.onUpdateButtonClicked()

            // then
            verify(productRepository, times(1)).getProduct(1L)

            assertThat(successSnackbarShown).isTrue()
            assertThat(productData?.isProgressDialogShown).isFalse()
            assertThat(productData?.isProductUpdated).isFalse()
            assertThat(productData?.productDraft).isEqualTo(product)

            // when
            doReturn(true).whenever(productRepository).updateProduct(any())

            viewModel.onUpdateButtonClicked()
            verify(productRepository, times(1)).updateProduct(any())

            viewModel.event.observeForever {
                if (it is ShowSnackbar && it.message == R.string.product_detail_update_product_success) {
                    successSnackbarShown = true
                }
            }

            // then
            assertThat(successSnackbarShown).isTrue()
            assertThat(productData?.isProgressDialogShown).isFalse()
            assertThat(productData?.isProductUpdated).isFalse()
            assertThat(productData?.productDraft).isEqualTo(product)
        }

    @Test
    fun `Save as draft shown in discard dialog when changes made in add flow`() {
        doReturn(true).whenever(viewModel).isProductUnderCreation

        viewModel.start()

        // change the status to draft so we can verify that isDraftProduct works - this will also
        // force the viewModel to consider the product as changed, so when we click the back button
        // below it will show the discard dialog
        viewModel.updateProductDraft(productStatus = DRAFT)
        assertThat(viewModel.isDraftProduct()).isTrue()

        var saveAsDraftShown = false
        viewModel.event.observeForever {
            if (it is ShowDialog && it.neutralBtnAction != null) {
                saveAsDraftShown = true
            }
        }

        viewModel.onBackButtonClickedProductDetail()
        assertThat(saveAsDraftShown).isTrue()
    }

    @Test
    fun `Save as draft not shown in discard dialog when not in add flow`() {
        doReturn(false).whenever(viewModel).isProductUnderCreation

        viewModel.start()
        viewModel.updateProductDraft(productStatus = DRAFT)

        var saveAsDraftShown = false
        viewModel.event.observeForever {
            if (it is ShowDialog && it.neutralBtnAction != null) {
                saveAsDraftShown = true
            }
        }

        viewModel.onBackButtonClickedProductDetail()
        assertThat(saveAsDraftShown).isFalse()
    }
}
