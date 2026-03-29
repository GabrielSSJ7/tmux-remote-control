package com.remotecontrol.ui.commands

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.remotecontrol.data.api.CommandsApi
import com.remotecontrol.data.model.Command
import com.remotecontrol.data.model.CreateCommand
import com.remotecontrol.data.model.UpdateCommand
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class CommandsViewModel(private val api: CommandsApi) : ViewModel() {
    private val _commands = MutableStateFlow<List<Command>>(emptyList())
    private val _search = MutableStateFlow("")

    val groupedCommands: StateFlow<Map<String, List<Command>>> =
        combine(_commands, _search) { commands, search ->
            val filtered = if (search.isBlank()) commands
            else commands.filter { it.name.contains(search, ignoreCase = true) || it.command.contains(search, ignoreCase = true) || it.category.contains(search, ignoreCase = true) }
            filtered.groupBy { it.category }.toSortedMap()
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    fun loadCommands() { viewModelScope.launch { try { _commands.value = api.list() } catch (_: Exception) {} } }
    fun setSearch(query: String) { _search.value = query }
    fun createCommand(input: CreateCommand) { viewModelScope.launch { try { api.create(input); loadCommands() } catch (_: Exception) {} } }
    fun updateCommand(id: String, input: UpdateCommand) { viewModelScope.launch { try { api.update(id, input); loadCommands() } catch (_: Exception) {} } }
    fun deleteCommand(id: String) { viewModelScope.launch { try { api.delete(id); loadCommands() } catch (_: Exception) {} } }
}
