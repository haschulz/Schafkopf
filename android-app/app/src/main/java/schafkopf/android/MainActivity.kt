package schafkopf.android

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import schafkopf.ai.SimpleBot
import schafkopf.ai.policy.PolicyGenome
import schafkopf.analysis.GameRecord
import schafkopf.analysis.PostGameAnalyzer
import schafkopf.model.BettelContract
import schafkopf.model.Card as GameCard
import schafkopf.model.Deck
import schafkopf.model.GameType
import schafkopf.model.GeierContract
import schafkopf.model.KreuzbockContract
import schafkopf.model.Player
import schafkopf.model.RamschContract
import schafkopf.model.RufspielContract
import schafkopf.model.SoloContract
import schafkopf.model.Suit
import schafkopf.model.WenzContract
import schafkopf.rules.RuleEngine
import schafkopf.state.GameSession
import java.util.Random

private val Gold = Color(0xFFE2B54A)
private val OpponentBlue = Color(0xE05A55C9)
private val HumanRed = Color(0xE0B3261E)
private const val REVIEW_SAMPLES = 40

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val genome = runCatching {
            assets.open("policy-genome.properties").use { PolicyGenome.load(it) }
        }.getOrElse { PolicyGenome.baseline() }
        val store = ScoreStore(getSharedPreferences("schafkopf", MODE_PRIVATE))
        setContent {
            MaterialTheme {
                GameScreen(genome, store, ::share)
            }
        }
    }

    private fun share(text: String) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        startActivity(Intent.createChooser(send, "Partie teilen"))
    }
}

internal class ScoreStore(private val prefs: SharedPreferences) {
    fun scores(): List<Int> = (0..3).map { prefs.getInt("score$it", 0) }

    /** Adds the points once per game; scores and the settled marker are written in one commit. */
    fun settle(gameId: Long, points: List<Int>) {
        if (prefs.getLong("settledGame", 0L) == gameId) return
        val editor = prefs.edit()
        points.forEachIndexed { seat, value -> editor.putInt("score$seat", prefs.getInt("score$seat", 0) + value) }
        editor.putInt("games", prefs.getInt("games", 0) + 1).putLong("settledGame", gameId).commit()
    }

    // commit() instead of apply(): the app may be killed right after any move.
    fun saveSession(gameId: Long, state: String) {
        prefs.edit().putLong("gameId", gameId).putString("session", state).commit()
    }

    fun savedSession(): Pair<Long, String>? {
        val state = prefs.getString("session", null) ?: return null
        return prefs.getLong("gameId", 0L) to state
    }

    fun clearSession() {
        prefs.edit().remove("session").remove("gameId").commit()
    }

    fun reset() {
        val editor = prefs.edit()
        (0..3).forEach { editor.remove("score$it") }
        editor.remove("games").commit()
    }
}

private class RestoredGame(val gameId: Long, val session: GameSession?, val failed: Boolean)

private fun restoreSavedGame(store: ScoreStore, genome: PolicyGenome): RestoredGame {
    val (gameId, state) = store.savedSession() ?: return RestoredGame(0L, null, false)
    return try {
        RestoredGame(gameId, GameSession.restore(state, genome), false)
    } catch (invalid: RuntimeException) {
        store.clearSession()
        RestoredGame(0L, null, true)
    }
}

