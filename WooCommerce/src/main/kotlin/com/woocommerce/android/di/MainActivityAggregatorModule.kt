package com.woocommerce.android.di

import com.woocommerce.android.ui.aztec.AztecModule
import com.woocommerce.android.ui.reviews.ReviewsModule
import com.woocommerce.android.ui.sitepicker.SitePickerModule
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent

/**
 * TODO A temporary class to allow injecting fragments relying on dagger.
 * We should remove it after finishing the migration
 */
@InstallIn(ActivityComponent::class)
@Module(
    includes = [
        ReviewsModule::class,
        SitePickerModule::class,
        AztecModule::class
    ]
)
interface MainActivityAggregatorModule