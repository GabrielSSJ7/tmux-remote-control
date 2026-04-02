package com.remotecontrol.ui.commands

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.remotecontrol.data.api.CommandsApi
import com.remotecontrol.data.model.Command
import com.remotecontrol.data.model.CreateCommand
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class CommandsViewModel(private val api: CommandsApi) : ViewModel() {
    private val _commands = MutableStateFlow<List<Command>>(emptyList())
    private val _search = MutableStateFlow("")
    private val _error = MutableStateFlow<String?>(null)

    val filteredCommands: StateFlow<List<Command>> =
        combine(_commands, _search) { commands, search ->
            if (search.isBlank()) commands
            else commands.filter {
                it.name.contains(search, ignoreCase = true) ||
                    it.command.contains(search, ignoreCase = true)
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val error: StateFlow<String?> = _error.asStateFlow()

    fun loadCommands() {
        viewModelScope.launch {
            try {
                _commands.value = api.list()
                _error.value = null
            } catch (e: Exception) {
                _error.value = "Failed to load commands"
            }
        }
    }

    fun setSearch(query: String) { _search.value = query }

    fun createCommand(name: String, command: String) {
        viewModelScope.launch {
            try {
                api.create(CreateCommand(name = name, command = command))
                loadCommands()
            } catch (e: Exception) {
                _error.value = "Failed to create command"
            }
        }
    }

    fun updateCommand(id: String, name: String, command: String) {
        viewModelScope.launch {
            try {
                api.update(id, CreateCommand(name = name, command = command))
                loadCommands()
            } catch (e: Exception) {
                _error.value = "Failed to update command"
            }
        }
    }

    fun deleteCommand(id: String) {
        viewModelScope.launch {
            try {
                api.delete(id)
                loadCommands()
            } catch (e: Exception) {
                _error.value = "Failed to delete command"
            }
        }
    }

    fun clearError() { _error.value = null }
}
