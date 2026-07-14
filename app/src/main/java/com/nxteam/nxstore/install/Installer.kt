package com.nxteam.nxstore.install

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import com.nxteam.nxstore.network.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object Installer {

    sealed interface Result {
        data object PendingUserAction : Result
        data class Failed(val message: String) : Result
    }

    suspend fun downloadApk(
        context: Context,
        url: String,
        packageName: String,
        onProgress: (Float) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "apks").apply { mkdirs() }
        val outFile = File(dir, "$packageName.apk")
        Http.get(url).use { resp ->
            if (!resp.isSuccessful) throw java.io.IOException("Download failed HTTP ${resp.code}")
            val body = resp.body ?: throw java.io.IOException("Empty download body")
            val total = body.contentLength()
            body.byteStream().use { input ->
                outFile.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var read: Int
                    var downloaded = 0L
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        downloaded += read
                        if (total > 0) onProgress(downloaded.toFloat() / total.toFloat())
                    }
                }
            }
        }
        outFile
    }

    suspend fun install(
        context: Context,
        apk: File,
        packageName: String
    ): Result = withContext(Dispatchers.IO) {
        try {
            val installer = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL
            )
            params.setAppPackageName(packageName)
            val sessionId = installer.createSession(params)
            installer.openSession(sessionId).use { session ->
                apk.inputStream().use { input ->
                    session.openWrite("base.apk", 0, apk.length()).use { output ->
                        input.copyTo(output)
                        session.fsync(output)
                    }
                }
                val intent = Intent(context, InstallReceiver::class.java).apply {
                    action = InstallReceiver.ACTION_INSTALL_STATUS
                }
                val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                else PendingIntent.FLAG_UPDATE_CURRENT
                val pending = PendingIntent.getBroadcast(context, sessionId, intent, flags)
                session.commit(pending.intentSender)
            }
            Result.PendingUserAction
        } catch (e: Exception) {
            Result.Failed(e.message ?: "Install failed")
        }
    }
}
