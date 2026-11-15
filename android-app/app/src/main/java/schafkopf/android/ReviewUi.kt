package schafkopf.android

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import schafkopf.analysis.GameRecord
import schafkopf.analysis.GameRecordParser
import schafkopf.analysis.PostGameAnalyzer
import java.util.Locale

private val Paper = Color(0xFFF4EEDF)
private val DarkInk = Color(0xFF2A2118)

private fun verdictColor(verdict: PostGameAnalyzer.Verdict): Color = when (verdict) {
    PostGameAnalyzer.Verdict.FEHLER -> Color(0xFFC62828)
    PostGameAnalyzer.Verdict.RUECKSCHAU -> Color(0xFFB7791F)
    PostGameAnalyzer.Verdict.IN_ORDNUNG -> Color(0xFF2E7D32)
    PostGameAnalyzer.Verdict.ZWANGSZUG -> Color(0xFF7A746A)
}

@Composable
internal fun ReviewScreen(review: PostGameAnalyzer.GameReview, onClose: () -> Unit, onShare: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = Paper) {
            LazyColumn(
                modifier = Modifier.safeDrawingPadding(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text("NACHKARTELN", color = Color(0xFF6B5B45), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "${review.playerName()} · ${if (review.won()) "gewonnen" else "verloren"} mit ${review.teamEyes()} Augen",
                        color = DarkInk, fontSize = 22.sp, fontWeight = FontWeight.Bold
                    )
                    Text(
                        String.format(Locale.GERMAN, "Gewinnchance beim ersten eigenen Zug: %.0f %%",
                            review.firstDecisionWinChance() * 100),
                        color = DarkInk
                    )
                    Text(conclusion(review), color = DarkInk, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 6.dp))
                }
                items(review.decisions()) { decision -> DecisionCard(decision) }
                item {
                    Text(
                        "Bewertet wird nur mit dem Wissen zum Zeitpunkt des Zuges: jede Alternative wird in denselben " +
                            "möglichen Kartenverteilungen zu Ende gespielt. Die Fortsetzungen spielt der vorhandene Bot – " +
                            "die Bewertung ist nur so gut wie er. Signale werden noch nicht gedeutet.",
                        color = Color(0xFF6B5B45), fontSize = 12.sp
                    )
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = onShare, modifier = Modifier.weight(1f)) { Text("Teilen") }
                        Button(
                            onClick = onClose, modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5B3A1E))
                        ) { Text("Schließen") }
                    }
                }
            }
        }
    }
}

private fun conclusion(review: PostGameAnalyzer.GameReview): String {
    val mistakes = review.mistakes()
    return when {
        review.won() && mistakes == 0L -> "Gewonnen ohne erkennbaren Fehler."
        review.won() -> "Gewonnen, aber mit $mistakes vermeidbaren Fehler(n)."
        mistakes == 0L -> "Verloren ohne vermeidbaren Fehler – Pech mit der Kartenverteilung."
        else -> "Verloren mit $mistakes vermeidbaren Fehler(n)."
    }
}

