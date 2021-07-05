package com.woocommerce.android.ui.main

import androidx.annotation.StringRes
import com.woocommerce.android.ui.base.BasePresenter
import com.woocommerce.android.ui.base.BaseView
import com.woocommerce.android.ui.base.TopLevelFragmentRouter
import org.wordpress.android.fluxc.model.SiteModel
import org.wordpress.android.fluxc.model.notification.NotificationModel

interface MainContract {
    interface Presenter : BasePresenter<View> {
        fun userIsLoggedIn(): Boolean
        fun storeMagicLinkToken(token: String)
        fun getNotificationByRemoteNoteId(remoteNoteId: Long): NotificationModel?
        fun hasMultipleStores(): Boolean
        fun selectedSiteChanged(site: SiteModel)
        fun fetchUnfilledOrderCount()
        fun fetchSitesAfterDowngrade()
        fun isUserEligible(): Boolean
    }

    interface View : BaseView<Presenter>, TopLevelFragmentRouter {
        fun notifyTokenUpdated()
        fun showLoginScreen()
        fun showSitePickerScreen()
        fun updateSelectedSite()
        fun updateOfflineStatusBar(isConnected: Boolean)
        fun hideBottomNav()
        fun showBottomNav()
        fun hideReviewsBadge()
        fun showReviewsBadge()
        fun updateReviewsBadge()
        fun hideOrderBadge()
        fun showOrderBadge(count: Int)
        fun updateOrderBadge(hideCountUntilComplete: Boolean)
        fun hideProgressDialog()
        fun showProgressDialog(@StringRes stringId: Int)
        fun showUserEligibilityErrorScreen()
    }
}
