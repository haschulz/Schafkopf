package schafkopf.android

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import schafkopf.model.Card as GameCard
import schafkopf.model.Rank
import schafkopf.model.Suit
import kotlin.math.min
import kotlin.math.sin

private val CardCream = Color(0xFFF8F3E6)
private val CardPaper = Color(0xFFF4E7CB)
private val CardInk = Color(0xFF392D22)
private val CardBrass = Color(0xFFC49A45)
private val CardShape = RoundedCornerShape(7.dp)

private fun rankIndex(rank: Rank): String = when (rank) {
    Rank.SAU -> "A"
    Rank.KOENIG -> "K"
    Rank.OBER -> "O"
    Rank.UNTER -> "U"
    else -> rank.displayName()
}

private val cardFaceRanks = listOf(
    Rank.SAU, Rank.KOENIG, Rank.OBER, Rank.UNTER,
    Rank.ZEHN, Rank.NEUN, Rank.ACHT, Rank.SIEBEN
)

private val cardFaceResources = mapOf(
    Suit.GRAS to listOf(
        R.drawable.card_gras_sau, R.drawable.card_gras_koenig, R.drawable.card_gras_ober,
        R.drawable.card_gras_unter, R.drawable.card_gras_zehn, R.drawable.card_gras_neun,
        R.drawable.card_gras_acht, R.drawable.card_gras_sieben
    ),
    Suit.EICHEL to listOf(
        R.drawable.card_eichel_sau, R.drawable.card_eichel_koenig, R.drawable.card_eichel_ober,
        R.drawable.card_eichel_unter, R.drawable.card_eichel_zehn, R.drawable.card_eichel_neun,
        R.drawable.card_eichel_acht, R.drawable.card_eichel_sieben
    ),
    Suit.SCHELLEN to listOf(
        R.drawable.card_schellen_sau, R.drawable.card_schellen_koenig, R.drawable.card_schellen_ober,
        R.drawable.card_schellen_unter, R.drawable.card_schellen_zehn, R.drawable.card_schellen_neun,
        R.drawable.card_schellen_acht, R.drawable.card_schellen_sieben
    ),
    Suit.HERZ to listOf(
        R.drawable.card_herz_sau, R.drawable.card_herz_koenig, R.drawable.card_herz_ober,
        R.drawable.card_herz_unter, R.drawable.card_herz_zehn, R.drawable.card_herz_neun,
        R.drawable.card_herz_acht, R.drawable.card_herz_sieben
    )
)

private val suitSymbolResources = mapOf(
    Suit.GRAS to R.drawable.suit_gras,
    Suit.EICHEL to R.drawable.suit_eichel,
    Suit.SCHELLEN to R.drawable.suit_schellen,
    Suit.HERZ to R.drawable.suit_herz
)

private fun cardFaceResource(card: GameCard): Int =
    cardFaceResources.getValue(card.suit())[cardFaceRanks.indexOf(card.rank())]

