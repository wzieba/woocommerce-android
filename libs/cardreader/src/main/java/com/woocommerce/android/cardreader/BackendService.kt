package com.woocommerce.android.cardreader

import retrofit2.Call
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST

/**
 * The `BackendService` interface handles the two simple calls we need to make to our backend.
 *
 * Note: This code has been copied from https://github.com/stripe/stripe-terminal-android and will
 * be removed in the near future.
 */
interface BackendService {
    /**
     * Get a connection token string from the backend
     */
    @POST("connection_token")
    fun getConnectionToken(): Call<ConnectionToken>

    /**
     * Capture a specific payment intent on our backend
     */
    @FormUrlEncoded
    @POST("capture_payment_intent")
    fun capturePaymentIntent(@Field("payment_intent_id") id: String): Call<Void>
}
