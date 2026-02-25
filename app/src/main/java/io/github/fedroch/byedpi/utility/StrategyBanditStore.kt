package io.github.fedroch.byedpi.utility

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class StrategyBanditStats(
    var trials: Int = 0,
    var rewardSum: Double = 0.0,
    var successSum: Double = 0.0,
    var latencySumMs: Double = 0.0
) {
    val avgReward: Double
        get() = if (trials > 0) rewardSum / trials else 0.0

    val avgSuccessRate: Double
        get() = if (trials > 0) successSum / trials else 0.0

    val avgLatencyMs: Double
        get() = if (trials > 0) latencySumMs / trials else 0.0
}

class StrategyBanditStore(context: Context) : SQLiteOpenHelper(
    context,
    DB_NAME,
    null,
    DB_VERSION
) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE strategy_stats (
                context_key TEXT NOT NULL,
                command TEXT NOT NULL,
                trials INTEGER NOT NULL DEFAULT 0,
                reward_sum REAL NOT NULL DEFAULT 0,
                success_sum REAL NOT NULL DEFAULT 0,
                latency_sum_ms REAL NOT NULL DEFAULT 0,
                updated_at INTEGER NOT NULL,
                PRIMARY KEY (context_key, command)
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE strategy_attempts (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                ts INTEGER NOT NULL,
                context_key TEXT NOT NULL,
                command TEXT NOT NULL,
                success_rate REAL NOT NULL,
                latency_ms REAL NOT NULL,
                reward REAL NOT NULL,
                success_count INTEGER NOT NULL,
                total_requests INTEGER NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL("CREATE INDEX idx_attempts_context_ts ON strategy_attempts(context_key, ts)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL(
                """
                CREATE TABLE strategy_attempts (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    ts INTEGER NOT NULL,
                    context_key TEXT NOT NULL,
                    command TEXT NOT NULL,
                    success_rate REAL NOT NULL,
                    latency_ms REAL NOT NULL,
                    reward REAL NOT NULL,
                    success_count INTEGER NOT NULL,
                    total_requests INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX idx_attempts_context_ts ON strategy_attempts(context_key, ts)")
        }
    }

    fun loadStatsForCommands(contextKey: String, commands: List<String>): Map<String, StrategyBanditStats> {
        if (commands.isEmpty()) return emptyMap()

        val db = readableDatabase
        val result = mutableMapOf<String, StrategyBanditStats>()

        commands.distinct().forEach { command ->
            db.query(
                "strategy_stats",
                arrayOf("trials", "reward_sum", "success_sum", "latency_sum_ms"),
                "context_key = ? AND command = ?",
                arrayOf(contextKey, command),
                null,
                null,
                null
            ).use { cursor ->
                if (cursor.moveToFirst()) {
                    result[command] = StrategyBanditStats(
                        trials = cursor.getInt(0),
                        rewardSum = cursor.getDouble(1),
                        successSum = cursor.getDouble(2),
                        latencySumMs = cursor.getDouble(3)
                    )
                }
            }
        }

        return result
    }

    fun updateStatsAndLogAttempt(
        contextKey: String,
        command: String,
        reward: Double,
        successRate: Double,
        latencyMs: Double,
        successCount: Int,
        totalRequests: Int
    ) {
        val now = System.currentTimeMillis()
        val db = writableDatabase

        db.beginTransaction()
        try {
            val rowsUpdated = db.compileStatement(
                """
                UPDATE strategy_stats
                SET
                    trials = trials + 1,
                    reward_sum = reward_sum + ?,
                    success_sum = success_sum + ?,
                    latency_sum_ms = latency_sum_ms + ?,
                    updated_at = ?
                WHERE context_key = ? AND command = ?
                """.trimIndent()
            )
            rowsUpdated.bindDouble(1, reward)
            rowsUpdated.bindDouble(2, successRate)
            rowsUpdated.bindDouble(3, latencyMs)
            rowsUpdated.bindLong(4, now)
            rowsUpdated.bindString(5, contextKey)
            rowsUpdated.bindString(6, command)
            val updated = rowsUpdated.executeUpdateDelete()

            if (updated == 0) {
                db.insert(
                    "strategy_stats",
                    null,
                    ContentValues().apply {
                        put("context_key", contextKey)
                        put("command", command)
                        put("trials", 1)
                        put("reward_sum", reward)
                        put("success_sum", successRate)
                        put("latency_sum_ms", latencyMs)
                        put("updated_at", now)
                    }
                )
            }

            db.insert(
                "strategy_attempts",
                null,
                ContentValues().apply {
                    put("ts", now)
                    put("context_key", contextKey)
                    put("command", command)
                    put("success_rate", successRate)
                    put("latency_ms", latencyMs)
                    put("reward", reward)
                    put("success_count", successCount)
                    put("total_requests", totalRequests)
                }
            )

            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    companion object {
        private const val DB_NAME = "strategy_bandit.db"
        private const val DB_VERSION = 2
    }
}
