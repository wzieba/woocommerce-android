package com.woocommerce.android.ui.refunds

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import com.woocommerce.android.R
import com.woocommerce.android.initSavedStateHandle
import com.woocommerce.android.tools.NetworkStatus
import com.woocommerce.android.tools.SelectedSite
import com.woocommerce.android.ui.orders.OrderTestUtils
import com.woocommerce.android.ui.orders.details.OrderDetailRepository
import com.woocommerce.android.ui.refunds.IssueRefundViewModel.RefundByItemsViewState
import com.woocommerce.android.util.CurrencyFormatter
import com.woocommerce.android.viewmodel.BaseUnitTest
import com.woocommerce.android.viewmodel.ResourceProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.wordpress.android.fluxc.model.SiteModel
import org.wordpress.android.fluxc.store.WCGatewayStore
import org.wordpress.android.fluxc.store.WCOrderStore
import org.wordpress.android.fluxc.store.WCRefundStore
import org.wordpress.android.fluxc.store.WooCommerceStore
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
class IssueRefundViewModelTest : BaseUnitTest() {
    private val orderStore: WCOrderStore = mock()
    private val wooStore: WooCommerceStore = mock()
    private val selectedSite: SelectedSite = mock()
    private val networkStatus: NetworkStatus = mock()
    private val orderDetailRepository: OrderDetailRepository = mock()
    private val gatewayStore: WCGatewayStore = mock()
    private val refundStore: WCRefundStore = mock()
    private val currencyFormatter: CurrencyFormatter = mock()
    private val resourceProvider: ResourceProvider = mock() {
        on(it.getString(R.string.taxes)).thenAnswer { "Taxes" }
        on(it.getString(R.string.orderdetail_payment_fees)).thenAnswer { "Fees" }
        on(it.getString(R.string.multiple_shipping)).thenAnswer { "Multiple shipping lines" }
        on(it.getString(R.string.and)).thenAnswer { "and" }
        on(it.getString(any(), any())).thenAnswer {
            i -> "You can refund " + i.arguments[1].toString()
        }
    }

    private val savedState = IssueRefundFragmentArgs(0).initSavedStateHandle()

    private lateinit var viewModel: IssueRefundViewModel

    private fun initViewModel() {
        whenever(selectedSite.get()).thenReturn(SiteModel())
        whenever(currencyFormatter.buildBigDecimalFormatter(any())).thenReturn { "" }

        viewModel = IssueRefundViewModel(
            savedState,
            coroutinesTestRule.testDispatchers,
            currencyFormatter,
            orderStore,
            wooStore,
            selectedSite,
            networkStatus,
            resourceProvider,
            orderDetailRepository,
            gatewayStore,
            refundStore
        )
    }

    @Test
    fun `when order has zero taxes and no shipping and fees, then refund notice is not visible`() {
        coroutinesTestRule.testDispatcher.runBlockingTest {
            whenever(orderStore.getOrderByIdentifier(any())).thenReturn(OrderTestUtils.generateOrder())

            initViewModel()

            var viewState: RefundByItemsViewState? = null
            viewModel.refundByItemsStateLiveData.observeForever { _, new -> viewState = new }

            assertFalse(viewState!!.isRefundNoticeVisible)
        }
    }

    @Test
    fun `when order has taxes and no shipping and fees, only the taxes are mentioned in the notice`() {
        coroutinesTestRule.testDispatcher.runBlockingTest {
            val orderWithTax = OrderTestUtils.generateOrder().apply { totalTax = "4.00" }
            whenever(orderStore.getOrderByIdentifier(any())).thenReturn(orderWithTax)

            initViewModel()

            var viewState: RefundByItemsViewState? = null
            viewModel.refundByItemsStateLiveData.observeForever { _, new -> viewState = new }

            assertTrue(viewState!!.isRefundNoticeVisible)
            assertEquals("You can refund taxes", viewState!!.refundNotice)
        }
    }

    @Test
    fun `when order has one shipping and fees, the taxes are not mentioned in the notice`() {
        coroutinesTestRule.testDispatcher.runBlockingTest {
            val orderWithFeesAndShipping = OrderTestUtils.generateOrderWithFee()
            whenever(orderStore.getOrderByIdentifier(any())).thenReturn(orderWithFeesAndShipping)

            initViewModel()

            var viewState: RefundByItemsViewState? = null
            viewModel.refundByItemsStateLiveData.observeForever { _, new -> viewState = new }

            assertTrue(viewState!!.isRefundNoticeVisible)
            assertEquals("You can refund fees", viewState!!.refundNotice)
        }
    }

    @Test
    fun `when order has one shipping, and fees and taxes, shipping are mentioned in the notice`() {
        coroutinesTestRule.testDispatcher.runBlockingTest {
            val orderWithFeesAndShipping = OrderTestUtils.generateOrderWithFee().apply { totalTax = "4.00" }
            whenever(orderStore.getOrderByIdentifier(any())).thenReturn(orderWithFeesAndShipping)

            initViewModel()

            var viewState: RefundByItemsViewState? = null
            viewModel.refundByItemsStateLiveData.observeForever { _, new -> viewState = new }

            assertTrue(viewState!!.isRefundNoticeVisible)
            assertEquals("You can refund fees and taxes", viewState!!.refundNotice)
        }
    }

    @Test
    fun `when order has multiple shipping, multiple shipping are mentioned in the notice`() {
        val orderWithMultipleShipping = OrderTestUtils.generateOrderWithMultipleShippingLines()
        whenever(orderStore.getOrderByIdentifier(any())).thenReturn(orderWithMultipleShipping)

        initViewModel()

        var viewState: RefundByItemsViewState? = null
        viewModel.refundByItemsStateLiveData.observeForever { _, new -> viewState = new }

        assertTrue(viewState!!.isRefundNoticeVisible)
        assertEquals("You can refund multiple shipping lines", viewState!!.refundNotice)
    }
}
