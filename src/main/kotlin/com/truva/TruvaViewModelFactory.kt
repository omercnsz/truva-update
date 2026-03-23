package com.truva

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class TruvaViewModelFactory(
    private val dao: AppDao,
    private val simDao: SimProtectionDao,
    private val application: Application? = null
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TruvaViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TruvaViewModel(dao, simDao, application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
