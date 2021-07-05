package com.woocommerce.android.ui.orders.shippinglabels.creation

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.woocommerce.android.initSavedStateHandle
import com.woocommerce.android.model.ShippingAccountSettings
import com.woocommerce.android.model.ShippingLabelPackage
import com.woocommerce.android.model.StoreOwnerDetails
import com.woocommerce.android.ui.orders.OrderTestUtils
import com.woocommerce.android.ui.orders.details.OrderDetailRepository
import com.woocommerce.android.ui.orders.shippinglabels.ShippingLabelRepository
import com.woocommerce.android.ui.orders.shippinglabels.creation.EditShippingLabelPackagesViewModel.ViewState
import com.woocommerce.android.ui.products.ParameterRepository
import com.woocommerce.android.ui.products.ProductDetailRepository
import com.woocommerce.android.ui.products.ProductTestUtils
import com.woocommerce.android.ui.products.variations.VariationDetailRepository
import com.woocommerce.android.viewmodel.BaseUnitTest
import com.woocommerce.android.viewmodel.MultiLiveEvent
import com.woocommerce.android.viewmodel.MultiLiveEvent.Event.Exit
import com.woocommerce.android.viewmodel.MultiLiveEvent.Event.ExitWithResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.wordpress.android.fluxc.network.rest.wpcom.wc.WooResult

@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
class EditShippingLabelPackagesViewModelTest : BaseUnitTest() {
    companion object {
        private const val ORDER_ID = "1-1-1"
    }

    private val availablePackages = listOf(
        CreateShippingLabelTestUtils.generatePackage("id1", "provider1"),
        CreateShippingLabelTestUtils.generatePackage("id2", "provider2")
    )

    private val shippingAccountSettings = ShippingAccountSettings(
        canEditSettings = true,
        canManagePayments = true,
        paymentMethods = emptyList(),
        selectedPaymentId = null,
        lastUsedBoxId = "id1",
        storeOwnerDetails = StoreOwnerDetails(
            "email", "username", "username", "name"
        ),
        isEmailReceiptEnabled = true
    )

    private val testOrder = OrderTestUtils.generateTestOrder(ORDER_ID)
    private val testProduct = ProductTestUtils.generateProduct()

    private val orderDetailRepository: OrderDetailRepository = mock()
    private val productDetailRepository: ProductDetailRepository = mock()
    private val variationDetailRepository: VariationDetailRepository = mock()
    private val shippingLabelRepository: ShippingLabelRepository = mock()
    private val parameterRepository: ParameterRepository = mock()

    private lateinit var viewModel: EditShippingLabelPackagesViewModel

    suspend fun setup(currentPackages: Array<ShippingLabelPackage>) {
        val savedState = EditShippingLabelPackagesFragmentArgs(ORDER_ID, currentPackages).initSavedStateHandle()
        whenever(shippingLabelRepository.getShippingPackages()).thenReturn(WooResult(availablePackages))
        whenever(orderDetailRepository.getOrder(ORDER_ID)).thenReturn(testOrder)
        whenever(productDetailRepository.getProduct(any())).thenReturn(testProduct)
        viewModel = EditShippingLabelPackagesViewModel(
            savedState,
            productDetailRepository = productDetailRepository,
            orderDetailRepository = orderDetailRepository,
            variationDetailRepository = variationDetailRepository,
            shippingLabelRepository = shippingLabelRepository,
            parameterRepository = parameterRepository
        )
    }

    @Test
    fun `test first opening of the screen`() = coroutinesTestRule.testDispatcher.runBlockingTest {
        whenever(shippingLabelRepository.getAccountSettings()).thenReturn(WooResult(shippingAccountSettings))

        setup(emptyArray())
        var viewState: ViewState? = null
        viewModel.viewStateData.observeForever { _, new -> viewState = new }

        verify(orderDetailRepository).getOrder(any())
        verify(shippingLabelRepository).getAccountSettings()
        assertThat(viewState!!.shippingLabelPackages.size).isEqualTo(1)
        assertThat(viewState!!.shippingLabelPackages.first().selectedPackage).isEqualTo(availablePackages.first())
    }

