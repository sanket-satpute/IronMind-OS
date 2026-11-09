package com.sanket_satpute_20.ironmind.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sanket_satpute_20.ironmind.data.local.entity.AppUsageObservationCheckpointEntity
import com.sanket_satpute_20.ironmind.data.local.entity.ObservationEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.system.measureNanoTime
import kotlin.system.measureTimeMillis

/**
 * Phase 3.6.6.4R: Section 3 — Room Database Performance, Schema, & Index Benchmark
 *
 * Evaluates core app usage query performance, query plans, and indexing:
 * 1. Per-user observations by type and time range.
 * 2. Per-user checkpoint lookup.
 * 3. Historical observation bounds (latest observation).
 * 4. Multi-type range aggregation.
 *
 * Compares:
 * - Unindexed schema (version 29 baseline) vs Indexed schema (with composite index).
 * - SQLite EXPLAIN QUERY PLAN analysis.
 * - Insertion and query latencies at scale (10,000 synthetic rows).
 */
@RunWith(Jdk25CompatibleRobolectricTestRunner::class)
@Config(sdk = [34])
class ObservationDatabasePerformanceTest {

    private lateinit var database: IronMindDatabase
    private val userId = "user_perf_benchmark"

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, IronMindDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun explainQueryPlan(query: String, args: Array<String> = emptyArray()): List<String> {
        val db = database.openHelper.readableDatabase
        val cursor = db.query("EXPLAIN QUERY PLAN $query", args)
        val plan = mutableListOf<String>()
        while (cursor.moveToNext()) {
            val detail = cursor.getString(cursor.getColumnIndexOrThrow("detail"))
            plan.add(detail)
        }
        cursor.close()
        return plan
    }

    @Test
    fun testExplainQueryPlan_baselineVsIndexed_provesCompositeIndexEliminatesTableScanAndTempBTree() {
        val timeWindowSql = "SELECT * FROM observations WHERE userId = ? AND type = ? AND occurredAt >= ? AND occurredAt < ? ORDER BY occurredAt ASC"
        val checkpointSql = "SELECT * FROM app_usage_observation_checkpoints WHERE userId = ?"
        val latestObsSql = "SELECT * FROM observations WHERE userId = ? AND type = ? ORDER BY occurredAt DESC LIMIT 1"

        // 1. Checkpoint Lookup Query Plan (Uses PRIMARY KEY(userId))
        val cpPlan = explainQueryPlan(checkpointSql, arrayOf(userId))
        println("=== Checkpoint Query Plan ===")
        cpPlan.forEach { println("  $it") }
        assertTrue(
            "Checkpoint lookup must use PRIMARY KEY index",
            cpPlan.any { it.contains("USING PRIMARY KEY") || it.contains("USING INDEX") || it.contains("SEARCH") }
        )

        // Ensure baseline is unindexed by dropping the entity-level index if present
        val db = database.openHelper.writableDatabase
        db.execSQL("DROP INDEX IF EXISTS index_observations_userId_type_occurredAt")

        // 2. Unindexed Time Window Query Plan (Baseline)
        val unindexedPlan = explainQueryPlan(timeWindowSql, arrayOf(userId, "APP_USAGE_SESSION", "1000", "2000"))
        println("=== Baseline Unindexed Time Window Query Plan ===")
        unindexedPlan.forEach { println("  $it") }
        assertTrue(
            "Unindexed query must perform full table SCAN",
            unindexedPlan.any { it.contains("SCAN") }
        )

        // 3. Create Composite Index on (userId, type, occurredAt)
        db.execSQL("CREATE INDEX IF NOT EXISTS index_observations_userId_type_occurredAt ON observations(userId, type, occurredAt)")

        // 4. Indexed Time Window Query Plan
        val indexedPlan = explainQueryPlan(timeWindowSql, arrayOf(userId, "APP_USAGE_SESSION", "1000", "2000"))
        println("=== Indexed Time Window Query Plan ===")
        indexedPlan.forEach { println("  $it") }
        assertTrue(
            "Indexed query must perform SEARCH USING INDEX index_observations_userId_type_occurredAt",
            indexedPlan.any { it.contains("SEARCH") && it.contains("index_observations_userId_type_occurredAt") }
        )
        assertTrue(
            "Indexed query must NOT require TEMP B-TREE for ORDER BY",
            indexedPlan.none { it.contains("TEMP B-TREE") }
        )

        // 5. Indexed Latest Observation Query Plan
        val indexedLatestPlan = explainQueryPlan(latestObsSql, arrayOf(userId, "APP_USAGE_SESSION"))
        println("=== Indexed Latest Observation Query Plan ===")
        indexedLatestPlan.forEach { println("  $it") }
        assertTrue(
            "Latest observation query must use composite index",
            indexedLatestPlan.any { it.contains("SEARCH") && it.contains("index_observations_userId_type_occurredAt") }
        )
    }

