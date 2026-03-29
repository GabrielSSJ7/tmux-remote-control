package com.remotecontrol.ui.sessions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.remotecontrol.data.api.SessionsApi
import com.remotecontrol.data.model.Session
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SessionsViewModel(private val api: SessionsApi) : ViewModel() {
    private val _sessions = MutableStateFlow<List<Session>>(emptyList())
    val sessions: StateFlow<List<Session>> = _sessions
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun loadSessions() {
        viewModelScope.launch {
            _isLoading.value = true; _error.value = null
            try { _sessions.value = api.list() }
            catch (e: Exception) { _error.value = e.message ?: "Failed to load sessions" }
            finally { _isLoading.value = false }
        }
    }

    fun createSession(name: String? = null) {
        viewModelScope.launch {
            try { api.create(mapOf("name" to name)); loadSessions() }
            catch (e: Exception) { _error.value = e.message ?: "Failed to create session" }
        }
    }

    fun deleteSession(name: String) {
        viewModelScope.launch {
            try { api.delete(name); loadSessions() }
            catch (e: Exception) { _error.value = e.message ?: "Failed to delete session" }
        }
    }
}
