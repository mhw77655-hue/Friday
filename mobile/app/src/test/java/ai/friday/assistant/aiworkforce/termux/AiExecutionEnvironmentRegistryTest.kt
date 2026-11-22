package ai.friday.assistant.aiworkforce.termux

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AiExecutionEnvironmentRegistryTest {
    private class FakeEnvironment(
        override val kind: AiExecutionEnvironmentKind,
        private val state: AiExecutionEnvironmentState
    ) : AiExecutionEnvironment {
        override suspend fun probe() = AiExecutionEnvironmentProbe(kind, state, state.name)
        override suspend fun executeShell(
            command: String,
            stdin: String,
            workingDirectory: String,
            timeoutMs: Long
        ) = AiEnvironmentCommandResult("ok", "", 0)
    }

    @Test fun `selects first ready environment without assuming Termux`() = runBlocking {
        val app = FakeEnvironment(
            AiExecutionEnvironmentKind.APP_MANAGED,
            AiExecutionEnvironmentState.READY
        )
        val termux = FakeEnvironment(
            AiExecutionEnvironmentKind.TERMUX_NATIVE,
            AiExecutionEnvironmentState.READY
        )
        val selected = AiExecutionEnvironmentRegistry(listOf(app, termux)).selectReady()
        assertNotNull(selected)
        assertEquals(AiExecutionEnvironmentKind.APP_MANAGED, selected!!.first.kind)
    }

    @Test fun `falls back to next ready environment when preferred environment is unavailable`() = runBlocking {
        val app = FakeEnvironment(
            AiExecutionEnvironmentKind.APP_MANAGED,
            AiExecutionEnvironmentState.NOT_INSTALLED
        )
        val termux = FakeEnvironment(
            AiExecutionEnvironmentKind.TERMUX_NATIVE,
            AiExecutionEnvironmentState.READY
        )
        val selected = AiExecutionEnvironmentRegistry(listOf(app, termux)).selectReady()
        assertNotNull(selected)
        assertEquals(AiExecutionEnvironmentKind.TERMUX_NATIVE, selected!!.first.kind)
    }

    @Test fun `keeps every ready environment available for per-provider routing`() = runBlocking {
        val registry = AiExecutionEnvironmentRegistry(
            listOf(
                FakeEnvironment(AiExecutionEnvironmentKind.APP_MANAGED, AiExecutionEnvironmentState.READY),
                FakeEnvironment(AiExecutionEnvironmentKind.TERMUX_NATIVE, AiExecutionEnvironmentState.READY),
                FakeEnvironment(AiExecutionEnvironmentKind.LINUX_PROOT, AiExecutionEnvironmentState.NOT_INSTALLED)
            )
        )
        assertEquals(
            listOf(AiExecutionEnvironmentKind.APP_MANAGED, AiExecutionEnvironmentKind.TERMUX_NATIVE),
            registry.readyEnvironments().map { it.first.kind }
        )
    }

    @Test fun `returns no selection when all registered environments are unavailable`() = runBlocking {
        val registry = AiExecutionEnvironmentRegistry(
            listOf(
                FakeEnvironment(AiExecutionEnvironmentKind.APP_MANAGED, AiExecutionEnvironmentState.UNSUPPORTED),
                FakeEnvironment(AiExecutionEnvironmentKind.TERMUX_NATIVE, AiExecutionEnvironmentState.PERMISSION_REQUIRED),
                FakeEnvironment(AiExecutionEnvironmentKind.LINUX_PROOT, AiExecutionEnvironmentState.NOT_INSTALLED)
            )
        )
        assertNull(registry.selectReady())
        assertEquals(3, registry.discover().size)
    }
}
