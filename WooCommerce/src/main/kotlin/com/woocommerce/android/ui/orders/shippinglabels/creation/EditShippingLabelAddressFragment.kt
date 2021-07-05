package com.woocommerce.android.ui.orders.shippinglabels.creation

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.Button
import androidx.annotation.StringRes
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.navigation.fragment.findNavController
import com.google.android.material.textfield.TextInputLayout
import com.woocommerce.android.R
import com.woocommerce.android.analytics.AnalyticsTracker
import com.woocommerce.android.databinding.FragmentEditShippingLabelAddressBinding
import com.woocommerce.android.extensions.handleResult
import com.woocommerce.android.extensions.hide
import com.woocommerce.android.extensions.navigateBackWithNotice
import com.woocommerce.android.extensions.navigateBackWithResult
import com.woocommerce.android.extensions.navigateSafely
import com.woocommerce.android.extensions.show
import com.woocommerce.android.extensions.takeIfNotEqualTo
import com.woocommerce.android.model.Address
import com.woocommerce.android.ui.base.BaseFragment
import com.woocommerce.android.ui.base.UIMessageResolver
import com.woocommerce.android.ui.main.MainActivity.Companion.BackPressListener
import com.woocommerce.android.ui.orders.shippinglabels.creation.CreateShippingLabelEvent.DialPhoneNumber
import com.woocommerce.android.ui.orders.shippinglabels.creation.CreateShippingLabelEvent.OpenMapWithAddress
import com.woocommerce.android.ui.orders.shippinglabels.creation.CreateShippingLabelEvent.ShowCountrySelector
import com.woocommerce.android.ui.orders.shippinglabels.creation.CreateShippingLabelEvent.ShowStateSelector
import com.woocommerce.android.ui.orders.shippinglabels.creation.CreateShippingLabelEvent.ShowSuggestedAddress
import com.woocommerce.android.ui.orders.shippinglabels.creation.ShippingLabelAddressSuggestionFragment.Companion.SELECTED_ADDRESS_ACCEPTED
import com.woocommerce.android.ui.orders.shippinglabels.creation.ShippingLabelAddressSuggestionFragment.Companion.SELECTED_ADDRESS_TO_BE_EDITED
import com.woocommerce.android.viewmodel.MultiLiveEvent.Event.Exit
import com.woocommerce.android.viewmodel.MultiLiveEvent.Event.ExitWithResult
import com.woocommerce.android.viewmodel.MultiLiveEvent.Event.ShowSnackbar
import com.woocommerce.android.widgets.CustomProgressDialog
import com.woocommerce.android.widgets.WCMaterialOutlinedSpinnerView
import dagger.hilt.android.AndroidEntryPoint
import org.wordpress.android.util.ActivityUtils
import org.wordpress.android.util.ToastUtils
import javax.inject.Inject