    @Test
    fun `test edit flow`() = coroutinesTestRule.testDispatcher.runBlockingTest {
        val currentShippingPackages = arrayOf(
            CreateShippingLabelTestUtils.generateShippingLabelPackage(
                selectedPackage = availablePackages[0]
            )
        )
        setup(currentShippingPackages)
        var viewState: ViewState? = null
        viewModel.viewStateData.observeForever { _, new -> viewState = new }

        verify(orderDetailRepository, never()).getOrder(any())
        verify(shippingLabelRepository, never()).getAccountSettings()
        assertThat(viewState!!.shippingLabelPackages).isEqualTo(currentShippingPackages.toList())
    }

    @Test
    fun `no last used package`() = coroutinesTestRule.testDispatcher.runBlockingTest {
        val shippingAccountSettings = shippingAccountSettings.copy(lastUsedBoxId = null)
        whenever(shippingLabelRepository.getAccountSettings()).thenReturn(WooResult(shippingAccountSettings))

        setup(emptyArray())
        var viewState: ViewState? = null
        viewModel.viewStateData.observeForever { _, new -> viewState = new }

        assertThat(viewState!!.shippingLabelPackages.first().selectedPackage).isNull()
    }

    @Test
    fun `last used package deleted`() = coroutinesTestRule.testDispatcher.runBlockingTest {
        val shippingAccountSettings = shippingAccountSettings.copy(lastUsedBoxId = "missingId")
        whenever(shippingLabelRepository.getAccountSettings()).thenReturn(WooResult(shippingAccountSettings))

        setup(emptyArray())
        var viewState: ViewState? = null
        viewModel.viewStateData.observeForever { _, new -> viewState = new }

        assertThat(viewState!!.shippingLabelPackages.first().selectedPackage).isNull()
    }

    @Test
    fun `edit weight of package`() = coroutinesTestRule.testDispatcher.runBlockingTest {
        whenever(shippingLabelRepository.getAccountSettings()).thenReturn(WooResult(shippingAccountSettings))

        setup(emptyArray())
        var viewState: ViewState? = null
        viewModel.viewStateData.observeForever { _, new -> viewState = new }

        viewModel.onWeightEdited(0, 10.0f)
        assertThat(viewState!!.shippingLabelPackages.first().weight).isEqualTo(10.0f)
        assertThat(viewState!!.isDataValid).isTrue()
    }

    @Test
    fun `select a package`() = coroutinesTestRule.testDispatcher.runBlockingTest {
        whenever(shippingLabelRepository.getAccountSettings()).thenReturn(WooResult(shippingAccountSettings))

        setup(emptyArray())
        var viewState: ViewState? = null
        viewModel.viewStateData.observeForever { _, new -> viewState = new }

        viewModel.onPackageSpinnerClicked(0)
        viewModel.onPackageSelected(0, availablePackages[1])
        assertThat(viewState!!.shippingLabelPackages.first().selectedPackage).isEqualTo(availablePackages[1])
    }

    @Test
    fun `exit without saving changes`() = coroutinesTestRule.testDispatcher.runBlockingTest {
        whenever(shippingLabelRepository.getAccountSettings()).thenReturn(WooResult(shippingAccountSettings))

        setup(emptyArray())
        var event: MultiLiveEvent.Event? = null
        viewModel.event.observeForever { event = it }
        viewModel.onBackButtonClicked()

        assertThat(event).isEqualTo(Exit)
    }

    @Suppress("UNCHECKED_CAST")
    @Test
    fun `save changes and exit`() = coroutinesTestRule.testDispatcher.runBlockingTest {
        whenever(shippingLabelRepository.getAccountSettings()).thenReturn(WooResult(shippingAccountSettings))

        setup(emptyArray())
        var event: MultiLiveEvent.Event? = null
        viewModel.event.observeForever { event = it }

        viewModel.onWeightEdited(0, 10.0f)
        viewModel.onDoneButtonClicked()

        assertThat(event).isInstanceOf(ExitWithResult::class.java)
        val createdShippingPackages = (event as ExitWithResult<List<ShippingLabelPackage>>).data
        assertThat(createdShippingPackages.size).isEqualTo(1)
        assertThat(createdShippingPackages.first().weight).isEqualTo(10.0f)
    }
}
