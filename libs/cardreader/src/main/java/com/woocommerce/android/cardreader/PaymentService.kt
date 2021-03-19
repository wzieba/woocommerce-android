package com.woocommerce.android.cardreader

interface PaymentService {
    // TODO cardreader: It uses Int instead of BigDecimal, is that ok?
    fun initPayment(amount: Int)
    fun collectPayment()
    fun processPayment()
    fun capturePayment()
}
