package com.viktorolsson.spotter.core.ui.component

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import com.viktorolsson.spotter.core.ui.resources.Res
import com.viktorolsson.spotter.core.ui.resources.share_action
import com.viktorolsson.spotter.core.ui.theme.Dimens
import com.viktorolsson.spotter.core.ui.theme.Spacing
import org.jetbrains.compose.resources.stringResource

// Share cards: a card on screen is recorded as it draws ([captureForShare]) and handed to the
// phone's share sheet as an image ([rememberImageSharer]).

/** What [captureForShare] last drew, ready to turn into an image. */
class ShareCapture internal constructor(internal val layer: GraphicsLayer) {
    suspend fun image(): ImageBitmap = layer.toImageBitmap()
}

@Composable
fun rememberShareCapture(): ShareCapture = ShareCapture(rememberGraphicsLayer())

/** Draws as usual, recording each frame into [capture] so the card can be shared as it looks. */
fun Modifier.captureForShare(capture: ShareCapture): Modifier = drawWithContent {
    capture.layer.record { this@drawWithContent.drawContent() }
    drawLayer(capture.layer)
}

/** Shares an image with a line of text through the phone's share sheet. */
fun interface ImageSharer {
    suspend fun share(image: ImageBitmap, text: String)
}

/** The phone's share sheet. A failure (no room to write the image, say) is thrown to the caller. */
@Composable
expect fun rememberImageSharer(): ImageSharer

/** "Share", for a card made shareable with [captureForShare]. */
@Composable
fun ShareButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(onClick = onClick, modifier = modifier) {
        Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(Dimens.IconSmall))
        Spacer(Modifier.width(Spacing.sm))
        Text(stringResource(Res.string.share_action))
    }
}
