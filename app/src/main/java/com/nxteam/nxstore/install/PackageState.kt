package com.nxteam.nxstore.install

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri

object PackageState {

    fun installedVersion(context: Context, packageName: String): String? = try {
        val info = context.packageManager.getPackageInfo(packageName, 0)
        info.versionName ?: ""
    } catch (e: PackageManager.NameNotFoundException) {
        null
    } catch (e: Exception) {
        null
    }

    fun isInstalled(context: Context, packageName: String): Boolean =
        installedVersion(context, packageName) != null

    fun open(context: Context, packageName: String): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun uninstall(context: Context, packageName: String): Boolean {
        val intent = Intent(Intent.ACTION_DELETE).apply {
            data = Uri.parse("package:$packageName")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }
}
