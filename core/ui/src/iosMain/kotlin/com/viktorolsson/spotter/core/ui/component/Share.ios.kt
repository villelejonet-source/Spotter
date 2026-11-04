package com.viktorolsson.spotter.core.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asSkiaBitmap
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import platform.Foundation.NSData
import platform.Foundation.create
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIImage
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.UIKit.popoverPresentationController

// iOS shares through UIActivityViewController, presented over whatever screen is on top. On an
// iPad the sheet is a popover and must be anchored, here to that screen's view.
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
@Composable
actual fun rememberImageSharer(): ImageSharer = remember {
    ImageSharer { image, text ->
        val png = withContext(Dispatchers.Default) {
            Image.makeFromBitmap(image.asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)?.bytes
        } ?: error("The image couldn't be encoded")
        val data = png.usePinned { NSData.create(bytes = it.addressOf(0), length = png.size.toULong()) }
        val top = topViewController() ?: error("No screen to share from")
        val sheet = UIActivityViewController(listOf(UIImage(data = data), text), null)
        sheet.popoverPresentationController?.sourceView = top.view
        top.presentViewController(sheet, animated = true, completion = null)
    }
}

private fun topViewController(): UIViewController? {
    val window = UIApplication.sharedApplication.connectedScenes
        .filterIsInstance<UIWindowScene>()
        .flatMap { it.windows.filterIsInstance<UIWindow>() }
        .firstOrNull { it.isKeyWindow() }
    var top = window?.rootViewController ?: return null
    while (true) top = top.presentedViewController ?: return top
}
