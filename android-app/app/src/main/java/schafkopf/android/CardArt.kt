package schafkopf.android

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import schafkopf.model.Card as GameCard
import schafkopf.model.Rank
import schafkopf.model.Suit
import kotlin.math.min
import kotlin.math.sin

private val CardCream = Color(0xFFF8F3E6)
private val CardShape = RoundedCornerShape(7.dp)

internal fun suitInk(suit: Suit): Color = when (suit) {
    Suit.EICHEL -> Color(0xFF7A4E1C)
    Suit.GRAS -> Color(0xFF2F7A45)
    Suit.HERZ -> Color(0xFFC0272D)
    Suit.SCHELLEN -> Color(0xFFC58A12)
}

private fun rankIndex(rank: Rank): String = when (rank) {
    Rank.SAU -> "A"
    Rank.KOENIG -> "K"
    Rank.OBER -> "O"
    Rank.UNTER -> "U"
    else -> rank.displayName()
}

@Composable
internal fun PlayingCard(card: GameCard, modifier: Modifier = Modifier, dimmed: Boolean = false) {
    val ink = suitInk(card.suit())
    Box(
        modifier = modifier
            .shadow(4.dp, CardShape)
            .clip(CardShape)
            .background(CardCream)
            .border(1.dp, Color(0xFF8F846E), CardShape)
    ) {
        Canvas(Modifier.fillMaxSize().padding(horizontal = 5.dp, vertical = 15.dp)) {
            drawCardCenter(card, ink)
        }
        Text(
            rankIndex(card.rank()), color = ink, fontSize = 15.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.TopStart).padding(start = 4.dp, top = 1.dp)
        )
        Canvas(Modifier.align(Alignment.TopStart).padding(start = 5.dp, top = 18.dp).size(11.dp)) {
            drawSuitMark(card.suit(), Offset.Zero, size.minDimension, ink)
        }
        Text(
            rankIndex(card.rank()), color = ink, fontSize = 15.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 4.dp, bottom = 1.dp).rotate(180f)
        )
        Canvas(Modifier.align(Alignment.BottomEnd).padding(end = 5.dp, bottom = 18.dp).size(11.dp).rotate(180f)) {
            drawSuitMark(card.suit(), Offset.Zero, size.minDimension, ink)
        }
        if (dimmed) {
            Box(Modifier.fillMaxSize().background(Color(0x8C1B1208)))
        }
    }
}

@Composable
internal fun CardBack(modifier: Modifier = Modifier) {
    Canvas(
        modifier = modifier
            .shadow(3.dp, CardShape)
            .clip(CardShape)
            .background(Color.White)
            .border(1.dp, Color(0xFF9AA7B8), CardShape)
    ) {
        val inset = size.minDimension * 0.07f
        val tile = size.width / 4.5f
        val half = tile / 2f
        clipRect(inset, inset, size.width - inset, size.height - inset) {
            var row = 0
            var y = inset
            while (y < size.height + tile) {
                var x = inset + if (row % 2 == 0) 0f else half
                while (x < size.width + tile) {
                    val diamond = Path().apply {
                        moveTo(x, y - half * 1.2f)
                        lineTo(x + half, y)
                        lineTo(x, y + half * 1.2f)
                        lineTo(x - half, y)
                        close()
                    }
                    drawPath(diamond, Color(0xFF2B6CB8))
                    x += tile
                }
                y += half * 1.2f
                row++
            }
        }
        drawRect(Color(0xFF2B6CB8), Offset(inset, inset), Size(size.width - 2 * inset, size.height - 2 * inset),
            style = Stroke(width = 2f))
    }
}

@Composable
internal fun WoodTable(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        drawRect(Brush.linearGradient(
            listOf(Color(0xFF17483D), Color(0xFF23634E), Color(0xFF173F37)),
            start = Offset.Zero,
            end = Offset(size.width, size.height)
        ))
        val random = java.util.Random(731L)
        repeat(520) {
            val x = random.nextFloat() * size.width
            val y = random.nextFloat() * size.height
            val speck = if (it % 3 == 0) Color.White else Color(0xFF071F19)
            drawCircle(speck.copy(alpha = 0.035f), radius = 0.7f + random.nextFloat(), center = Offset(x, y))
        }
        drawRect(
            Brush.radialGradient(
                listOf(Color.Transparent, Color(0x80000000)),
                center = center,
                radius = size.maxDimension * 0.75f
            )
        )
    }
}

