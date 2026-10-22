package com.pulso.tv.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pulso.tv.PulsoApp
import com.pulso.tv.model.ui.StreamProxy
import com.pulso.tv.repository.PlayerRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class StreamProxySettingsViewModel(
    private val playerRepository: PlayerRepository,
) : ViewModel() {

    val list = MutableStateFlow<MutableList<StreamProxy>>(mutableListOf())

    fun getProxies() {
        viewModelScope.launch {
            list.value = playerRepository.getStreamProxies().sortedBy { it.position }.toMutableList()
        }
    }

    fun updateProxies() {
        val items = list.value.onEachIndexed { index, proxy ->
            proxy.position = index
        }
        viewModelScope.launch {
            playerRepository.updateStreamProxies(items)
        }
    }

    fun saveProxy(item: StreamProxy) {
        viewModelScope.launch {
            val id = playerRepository.saveStreamProxy(item)
            item.id = id.toInt()
        }
    }

    fun deleteProxy(item: StreamProxy) {
        viewModelScope.launch {
            playerRepository.deleteStreamProxy(item)
            updateProxies()
        }
    }

    fun updateProxy(item: StreamProxy) {
        viewModelScope.launch {
            playerRepository.updateStreamProxy(item)
        }
    }

    companion object {
        val StreamProxySettingsViewModelFactory = viewModelFactory {
            initializer {
                val application = (this[APPLICATION_KEY] as PulsoApp)
                val pulsoModule = application.pulsoModule
                StreamProxySettingsViewModel(pulsoModule.playerRepository)
            }
        }
    }
}
