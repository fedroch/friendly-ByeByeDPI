package io.github.fedroch.byedpi.utility

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import kotlin.math.exp
import kotlin.math.ln
import kotlin.random.Random

data class StrategyEvaluation(
    val reward: Double,
    val successRate: Double,
    val latencyMs: Double
)

class StrategyBanditSelector(private val context: Context) {

    private val store = StrategyBanditStore(context)
    private val random = Random(System.currentTimeMillis())

    fun buildContextKey(proxyIp: String, proxyPort: Int): String {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork
        val capabilities = network?.let { connectivityManager.getNetworkCapabilities(it) }

        val transport = when {
            capabilities == null -> "unknown"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "vpn"
            else -> "other"
        }

        val metered = connectivityManager.isActiveNetworkMetered
        val validated = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
        } else {
            true
        }

        return "transport=$transport|metered=$metered|validated=$validated|proxy=$proxyIp:$proxyPort"
    }

    fun createSession(contextKey: String, commands: List<String>): Session {
        val stats = store.loadStatsForCommands(contextKey, commands).toMutableMap()
        return Session(contextKey, stats)
    }

    inner class Session(
        private val contextKey: String,
        private val statsByCommand: MutableMap<String, StrategyBanditStats>
    ) {

        fun pickNextIndex(commands: List<String>, remainingIndexes: Set<Int>): Int {
            var bestIndex = remainingIndexes.first()
            var bestScore = Double.NEGATIVE_INFINITY

            for (index in remainingIndexes) {
                val command = commands[index]
                val stats = statsByCommand[command]
                val score = if (stats == null || stats.trials == 0) {
                    1.2 + random.nextDouble(0.0, 0.25)
                } else {
                    val exploitation = stats.avgReward
                    val exploration = 0.5 * ln((commands.size + 1).toDouble()) / stats.trials
                    val latencyPenalty = (stats.avgLatencyMs / 6000.0).coerceAtMost(0.3)
                    exploitation + exploration - latencyPenalty + random.nextDouble(0.0, 0.05)
                }

                if (score > bestScore) {
                    bestScore = score
                    bestIndex = index
                }
            }

            return bestIndex
        }

        fun evaluate(successCount: Int, totalRequests: Int, elapsedMs: Long): StrategyEvaluation {
            val safeTotal = totalRequests.coerceAtLeast(1)
            val successRate = successCount.toDouble() / safeTotal.toDouble()
            val latencyMs = elapsedMs.toDouble().coerceAtLeast(1.0)

            // Smoothly decreases from ~1.0 for fast responses to near 0.0 for very slow responses.
            val latencyScore = exp(-latencyMs / 5000.0)
            val reward = (successRate * 0.85) + (latencyScore * 0.15)

            return StrategyEvaluation(
                reward = reward.coerceIn(0.0, 1.0),
                successRate = successRate.coerceIn(0.0, 1.0),
                latencyMs = latencyMs
            )
        }

        fun recordResult(
            command: String,
            successCount: Int,
            totalRequests: Int,
            elapsedMs: Long
        ): StrategyEvaluation {
            val evaluation = evaluate(successCount, totalRequests, elapsedMs)

            val stats = statsByCommand.getOrPut(command) { StrategyBanditStats() }
            stats.trials += 1
            stats.rewardSum += evaluation.reward
            stats.successSum += evaluation.successRate
            stats.latencySumMs += evaluation.latencyMs

            store.updateStatsAndLogAttempt(
                contextKey = contextKey,
                command = command,
                reward = evaluation.reward,
                successRate = evaluation.successRate,
                latencyMs = evaluation.latencyMs,
                successCount = successCount,
                totalRequests = totalRequests
            )

            return evaluation
        }
    }
}
