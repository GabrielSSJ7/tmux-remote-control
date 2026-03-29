package com.remotecontrol.ui.sessions

import com.remotecontrol.data.api.SessionsApi
import com.remotecontrol.data.model.Session
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val api = mockk<SessionsApi>()

    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun loadSessionsFetchesFromApi() = runTest {
        val sessions = listOf(Session(id = "$1", name = "main", createdAt = "2024-01-01", attached = false))
        coEvery { api.list() } returns sessions
        val vm = SessionsViewModel(api)
        vm.loadSessions(); advanceUntilIdle()
        assertEquals(sessions, vm.sessions.value)
        assertFalse(vm.isLoading.value)
    }

    @Test
    fun loadSessionsSetsErrorOnFailure() = runTest {
        coEvery { api.list() } throws RuntimeException("Network error")
        val vm = SessionsViewModel(api)
        vm.loadSessions(); advanceUntilIdle()
        assertNotNull(vm.error.value)
        assertTrue(vm.error.value!!.contains("Network error"))
    }

    @Test
    fun createSessionCallsApiAndReloads() = runTest {
        coEvery { api.list() } returns emptyList()
        coEvery { api.create(any()) } returns Session("$2", "rc-abc", "2024-01-01", false)
        val vm = SessionsViewModel(api)
        vm.createSession(); advanceUntilIdle()
        coVerify { api.create(any()) }
        coVerify(atLeast = 1) { api.list() }
    }

    @Test
    fun deleteSessionCallsApiAndReloads() = runTest {
        coEvery { api.list() } returns emptyList()
        coEvery { api.delete("main") } returns Unit
        val vm = SessionsViewModel(api)
        vm.deleteSession("main"); advanceUntilIdle()
        coVerify { api.delete("main") }
    }
}