private fun DrawScope.drawCardCenter(card: GameCard, ink: Color) {
    val w = size.width
    val h = size.height
    when (card.rank()) {
        Rank.SAU -> {
            val side = min(w, h) * 0.78f
            drawSuitMark(card.suit(), Offset((w - side) / 2f, h * 0.42f - side / 2f), side, ink)
            drawRoundRect(ink.copy(alpha = 0.18f), Offset(w * 0.12f, h * 0.82f), Size(w * 0.76f, h * 0.12f),
                androidx.compose.ui.geometry.CornerRadius(6f, 6f))
        }
        Rank.OBER, Rank.UNTER, Rank.KOENIG -> drawFigure(card, ink)
        else -> {
            val pips = pipLayout(card.rank())
            val side = min(w * 0.36f, h * 0.18f)
            pips.forEach { (px, py) ->
                drawSuitMark(card.suit(), Offset(px * w - side / 2f, py * h - side / 2f), side, ink)
            }
        }
    }
}

private fun pipLayout(rank: Rank): List<Pair<Float, Float>> {
    val left = 0.27f
    val right = 0.73f
    fun rows(vararg ys: Float) = ys.flatMap { listOf(left to it, right to it) }
    return when (rank) {
        Rank.SIEBEN -> rows(0.12f, 0.5f, 0.88f) + listOf(0.5f to 0.31f)
        Rank.ACHT -> rows(0.12f, 0.37f, 0.63f, 0.88f)
        Rank.NEUN -> rows(0.12f, 0.37f, 0.63f, 0.88f) + listOf(0.5f to 0.5f)
        else -> rows(0.1f, 0.3f, 0.5f, 0.7f, 0.9f)
    }
}

private fun DrawScope.drawFigure(card: GameCard, ink: Color) {
    val w = size.width
    val h = size.height
    val s = min(w, h / 1.5f)
    val cx = w / 2f
    val headY = h * 0.36f
    val headR = s * 0.15f
    val robe = Path().apply {
        moveTo(cx - s * 0.42f, h)
        lineTo(cx - s * 0.26f, headY + headR * 1.1f)
        lineTo(cx + s * 0.26f, headY + headR * 1.1f)
        lineTo(cx + s * 0.42f, h)
        close()
    }
    drawPath(robe, ink)
    drawPath(robe, Color(0xFF3B2A1D), style = Stroke(width = 2.5f))
    drawRect(Color(0xFFF1E3B5), Offset(cx - s * 0.05f, headY + headR * 1.1f), Size(s * 0.1f, h - headY - headR))
    drawLine(Color(0xFFD5AA47), Offset(cx - s * 0.25f, h * 0.72f),
        Offset(cx + s * 0.25f, h * 0.72f), strokeWidth = maxOf(2f, s * 0.035f))
    drawCircle(Color(0xFFF2CCA4), headR, Offset(cx, headY))
    drawCircle(Color(0xFF5B3A1E), headR, Offset(cx, headY), style = Stroke(width = 2f))
    when (card.rank()) {
        Rank.KOENIG -> {
            val crown = Path().apply {
                val top = headY - headR * 2.0f
                val base = headY - headR * 0.75f
                moveTo(cx - headR, base)
                lineTo(cx - headR, top + headR * 0.4f)
                lineTo(cx - headR * 0.5f, base - headR * 0.45f)
                lineTo(cx, top)
                lineTo(cx + headR * 0.5f, base - headR * 0.45f)
                lineTo(cx + headR, top + headR * 0.4f)
                lineTo(cx + headR, base)
                close()
            }
            drawPath(crown, Color(0xFFE0B02A))
            val mark = s * 0.27f
            drawSuitMark(card.suit(), Offset(cx - mark / 2f, h * 0.63f), mark, Color(0xFFF8F3E6))
        }
        Rank.OBER -> {
            val cap = Path().apply {
                moveTo(cx - headR * 1.3f, headY - headR * 0.9f)
                quadraticTo(cx - headR * 0.35f, headY - headR * 2.5f, cx + headR * 1.25f, headY - headR * 1.1f)
                lineTo(cx + headR * 1.1f, headY - headR * 0.55f)
                lineTo(cx - headR * 1.3f, headY - headR * 0.55f)
                close()
            }
            drawPath(cap, Color(0xFF2D2A26))
            drawPath(cap, Color(0xFFD5AA47), style = Stroke(width = 2f))
            val mark = s * 0.28f
            drawSuitMark(card.suit(), Offset(cx - mark / 2f, h * 0.63f), mark, Color(0xFFF8F3E6))
        }
        else -> {
            drawArc(Color(0xFF8E2C24), 180f, 180f, true, Offset(cx - headR, headY - headR * 1.15f), Size(headR * 2f, headR * 1.6f))
            val mark = s * 0.27f
            drawCircle(Color(0xFFD5AA47), mark * 0.7f, Offset(cx, h * 0.63f))
            drawSuitMark(card.suit(), Offset(cx - mark / 2f, h * 0.63f - mark / 2f), mark,
                Color(0xFFF8F3E6))
        }
    }
}

