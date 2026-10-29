package io.embodit.memorypalace.ui.palace.components

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.isSpecified

/**
 * QA q8-1 (profile D16, no truncated text): text in a slot of fixed size first shrinks, in steps down to [minScale]
 * of its style, until it fits [maxLines]; only a text still too long after that ends in an ellipsis. It is drawn
 * once settled, so the oversized first layout never shows. [oneLineFirst]: a name tries one smaller line before it
 * wraps (a name broken mid-word reads worse than a smaller one).
 */
@Composable
internal fun FitText(text: String, style: TextStyle, color: Color, modifier: Modifier = Modifier, maxLines: Int = 1, minScale: Float = 0.6f, oneLineFirst: Boolean = false) {
    var lines by remember(text, style, maxLines) { mutableIntStateOf(if (oneLineFirst) 1 else maxLines) }
    var scale by remember(text, style, maxLines) { mutableFloatStateOf(1f) }
    var settled by remember(text, style, maxLines) { mutableStateOf(false) }
    val sized =
        if (scale < 1f) style.copy(fontSize = style.fontSize * scale, lineHeight = if (style.lineHeight.isSpecified) style.lineHeight * scale else style.lineHeight)
        else style
    BasicText(
        text = text,
        modifier = modifier.drawWithContent { if (settled) drawContent() },
        style = sized.copy(color = color),
        overflow = TextOverflow.Ellipsis,
        maxLines = lines,
        onTextLayout = { r ->
            when {
                !r.hasVisualOverflow -> settled = true
                scale > minScale -> scale = maxOf(minScale, scale - 0.08f)
                lines < maxLines -> {
                    lines = maxLines
                    scale = 1f
                }
                else -> settled = true
            }
        },
    )
}
