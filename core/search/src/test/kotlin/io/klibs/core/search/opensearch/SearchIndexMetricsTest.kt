package io.klibs.core.search.opensearch

import io.klibs.core.search.dto.opensearch.OpenSearchIndexSpec
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SearchIndexMetricsTest {

    private val registry = SimpleMeterRegistry()
    private val metrics = SearchIndexMetrics(registry)

    private val project = spec("project")
    private val packages = spec("package")

    @Test
    fun `a successful rebuild reports a fresh index with its document count`() {
        metrics.recordSuccess(project, docCount = 1_234, took = Duration.ofSeconds(4))

        assertEquals(1_234.0, gauge("klibs.search.index.docs", "project"))
        assertEquals(0.0, gauge("klibs.search.index.age.seconds", "project"))
        assertEquals(4.0, syncSeconds("project"))
        assertEquals(1L, syncCount("project"))
        assertEquals(project.hash, registry.get("klibs.search.index.docs").tag("index", "project").gauge()
            .id.getTag("hash"))
    }

    @Test
    fun `a failed rebuild is counted and leaves no freshness claim`() {
        metrics.recordFailure(project)

        assertEquals(1.0, failures("project"))
        assertNull(registry.find("klibs.search.index.age.seconds").tag("index", "project").gauge())
    }

    @Test
    fun `indices are reported separately`() {
        metrics.recordSuccess(project, docCount = 10, took = Duration.ofSeconds(1))
        metrics.recordSuccess(packages, docCount = 20, took = Duration.ofSeconds(2))
        metrics.recordFailure(packages)

        assertEquals(10.0, gauge("klibs.search.index.docs", "project"))
        assertEquals(20.0, gauge("klibs.search.index.docs", "package"))
        assertNull(registry.find("klibs.search.index.sync.failures").tag("index", "project").counter())
        assertEquals(1.0, failures("package"))
    }

    private fun gauge(name: String, index: String): Double =
        registry.get(name).tag("index", index).gauge().value()

    private fun syncSeconds(index: String): Double = registry.get("klibs.search.index.sync.duration")
        .tag("index", index).timer().totalTime(TimeUnit.SECONDS)

    private fun syncCount(index: String): Long =
        registry.get("klibs.search.index.sync.duration").tag("index", index).timer().count()

    private fun failures(index: String): Double =
        registry.get("klibs.search.index.sync.failures").tag("index", index).counter().count()

    private fun spec(base: String) = OpenSearchIndexSpec(
        base = base,
        settings = "{}",
        mappings = "{}",
        sql = "select 1",
    ) { it.toString() }
}