/** Draws a Bavarian suit sign (Eichel, Gras, Herz, Schellen) into a square of the given side. */
internal fun DrawScope.drawSuitMark(suit: Suit, topLeft: Offset, side: Float, color: Color) {
    translate(topLeft.x, topLeft.y) {
        scale(side, side, pivot = Offset.Zero) {
            when (suit) {
                Suit.EICHEL -> {
                    drawPath(Path().apply {
                        moveTo(0.5f, 0.97f)
                        cubicTo(0.12f, 0.75f, 0.24f, 0.36f, 0.5f, 0.33f)
                        cubicTo(0.76f, 0.36f, 0.88f, 0.75f, 0.5f, 0.97f)
                        close()
                    }, color)
                    drawOval(Color(0xFF4A2E12), Offset(0.2f, 0.25f), Size(0.6f, 0.22f))
                    drawLine(Color(0xFF4A2E12), Offset(0.5f, 0.04f), Offset(0.5f, 0.27f), 0.08f)
                }
                Suit.GRAS -> {
                    drawPath(Path().apply {
                        moveTo(0.5f, 0.97f)
                        cubicTo(0.04f, 0.74f, 0.1f, 0.24f, 0.5f, 0.03f)
                        cubicTo(0.9f, 0.24f, 0.96f, 0.74f, 0.5f, 0.97f)
                        close()
                    }, color)
                    drawLine(Color(0xFFDDEBD5), Offset(0.5f, 0.92f), Offset(0.5f, 0.16f), 0.05f)
                    drawLine(Color(0xFFDDEBD5), Offset(0.5f, 0.5f), Offset(0.3f, 0.36f), 0.035f)
                    drawLine(Color(0xFFDDEBD5), Offset(0.5f, 0.62f), Offset(0.7f, 0.48f), 0.035f)
                }
                Suit.HERZ -> drawPath(Path().apply {
                    moveTo(0.5f, 0.93f)
                    cubicTo(0.3f, 0.75f, 0.02f, 0.52f, 0.1f, 0.28f)
                    cubicTo(0.18f, 0.06f, 0.44f, 0.08f, 0.5f, 0.3f)
                    cubicTo(0.56f, 0.08f, 0.82f, 0.06f, 0.9f, 0.28f)
                    cubicTo(0.98f, 0.52f, 0.7f, 0.75f, 0.5f, 0.93f)
                    close()
                }, color)
                Suit.SCHELLEN -> {
                    drawPath(Path().apply {
                        moveTo(0.39f, 0.13f)
                        lineTo(0.61f, 0.13f)
                        lineTo(0.62f, 0.27f)
                        cubicTo(0.72f, 0.35f, 0.87f, 0.48f, 0.84f, 0.66f)
                        lineTo(0.75f, 0.82f)
                        lineTo(0.25f, 0.82f)
                        lineTo(0.16f, 0.66f)
                        cubicTo(0.13f, 0.48f, 0.28f, 0.35f, 0.38f, 0.27f)
                        close()
                    }, color)
                    drawPath(Path().apply {
                        moveTo(0.39f, 0.13f)
                        lineTo(0.61f, 0.13f)
                        lineTo(0.62f, 0.27f)
                        cubicTo(0.72f, 0.35f, 0.87f, 0.48f, 0.84f, 0.66f)
                        lineTo(0.75f, 0.82f)
                        lineTo(0.25f, 0.82f)
                        lineTo(0.16f, 0.66f)
                        cubicTo(0.13f, 0.48f, 0.28f, 0.35f, 0.38f, 0.27f)
                        close()
                    }, Color(0xFF6E5016), style = Stroke(0.045f))
                    drawOval(Color(0xFFD5AA47), Offset(0.39f, 0.06f), Size(0.22f, 0.13f))
                    drawLine(Color(0xFF6E5016), Offset(0.25f, 0.53f), Offset(0.75f, 0.53f), 0.035f)
                    drawCircle(Color(0xFF6E5016), 0.055f, Offset(0.5f, 0.78f))
                }
            }
        }
    }
}
