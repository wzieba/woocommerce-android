package com.woocommerce.android.cardreader

import android.app.Application

interface CardReaderService {
    fun isInitialized(): Boolean
    fun init(appCtx: Application)
    fun onTrimMemory(level: Int)
}
