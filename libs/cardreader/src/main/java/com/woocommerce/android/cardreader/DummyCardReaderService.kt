package com.woocommerce.android.cardreader

import android.app.Application
import android.util.Log
import com.stripe.stripeterminal.Terminal
import com.stripe.stripeterminal.TerminalLifecycleObserver
import com.stripe.stripeterminal.callable.Callback
import com.stripe.stripeterminal.callable.DiscoveryListener
import com.stripe.stripeterminal.callable.ReaderCallback
import com.stripe.stripeterminal.callable.TerminalListener
import com.stripe.stripeterminal.log.LogLevel
import com.stripe.stripeterminal.model.external.ConnectionStatus
import com.stripe.stripeterminal.model.external.DeviceType
import com.stripe.stripeterminal.model.external.DiscoveryConfiguration
import com.stripe.stripeterminal.model.external.PaymentStatus
import com.stripe.stripeterminal.model.external.Reader
import com.stripe.stripeterminal.model.external.ReaderEvent
import com.stripe.stripeterminal.model.external.TerminalException

class DummyCardReaderService : CardReaderService {
    val paymentService: PaymentService = DummyPaymentService()
    private val observer: TerminalLifecycleObserver = TerminalLifecycleObserver.getInstance()
    private lateinit var application: Application

    override fun isInitialized(): Boolean {
        return Terminal.isInitialized()
    }

    override fun init(application: Application) {
        this.application = application

        // Register the observer for all lifecycle hooks
        application.registerActivityLifecycleCallbacks(observer)

        // Create your listener object. Override any methods that you want to be notified about
        val listener = object : TerminalListener {
            override fun onUnexpectedReaderDisconnect(reader: Reader) {
                // TODO cardreader: Implement onUnexpectedReaderDisconnect
                Log.d("CardReader", "onUnexpectedReaderDisconnect")
            }

            override fun onConnectionStatusChange(status: ConnectionStatus) {
                super.onConnectionStatusChange(status)
                Log.d("CardReader", "onConnectionStatusChange: ${status.name}")
            }

            override fun onPaymentStatusChange(status: PaymentStatus) {
                super.onPaymentStatusChange(status)
                Log.d("CardReader", "onPaymentStatusChange: ${status.name}")
            }

            override fun onReportLowBatteryWarning() {
                super.onReportLowBatteryWarning()
                Log.d("CardReader", "onReportLowBatteryWarning")
            }

            override fun onReportReaderEvent(event: ReaderEvent) {
                super.onReportReaderEvent(event)
                Log.d("CardReader", "onReportReaderEvent: $event.name")
            }
        }

        // Choose the level of messages that should be logged to your console
        val logLevel = LogLevel.VERBOSE

        // Create your token provider.
        val tokenProvider = DummyTokenProvider()

        // Pass in the current application context, your desired logging level, your token provider, and the listener you created
        if (!Terminal.isInitialized()) {
            Terminal.initTerminal(application, logLevel, tokenProvider, listener)
        }
    }

    override fun onTrimMemory(level: Int) {
        if (Terminal.isInitialized()) {
            observer.onTrimMemory(level, application)
        }
    }

    fun onConnect() {
        val config = DiscoveryConfiguration(0, DeviceType.CHIPPER_2X, true)
        val cancelable = Terminal.getInstance().discoverReaders(config, object : DiscoveryListener {
            override fun onUpdateDiscoveredReaders(readers: List<Reader>) {
                Terminal.getInstance().connectReader(readers.first(), object : ReaderCallback {
                    override fun onSuccess(r: Reader) {
                        Log.d("CardReader", "Connected to a reader ${r.serialNumber}")
                    }

                    override fun onFailure(e: TerminalException) {
                        e.printStackTrace()
                        Log.d("CardReader", "Initializing connection to a reader failed.")
                    }
                })
            }
        }, object : Callback {
            override fun onFailure(e: TerminalException) {
                Log.d("CardReader", "Reader discovery failed.")
            }

            override fun onSuccess() {
                Log.d("CardReader", "Reader discovery succeeded.")
            }
        })
        // TODO cardreader: manually stop the discovery when the user leaves the app
    }
}
