package ai.friday.assistant.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneMemoryPressureAnalyzerTest {
    @Test fun `reads memory totals PSI stalls and OOM counters`() {
        val report = PhoneMemoryPressureAnalyzer.analyze(
            meminfo = "MemTotal: 8192000 kB\nMemAvailable: 2048000 kB\nSwapTotal: 4096000 kB\nSwapFree: 1024000 kB",
            psi = "some avg10=12.00 avg60=8.00 avg300=2.00 total=1234\nfull avg10=1.50 avg60=0.50 avg300=0.10 total=456",
            vmstat = "oom_kill 3\npgscan_direct 77",
            memoryProcesses = "123456 kB: com.example.heavy (pid 99)\n65536 kB: com.example.light (pid 88)"
        )
        assertEquals(8000L, report.totalMb)
        assertEquals(2000L, report.availableMb)
        assertEquals(4000L, report.swapTotalMb)
        assertEquals(1000L, report.swapFreeMb)
        assertEquals(12.0, report.someAvg10!!, 0.001)
        assertEquals(1.5, report.fullAvg10!!, 0.001)
        assertEquals(3L, report.oomKills)
        assertEquals(77L, report.directPageScans)
        assertEquals("com.example.heavy", report.topProcesses.first().first)
        assertEquals(120L, report.topProcesses.first().second)
        assertTrue(report.assessment.contains("SEVERE"))
    }

    @Test fun `low available RAM without PSI stalls is not mislabeled as severe pressure`() {
        val report = PhoneMemoryPressureAnalyzer.analyze(
            meminfo = "MemTotal: 8192000 kB\nMemAvailable: 128000 kB",
            psi = "some avg10=0.00 avg60=0.00 avg300=0.00 total=0\nfull avg10=0.00 avg60=0.00 avg300=0.00 total=0",
            vmstat = ""
        )
        assertEquals("NO SIGNIFICANT PSI STALL SIGNAL", report.assessment)
        assertEquals(125L, report.availableMb)
    }

    @Test fun `missing PSI remains explicitly unknown`() {
        val report = PhoneMemoryPressureAnalyzer.analyze(
            meminfo = "MemTotal: 8192000 kB",
            psi = "",
            vmstat = ""
        )
        assertEquals("PSI UNAVAILABLE", report.assessment)
        assertEquals(null, report.someAvg10)
    }
}
