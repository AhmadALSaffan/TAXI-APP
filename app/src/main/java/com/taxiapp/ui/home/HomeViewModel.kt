package com.taxiapp.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.taxiapp.data.model.User
import com.taxiapp.data.repository.HomeRepository
import com.taxiapp.data.repository.RecentDestination
import com.taxiapp.util.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: HomeRepository
) : ViewModel() {


    private val _userState = MutableStateFlow<Resource<User>>(Resource.Loading as Resource<User>)
    val userState: StateFlow<Resource<User>> = _userState.asStateFlow()


    private val _recentDestinations = MutableStateFlow<List<RecentDestination>>(emptyList())
    val recentDestinations: StateFlow<List<RecentDestination>> = _recentDestinations.asStateFlow()


    private val _selectedDestination = MutableStateFlow<String?>(null)
    val selectedDestination: StateFlow<String?> = _selectedDestination.asStateFlow()

    init {
        loadUser()
        loadRecentDestinations()
    }


    private fun loadUser() {
        viewModelScope.launch {
            repository.observeUser().collect { result ->
                _userState.value = result
            }
        }
    }


    private fun loadRecentDestinations() {
        viewModelScope.launch {
            when (val result = repository.getRecentDestinations()) {
                is Resource.Success -> _recentDestinations.value = result.data
                else -> { /* silent fail — chips just won't show */ }
            }
        }
    }

    fun setSelectedDestination(address: String) {
        _selectedDestination.value = address
    }

    fun clearSelectedDestination() {
        _selectedDestination.value = null
    }
}