package com.antigravity.permissiontest.utils

import android.content.Context
import android.util.Log
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader

object RootGranter {

    private const val TAG = "RootGranter"

    fun isRootAvailable(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec("su")
            val os = DataOutputStream(process.outputStream)
            os.writeBytes("exit\n")
            os.flush()
            process.waitFor() == 0
        } catch (e: Exception) {
            false
        }
    }

    fun getAdbCommands(context: Context): List<String> {
        val pkg = context.packageName
        return listOf(
            "# 1. Write Secure Settings",
            "adb shell pm grant $pkg android.permission.WRITE_SECURE_SETTINGS",
            "",
            "# 2. App Usage Data (UsageStats)",
            "adb shell appops set $pkg GET_USAGE_STATS allow",
            "",
            "# 3. Install Unknown Apps",
            "adb shell appops set $pkg REQUEST_INSTALL_PACKAGES allow",
            "",
            "# 4. Restricted Settings Bypass (Android 13/14/15/16)",
            "adb shell appops set $pkg ACCESS_RESTRICTED_SETTINGS allow",
            "",
            "# 5. Enable Notification Listener Service",
            "adb shell cmd notification set_listeners $pkg/$pkg.services.TestNotificationListenerService",
            "",
            "# 6. Enable Accessibility Service",
            "adb shell settings put secure enabled_accessibility_services $pkg/$pkg.services.TestAccessibilityService",
            "adb shell settings put secure accessibility_enabled 1"
        )
    }

    fun grantAllWithRoot(context: Context): Pair<Boolean, String> {
        val pkg = context.packageName
        val commands = listOf(
            "pm grant $pkg android.permission.WRITE_SECURE_SETTINGS",
            "appops set $pkg GET_USAGE_STATS allow",
            "appops set $pkg REQUEST_INSTALL_PACKAGES allow",
            "appops set $pkg ACCESS_RESTRICTED_SETTINGS allow",
            "cmd notification set_listeners $pkg/$pkg.services.TestNotificationListenerService",
            "settings put secure enabled_accessibility_services $pkg/$pkg.services.TestAccessibilityService",
            "settings put secure accessibility_enabled 1"
        )

        return executeSuCommands(commands)
    }

    private fun executeSuCommands(commands: List<String>): Pair<Boolean, String> {
        val output = StringBuilder()
        var success = false
        try {
            val process = Runtime.getRuntime().exec("su")
            val os = DataOutputStream(process.outputStream)
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val errorReader = BufferedReader(InputStreamReader(process.errorStream))

            for (cmd in commands) {
                os.writeBytes("$cmd\n")
                Log.d(TAG, "SU Executing: $cmd")
            }
            os.writeBytes("exit\n")
            os.flush()

            val exitCode = process.waitFor()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }
            while (errorReader.readLine().also { line = it } != null) {
                output.append("[ERR] ").append(line).append("\n")
            }

            success = (exitCode == 0)
            if (success) {
                output.append("All permissions successfully granted via Root (SU)!\n")
            } else {
                output.append("Process exited with code: $exitCode\n")
            }
        } catch (e: Exception) {
            output.append("Root execution error: ${e.message}\n")
            Log.e(TAG, "Root execution failed", e)
        }
        return Pair(success, output.toString())
    }
}
