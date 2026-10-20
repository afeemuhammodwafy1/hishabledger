package com.wafyro.hishabledger

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.text.SimpleDateFormat
import java.util.*

/** Opens the feedback page in the phone's own browser (never inside the app). */
fun openFeedbackPage(context: Context): Boolean = try {
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(FEEDBACK_URL)).addCategory(Intent.CATEGORY_BROWSABLE))
    true
} catch (_: Exception) { false }

/**
 * Writes the backup to a private cache file and hands it to the Google Drive app's upload screen.
 * Returns true when Drive was opened directly, false when the generic share sheet was used instead.
 */
fun shareBackupToDrive(context: Context, json: String): Boolean {
    val dir = context.cacheDir.resolve("backups").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
    val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
    val file = dir.resolve("HishabLedger_Backup_$stamp.json")
    file.writeText(json, Charsets.UTF_8)
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "application/json"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, file.name)
        clipData = android.content.ClipData.newRawUri(file.name, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val driveIntent = Intent(send).setPackage(DRIVE_PACKAGE)
    val hasDrive = driveIntent.resolveActivity(context.packageManager) != null
    context.startActivity(if (hasDrive) driveIntent else Intent.createChooser(send, "Save backup"))
    return hasDrive
}
