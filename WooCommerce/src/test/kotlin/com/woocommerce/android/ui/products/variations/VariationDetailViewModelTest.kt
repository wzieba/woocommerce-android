package com.woocommerce.android.ui.products.variations

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.woocommerce.android.initSavedStateHandle
import com.woocommerce.android.model.ProductVariation
import com.woocommerce.android.ui.products.ParameterRepository
import com.woocommerce.android.ui.products.generateVariation
import com.woocommerce.android.ui.products.models.SiteParameters
import com.woocommerce.android.ui.products.variations.VariationDetailViewModel.VariationViewState
import com.woocommerce.android.viewmodel.BaseUnitTest
import com.woocommerce.android.viewmodel.ResourceProvider
import com.woocommerce.android.viewmodel.SavedStateWithArgs
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Date

@RunWith(RobolectricTestRunner::class)
class VariationDetailViewModelTest : BaseUnitTest() {
    companion object {
        private val SALE_START_DATE = Date.from(
            LocalDateTime.of(2020, 3, 1, 8, 0).toInstant(ZoneOffset.UTC)
        )
        private val SALE_END_DATE = Date.from(
            LocalDateTime.of(2020, 4, 1, 8, 0).toInstant(ZoneOffset.UTC)
        )
        val TEST_VARIATION = generateVariation().copy(
            saleStartDateGmt = SALE_START_DATE,
            saleEndDateGmt = SALE_END_DATE
        )
    }

    private lateinit var sut: VariationDetailViewModel

    private val siteParams = SiteParameters(
        currencyCode = "USD",
        currencySymbol = "$",
        currencyPosition = null,
        weightUnit = "kg",
        dimensionUnit = "cm",
        gmtOffset = 0f
    )
    private val parameterRepository: ParameterRepository = mock {
        on { getParameters(any(), any<SavedStateWithArgs>()) } doReturn (siteParams)
    }
    private val variationRepository: VariationDetailRepository = mock {
        on { getVariation(any(), any()) } doReturn (TEST_VARIATION)
    }

    private val resourceProvider: ResourceProvider = mock {
        on { getString(any()) } doAnswer { answer -> answer.arguments[0].toString() }
    }

    private val savedState = VariationDetailFragmentArgs(
        TEST_VARIATION.remoteProductId,
        TEST_VARIATION.remoteVariationId
    ).initSavedStateHandle()

    @Before
    fun setup() {
        sut = VariationDetailViewModel(
            savedState = savedState,
            variationRepository = variationRepository,
            productRepository = mock(),
            networkStatus = mock(),
            currencyFormatter = mock(),
            parameterRepository = parameterRepository,
            resources = resourceProvider
        )
    }

    @Test
    fun `should initialize sale end date`() {
        sut.variationViewStateData.observeForever { _, _ -> }

        assertThat(variation?.saleEndDateGmt).isEqualTo(SALE_END_DATE)
    }

    @Test
    fun `should initialize sale start date`() {
        sut.variationViewStateData.observeForever { _, _ -> }

        assertThat(variation?.saleStartDateGmt).isEqualTo(SALE_START_DATE)
    }

    @Test
    fun `should update sale end date to null when requested`() {
        sut.variationViewStateData.observeForever { _, _ -> }

        sut.onVariationChanged(saleEndDate = null)

        assertThat(variation!!.saleEndDateGmt).isNull()
    }

    @Test
    fun `should initialize uploading image uri with null value`() {
        sut.variationViewStateData.observeForever { _, _ -> }

        assertThat(viewState!!.uploadingImageUri).isNull()
    }

    private val variation: ProductVariation?
        get() = viewState?.variation

    private val viewState: VariationViewState?
        get() = sut.variationViewStateData.liveData.value
}
