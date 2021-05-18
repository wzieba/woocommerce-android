package com.woocommerce.android.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import org.wordpress.android.fluxc.model.user.WCUserModel

@Parcelize
data class User(
    val id: Long,
    val firstName: String,
    val lastName: String,
    val username: String,
    val roles: List<String>
) : Parcelable

fun WCUserModel.toAppModel(): User {
    return User(
        id = this.remoteUserId,
        firstName = this.firstName,
        lastName = this.lastName,
        username = this.username,
        roles = this.getUserRoles().map { it.value }.toList()
    )
}