    @Test
    fun testBenchmark_10000Observations_measuresInsertionAndQueryPerformance() = runBlocking {
        val totalRows = 10_000
        val baseTime = 1700000000000L
        val obsDao = database.observationDao()
        val cpDao = database.appUsageObservationCheckpointDao()

        // 1. Measure Checkpoint Insertion & Point Lookup
        val cpEntity = AppUsageObservationCheckpointEntity(
            userId = userId,
            lastCollectedIntervalEndMs = baseTime + 100_000L,
            activePackageName = "com.benchmark.app",
            activeSessionStartTimeMs = baseTime,
            updatedAt = baseTime + 100_000L,
            schemaVersion = 1
        )
        cpDao.upsertCheckpoint(cpEntity)
        val cpLookupNanos = measureNanoTime {
            val retrieved = cpDao.getCheckpoint(userId)
            assertNotNull(retrieved)
        }
        println("Checkpoint Point Lookup Latency: ${cpLookupNanos / 1000} µs")

        // 2. Measure Batch Insertion Time for 10,000 synthetic observations
        val syntheticObservations = (1..totalRows).map { i ->
            val ownerId = if (i % 2 == 0) userId else "other_user_${i % 10}"
            val occurred = baseTime + (i * 60_000L) // 1 minute apart
            ObservationEntity(
                id = "obs_bench_$i",
                userId = ownerId,
                type = if (i % 3 == 0) "APP_USAGE_SESSION" else "OTHER_TYPE",
                source = "APP_USAGE",
                occurredAt = occurred,
                recordedAt = baseTime + 10_000_000L,
                subjectId = null,
                value = "com.test.pkg_${i % 20}",
                context = "duration_ms=60000",
                confidence = null,
                provenanceSource = "APP_USAGE",
                provenanceSourceReference = "UsageStatsManager",
                provenanceCapturedAt = baseTime + 10_000_000L,
                schemaVersion = 1
            )
        }

        val insertMillis = measureTimeMillis {
            database.runInTransaction {
                for (obs in syntheticObservations) {
                    obsDao.insertObservation(obs)
                }
            }
        }
        println("Inserted $totalRows observations in: ${insertMillis}ms (rate: ${(totalRows * 1000L) / insertMillis} rows/sec)")

        // Ensure baseline is unindexed by dropping the entity-level index if present
        database.openHelper.writableDatabase.execSQL("DROP INDEX IF EXISTS index_observations_userId_type_occurredAt")

        // 3. Measure Unindexed Query Execution Time (average over 30 runs)
        val queryStart = baseTime + 100 * 60_000L
        val queryEnd = baseTime + 500 * 60_000L

        // Warmup
        obsDao.getObservationsForTimeWindow(userId, "APP_USAGE_SESSION", queryStart, queryEnd)

        val unindexedRuns = 30
        var totalUnindexedTimeNanos = 0L
        repeat(unindexedRuns) {
            totalUnindexedTimeNanos += measureNanoTime {
                val results = obsDao.getObservationsForTimeWindow(userId, "APP_USAGE_SESSION", queryStart, queryEnd)
                assertTrue(results.isNotEmpty())
            }
        }
        val avgUnindexedMicros = (totalUnindexedTimeNanos / unindexedRuns) / 1000
        println("Unindexed Query Latency (Mean of $unindexedRuns runs): ${avgUnindexedMicros} µs")

        // 4. Create Composite Index
        val indexCreationMillis = measureTimeMillis {
            database.openHelper.writableDatabase.execSQL(
                "CREATE INDEX IF NOT EXISTS index_observations_userId_type_occurredAt ON observations(userId, type, occurredAt)"
            )
        }
        println("Created composite index on $totalRows rows in: ${indexCreationMillis}ms")

        // 5. Measure Indexed Query Execution Time (average over 30 runs)
        // Warmup
        obsDao.getObservationsForTimeWindow(userId, "APP_USAGE_SESSION", queryStart, queryEnd)

        val indexedRuns = 30
        var totalIndexedTimeNanos = 0L
        repeat(indexedRuns) {
            totalIndexedTimeNanos += measureNanoTime {
                val results = obsDao.getObservationsForTimeWindow(userId, "APP_USAGE_SESSION", queryStart, queryEnd)
                assertTrue(results.isNotEmpty())
            }
        }
        val avgIndexedMicros = (totalIndexedTimeNanos / indexedRuns) / 1000
        println("Indexed Query Latency (Mean of $indexedRuns runs): ${avgIndexedMicros} µs")

        println("Speedup Factor: ${String.format("%.2f", avgUnindexedMicros.toDouble() / avgIndexedMicros.toDouble())}x")
        assertTrue("Indexed query must complete faster than unindexed query", avgIndexedMicros <= avgUnindexedMicros)
    }
}