@Composable
private fun GameScreen(genome: PolicyGenome, store: ScoreStore, share: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val restored = remember { restoreSavedGame(store, genome) }
    val restoredSnapshot = remember { restored.session?.snapshot(Suit.EICHEL) }
    var gameId by remember { mutableLongStateOf(restored.gameId) }
    var pendingDeck by remember { mutableStateOf(shuffledDeck()) }
    var awaitingCut by remember { mutableStateOf(restored.session == null) }
    var session by remember { mutableStateOf(restored.session) }
    var snapshot by remember { mutableStateOf(restoredSnapshot) }
    var soloSuit by remember { mutableStateOf(Suit.EICHEL) }
    var displayedPlays by remember {
        mutableIntStateOf(restoredSnapshot?.let { it.completedTricks().size * 4 + it.tableCards().size } ?: 0)
    }
    var clearedTricks by remember { mutableIntStateOf(restoredSnapshot?.completedTricks()?.size ?: 0) }
    var selected by remember { mutableStateOf<GameCard?>(null) }
    var showLastTrick by remember { mutableStateOf(false) }
    var scores by remember { mutableStateOf(store.scores()) }
    var outcome by remember { mutableStateOf<GameSession.Outcome?>(null) }
    var finishedRecord by remember { mutableStateOf<GameRecord?>(null) }
    var settled by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var importOpen by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf<String?>(null) }
    var notice by remember {
        mutableStateOf(if (restored.failed) "Die gespeicherte Partie konnte nicht geladen werden." else null)
    }
    var review by remember { mutableStateOf<PostGameAnalyzer.GameReview?>(null) }
    var reviewRecord by remember { mutableStateOf<GameRecord?>(null) }

    fun persist(next: GameSession.Snapshot?) {
        snapshot = next
        session?.let { store.saveSession(gameId, it.saveState()) }
    }

    fun newDeal() {
        store.clearSession()
        pendingDeck = shuffledDeck()
        awaitingCut = true
        session = null
        snapshot = null
        displayedPlays = 0
        clearedTricks = 0
        selected = null
        showLastTrick = false
        outcome = null
        finishedRecord = null
        settled = false
    }

    fun deal(cutCards: Int?) {
        cutCards?.let { pendingDeck.cut(it) }
        val created = GameSession(pendingDeck.deal4x8(), genome)
        gameId = System.currentTimeMillis()
        session = created
        persist(created.snapshot(soloSuit))
        awaitingCut = false
    }

    fun runReview(record: GameRecord, seat: Int) {
        busy = "Nachkarteln läuft …"
        scope.launch {
            val result = withContext(Dispatchers.Default) {
                runCatching {
                    val rules = RuleEngine()
                    PostGameAnalyzer(rules, SimpleBot(rules, true, genome), REVIEW_SAMPLES)
                        .review(record, seat, System.nanoTime())
                }
            }
            busy = null
            result.onSuccess {
                review = it
                reviewRecord = record
            }.onFailure { notice = "Nachkarteln nicht möglich: ${it.message}" }
        }
    }

    val current = snapshot
    val names = current?.seats()?.map { it.name() } ?: listOf("Sepp", "Hans", "Franz", "Du")
    val completedTricks = current?.completedTricks().orEmpty()
    val allPlays = completedTricks.flatMap { it.cards() } + current?.tableCards().orEmpty()
    val shownPlays = allPlays.take(displayedPlays)
    val visibleTrick = shownPlays.drop(clearedTricks * 4)
    val trickFull = visibleTrick.size == 4
    val caughtUp = displayedPlays == allPlays.size && !trickFull
    val stage = current?.stage()
    val bidding = stage == GameSession.Stage.BIDDING
    val canPlay = stage == GameSession.Stage.TRICK_PLAY && current.yourTurn() && caughtUp
    val legal = if (canPlay) current?.legalCards().orEmpty() else emptyList()
    val hand = current?.let(::sortHandForSnapshot).orEmpty()
    val eyes = names.map { name ->
        completedTricks.take(clearedTricks).filter { it.winnerName() == name }.sumOf { it.points() }
    }
    val cardsLeft = names.map { name -> if (current == null) 8 else 8 - shownPlays.count { it.playerName() == name } }
    val seatCalls = current?.seatCalls().orEmpty()
    val callerSeat = seatCalls.indexOfLast { it != null && it != "Weiter" }

    LaunchedEffect(session, allPlays.size) {
        while (true) {
            if (displayedPlays - clearedTricks * 4 >= 4) {
                delay(1200)
                clearedTricks += 1
                continue
            }
            if (displayedPlays >= allPlays.size) {
                break
            }
            val next = allPlays[displayedPlays]
            delay(if (next.playerName() == names[3]) 0 else 650)
            displayedPlays += 1
        }
    }

    LaunchedEffect(stage, clearedTricks) {
        val finishedSession = session
        if (stage == GameSession.Stage.FINISHED && clearedTricks == 8 && !settled && finishedSession != null) {
            val result = finishedSession.outcome()
            store.settle(gameId, result.seatPoints())
            scores = store.scores()
            finishedRecord = runCatching { GameRecord.from(finishedSession.finishedGame()) }.getOrNull()
            outcome = result
            settled = true
        }
    }

    fun playCard(card: GameCard) {
        if (!canPlay || card !in legal) return
        selected = null
        showLastTrick = false
        persist(session?.playHumanCard(card, soloSuit))
    }

    val status = when {
        awaitingCut -> "Neues Spiel – Rückhand hebt ab"
        bidding -> "Wer spielt?"
        current == null -> ""
        current.contractType() == GameType.RAMSCH -> "Keiner spielt – Ramsch"
        else -> {
            val who = if (callerSeat >= 0) names[callerSeat] else "?"
            val verb = if (callerSeat == 3) "spielst" else "spielt"
            "$who $verb ${contractPhrase(current)}"
        }
    }

    Box(Modifier.fillMaxSize()) {
        WoodTable(Modifier.fillMaxSize())
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Row(
                Modifier.fillMaxWidth().background(Color(0xD92A2622)).padding(horizontal = 4.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box {
                    TextButton(onClick = { menuOpen = true }) {
                        Text("⋮", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text("Neues Spiel") }, onClick = { menuOpen = false; newDeal() })
                        DropdownMenuItem(text = { Text("Partie eingeben & nachkarteln") },
                            onClick = { menuOpen = false; importOpen = true })
                        finishedRecord?.let { record ->
                            DropdownMenuItem(text = { Text("Letzte Partie nachkarteln") },
                                onClick = { menuOpen = false; runReview(record, 3) })
                            DropdownMenuItem(text = { Text("Letzte Partie teilen") },
                                onClick = { menuOpen = false; share(record.toText()) })
                        }
                        DropdownMenuItem(text = { Text("Punktestand zurücksetzen") }, onClick = {
                            menuOpen = false
                            store.reset()
                            scores = store.scores()
                        })
                    }
                }
                Text(
                    status, color = Color(0xFFF3EBD8), fontSize = 19.sp, fontFamily = FontFamily.Serif,
                    textAlign = TextAlign.Center, modifier = Modifier.weight(1f).padding(end = 40.dp)
                )
            }

            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        selected = null
                        if (completedTricks.isNotEmpty() && clearedTricks > 0) showLastTrick = !showLastTrick
                    }
            ) {
                OpponentSeat(names[1], scores[1], cardsLeft[1], eyes[1], seatCalls.getOrNull(1).takeIf { bidding },
                    active = isActive(current, 1, caughtUp), caller = callerSeat == 1 && !bidding,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 18.dp))
                OpponentSeat(names[0], scores[0], cardsLeft[0], eyes[0], seatCalls.getOrNull(0).takeIf { bidding },
                    active = isActive(current, 0, caughtUp), caller = callerSeat == 0 && !bidding,
                    modifier = Modifier.align(Alignment.CenterStart).padding(start = 6.dp, bottom = 60.dp))
                OpponentSeat(names[2], scores[2], cardsLeft[2], eyes[2], seatCalls.getOrNull(2).takeIf { bidding },
                    active = isActive(current, 2, caughtUp), caller = callerSeat == 2 && !bidding,
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 6.dp, bottom = 60.dp))

                if (showLastTrick && clearedTricks > 0) {
                    val last = completedTricks[clearedTricks - 1]
                    Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        Caption("Letzter Stich · ${last.winnerName()} · ${last.points()} Augen")
                        TrickArea(last.cards().map { names.indexOf(it.playerName()) to it.card() })
                    }
                } else {
                    TrickArea(
                        visibleTrick.map { names.indexOf(it.playerName()) to it.card() },
                        Modifier.align(Alignment.Center)
                    )
                    if (trickFull) {
                        val done = completedTricks.getOrNull(clearedTricks)
                        if (done != null) {
                            Caption("Stich an ${done.winnerName()} · ${done.points()} Augen",
                                Modifier.align(Alignment.Center).offset(y = 150.dp))
                        }
                    }
                }

                Row(
                    Modifier.align(Alignment.BottomStart).padding(start = 12.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    NamePill("${names[3]}: ${scores[3]}", HumanRed, highlight = canPlay || (bidding && !awaitingCut))
                    if (eyes[3] > 0) TrickPile(eyes[3])
                    if (callerSeat == 3 && !bidding) CallerBadge()
                }
                if (canPlay) {
                    Caption(if (selected == null) "Du bist dran" else "Nochmal tippen oder nach oben wischen",
                        Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 8.dp))
                }
            }

            HandRow(hand, legal, canPlay, selected,
                onTap = { card -> if (selected == card) playCard(card) else selected = card },
                onSwipe = ::playCard)
        }

        if (bidding && current != null) {
            BiddingPanel(current, names, soloSuit,
                onSuit = { suit -> soloSuit = suit; snapshot = session?.snapshot(suit) },
                onAnnounce = { id -> persist(session?.announce(id, soloSuit)) },
                onPass = { persist(session?.pass(soloSuit)) },
                modifier = Modifier.align(Alignment.Center).padding(horizontal = 18.dp))
        }

        busy?.let { BusyOverlay(it) }
    }

    if (awaitingCut) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Rückhand hebt ab?") },
            text = { DeckCutGraphic(onCut = { deal(it) }) },
            confirmButton = { },
            dismissButton = {
                TextButton(onClick = { deal(null) }) { Text("Ohne Abheben") }
            },
            containerColor = Color(0xFFF4EEDF)
        )
    }

    outcome?.let { result ->
        ResultDialog(result, names, scores, contractLine = status,
            canReview = finishedRecord != null,
            onReview = { finishedRecord?.let { runReview(it, 3) } },
            onNext = { newDeal() })
    }

    if (importOpen) {
        ImportGameDialog(onDismiss = { importOpen = false }, onAnalyze = { record, seat ->
            importOpen = false
            runReview(record, seat)
        })
    }

    review?.let { result ->
        ReviewScreen(result, onClose = { review = null }, onShare = {
            share((reviewRecord?.toText().orEmpty()) + "\n" + PostGameAnalyzer.report(result))
        })
    }

    notice?.let { text ->
        AlertDialog(
            onDismissRequest = { notice = null },
            text = { Text(text) },
            confirmButton = { TextButton(onClick = { notice = null }) { Text("OK") } }
        )
    }
}

