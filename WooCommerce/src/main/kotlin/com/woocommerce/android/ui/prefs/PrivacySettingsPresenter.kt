package com.woocommerce.android.ui.prefs

import com.woocommerce.android.analytics.AnalyticsTracker
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.wordpress.android.fluxc.Dispatcher
import org.wordpress.android.fluxc.generated.AccountActionBuilder
import org.wordpress.android.fluxc.store.AccountStore
import org.wordpress.android.fluxc.store.AccountStore.OnAuthenticationChanged
import org.wordpress.android.fluxc.store.AccountStore.PushAccountSettingsPayload
import javax.inject.Inject

class PrivacySettingsPresenter @Inject constructor(
    private val dispatcher: Dispatcher,
    private val accountStore: AccountStore
) : PrivacySettingsContract.Presenter {
    private var privacySettingsFragmentView: PrivacySettingsContract.View? = null

    override fun takeView(view: PrivacySettingsContract.View) {
        dispatcher.register(this)
        privacySettingsFragmentView = view
    }

    override fun dropView() {
        dispatcher.unregister(this)
        privacySettingsFragmentView = null
    }

    override fun getSendUsageStats() = !accountStore.account.tracksOptOut

    override fun setSendUsageStats(sendUsageStats: Boolean) {
        AnalyticsTracker.sendUsageStats = sendUsageStats

        // sync with wpcom if a token is available
        if (accountStore.hasAccessToken()) {
            val payload = PushAccountSettingsPayload()
            payload.params = HashMap<String, Any>()
            payload.params["tracks_opt_out"] = !sendUsageStats
            dispatcher.dispatch(AccountActionBuilder.newPushSettingsAction(payload))
        }
    }

    @Suppress("unused")
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun OnAuthenticationChanged(event: OnAuthenticationChanged) {
        /*
         * this is empty but is necessary because the event bus requires at least one
         * public method with the @Subscribe annotation
         */
    }
}
