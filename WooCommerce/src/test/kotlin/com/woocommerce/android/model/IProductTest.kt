package com.woocommerce.android.model

import com.woocommerce.android.extensions.formatToString
import com.woocommerce.android.ui.products.ProductHelper
import com.woocommerce.android.ui.products.ProductType
import org.junit.Test
import kotlin.test.assertEquals

class IProductTest {
    private val product = ProductHelper.getDefaultNewProduct(ProductType.SIMPLE, false)

    /* WEIGHT */

    @Test
    fun `given negative weight, when getting weight, then return empty`() {
        val product = product.copy(weight = -1f)

        val result = product.getWeightWithUnits(null)

        assertEquals(EMPTY, result)
    }

    @Test
    fun `given zero weight, when getting weight, then return empty`() {
        val product = product.copy(weight = 0f)

        val result = product.getWeightWithUnits(null)

        assertEquals(EMPTY, result)
    }

    @Test
    fun `given weight without unit, when getting weight, then return weight without unit`() {
        val weight = 1f
        val product = product.copy(weight = weight)

        val result = product.getWeightWithUnits(null)

        val expected = weight.formatToString()
        assertEquals(expected, result)
    }

    @Test
    fun `given weight with unit, when getting weight, then return weight with unit`() {
        val weight = 1f
        val product = product.copy(weight = weight)

        val result = product.getWeightWithUnits(WEIGHT_UNIT)

        val expected = weight.formatToString() + WEIGHT_UNIT
        assertEquals(expected, result)
    }

    /* SIZE */

    @Test
    fun `given negative dimens, when getting size, then return empty`() {
        val length = -1f
        val width = -2f
        val height = -3f
        val product = product.copy(length = length, width = width, height = height)

        val result = product.getSizeWithUnits(null)

        assertEquals(EMPTY, result)
    }

    @Test
    fun `given no dimens, when getting size, then return empty`() {
        val length = 0f
        val width = 0f
        val height = 0f
        val product = product.copy(length = length, width = width, height = height)

        val result = product.getSizeWithUnits(null)

        assertEquals(EMPTY, result)
    }

    @Test
    fun `given dimens without unit, when getting size, then return dimensions without unit`() {
        val length = 1f
        val width = 2f
        val height = 3f
        val product = product.copy(length = length, width = width, height = height)

        val result = product.getSizeWithUnits(null)

        val expected = length.formatToString() +
            X + width.formatToString() +
            X + height.formatToString()
        assertEquals(expected, result)
    }

    @Test
    fun `given dimens with unit, when getting size, then return dimensions with unit`() {
        val length = 1f
        val width = 2f
        val height = 3f
        val product = product.copy(length = length, width = width, height = height)

        val result = product.getSizeWithUnits(DIMENSION_UNIT)

        val expected = length.formatToString() +
            X + width.formatToString() +
            X + height.formatToString() +
            SPACE + DIMENSION_UNIT
        assertEquals(expected, result)
    }

    @Test
    fun `given length and width without unit, when getting size, then return length and width without unit`() {
        val length = 1f
        val width = 2f
        val product = product.copy(length = length, width = width, height = 0f)

        val result = product.getSizeWithUnits(null)

        val expected = length.formatToString() +
            X + width.formatToString()
        assertEquals(expected, result)
    }

    @Test
    fun `given length and width with unit, when getting size, then return length and width with unit`() {
        val length = 1f
        val width = 2f
        val product = product.copy(length = length, width = width, height = 0f)

        val result = product.getSizeWithUnits(DIMENSION_UNIT)

        val expected = length.formatToString() +
            X + width.formatToString() +
            SPACE + DIMENSION_UNIT
        assertEquals(expected, result)
    }

    @Test
    fun `given length and height without unit, when getting size, then return length and height without unit`() {
        val length = 1f
        val height = 3f
        val product = product.copy(length = length, width = 0f, height = height)

        val result = product.getSizeWithUnits(null)

        val expected = length.formatToString() +
            X + height.formatToString()
        assertEquals(expected, result)
    }

    @Test
    fun `given length and height with unit, when getting size, then return length and height with unit`() {
        val length = 1f
        val height = 3f
        val product = product.copy(length = length, width = 0f, height = height)

        val result = product.getSizeWithUnits(DIMENSION_UNIT)

        val expected = length.formatToString() +
            X + height.formatToString() +
            SPACE + DIMENSION_UNIT
        assertEquals(expected, result)
    }

    @Test
    fun `given width and height without unit, when getting size, then return width and height without unit`() {
        val width = 2f
        val height = 3f
        val product = product.copy(length = 0f, width = width, height = height)

        val result = product.getSizeWithUnits(null)

        val expected = width.formatToString() +
            X + height.formatToString()
        assertEquals(expected, result)
    }

    @Test
    fun `given width and height with unit, when getting size, then return width and height with unit`() {
        val width = 2f
        val height = 3f
        val product = product.copy(length = 0f, width = width, height = height)

        val result = product.getSizeWithUnits(DIMENSION_UNIT)

        val expected = width.formatToString() +
            X + height.formatToString() +
            SPACE + DIMENSION_UNIT
        assertEquals(expected, result)
    }

    @Test
    fun `given length without unit, when getting size, then return length without unit`() {
        val length = 1f
        val product = product.copy(length = length, width = 0f, height = 0f)

        val result = product.getSizeWithUnits(null)

        val expected = length.formatToString()
        assertEquals(expected, result)
    }

    @Test
    fun `given length with unit, when getting size, then return length with unit`() {
        val length = 1f
        val product = product.copy(length = length, width = 0f, height = 0f)

        val result = product.getSizeWithUnits(DIMENSION_UNIT)

        val expected = length.formatToString() + DIMENSION_UNIT
        assertEquals(expected, result)
    }

    @Test
    fun `given width without unit, when getting size, then return width without unit`() {
        val width = 2f
        val product = product.copy(length = 0f, width = width, height = 0f)

        val result = product.getSizeWithUnits(null)

        val expected = width.formatToString()
        assertEquals(expected, result)
    }

    @Test
    fun `given width with unit, when getting size, then return width with unit`() {
        val width = 2f
        val product = product.copy(length = 0f, width = width, height = 0f)

        val result = product.getSizeWithUnits(DIMENSION_UNIT)

        val expected = width.formatToString() + DIMENSION_UNIT
        assertEquals(expected, result)
    }

    @Test
    fun `given height without unit, when getting size, then return height without unit`() {
        val height = 3f
        val product = product.copy(length = 0f, width = 0f, height = height)

        val result = product.getSizeWithUnits(null)

        val expected = height.formatToString()
        assertEquals(expected, result)
    }

    @Test
    fun `given height with unit, when getting size, then return height with unit`() {
        val height = 3f
        val product = product.copy(length = 0f, width = 0f, height = height)

        val result = product.getSizeWithUnits(DIMENSION_UNIT)

        val expected = height.formatToString() + DIMENSION_UNIT
        assertEquals(expected, result)
    }

    companion object {
        private const val EMPTY = ""
        private const val SPACE = " "
        private const val X = " x "
        private const val WEIGHT_UNIT = "kg"
        private const val DIMENSION_UNIT = "cm"
    }
}
