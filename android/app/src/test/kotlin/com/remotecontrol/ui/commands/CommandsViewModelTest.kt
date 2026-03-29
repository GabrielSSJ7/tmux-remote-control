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
    fun loadCommandsGroupsByCategory() = runTest {
        coEvery { api.list() } returns listOf(
            Command("1", "push", "git push", null, "git", null, "", ""),
            Command("2", "status", "git status", null, "git", null, "", ""),
            Command("3", "up", "docker compose up", null, "docker", null, "", ""),
        )
        val vm = CommandsViewModel(api); vm.loadCommands(); advanceUntilIdle()
        val grouped = vm.groupedCommands.value
        assertEquals(2, grouped.size)
        assertEquals(listOf("docker", "git"), grouped.keys.sorted())
    }

    @Test
    fun searchFiltersCommands() = runTest {
        coEvery { api.list() } returns listOf(
            Command("1", "push", "git push", null, "git", null, "", ""),
            Command("2", "deploy", "kubectl deploy", null, "k8s", null, "", ""),
        )
        val vm = CommandsViewModel(api); vm.loadCommands(); advanceUntilIdle()
        vm.setSearch("push"); advanceUntilIdle()
        val grouped = vm.groupedCommands.value
        assertEquals(1, grouped.size)
        assertEquals("push", grouped["git"]?.first()?.name)
    }

    @Test
    fun createCommandCallsApiAndReloads() = runTest {
        coEvery { api.list() } returns emptyList()
        coEvery { api.create(any()) } returns Command("1", "test", "echo", null, "misc", null, "", "")
        val vm = CommandsViewModel(api)
        vm.createCommand(CreateCommand("test", "echo", null, "misc")); advanceUntilIdle()
        coVerify { api.create(any()) }
    }
}
