package com.woocommerce.android.push

import android.os.Bundle
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.woocommerce.android.support.ZendeskHelper
import com.woocommerce.android.util.WooLog
import com.woocommerce.android.util.WooLog.T
import dagger.hilt.android.AndroidEntryPoint
import org.wordpress.android.fluxc.store.AccountStore
import javax.inject.Inject

private const val PUSH_TYPE_ZENDESK = "zendesk"
private const val PUSH_ARG_ZENDESK_REQUEST_ID = "zendesk_sdk_request_id"

@AndroidEntryPoint
class FCMMessageService : FirebaseMessagingService() {
    @Inject lateinit var accountStore: AccountStore
    @Inject lateinit var zendeskHelper: ZendeskHelper
    @Inject lateinit var notificationHandler: NotificationHandler

    override fun onMessageReceived(message: RemoteMessage) {
        WooLog.v(T.NOTIFS, "Received message from Firebase")

        if (!accountStore.hasAccessToken()) return

        if (PUSH_TYPE_ZENDESK == message.data["type"]) {
            val zendeskRequestId = message.data[PUSH_ARG_ZENDESK_REQUEST_ID]

            // Try to refresh the Zendesk request page if it's currently being displayed;
            // otherwise show a notification
            if (!zendeskHelper.refreshRequest(this, zendeskRequestId)) {
                notificationHandler.handleZendeskNotification(this.applicationContext)
            }
        } else {
            notificationHandler.buildAndShowNotificationFromNoteData(
                    this.applicationContext,
                    convertMapToBundle(message.data),
                    accountStore.account
            )
        }
    }

    private fun convertMapToBundle(data: Map<String, String>): Bundle {
        return Bundle().apply {
            data.forEach { (key, value) -> putString(key, value) }
        }
    }
}