@Composable
internal fun PlayingCard(card: GameCard, modifier: Modifier = Modifier, dimmed: Boolean = false) {
    BoxWithConstraints(
        modifier = modifier
            .shadow(4.dp, CardShape)
            .clip(CardShape)
    ) {
        Image(
            painter = painterResource(cardFaceResource(card)),
            contentDescription = "${rankIndex(card.rank())} ${card.suit().displayName()}",
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.fillMaxSize()
        )
        SuitSymbolOverlays(card, maxWidth, maxHeight)
        Row(
            modifier = Modifier.align(Alignment.TopStart).padding(2.dp)
                .background(CardPaper, RoundedCornerShape(3.dp)).padding(horizontal = 3.dp, vertical = 1.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(rankIndex(card.rank()), color = CardInk, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Image(
                painter = painterResource(suitSymbolResources.getValue(card.suit())),
                contentDescription = card.suit().displayName(),
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(12.dp)
            )
        }
        if (dimmed) {
            Box(Modifier.fillMaxSize().background(Color(0x8C1B1208)))
        }
    }
}

@Composable
private fun SuitSymbolOverlays(card: GameCard, width: androidx.compose.ui.unit.Dp, height: androidx.compose.ui.unit.Dp) {
    val resource = suitSymbolResources.getValue(card.suit())
    val placements = buildList {
        add(SuitSymbolPlacement(width * 0.895f, height * 0.75f,
            width * 0.14f, height * 0.15f, width * 0.18f, height * 0.18f, 180f))
        suitPipCenters(card.rank()).forEach { (x, y) ->
            add(SuitSymbolPlacement(width * x, height * y,
                width * 0.23f, height * 0.25f, width * 0.27f, height * 0.3f))
        }
    }
    placements.forEach { placement ->
        Box(
            Modifier.offset(placement.centerX - placement.maskWidth / 2f,
                placement.centerY - placement.maskHeight / 2f)
                .size(placement.maskWidth, placement.maskHeight)
                .background(CardPaper, RoundedCornerShape(percent = 42))
        )
    }
    placements.forEach { placement ->
        Image(
            painter = painterResource(resource),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.offset(placement.centerX - placement.markWidth / 2f,
                placement.centerY - placement.markHeight / 2f)
                .size(placement.markWidth, placement.markHeight)
                .rotate(placement.rotation)
        )
    }
}

private data class SuitSymbolPlacement(
    val centerX: androidx.compose.ui.unit.Dp,
    val centerY: androidx.compose.ui.unit.Dp,
    val markWidth: androidx.compose.ui.unit.Dp,
    val markHeight: androidx.compose.ui.unit.Dp,
    val maskWidth: androidx.compose.ui.unit.Dp,
    val maskHeight: androidx.compose.ui.unit.Dp,
    val rotation: Float = 0f
)

private fun suitPipCenters(rank: Rank): List<Pair<Float, Float>> = when (rank) {
    Rank.SIEBEN -> listOf(
        0.3f to 0.24f, 0.75f to 0.24f, 0.52f to 0.44f,
        0.3f to 0.63f, 0.75f to 0.63f, 0.3f to 0.87f, 0.75f to 0.87f
    )
    Rank.ACHT -> listOf(
        0.32f to 0.23f, 0.75f to 0.23f, 0.32f to 0.5f,
        0.75f to 0.5f, 0.32f to 0.82f, 0.75f to 0.82f
    )
    Rank.NEUN -> listOf(
        0.28f to 0.21f, 0.74f to 0.21f, 0.5f to 0.4f,
        0.28f to 0.57f, 0.74f to 0.57f, 0.5f to 0.77f,
        0.28f to 0.89f, 0.74f to 0.89f
    )
    Rank.ZEHN -> listOf(
        0.28f to 0.2f, 0.5f to 0.31f, 0.74f to 0.2f,
        0.28f to 0.52f, 0.74f to 0.52f, 0.5f to 0.72f,
        0.28f to 0.88f, 0.74f to 0.88f
    )
    else -> emptyList()
}

@Composable
internal fun CardBack(modifier: Modifier = Modifier) {
    Canvas(
        modifier = modifier
            .shadow(3.dp, CardShape)
            .clip(CardShape)
            .background(Color(0xFF17483D))
            .border(1.dp, CardBrass, CardShape)
    ) {
        val inset = size.minDimension * 0.065f
        val tileWidth = size.width / 3.4f
        val tileHeight = size.height / 5.2f
        val mark = min(tileWidth, tileHeight) * 0.43f
        clipRect(inset, inset, size.width - inset, size.height - inset) {
            var row = 0
            var y = inset - mark
            while (y < size.height) {
                var x = inset - mark / 2f + if (row % 2 == 0) 0f else tileWidth / 2f
                while (x < size.width) {
                    val suit = when ((row + (x / tileWidth).toInt()) % 4) {
                        0 -> Suit.EICHEL
                        1 -> Suit.GRAS
                        2 -> Suit.HERZ
                        else -> Suit.SCHELLEN
                    }
                    val signColor = when (suit) {
                        Suit.HERZ -> Color(0xFFD64A3F)
                        Suit.SCHELLEN -> CardBrass
                        else -> Color(0xFFC8D4BD)
                    }
                    drawSuitMark(suit, Offset(x, y), mark, signColor.copy(alpha = 0.88f))
                    x += tileWidth
                }
                y += tileHeight
                row++
            }
        }
        val frameInset = size.minDimension * 0.045f
        drawRoundRect(CardBrass, Offset(frameInset, frameInset),
            Size(size.width - frameInset * 2f, size.height - frameInset * 2f),
            CornerRadius(size.minDimension * 0.04f), style = Stroke(width = maxOf(1.5f, size.minDimension * 0.018f)))
        drawRoundRect(Color(0xFFDBB963), Offset(frameInset * 1.55f, frameInset * 1.55f),
            Size(size.width - frameInset * 3.1f, size.height - frameInset * 3.1f),
            CornerRadius(size.minDimension * 0.03f), style = Stroke(width = maxOf(1f, size.minDimension * 0.009f)))
        drawSuitMark(Suit.SCHELLEN, Offset(size.width * 0.37f, size.height * 0.39f),
            min(size.width, size.height) * 0.26f, Color(0xFFD64A3F))
        drawSuitMark(Suit.EICHEL, Offset(size.width * 0.48f, size.height * 0.39f),
            min(size.width, size.height) * 0.26f, Color(0xFFC8D4BD))
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
            val side = min(w, h) * 0.5f
            drawCircle(CardBrass.copy(alpha = 0.22f), side * 0.72f, Offset(w / 2f, h / 2f))
            drawSuitMark(card.suit(), Offset((w - side) / 2f, h * 0.22f), side, ink)
            drawSuitMark(card.suit(), Offset((w - side) / 2f, h * 0.59f), side, ink)
            drawLine(CardBrass, Offset(w * 0.15f, h * 0.5f), Offset(w * 0.85f, h * 0.5f),
                strokeWidth = maxOf(1f, min(w, h) * 0.018f))
        }
        Rank.OBER, Rank.UNTER, Rank.KOENIG -> drawFigure(card)
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
        Rank.SIEBEN -> rows(0.12f, 0.5f, 0.88f) + listOf(0.5f to 0.3f)
        Rank.ACHT -> rows(0.12f, 0.37f, 0.63f, 0.88f)
        Rank.NEUN -> rows(0.12f, 0.37f, 0.63f, 0.88f) + listOf(0.5f to 0.5f)
        else -> rows(0.1f, 0.3f, 0.5f, 0.7f, 0.9f)
    }
}

private fun DrawScope.drawFigure(card: GameCard) {
    val halfHeight = size.height / 2f
    drawFigurePortrait(card, halfHeight)
    scale(scaleX = 1f, scaleY = -1f, pivot = Offset(0f, halfHeight)) {
        drawFigurePortrait(card, halfHeight)
    }
    drawLine(CardBrass.copy(alpha = 0.86f), Offset(size.width * 0.12f, halfHeight),
        Offset(size.width * 0.88f, halfHeight), strokeWidth = maxOf(1f, size.minDimension * 0.016f))
}

private fun DrawScope.drawFigurePortrait(card: GameCard, h: Float) {
    val w = size.width
    val s = min(w, h)
    val cx = w / 2f
    val headY = h * 0.3f
    val headR = s * 0.15f
    val mantle = when (card.rank()) {
        Rank.KOENIG -> Color(0xFF8E2E25)
        Rank.OBER -> Color(0xFF2E6747)
        else -> Color(0xFF35618C)
    }
    val robe = Path().apply {
        moveTo(cx - s * 0.45f, h)
        lineTo(cx - s * 0.31f, headY + headR * 1.1f)
        lineTo(cx + s * 0.31f, headY + headR * 1.1f)
        lineTo(cx + s * 0.45f, h)
        close()
    }
    drawPath(robe, mantle)
    drawPath(robe, CardInk, style = Stroke(width = maxOf(1.5f, s * 0.025f)))
    drawPath(Path().apply {
        moveTo(cx - headR * 1.12f, headY + headR * 1.04f)
        lineTo(cx, headY + headR * 1.62f)
        lineTo(cx + headR * 1.12f, headY + headR * 1.04f)
        lineTo(cx + headR * 0.64f, headY + headR * 2.18f)
        lineTo(cx - headR * 0.64f, headY + headR * 2.18f)
        close()
    }, Color(0xFFF2E4C0))
    drawRect(Color(0xFFD9B45C), Offset(cx - s * 0.045f, headY + headR * 1.1f),
        Size(s * 0.09f, h - headY - headR * 1.1f))
    drawLine(CardBrass, Offset(cx - s * 0.28f, h * 0.78f),
        Offset(cx + s * 0.28f, h * 0.78f), strokeWidth = maxOf(2f, s * 0.045f))
    drawCircle(Color(0xFFF1D6AF), headR, Offset(cx, headY))
    drawCircle(CardInk, headR, Offset(cx, headY), style = Stroke(width = maxOf(1f, s * 0.02f)))
    drawCircle(CardInk, maxOf(1f, headR * 0.06f), Offset(cx - headR * 0.36f, headY - headR * 0.04f))
    drawCircle(CardInk, maxOf(1f, headR * 0.06f), Offset(cx + headR * 0.36f, headY - headR * 0.04f))
    drawLine(CardInk, Offset(cx, headY), Offset(cx - headR * 0.08f, headY + headR * 0.24f),
        strokeWidth = maxOf(1f, s * 0.016f))
    drawLine(Color(0xFF8E2C24), Offset(cx - headR * 0.22f, headY + headR * 0.46f),
        Offset(cx + headR * 0.22f, headY + headR * 0.46f), strokeWidth = maxOf(1f, s * 0.025f))
    when (card.rank()) {
        Rank.KOENIG -> {
            val crown = Path().apply {
                val top = headY - headR * 1.75f
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
            drawPath(crown, CardBrass)
            drawPath(crown, CardInk, style = Stroke(width = maxOf(1f, s * 0.018f)))
            drawPath(Path().apply {
                moveTo(cx - headR * 0.72f, headY + headR * 0.62f)
                lineTo(cx - headR * 0.35f, headY + headR * 1.22f)
                lineTo(cx, headY + headR * 0.82f)
                lineTo(cx + headR * 0.35f, headY + headR * 1.22f)
                lineTo(cx + headR * 0.72f, headY + headR * 0.62f)
                close()
            }, Color(0xFF57382A))
        }
        Rank.OBER -> {
            val cap = Path().apply {
                moveTo(cx - headR * 1.3f, headY - headR * 0.9f)
                quadraticTo(cx - headR * 0.35f, headY - headR * 2.25f, cx + headR * 1.25f, headY - headR * 1.1f)
                lineTo(cx + headR * 1.1f, headY - headR * 0.55f)
                lineTo(cx - headR * 1.3f, headY - headR * 0.55f)
                close()
            }
            drawPath(cap, Color(0xFF263B38))
            drawPath(cap, CardBrass, style = Stroke(width = maxOf(1f, s * 0.025f)))
            drawLine(Color(0xFF8E2C24), Offset(cx - headR * 0.45f, headY - headR * 1.24f),
                Offset(cx + headR * 0.82f, headY - headR * 1.86f), strokeWidth = maxOf(1f, s * 0.035f))
        }
        else -> {
            drawArc(Color(0xFF8E2C24), 180f, 180f, true,
                Offset(cx - headR, headY - headR * 1.15f), Size(headR * 2f, headR * 1.6f))
            drawLine(CardBrass, Offset(cx - headR * 1.4f, headY - headR * 0.7f),
                Offset(cx + headR * 1.4f, headY - headR * 0.7f), strokeWidth = maxOf(1f, s * 0.025f))
        }
    }
    val mark = s * 0.24f
    drawSuitMark(card.suit(), Offset(cx - mark / 2f, h * 0.63f), mark, CardInk)
}

private fun DrawScope.drawCardFrame(ink: Color) {
    val inset = size.minDimension * 0.07f
    val outerWidth = maxOf(1f, size.minDimension * 0.018f)
    val innerInset = inset * 1.45f
    drawRoundRect(ink, Offset(inset, inset), Size(size.width - inset * 2f, size.height - inset * 2f),
        CornerRadius(size.minDimension * 0.045f), style = Stroke(width = outerWidth))
    drawRoundRect(CardBrass, Offset(innerInset, innerInset),
        Size(size.width - innerInset * 2f, size.height - innerInset * 2f),
        CornerRadius(size.minDimension * 0.035f), style = Stroke(width = maxOf(0.75f, outerWidth * 0.55f)))
    val ornamentSize = size.minDimension * 0.055f
    listOf(
        Offset(inset * 1.55f, inset * 1.55f),
        Offset(size.width - inset * 1.55f - ornamentSize, inset * 1.55f),
        Offset(inset * 1.55f, size.height - inset * 1.55f - ornamentSize),
        Offset(size.width - inset * 1.55f - ornamentSize,
            size.height - inset * 1.55f - ornamentSize)
    ).forEach { origin ->
        drawSuitMark(Suit.SCHELLEN, origin, ornamentSize, CardBrass)
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
