package com.example.nexuswallet.feature.wallet.ui.addressbook

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nexuswallet.feature.wallet.domain.model.AddressBookEntry
import com.example.nexuswallet.feature.wallet.domain.repository.AddressBookRepository
import com.example.nexuswallet.feature.wallet.domain.usecase.AddAddressBookEntryUseCase
import com.example.nexuswallet.feature.wallet.domain.usecase.GetAddressBookEntriesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AddressBookViewModel @Inject constructor(
    private val getAddressBookEntriesUseCase: GetAddressBookEntriesUseCase,
    private val addAddressBookEntryUseCase: AddAddressBookEntryUseCase,
    private val repository: AddressBookRepository
) : ViewModel() {

    val entries: StateFlow<List<AddressBookEntry>> = getAddressBookEntriesUseCase()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun addEntry(alias: String, address: String, networkName: String) {
        viewModelScope.launch {
            addAddressBookEntryUseCase(alias, address, networkName)
        }
    }

    fun deleteEntry(id: String) {
        viewModelScope.launch {
            repository.deleteEntryById(id)
        }
    }
}