@AndroidEntryPoint
class EditShippingLabelAddressFragment : BaseFragment(R.layout.fragment_edit_shipping_label_address),
    BackPressListener {
    companion object {
        const val SELECT_COUNTRY_REQUEST = "select_country_request"
        const val SELECT_STATE_REQUEST = "select_state_request"
        const val EDIT_ADDRESS_RESULT = "key_edit_address_dialog_result"
        const val EDIT_ADDRESS_CLOSED = "key_edit_address_dialog_closed"
    }

    @Inject lateinit var uiMessageResolver: UIMessageResolver

    private var progressDialog: CustomProgressDialog? = null
    private var _binding: FragmentEditShippingLabelAddressBinding? = null
    private val binding get() = _binding!!

    val viewModel: EditShippingLabelAddressViewModel by viewModels()

    private var screenTitle = ""
        set(value) {
            field = value
            updateActivityTitle()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setHasOptionsMenu(true)
    }

    override fun onResume() {
        super.onResume()
        AnalyticsTracker.trackViewShown(this)
    }

    override fun onPause() {
        super.onPause()
        progressDialog?.dismiss()
    }

    override fun onStop() {
        super.onStop()
        activity?.let {
            ActivityUtils.hideKeyboard(it)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        _binding = FragmentEditShippingLabelAddressBinding.bind(view)

        initializeViewModel()
        initializeViews()
    }

    private fun initializeViewModel() {
        subscribeObservers()
        setupResultHandlers()
    }

    private fun setupResultHandlers() {
        handleResult<String>(SELECT_COUNTRY_REQUEST) {
            viewModel.onCountrySelected(it)
        }
        handleResult<String>(SELECT_STATE_REQUEST) {
            viewModel.onStateSelected(it)
        }
        handleResult<Address>(SELECTED_ADDRESS_ACCEPTED) {
            viewModel.onAddressSelected(it)
        }
        handleResult<Address>(SELECTED_ADDRESS_TO_BE_EDITED) {
            viewModel.onEditRequested(it)
        }
    }

    override fun getFragmentTitle() = screenTitle

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)

        inflater.inflate(R.menu.menu_done, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_done -> {
                ActivityUtils.hideKeyboard(activity)
                viewModel.onDoneButtonClicked(gatherData())
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    @SuppressLint("SetTextI18n")
    private fun subscribeObservers() {
        viewModel.viewStateData.observe(viewLifecycleOwner) { old, new ->
            new.address?.takeIfNotEqualTo(old?.address) {
                binding.company.setText(it.company)
                binding.name.setText("${it.firstName} ${it.lastName}".trim())
                binding.phone.setText(it.phone)
                binding.address1.setText(it.address1)
                binding.address2.setText(it.address2)
                binding.zip.setText(it.postcode)
                binding.city.setText(it.city)
                binding.countrySpinner.tag = it.country
                binding.stateSpinner.tag = it.state
            }
            new.title?.takeIfNotEqualTo(old?.title) {
                screenTitle = getString(it)
            }
            new.addressError.takeIfNotEqualTo(old?.addressError) {
                showErrorOrClear(binding.address1Layout, it)
            }
            new.phoneError.takeIfNotEqualTo(old?.phoneError) {
                showErrorOrClear(binding.phoneLayout, it)
            }
            new.nameError.takeIfNotEqualTo(old?.nameError) {
                showErrorOrClear(binding.nameLayout, it)
            }
            new.cityError.takeIfNotEqualTo(old?.cityError) {
                showErrorOrClear(binding.cityLayout, it)
            }
            new.zipError.takeIfNotEqualTo(old?.zipError) {
                showErrorOrClear(binding.zipLayout, it)
            }
            new.bannerMessage?.takeIfNotEqualTo(old?.bannerMessage) {
                if (it.isBlank()) {
                    binding.errorBanner.hide()
                } else {
                    binding.errorBannerMessage.text = it
                    binding.errorBanner.show()
                }
            }
            new.isValidationProgressDialogVisible
                ?.takeIfNotEqualTo(old?.isValidationProgressDialogVisible) { isVisible ->
                    if (isVisible) {
                        showProgressDialog(
                            getString(R.string.shipping_label_edit_address_validation_progress_title),
                            getString(R.string.shipping_label_edit_address_progress_message)
                        )
                    } else {
                        hideProgressDialog()
                    }
                }
            new.isLoadingProgressDialogVisible?.takeIfNotEqualTo(old?.isLoadingProgressDialogVisible) { isVisible ->
                if (isVisible) {
                    showProgressDialog(
                        getString(R.string.shipping_label_edit_address_loading_progress_title),
                        getString(R.string.shipping_label_edit_address_progress_message)
                    )
                } else {
                    hideProgressDialog()
                }
            }
            new.selectedCountryName?.takeIfNotEqualTo(old?.selectedCountryName) {
                binding.countrySpinner.setText(it)
            }
            new.selectedStateName?.takeIfNotEqualTo(old?.selectedStateName) {
                binding.stateSpinner.setText(it)
                binding.state.setText(it)
            }
            new.isStateFieldSpinner?.takeIfNotEqualTo(old?.isStateFieldSpinner) { isSpinner ->
                binding.stateSpinner.isVisible = isSpinner
                binding.stateLayout.isVisible = !isSpinner
            }
            new.isContactCustomerButtonVisible.takeIfNotEqualTo(old?.isContactCustomerButtonVisible) { isVisible ->
                binding.contactCustomerButton.isVisible = isVisible
            }
        }

        viewModel.event.observe(viewLifecycleOwner, Observer { event ->
            when (event) {
                is ShowSnackbar -> uiMessageResolver.showSnack(event.message)
                is ExitWithResult<*> -> navigateBackWithResult(EDIT_ADDRESS_RESULT, event.data)
                is Exit -> navigateBackWithNotice(EDIT_ADDRESS_CLOSED)
                is ShowSuggestedAddress -> {
                    val action = EditShippingLabelAddressFragmentDirections
                        .actionEditShippingLabelAddressFragmentToShippingLabelAddressSuggestionFragment(
                            event.originalAddress,
                            event.suggestedAddress,
                            event.type
                        )
                    findNavController().navigateSafely(action)
                }
                is ShowCountrySelector -> {
                    val action = EditShippingLabelAddressFragmentDirections
                        .actionEditShippingLabelAddressFragmentToItemSelectorDialog(
                            event.currentCountry,
                            event.locations.map { it.name }.toTypedArray(),
                            event.locations.map { it.code }.toTypedArray(),
                            SELECT_COUNTRY_REQUEST,
                            getString(R.string.shipping_label_edit_address_country)
                        )
                    findNavController().navigateSafely(action)
                }
                is ShowStateSelector -> {
                    val action = EditShippingLabelAddressFragmentDirections
                        .actionEditShippingLabelAddressFragmentToItemSelectorDialog(
                            event.currentState,
                            event.locations.map { it.name }.toTypedArray(),
                            event.locations.map { it.code }.toTypedArray(),
                            SELECT_STATE_REQUEST,
                            getString(R.string.shipping_label_edit_address_state)
                        )
                    findNavController().navigateSafely(action)
                }
                is OpenMapWithAddress -> launchMapsWithAddress(event.address)
                is DialPhoneNumber -> dialPhoneNumber(event.phoneNumber)
                else -> event.isHandled = false
            }
        })
    }

    private fun showErrorOrClear(inputLayout: TextInputLayout, @StringRes message: Int?) {
        if (message == null || message == 0) {
            inputLayout.error = null
        } else {
            inputLayout.error = resources.getString(message)
        }
    }

    private fun showProgressDialog(title: String, message: String) {
        hideProgressDialog()
        progressDialog = CustomProgressDialog.show(
            title = title,
            message = message
        ).also { it.show(parentFragmentManager, CustomProgressDialog.TAG) }
        progressDialog?.isCancelable = false
    }

    private fun hideProgressDialog() {
        progressDialog?.dismiss()
        progressDialog = null
    }

    private fun launchMapsWithAddress(address: Address) {
        val cleanAddress = address.copy(
            firstName = "",
            lastName = "",
            phone = "",
            email = ""
        ).toString().replace("\n", ", ")
        val gmmIntentUri: Uri = Uri.parse("geo:0,0?q=$cleanAddress")
        val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
        mapIntent.setPackage("com.google.android.apps.maps")

        try {
            startActivity(mapIntent)
        } catch (e: ActivityNotFoundException) {
            ToastUtils.showToast(context, R.string.error_no_gmaps_app)
        }
    }

    private fun dialPhoneNumber(phone: String) {
        val intent = Intent(Intent.ACTION_DIAL)
        intent.data = Uri.parse("tel:$phone")
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            ToastUtils.showToast(context, R.string.error_no_phone_app)
        }
    }

    private fun initializeViews() {
        binding.useAddressAsIsButton.onClick {
            viewModel.onUseAddressAsIsButtonClicked()
        }
        binding.countrySpinner.onClick {
            viewModel.onCountrySpinnerTapped()
        }
        binding.stateSpinner.onClick {
            viewModel.onStateSpinnerTapped()
        }
        binding.openMapButton.onClick {
            viewModel.onOpenMapTapped()
        }
        binding.contactCustomerButton.onClick {
            viewModel.onContactCustomerTapped()
        }
    }

    private fun WCMaterialOutlinedSpinnerView.onClick(onClick: () -> Unit) {
        this.setClickListener {
            viewModel.updateAddress(gatherData())
            onClick()
        }
    }

    private fun Button.onClick(onButtonClick: () -> Unit) {
        setOnClickListener {
            viewModel.updateAddress(gatherData())
            onButtonClick()
        }
    }

    private fun gatherData(): Address {
        return Address(
            company = binding.company.text.toString(),
            firstName = binding.name.text.toString(),
            lastName = "",
            phone = binding.phone.text.toString(),
            address1 = binding.address1.text.toString(),
            address2 = binding.address2.text.toString(),
            postcode = binding.zip.text.toString(),
            state = binding.stateSpinner.tag as String,
            city = binding.city.text.toString(),
            country = binding.countrySpinner.tag as String,
            email = ""
        )
    }

    // Let the ViewModel know the user is attempting to close the screen
    override fun onRequestAllowBackPress(): Boolean {
        return (viewModel.event.value == Exit).also { if (it.not()) viewModel.onExit() }
    }
}
