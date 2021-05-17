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
)  : Parcelable

fun WCUserModel.toAppModel(): User {
    return User(
        id = this.id,
        firstName = this.firstName,
        lastName = this.lastName,
        username = this.username,
        roles = this.roles.map { it.value }.toList()
    )
}
