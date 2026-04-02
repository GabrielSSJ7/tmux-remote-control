package com.remotecontrol.ui.commands

import com.remotecontrol.data.api.CommandsApi
import com.remotecontrol.data.model.Command
import com.remotecontrol.data.model.CreateCommand
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
class CommandsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val api = mockk<CommandsApi>()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun loadCommandsReturnsFlatList() = runTest {
        coEvery { api.list() } returns listOf(
            Command("1", "push", "git push"),
            Command("2", "status", "git status"),
            Command("3", "up", "docker compose up"),
        )
        val vm = CommandsViewModel(api); vm.loadCommands(); advanceUntilIdle()
        assertEquals(3, vm.filteredCommands.value.size)
    }

    @Test
    fun searchFiltersCommands() = runTest {
        coEvery { api.list() } returns listOf(
            Command("1", "push", "git push"),
            Command("2", "deploy", "kubectl deploy"),
        )
        val vm = CommandsViewModel(api); vm.loadCommands(); advanceUntilIdle()
        vm.setSearch("push"); advanceUntilIdle()
        val filtered = vm.filteredCommands.value
        assertEquals(1, filtered.size)
        assertEquals("push", filtered.first().name)
    }

    @Test
    fun createCommandCallsApiAndReloads() = runTest {
        coEvery { api.list() } returns emptyList()
        coEvery { api.create(any()) } returns Command("1", "test", "echo")
        val vm = CommandsViewModel(api)
        vm.createCommand("test", "echo"); advanceUntilIdle()
        coVerify { api.create(any()) }
    }

    @Test
    fun deleteCommandCallsApiAndReloads() = runTest {
        coEvery { api.list() } returns listOf(Command("1", "test", "echo"))
        coEvery { api.delete("1") } returns Unit
        val vm = CommandsViewModel(api); vm.loadCommands(); advanceUntilIdle()
        vm.deleteCommand("1"); advanceUntilIdle()
        coVerify { api.delete("1") }
    }

    @Test
    fun updateCommandCallsApiAndReloads() = runTest {
        coEvery { api.list() } returns emptyList()
        coEvery { api.update(any(), any()) } returns Command("1", "updated", "echo updated")
        val vm = CommandsViewModel(api)
        vm.updateCommand("1", "updated", "echo updated"); advanceUntilIdle()
        coVerify { api.update("1", any()) }
    }

    @Test
    fun errorStateOnLoadFailure() = runTest {
        coEvery { api.list() } throws RuntimeException("network error")
        val vm = CommandsViewModel(api); vm.loadCommands(); advanceUntilIdle()
        assertNotNull(vm.error.value)
        vm.clearError()
        assertNull(vm.error.value)
    }
}
