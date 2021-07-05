package com.woocommerce.android.cardreader.internal.wrappers

import android.app.Application
import com.stripe.stripeterminal.Terminal
import com.stripe.stripeterminal.TerminalLifecycleObserver
import com.stripe.stripeterminal.callable.Callback
import com.stripe.stripeterminal.callable.Cancelable
import com.stripe.stripeterminal.callable.DiscoveryListener
import com.stripe.stripeterminal.callable.PaymentIntentCallback
import com.stripe.stripeterminal.callable.ReaderCallback
import com.stripe.stripeterminal.callable.ReaderDisplayListener
import com.stripe.stripeterminal.callable.ReaderSoftwareUpdateCallback
import com.stripe.stripeterminal.callable.ReaderSoftwareUpdateListener
import com.stripe.stripeterminal.callable.TerminalListener
import com.stripe.stripeterminal.log.LogLevel
import com.stripe.stripeterminal.model.external.DiscoveryConfiguration
import com.stripe.stripeterminal.model.external.PaymentIntent
import com.stripe.stripeterminal.model.external.PaymentIntentParameters
import com.stripe.stripeterminal.model.external.Reader
import com.stripe.stripeterminal.model.external.ReaderSoftwareUpdate
import com.woocommerce.android.cardreader.CardReader
import com.woocommerce.android.cardreader.CardReaderImpl
import com.woocommerce.android.cardreader.internal.TokenProvider

/**
 * Injectable wrapper for Stripe's Terminal object.
 */
internal class TerminalWrapper {
    fun isInitialized() = Terminal.isInitialized()
    fun getLifecycleObserver() = TerminalLifecycleObserver.getInstance()
    fun initTerminal(
        application: Application,
        logLevel: LogLevel,
        tokenProvider: TokenProvider,
        listener: TerminalListener
    ) = Terminal.initTerminal(application, logLevel, tokenProvider, listener)

    fun discoverReaders(
        config: DiscoveryConfiguration,
        discoveryListener: DiscoveryListener,
        callback: Callback
    ): Cancelable = Terminal.getInstance().discoverReaders(config, discoveryListener, callback)

    fun connectToReader(reader: Reader, callback: ReaderCallback) =
        Terminal.getInstance().connectReader(reader, callback)

    fun disconnectReader(callback: Callback) =
        Terminal.getInstance().disconnectReader(callback)

    fun clearCachedCredentials() = Terminal.getInstance().clearCachedCredentials()

    fun createPaymentIntent(params: PaymentIntentParameters, callback: PaymentIntentCallback) =
        Terminal.getInstance().createPaymentIntent(params, callback)

    fun collectPaymentMethod(
        paymentIntent: PaymentIntent,
        listener: ReaderDisplayListener,
        callback: PaymentIntentCallback
    ): Cancelable = Terminal.getInstance().collectPaymentMethod(paymentIntent, listener, callback)

    fun processPayment(paymentIntent: PaymentIntent, callback: PaymentIntentCallback) =
        Terminal.getInstance().processPayment(paymentIntent, callback)

    fun checkForUpdate(callback: ReaderSoftwareUpdateCallback) = Terminal.getInstance().checkForUpdate(callback)
    fun installSoftwareUpdate(
        updateData: ReaderSoftwareUpdate,
        listener: ReaderSoftwareUpdateListener,
        callback: Callback
    ) = Terminal.getInstance().installUpdate(updateData, listener, callback)

    fun getConnectedReader(): CardReader? = Terminal.getInstance().connectedReader?.let { CardReaderImpl(it) }
}
