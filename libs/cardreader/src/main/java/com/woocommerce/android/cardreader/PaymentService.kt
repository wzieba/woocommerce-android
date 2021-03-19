package com.woocommerce.android.cardreader

import android.util.Log
import com.stripe.stripeterminal.Terminal
import com.stripe.stripeterminal.callable.PaymentIntentCallback
import com.stripe.stripeterminal.callable.ReaderDisplayListener
import com.stripe.stripeterminal.model.external.PaymentIntent
import com.stripe.stripeterminal.model.external.PaymentIntentParameters
import com.stripe.stripeterminal.model.external.ReaderDisplayMessage
import com.stripe.stripeterminal.model.external.ReaderInputOptions
import com.stripe.stripeterminal.model.external.TerminalException
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class PaymentService : ReaderDisplayListener {
    private var paymentIntent: PaymentIntent? = null

    // TODO cardreader: It uses Int instead of BigDecimal, is that ok?
    fun collectPayment(amount: Int) {
        val params = PaymentIntentParameters.Builder()
            .setAmount(amount)
            .setCurrency("usd")
            .build()
        Terminal.getInstance().createPaymentIntent(params, object: PaymentIntentCallback {
            override fun onSuccess(paymentIntent: PaymentIntent) {
                // TODO cardreader: handle cancelation when the user leaves or hits cancel
                Log.d("CardReader", "Creating payment intent succeeded")
                this@PaymentService.paymentIntent = paymentIntent
            }

            override fun onFailure(exception: TerminalException) {
                Log.d("CardReader", "Creating payment intent failed")
            }
        })
    }

    fun collectPayment() {
        val cancelable = Terminal.getInstance().collectPaymentMethod(paymentIntent!!, this@PaymentService,
            object : PaymentIntentCallback {
                override fun onSuccess(paymentIntent: PaymentIntent) {
                    Log.d("CardReader", "Payment collected")
                    this@PaymentService.paymentIntent = paymentIntent
                }

                override fun onFailure(exception: TerminalException) {
                    Log.d("CardReader", "Payment collection failed")
                }
            })
    }

    fun processPayment() {
        Terminal.getInstance().processPayment(paymentIntent!!, object : PaymentIntentCallback {
            override fun onSuccess(paymentIntent: PaymentIntent) {
                // Placeholder for notifying your backend to capture paymentIntent.id
                    Log.d("CardReader", "Processing payment succeeded")
                this@PaymentService.paymentIntent = paymentIntent
            }

            override fun onFailure(exception: TerminalException) {
                // Placeholder for handling the exception
                    Log.d("CardReader", "Processing payment failed")
            }
        })
    }

    fun capturePayment() {
        ApiClient.capturePaymentIntent(paymentIntent!!.id, object: Callback<Void> {
            override fun onResponse(call: Call<Void>, response: Response<Void>) {
                Log.d("CardReader", "Capturing payment succeeded")
            }

            override fun onFailure(call: Call<Void>, t: Throwable) {
                Log.d("CardReader", "Capturing payment failed")
            }
        })
    }

    override fun onRequestReaderDisplayMessage(message: ReaderDisplayMessage) {
        Log.d("CardReader", "ReaderRequest: $message")
    }

    override fun onRequestReaderInput(options: ReaderInputOptions) {
        Log.d("CardReader", "ReaderRequestInput: $options")
    }
}
