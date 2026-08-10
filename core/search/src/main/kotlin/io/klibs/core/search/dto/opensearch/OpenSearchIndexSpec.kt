package io.klibs.core.search.dto.opensearch

import com.fasterxml.jackson.databind.node.ObjectNode
import io.klibs.core.search.opensearch.IndexDefinitions
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Everything one index is built from, plus the names derived from it:
 *
 * ```
 * alias      project-a3f9c1e2
 * generation project-a3f9c1e2-20260730t101500
 * ```
 */
class OpenSearchIndexSpec(
    val base: String,
    val settings: String,
    val mappings: String,
    val sql: String,
    val idOf: (ObjectNode) -> String,
) {

    /** Covers everything that shapes a doc, so a contract change lands on a fresh alias. */
    val hash = IndexDefinitions.shortSha256Hex(settings, mappings, sql)

    val alias = "$base-$hash"
    val currentAliasGlob = "$alias-*"

    fun generation(now: Instant = Instant.now()): String = "$alias-${TIMESTAMP_FORMATTER.format(now)}"

    fun aliasMatches(index: String): Boolean = currentAliasWithTimestamp.matches(index)

    fun timestampOf(index: String): Instant? = runCatching {
        LocalDateTime.parse(index.takeLast(TIMESTAMP_LENGTH), TIMESTAMP_FORMATTER).toInstant(ZoneOffset.UTC)
    }.getOrNull()

    private val currentAliasWithTimestamp = Regex("^${Regex.escape(alias)}-\\d{8}t\\d{6}$")

    private companion object {
        private const val TIMESTAMP_LENGTH = 15

        private val TIMESTAMP_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyyMMdd't'HHmmss").withZone(ZoneOffset.UTC)
    }
}
