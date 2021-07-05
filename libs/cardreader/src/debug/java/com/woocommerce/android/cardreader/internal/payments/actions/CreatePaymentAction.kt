package com.woocommerce.android.cardreader.internal.payments.actions

import com.stripe.stripeterminal.callable.PaymentIntentCallback
import com.stripe.stripeterminal.model.external.PaymentIntent
import com.stripe.stripeterminal.model.external.PaymentIntentParameters
import com.stripe.stripeterminal.model.external.TerminalException
import com.woocommerce.android.cardreader.internal.payments.actions.CreatePaymentAction.CreatePaymentStatus.Failure
import com.woocommerce.android.cardreader.internal.payments.actions.CreatePaymentAction.CreatePaymentStatus.Success
import com.woocommerce.android.cardreader.internal.wrappers.LogWrapper
import com.woocommerce.android.cardreader.internal.wrappers.PaymentIntentParametersFactory
import com.woocommerce.android.cardreader.internal.wrappers.TerminalWrapper
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.sendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

internal class CreatePaymentAction(
    private val paymentIntentParametersFactory: PaymentIntentParametersFactory,
    private val terminal: TerminalWrapper,
    private val logWrapper: LogWrapper
) {
    sealed class CreatePaymentStatus {
        data class Success(val paymentIntent: PaymentIntent) : CreatePaymentStatus()
        data class Failure(val exception: TerminalException) : CreatePaymentStatus()
    }

    fun createPaymentIntent(
        paymentDescription: String,
        amount: Int,
        currency: String,
        customerEmail: String?
    ): Flow<CreatePaymentStatus> {
        return callbackFlow {
            terminal.createPaymentIntent(
                createParams(paymentDescription, amount, currency, customerEmail),
                object : PaymentIntentCallback {
                    override fun onSuccess(paymentIntent: PaymentIntent) {
                        logWrapper.d("CardReader", "Creating payment intent succeeded")
                        this@callbackFlow.sendBlocking(Success(paymentIntent))
                        this@callbackFlow.close()
                    }

                    override fun onFailure(e: TerminalException) {
                        logWrapper.d("CardReader", "Creating payment intent failed")
                        this@callbackFlow.sendBlocking(Failure(e))
                        this@callbackFlow.close()
                    }
                })
            awaitClose()
        }
    }

    private fun createParams(
        paymentDescription: String,
        amount: Int,
        currency: String,
        email: String?
    ): PaymentIntentParameters {
        val builder = paymentIntentParametersFactory.createBuilder()
            .setDescription(paymentDescription)
            .setAmount(amount)
            .setCurrency(currency)
        email?.takeIf { it.isNotEmpty() }?.let { builder.setReceiptEmail(it) }
        return builder.build()
    }
}
