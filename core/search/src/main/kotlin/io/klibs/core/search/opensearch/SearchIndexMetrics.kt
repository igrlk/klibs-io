package io.klibs.core.search.opensearch

import io.klibs.core.search.dto.opensearch.OpenSearchIndexSpec
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tags
import io.micrometer.core.instrument.Timer
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Freshness and outcome of the OpenSearch index rebuild.
 */
@Component
@ConditionalOnProperty("klibs.search.opensearch.enabled", havingValue = "true")
class SearchIndexMetrics(
    private val registry: MeterRegistry,
) {

    private val states = ConcurrentHashMap<String, IndexState>()

    fun recordSuccess(spec: OpenSearchIndexSpec, docCount: Int, took: Duration) {
        val state = stateOf(spec)
        state.docs.set(docCount.toLong())
        state.lastSuccess.set(Instant.now())
        Timer.builder(SYNC_DURATION)
            .description("Klibs: Time to rebuild one index and swap its alias")
            .tags(tagsOf(spec))
            .register(registry)
            .record(took)
    }

    fun recordFailure(spec: OpenSearchIndexSpec) {
        Counter.builder(SYNC_FAILURES)
            .description("Klibs: Index rebuilds that failed")
            .tags(tagsOf(spec))
            .register(registry)
            .increment()
    }

    private fun stateOf(spec: OpenSearchIndexSpec): IndexState = states.computeIfAbsent(spec.alias) {
        IndexState().also { state ->
            Gauge.builder(INDEX_AGE, state) { it.ageSeconds() }
                .description("Klibs: Seconds since this pod last rebuilt the index")
                .tags(tagsOf(spec))
                .register(registry)
            Gauge.builder(INDEX_DOCS, state) { it.docs.get().toDouble() }
                .description("Klibs: Documents written by the last rebuild")
                .tags(tagsOf(spec))
                .register(registry)
        }
    }

    private class IndexState {
        val lastSuccess = AtomicReference<Instant>()
        val docs = AtomicLong()

        fun ageSeconds(): Double = lastSuccess.get()
            ?.let { Duration.between(it, Instant.now()).seconds.toDouble() }
            ?: Double.NaN
    }

    private companion object {
        fun tagsOf(spec: OpenSearchIndexSpec) = Tags.of("index", spec.base, "hash", spec.hash)

        const val SYNC_FAILURES = "klibs.search.index.sync.failures"
        const val INDEX_AGE = "klibs.search.index.age.seconds"
        const val INDEX_DOCS = "klibs.search.index.docs"
        const val SYNC_DURATION = "klibs.search.index.sync.duration"
    }
}