@Composable
private fun DecisionCard(decision: PostGameAnalyzer.DecisionReview) {
    val color = verdictColor(decision.verdict())
    Surface(shape = RoundedCornerShape(12.dp), color = Color.White, shadowElevation = 1.dp) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PlayingCard(decision.played(), Modifier.width(38.dp).height(62.dp))
                Column(Modifier.weight(1f)) {
                    Text("Stich ${decision.trickNumber()} · ${decision.positionInTrick()}. Karte im Stich",
                        color = Color(0xFF6B5B45), fontSize = 12.sp)
                    Text(decision.played().shortName(), color = DarkInk, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                }
                Box(Modifier.clip(RoundedCornerShape(50)).background(color).padding(horizontal = 10.dp, vertical = 4.dp)) {
                    Text(decision.verdict().label(), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
            if (decision.verdict() != PostGameAnalyzer.Verdict.ZWANGSZUG) {
                decision.alternatives().forEach { move -> AlternativeRow(move, move.card() == decision.played()) }
                when (decision.verdict()) {
                    PostGameAnalyzer.Verdict.FEHLER -> Text(
                        "Mit dem damaligen Wissen war ${decision.better().card().shortName()} klar besser.",
                        color = color, fontWeight = FontWeight.SemiBold)
                    PostGameAnalyzer.Verdict.RUECKSCHAU -> Text(
                        "Nur mit Kenntnis aller Karten wäre ${decision.better().card().shortName()} besser gewesen – kein Fehler.",
                        color = color)
                    else -> Unit
                }
            }
        }
    }
}

@Composable
private fun AlternativeRow(move: PostGameAnalyzer.MoveAssessment, played: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(if (played) "▶" else " ", color = DarkInk, modifier = Modifier.width(12.dp))
        Text(move.card().shortName(), color = DarkInk, fontSize = 13.sp,
            fontWeight = if (played) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.width(118.dp))
        Box(Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(5.dp)).background(Color(0xFFE6DFCF))) {
            Box(Modifier.fillMaxWidth(move.winRate().toFloat()).height(10.dp).background(Color(0xFF5B8C5A)))
        }
        Text(String.format(Locale.GERMAN, "%3.0f %%", move.winRate() * 100), color = DarkInk, fontSize = 12.sp,
            modifier = Modifier.width(40.dp))
        Text(String.format(Locale.GERMAN, "%.0f A.", move.meanEyes()), color = Color(0xFF6B5B45), fontSize = 12.sp,
            modifier = Modifier.width(40.dp))
    }
}

private const val IMPORT_EXAMPLE = """spieler: Du, Max, Daniel, Stefan
ansage: Du sauspiel eichel
stich: Max Schellen-9, Daniel Herz-7, Stefan Eichel-Ober, Du Gras-Ober
stich: ..."""

@Composable
internal fun ImportGameDialog(onDismiss: () -> Unit, onAnalyze: (GameRecord, Int) -> Unit) {
    var text by remember { mutableStateOf("") }
    var player by remember { mutableStateOf("Du") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Partie eingeben") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Spieler in Sitzreihenfolge, die Ansage und alle 8 Stiche – jeweils beginnend mit dem, der ausspielt. " +
                    "Aus den Stichen ergeben sich alle Hände.", fontSize = 12.sp)
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it; error = null },
                    placeholder = { Text(IMPORT_EXAMPLE, fontSize = 11.sp, fontFamily = FontFamily.Monospace) },
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, fontFamily = FontFamily.Monospace),
                    modifier = Modifier.fillMaxWidth().height(240.dp)
                )
                OutlinedTextField(
                    value = player,
                    onValueChange = { player = it; error = null },
                    label = { Text("Analysieren für") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let { Text(it, color = Color(0xFFC62828), fontSize = 12.sp) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                try {
                    val record = GameRecordParser.parse(text)
                    val seat = record.playerNames().indexOfFirst { it.equals(player.trim(), ignoreCase = true) }
                    if (seat < 0) {
                        error = "„${player.trim()}“ kommt nicht vor: ${record.playerNames().joinToString()}"
                    } else {
                        onAnalyze(record, seat)
                    }
                } catch (invalid: IllegalArgumentException) {
                    error = invalid.message
                }
            }) { Text("Nachkarteln") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
        containerColor = Paper
    )
}

@Composable
internal fun BusyOverlay(message: String) {
    Box(Modifier.fillMaxSize().background(Color(0x99000000)), contentAlignment = Alignment.Center) {
        Surface(shape = RoundedCornerShape(14.dp), color = Paper) {
            Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                androidx.compose.material3.CircularProgressIndicator(Modifier.size(28.dp), color = Color(0xFF5B3A1E))
                Text(message, color = DarkInk)
            }
        }
    }
}