private fun isActive(snapshot: GameSession.Snapshot?, seat: Int, caughtUp: Boolean): Boolean =
    caughtUp && snapshot?.stage() == GameSession.Stage.TRICK_PLAY && snapshot.seats()[seat].toMove()

private fun contractPhrase(snapshot: GameSession.Snapshot): String {
    val suit = snapshot.contractSuit()?.let(::suitLabel).orEmpty()
    val tout = if (snapshot.tout()) " Tout" else ""
    return when (snapshot.contractType()) {
        GameType.RUFSPIEL -> "auf die $suit-Sau"
        GameType.FARBSOLO -> "$suit-Solo$tout"
        GameType.WENZ -> "Wenz$tout"
        GameType.FARBWENZ -> "$suit-Wenz"
        else -> snapshot.contractName().orEmpty()
    }
}

@Composable
private fun OpponentSeat(
    name: String, score: Int, cards: Int, eyes: Int, call: String?, active: Boolean, caller: Boolean,
    modifier: Modifier = Modifier
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        NamePill("$name: $score", OpponentBlue, highlight = active)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FannedBacks(cards)
            if (eyes > 0) TrickPile(eyes)
        }
        if (caller) CallerBadge()
        call?.let {
            Box(Modifier.clip(RoundedCornerShape(10.dp)).background(Color(0xF2FFFFFF)).padding(horizontal = 10.dp, vertical = 3.dp)) {
                Text(it, color = Color(0xFF2A2118), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun FannedBacks(count: Int) {
    val step = 8.dp
    Box(Modifier.width(36.dp + step * (maxOf(count, 1) - 1)).height(58.dp)) {
        repeat(count) { index -> CardBack(Modifier.offset(x = step * index).size(36.dp, 58.dp)) }
    }
}

@Composable
private fun TrickPile(eyes: Int) {
    Box(contentAlignment = Alignment.Center) {
        CardBack(Modifier.size(28.dp, 44.dp))
        Box(Modifier.clip(RoundedCornerShape(4.dp)).background(Color(0xE6FFFFFF)).padding(horizontal = 3.dp)) {
            Text("$eyes", color = Color(0xFF2A2118), fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun CallerBadge() {
    Box(Modifier.clip(RoundedCornerShape(6.dp)).background(Gold).padding(horizontal = 6.dp, vertical = 1.dp)) {
        Text("Spieler", color = Color(0xFF2A2118), fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun NamePill(text: String, color: Color, highlight: Boolean, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier
            .clip(shape)
            .background(color)
            .border(2.dp, if (highlight) Gold else Color.Transparent, shape)
            .padding(horizontal = 9.dp, vertical = 2.dp)
    ) {
        Text(text, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, fontFamily = FontFamily.Serif)
    }
}

@Composable
private fun Caption(text: String, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(10.dp)).background(Color(0xB3000000)).padding(horizontal = 10.dp, vertical = 4.dp)) {
        Text(text, color = Color(0xFFF3EBD8), fontSize = 13.sp)
    }
}

@Composable
private fun TrickArea(cards: List<Pair<Int, GameCard>>, modifier: Modifier = Modifier) {
    Box(modifier.size(230.dp, 270.dp), contentAlignment = Alignment.Center) {
        cards.forEach { (seat, card) ->
            val (dx, dy, angle) = when (seat) {
                0 -> Triple((-60).dp, (-6).dp, -9f)
                1 -> Triple(0.dp, (-62).dp, 4f)
                2 -> Triple(60.dp, (-4).dp, 8f)
                else -> Triple(4.dp, 60.dp, -3f)
            }
            key(card) {
                PlayingCard(card, Modifier.offset(dx, dy).rotate(angle).size(72.dp, 120.dp))
            }
        }
    }
}

@Composable
private fun HandRow(
    hand: List<GameCard>, legal: List<GameCard>, canPlay: Boolean, selected: GameCard?,
    onTap: (GameCard) -> Unit, onSwipe: (GameCard) -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxWidth().height(172.dp).padding(horizontal = 6.dp)) {
        val cardWidth = 80.dp
        val cardHeight = 132.dp
        if (hand.isEmpty()) return@BoxWithConstraints
        val step = if (hand.size == 1) 0.dp else minOf(cardWidth * 0.98f, (maxWidth - cardWidth) / (hand.size - 1))
        val start = (maxWidth - cardWidth - step * (hand.size - 1)) / 2
        hand.forEachIndexed { index, card ->
            key(card) {
                val isLegal = card in legal
                val lift by animateDpAsState(if (card == selected) (-28).dp else 0.dp, label = "lift")
                PlayingCard(
                    card,
                    Modifier
                        .offset(start + step * index, 32.dp + lift)
                        .size(cardWidth, cardHeight)
                        .pointerInput(card, canPlay, isLegal) {
                            detectTapGestures { if (canPlay && isLegal) onTap(card) }
                        }
                        .pointerInput(card, canPlay, isLegal) {
                            var dragged = 0f
                            detectVerticalDragGestures(
                                onDragStart = { dragged = 0f },
                                onDragEnd = { if (dragged < -60f && canPlay && isLegal) onSwipe(card) }
                            ) { change, amount ->
                                dragged += amount
                                change.consume()
                            }
                        },
                    dimmed = canPlay && !isLegal
                )
            }
        }
    }
}

@Composable
private fun BiddingPanel(
    snapshot: GameSession.Snapshot, names: List<String>, soloSuit: Suit,
    onSuit: (Suit) -> Unit, onAnnounce: (String) -> Unit, onPass: () -> Unit, modifier: Modifier = Modifier
) {
    Surface(modifier, shape = RoundedCornerShape(16.dp), color = Color(0xEB231B14), shadowElevation = 8.dp) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Spielst du?", color = Color(0xFFF3EBD8), fontSize = 20.sp, fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold)
            Text(
                (0..2).joinToString("  ·  ") { "${names[it]}: ${snapshot.seatCalls()[it] ?: "–"}" },
                color = Color(0xFFCBBFA6), fontSize = 13.sp
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(Suit.EICHEL, Suit.GRAS, Suit.HERZ, Suit.SCHELLEN).forEach { suit ->
                    FilterChip(
                        selected = soloSuit == suit,
                        onClick = { onSuit(suit) },
                        label = { Text(suitLabel(suit), fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = Color(0xFF3B3027), labelColor = Color.White,
                            selectedContainerColor = Gold, selectedLabelColor = Color(0xFF2A2118)
                        )
                    )
                }
            }
            Text("Farbe gilt für Solo", color = Color(0xFF9C9079), fontSize = 11.sp)
            snapshot.bidOptions().chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { option ->
                        Button(
                            onClick = { onAnnounce(option.id()) },
                            enabled = option.enabled(),
                            modifier = Modifier.weight(1f).height(44.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFF1E7CF), contentColor = Color(0xFF2A2118),
                                disabledContainerColor = Color(0xFF4A4038), disabledContentColor = Color(0xFF8E8372)
                            )
                        ) { Text(option.label(), fontSize = 13.sp, textAlign = TextAlign.Center) }
                    }
                    if (row.size == 1) Box(Modifier.weight(1f))
                }
            }
            Button(
                onClick = onPass,
                modifier = Modifier.fillMaxWidth().height(46.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Color(0xFF2A2118))
            ) { Text("Weiter", fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun ResultDialog(
    result: GameSession.Outcome, names: List<String>, scores: List<Int>, contractLine: String,
    canReview: Boolean, onReview: () -> Unit, onNext: () -> Unit
) {
    val background = if (result.humanWon()) Color(0xFF2E6B3A) else Color(0xFF8E2A24)
    Dialog(onDismissRequest = { }) {
        Surface(shape = RoundedCornerShape(18.dp), color = background, shadowElevation = 10.dp) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (result.humanWon()) "Gewonnen!" else "Verloren", color = Color.White, fontSize = 28.sp,
                    fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold)
                Text(contractLine, color = Color(0xFFF3EBD8))
                Text("Deine Partei: ${result.humanPartyEyes()} Augen", color = Color.White, fontWeight = FontWeight.SemiBold)
                Text(result.description(), color = Color(0xFFE8DCC4), fontSize = 12.sp)
                Column(Modifier.padding(vertical = 6.dp)) {
                    names.forEachIndexed { seat, name ->
                        val points = result.seatPoints()[seat]
                        Row(Modifier.fillMaxWidth()) {
                            Text(name, color = Color.White, modifier = Modifier.weight(1f))
                            Text(if (points > 0) "+$points" else "$points", color = Color.White,
                                fontWeight = FontWeight.Bold, modifier = Modifier.width(60.dp), textAlign = TextAlign.End)
                            Text("${scores[seat]}", color = Color(0xFFE8DCC4), modifier = Modifier.width(70.dp),
                                textAlign = TextAlign.End)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (canReview) {
                        Button(onClick = onReview, modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0x33FFFFFF), contentColor = Color.White)
                        ) { Text("Nachkarteln") }
                    }
                    Button(onClick = onNext, modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Color(0xFF2A2118))
                    ) { Text("Neues Spiel") }
                }
            }
        }
    }
}

@Composable
private fun DeckCutGraphic(onCut: (Int) -> Unit) {
    var cutCards by remember { mutableIntStateOf(16) }
    var cutting by remember { mutableStateOf(false) }
    val progress = remember { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Tippe auf die Stelle, an der abgehoben wird.")
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .semantics { contentDescription = "Gemischter Kartenstapel. Tippe auf die Schnittstelle." }
                .pointerInput(cutting) {
                    detectTapGestures { offset ->
                        if (!cutting) {
                            val selectedCut = ((offset.y / size.height) * 31f).toInt().coerceIn(0, 30) + 1
                            cutCards = selectedCut
                            cutting = true
                            coroutineScope.launch {
                                progress.animateTo(1f, animationSpec = tween(700))
                                onCut(selectedCut)
                            }
                        }
                    }
                }
        ) {
            val spacing = size.height / 38f
            val cardHeight = spacing * 6f
            val cardWidth = size.width * 0.78f
            val left = (size.width - cardWidth) / 2f
            for (index in 31 downTo 0) {
                val destinationIndex = if (index < cutCards) 32 - cutCards + index else index - cutCards
                val startY = spacing + index * spacing
                val endY = spacing + destinationIndex * spacing
                val top = startY + (endY - startY) * progress.value
                drawRoundRect(Color(0xFF2B6CB8), Offset(left, top), Size(cardWidth, cardHeight),
                    androidx.compose.ui.geometry.CornerRadius(8f, 8f))
                drawRoundRect(Color.White, Offset(left + 3f, top + 2f), Size(cardWidth - 6f, cardHeight - 4f),
                    androidx.compose.ui.geometry.CornerRadius(6f, 6f))
            }
            if (!cutting) {
                val cutLineY = spacing + cutCards * spacing
                drawLine(Gold, Offset(left - 8f, cutLineY), Offset(left + cardWidth + 8f, cutLineY), strokeWidth = 5f)
            }
        }
        Text(if (cutting) "$cutCards Karten wandern unter den Rest …" else "Schnittstelle: $cutCards Karten")
    }
}

private fun sortHandForSnapshot(snapshot: GameSession.Snapshot): List<GameCard> {
    val player = Player("sort", "Sort")
    val contract = when (snapshot.contractType()) {
        GameType.RUFSPIEL -> RufspielContract(player, snapshot.contractSuit() ?: Suit.EICHEL)
        GameType.FARBSOLO -> SoloContract(player, snapshot.contractSuit() ?: Suit.EICHEL, snapshot.tout())
        GameType.WENZ -> if (snapshot.contractSuit() == null) {
            WenzContract(player, snapshot.tout())
        } else {
            WenzContract(player, snapshot.contractSuit())
        }
        GameType.FARBWENZ -> WenzContract(player, snapshot.contractSuit())
        GameType.GEIER -> GeierContract(player)
        GameType.BETTEL -> BettelContract(player)
        GameType.RAMSCH -> RamschContract()
        GameType.KREUZBOCK -> KreuzbockContract()
        null -> RufspielContract(player, Suit.EICHEL)
    }
    return HandDisplayOrder.sortForContract(snapshot.hand(), contract)
}

internal fun suitLabel(suit: Suit): String = when (suit) {
    Suit.SCHELLEN -> "Schelln"
    else -> suit.displayName()
}

private fun shuffledDeck(): Deck = Deck().apply { shuffle(Random()) }
