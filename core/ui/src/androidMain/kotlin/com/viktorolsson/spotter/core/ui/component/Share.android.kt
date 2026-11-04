package com.viktorolsson.spotter.core.ui.component

import android.content.ClipData
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

// Android hands another app a file only through a content:// URI: the image is written to the
// cache's share/ folder and served by the FileProvider the app declares (app/ manifest,
// res/xml/share_paths.xml), readable only by the app the person picks.
@Composable
actual fun rememberImageSharer(): ImageSharer {
    val context = LocalContext.current
    return remember(context) {
        ImageSharer { image, text ->
            val file = withContext(Dispatchers.IO) {
                // A hardware bitmap can't be read back directly; a software copy can.
                val bitmap = image.asAndroidBitmap().let { if (it.config == Bitmap.Config.HARDWARE) it.copy(Bitmap.Config.ARGB_8888, false) else it }
                val dir = File(context.cacheDir, SHARE_DIR).apply { mkdirs() }
                // A file of its own each time, so an app still reading the last one isn't
                // handed this one; older ones are cleared once nothing can still be reading them.
                val now = System.currentTimeMillis()
                dir.listFiles()?.filter { now - it.lastModified() > KEEP_MILLIS }?.forEach { it.delete() }
                File(dir, "spotter-$now.png").apply {
                    outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                }
            }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.share", file)
            val send = Intent(Intent.ACTION_SEND)
                .setType("image/png")
                .putExtra(Intent.EXTRA_STREAM, uri)
                .putExtra(Intent.EXTRA_TEXT, text)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            // The chooser passes the read grant on only for a URI in the clip data.
            send.clipData = ClipData.newRawUri(null, uri)
            context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}

private const val SHARE_DIR = "share"
private const val KEEP_MILLIS = 60 * 60 * 1000L
