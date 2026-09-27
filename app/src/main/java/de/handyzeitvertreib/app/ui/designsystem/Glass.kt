package de.handyzeitvertreib.app.ui.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object HzvShapes {
    val card = RoundedCornerShape(28.dp)
    val inner = RoundedCornerShape(20.dp)
    val pill = RoundedCornerShape(50)
}

object HzvSpacing {
    val screen = 20.dp
    val card = 20.dp
    val gap = 12.dp
    val section = 24.dp
}

/**
 * Layered backdrop: a soft vertical gradient with a few large, blurred-looking colour
 * fields. Real backdrop blur is not used; it is unavailable before Android 12 and costs
 * readability. The glass effect comes from translucency and edge highlights instead.
 */
@Composable
fun GlassBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = HzvTheme.colors
    Box(
        modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(colors.backgroundTop, colors.backgroundBottom))),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            drawCircle(
                Brush.radialGradient(listOf(colors.ambientA, colors.ambientA.copy(alpha = 0f)), center = Offset(w * 0.1f, h * 0.08f), radius = w * 0.9f),
                radius = w * 0.9f,
                center = Offset(w * 0.1f, h * 0.08f),
            )
            drawCircle(
                Brush.radialGradient(listOf(colors.ambientB, colors.ambientB.copy(alpha = 0f)), center = Offset(w * 0.95f, h * 0.35f), radius = w * 0.8f),
                radius = w * 0.8f,
                center = Offset(w * 0.95f, h * 0.35f),
            )
            drawCircle(
                Brush.radialGradient(listOf(colors.ambientC, colors.ambientC.copy(alpha = 0f)), center = Offset(w * 0.3f, h * 0.95f), radius = w * 0.85f),
                radius = w * 0.85f,
                center = Offset(w * 0.3f, h * 0.95f),
            )
        }
        content()
    }
}

/** Translucent surface with a light top edge, the base of every card. */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = HzvShapes.card,
    strong: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = HzvTheme.colors
    Box(
        modifier
            .clip(shape)
            .background(if (strong) colors.glassFillStrong else colors.glassFill)
            .border(1.dp, Brush.verticalGradient(listOf(colors.glassEdgeTop, colors.glassEdgeBottom)), shape),
        content = content,
    )
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    strong: Boolean = false,
    contentPadding: Dp = HzvSpacing.card,
    content: @Composable ColumnScope.() -> Unit,
) {
    GlassSurface(modifier = modifier, strong = strong) {
        Column(
            Modifier
                .then(if (onClick != null) Modifier.clickable(onClickLabel = onClickLabel, role = Role.Button, onClick = onClick) else Modifier)
                .padding(contentPadding),
            content = content,
        )
    }
}
