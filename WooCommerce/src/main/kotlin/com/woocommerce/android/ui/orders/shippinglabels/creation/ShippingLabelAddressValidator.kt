package com.woocommerce.android.ui.orders.shippinglabels.creation

import android.os.Parcelable
import com.woocommerce.android.analytics.AnalyticsTracker
import com.woocommerce.android.analytics.AnalyticsTracker.Stat
import com.woocommerce.android.model.Address
import com.woocommerce.android.model.toAppModel
import com.woocommerce.android.tools.SelectedSite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize
import org.wordpress.android.fluxc.model.shippinglabels.WCAddressVerificationResult
import org.wordpress.android.fluxc.model.shippinglabels.WCAddressVerificationResult.InvalidAddress
import org.wordpress.android.fluxc.model.shippinglabels.WCAddressVerificationResult.InvalidRequest
import org.wordpress.android.fluxc.model.shippinglabels.WCShippingLabelModel.ShippingLabelAddress.Type
import org.wordpress.android.fluxc.network.rest.wpcom.wc.WooErrorType
import org.wordpress.android.fluxc.network.rest.wpcom.wc.WooErrorType.GENERIC_ERROR
import org.wordpress.android.fluxc.store.WCShippingLabelStore
import javax.inject.Inject

class ShippingLabelAddressValidator @Inject constructor(
    private val shippingLabelStore: WCShippingLabelStore,
    private val selectedSite: SelectedSite
) {
    suspend fun validateAddress(
        address: Address,
        type: AddressType,
        requiresPhoneNumber: Boolean
    ): ValidationResult {
        return when {
            isNameMissing(address) -> ValidationResult.NameMissing
            requiresPhoneNumber && !address.hasValidPhoneNumber(type) -> ValidationResult.PhoneInvalid
            else -> verifyAddress(address, type)
        }
    }

    private suspend fun verifyAddress(address: Address, type: AddressType): ValidationResult {
        val result = withContext(Dispatchers.IO) {
            shippingLabelStore.verifyAddress(
                selectedSite.get(),
                address.toShippingLabelModel(),
                type.toDataType()
            )
        }

        if (result.isError) {
            AnalyticsTracker.track(
                Stat.SHIPPING_LABEL_ADDRESS_VALIDATION_FAILED,
                mapOf("error" to result.error.type.name)
            )

            return ValidationResult.Error(result.error.type)
        }
        return when (result.model) {
            null -> {
                AnalyticsTracker.track(
                    Stat.SHIPPING_LABEL_ADDRESS_VALIDATION_FAILED,
                    mapOf("error" to "response_model_null")
                )

                ValidationResult.Error(GENERIC_ERROR)
            }
            is InvalidRequest -> {
                AnalyticsTracker.track(
                    Stat.SHIPPING_LABEL_ADDRESS_VALIDATION_FAILED,
                    mapOf("error" to "address_not_found")
                )

                ValidationResult.NotFound((result.model as InvalidRequest).message)
            }
            is InvalidAddress -> {
                AnalyticsTracker.track(
                    Stat.SHIPPING_LABEL_ADDRESS_VALIDATION_FAILED,
                    mapOf("error" to "invalid_address")
                )

                ValidationResult.Invalid((result.model as InvalidAddress).message)
            }
            is WCAddressVerificationResult.Valid -> {
                AnalyticsTracker.track(Stat.SHIPPING_LABEL_ADDRESS_VALIDATION_SUCCEEDED)
                val suggestion =
                    (result.model as WCAddressVerificationResult.Valid).suggestedAddress.toAppModel()
                if (suggestion.toString() != address.toString()) {
                    ValidationResult.SuggestedChanges(suggestion)
                } else {
                    ValidationResult.Valid
                }
            }
        }
    }

    private fun isNameMissing(address: Address): Boolean {
        return (address.firstName + address.lastName).isBlank() && address.company.isBlank()
    }

    sealed class ValidationResult : Parcelable {
        @Parcelize
        object Valid : ValidationResult()

        @Parcelize
        object NameMissing : ValidationResult()

        @Parcelize
        object PhoneInvalid : ValidationResult()

        @Parcelize
        data class SuggestedChanges(val suggested: Address) : ValidationResult()

        @Parcelize
        data class Invalid(val message: String) : ValidationResult()

        @Parcelize
        data class NotFound(val message: String) : ValidationResult()

        @Parcelize
        data class Error(val type: WooErrorType) : ValidationResult()
    }

    enum class AddressType {
        ORIGIN,
        DESTINATION;

        fun toDataType(): Type {
            return when (this) {
                ORIGIN -> Type.ORIGIN
                DESTINATION -> Type.DESTINATION
            }
        }
    }
}
