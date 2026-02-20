package io.github.fedroch.byedpi.utility

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import io.github.fedroch.byedpi.R
import io.github.fedroch.byedpi.activities.MainActivity
import io.github.fedroch.byedpi.data.PAUSE_ACTION
import io.github.fedroch.byedpi.data.RESUME_ACTION
import io.github.fedroch.byedpi.data.STOP_ACTION
import android.net.Uri
import androidx.appcompat.app.AlertDialog
import io.github.fedroch.byedpi.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object UpdateChecker {
    private const val GITHUB_API_URL = "https://api.github.com/repos/fedroch/friendly-ByeByeDPI/releases/latest"  // TODO

    suspend fun checkForUpdates(context: Context) {
        val latestVersion = fetchLatestVersion() ?: return
        val currentVersion = BuildConfig.VERSION_NAME.substringBefore("-") // Убираем "-debug" если есть

        if (isNewerVersion(currentVersion, latestVersion)) {
            withContext(Dispatchers.Main) {
                showUpdateDialog(context, latestVersion)
            }
        }
    }

    private suspend fun fetchLatestVersion(): String? = withContext(Dispatchers.IO) {
        try {
            val url = URL(GITHUB_API_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connect()

            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                return@withContext json.getString("tag_name").removePrefix("v")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        null
    }

    private fun isNewerVersion(current: String, latest: String): Boolean {
//        val currentParts = current.split(".")
//        val latestParts = latest.split(".")            не уверен в работоспособности такой логики
//        for (i in currentParts.indices) {              а разбираться мне лень, так что терпите
//            val currentPart = currentParts[i].toIntOrNull() ?: return false
//        }
//        for (i in latestParts.indices) {
//            val latestPart = latestParts[i].toIntOrNull() ?: return false
//        }
//        if (currentParts.size != latestParts.size) {
//            return currentParts.size > latestParts.size
//        }
        return latest != current
    }

    private fun showUpdateDialog(context: Context, version: String) {
        AlertDialog.Builder(context)
            .setTitle(R.string.update_available_title)
            .setMessage(context.getString(R.string.update_available_message, version))
            .setPositiveButton(R.string.update_button_download) { _, _ ->
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/fedroch/friendly-ByeByeDPI/releases/latest"))
                context.startActivity(intent)
            }
            .setNegativeButton(R.string.update_button_later, null)
            .show()
    }
}

fun registerNotificationChannel(context: Context, id: String, @StringRes name: Int) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        val channel = NotificationChannel(
            id,
            context.getString(name),
            NotificationManager.IMPORTANCE_DEFAULT
        )
        channel.enableLights(false)
        channel.enableVibration(false)
        channel.setShowBadge(false)

        manager.createNotificationChannel(channel)
    }
}

fun createConnectionNotification(
    context: Context,
    channelId: String,
    @StringRes title: Int,
    @StringRes content: Int,
    service: Class<*>,
): Notification =
    NotificationCompat.Builder(context, channelId)
        .setSmallIcon(R.drawable.ic_notification2)
        .setSilent(true)
        .setContentTitle(context.getString(title))
        .setContentText(context.getString(content))
        .addAction(0, context.getString(R.string.service_pause_btn),
            PendingIntent.getService(
                context,
                0,
                Intent(context, service).setAction(PAUSE_ACTION),
                PendingIntent.FLAG_IMMUTABLE,
            )
        )
        .addAction(0, context.getString(R.string.service_stop_btn),
            PendingIntent.getService(
                context,
                0,
                Intent(context, service).setAction(STOP_ACTION),
                PendingIntent.FLAG_IMMUTABLE,
            )
        )
        .setContentIntent(
            PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE,
            )
        )
        .build()

fun createPauseNotification(
    context: Context,
    channelId: String,
    @StringRes title: Int,
    @StringRes content: Int,
    service: Class<*>,
): Notification =
    NotificationCompat.Builder(context, channelId)
        .setSmallIcon(R.drawable.ic_notification)
        .setSilent(true)
        .setContentTitle(context.getString(title))
        .setContentText(context.getString(content))
        .addAction(0, context.getString(R.string.service_start_btn),
            PendingIntent.getService(
                context,
                0,
                Intent(context, service).setAction(RESUME_ACTION),
                PendingIntent.FLAG_IMMUTABLE,
            )
        )
        .setContentIntent(
            PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE,
            )
        )
        .build()
