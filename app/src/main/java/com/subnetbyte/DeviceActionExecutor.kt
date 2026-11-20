
package com.subnetbyte

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.Settings
import java.util.Locale

/** Executes Android-supported, user-facing actions. */
object DeviceActionExecutor {

    data class Result(val success: Boolean, val message: String)

    fun execute(context: Context, command: AssistantCommand): Result = try {
        when (command) {
            is AssistantCommand.OpenApp -> openTarget(context, command.app)
            is AssistantCommand.Alarm -> setAlarm(context, command.time)
            else -> Result(false, "This action is not a device action.")
        }
    } catch (e: Exception) {
        Result(
            false,
            "Android could not complete that action: ${e.localizedMessage ?: "permission or system restriction"}"
        )
    }

    private fun openTarget(context: Context, raw: String): Result {
        val target = raw.lowercase(Locale.ROOT).trim().removePrefix("the ")

        val settingsIntent: Intent = when {
            target in setOf("settings", "phone settings", "system settings") ->
                Intent(Settings.ACTION_SETTINGS)

            target.contains("wifi") || target.contains("wi-fi") ->
                panelOrSettings("android.settings.panel.action.WIFI", Settings.ACTION_WIFI_SETTINGS)

            target.contains("bluetooth") ->
                panelOrSettings("android.settings.panel.action.BLUETOOTH", Settings.ACTION_BLUETOOTH_SETTINGS)

            target.contains("internet") || target.contains("network") ||
                target.contains("mobile data") || target.contains("airplane") ||
                target.contains("hotspot") ->
                panelOrSettings(
                    "android.settings.panel.action.INTERNET_CONNECTIVITY",
                    Settings.ACTION_WIRELESS_SETTINGS
                )

            target.contains("display") ->
                Intent(Settings.ACTION_DISPLAY_SETTINGS)

            target.contains("sound") || target.contains("volume") ->
                Intent(Settings.ACTION_SOUND_SETTINGS)

            target.contains("battery") ->
                Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)

            target.contains("location") || target.contains("gps") ->
                Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)

            target.contains("notifications") ->
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

            target.contains("app info") ->
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:${context.packageName}")
                )

            target.contains("camera") ->
                return launchByPackageOrAction(
                    context,
                    target,
                    "com.android.camera",
                    Intent("android.media.action.IMAGE_CAPTURE")
                )

            target.contains("phone") || target.contains("dialer") ->
                Intent(Intent.ACTION_DIAL)

            target.contains("message") || target.contains("sms") ->
                Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_APP_MESSAGING)

            target.contains("browser") || target.contains("chrome") ->
                return launchByPackageOrAction(
                    context,
                    target,
                    "com.android.chrome",
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com"))
                )

            target.contains("whatsapp business") ->
                return launchPackage(context, "com.whatsapp.w4b", "WhatsApp Business")

            target.contains("whatsapp") ->
                return launchPackage(context, "com.whatsapp", "WhatsApp")

            target.contains("youtube") ->
                return launchPackage(context, "com.google.android.youtube", "YouTube")

            target.contains("gmail") ->
                return launchPackage(context, "com.google.android.gm", "Gmail")

            target.contains("maps") || target.contains("google map") ->
                return launchPackage(context, "com.google.android.apps.maps", "Google Maps")

            target.contains("play store") ->
                return launchPackage(context, "com.android.vending", "Play Store")

            else ->
                return launchByVisibleAppName(context, target)
        }

        settingsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(settingsIntent)

        return Result(true, "Opening ${raw.trim()}.")
    }

    private fun panelOrSettings(panel: String, fallback: String): Intent {
        return if (android.os.Build.VERSION.SDK_INT >= 29) {
            Intent(panel)
        } else {
            Intent(fallback)
        }
    }

    private fun launchByPackageOrAction(
        context: Context,
        name: String,
        packageName: String,
        fallback: Intent
    ): Result {
        val launch = context.packageManager.getLaunchIntentForPackage(packageName)
        val intent = launch ?: fallback

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)

        return Result(true, "Opening ${name.trim()}.")
    }

    private fun launchPackage(
        context: Context,
        packageName: String,
        label: String
    ): Result {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            ?: return Result(false, "$label is not installed on this phone.")

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)

        return Result(true, "Opening $label.")
    }

    private fun launchByVisibleAppName(
        context: Context,
        target: String
    ): Result {
        val pm = context.packageManager

        val queryIntent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)

        val matches = pm.queryIntentActivities(queryIntent, 0)

        val exact = matches.firstOrNull {
            it.loadLabel(pm).toString().trim().equals(target, true)
        }

        val partial = exact ?: matches.firstOrNull {
            it.loadLabel(pm).toString().contains(target, true)
        }

        val activity = partial?.activityInfo
            ?: return Result(
                false,
                "I couldn't find an installed app named $target. Try its exact app name."
            )

        val launch = pm.getLaunchIntentForPackage(activity.packageName)
            ?: Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setClassName(activity.packageName, activity.name)

        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launch)

        return Result(true, "Opening ${activity.loadLabel(pm)}.")
    }

    private fun setAlarm(context: Context, time: String): Result {
        val match = Regex(
            "(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?",
            RegexOption.IGNORE_CASE
        ).find(time)
            ?: return Result(
                false,
                "Say the alarm time, for example set alarm for 7 AM."
            )

        var hour = match.groupValues[1].toIntOrNull()
            ?: return Result(false, "I couldn't understand that alarm time.")

        val minute = match.groupValues[2].ifBlank { "0" }.toIntOrNull() ?: 0

        if (hour !in 1..12 || minute !in 0..59) {
            return Result(false, "Please provide a valid time.")
        }

        when (match.groupValues[3].lowercase(Locale.ROOT)) {
            "pm" -> if (hour < 12) hour += 12
            "am" -> if (hour == 12) hour = 0
        }

        val intent = Intent(AlarmClock.ACTION_SET_ALARM)
            .putExtra(AlarmClock.EXTRA_HOUR, hour)
            .putExtra(AlarmClock.EXTRA_MINUTES, minute)
            .putExtra(AlarmClock.EXTRA_MESSAGE, "Subnet alarm")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        if (intent.resolveActivity(context.packageManager) == null) {
            return Result(false, "No alarm app is available on this phone.")
        }

        context.startActivity(intent)
        return Result(true, "Opening the clock app to set your alarm.")
    }
}
