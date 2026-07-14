package com.nxteam.nxstore.install

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.widget.Toast

class InstallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                confirm?.let { context.startActivity(it) }
            }
            PackageInstaller.STATUS_SUCCESS -> {
                Toast.makeText(context, "Installed", Toast.LENGTH_SHORT).show()
            }
            else -> {
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                Toast.makeText(context, "Install failed: $message", Toast.LENGTH_LONG).show()
            }
        }
    }

    companion object {
        const val ACTION_INSTALL_STATUS = "com.nxteam.nxstore.INSTALL_STATUS"
    }
}
