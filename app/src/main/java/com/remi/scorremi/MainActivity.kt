package com.remi.scorremi

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { RemiScorerApp(applicationContext) } }
    }
}

@Serializable
data class MatchRound(val scores: List<Int>)

@Serializable
data class Match(
    val id: Long,
    val name: String,
    val startDate: Long,
    val targetScore: Int,
    val players: List<String>,
    val rounds: List<MatchRound> = emptyList(),
    val finished: Boolean = false
)

@Serializable
data class AppState(val activeMatch: Match? = null, val history: List<Match> = emptyList())

class Storage(context: Context) {
    private val prefs = context.getSharedPreferences("remi_scorer", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun load(): AppState = prefs.getString("state", null)?.let { json.decodeFromString<AppState>(it) } ?: AppState()

    fun save(state: AppState) {
        prefs.edit().putString("state", json.encodeToString(state)).apply()
    }
}

private fun Match.totals(): List<Int> = players.indices.map { idx -> rounds.sumOf { it.scores[idx] } }
private fun Match.winnerIndex(): Int = totals().indices.minByOrNull { totals()[it] } ?: 0

@Composable
fun RemiScorerApp(context: Context) {
    val storage = remember { Storage(context) }
    var state by remember { mutableStateOf(storage.load()) }
    val nav = rememberNavController()
    val snackbarHost = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    fun persist(newState: AppState) {
        state = newState
        storage.save(newState)
    }

    Scaffold(snackbarHost = { SnackbarHost(hostState = snackbarHost) }) { pad ->
        NavHost(navController = nav, startDestination = "home", modifier = Modifier.padding(pad)) {
            composable("home") {
                HomeScreen(
                    hasActive = state.activeMatch != null,
                    onNew = { nav.navigate("new") },
                    onContinue = { nav.navigate("active") },
                    onHistory = { nav.navigate("history") },
                    onStats = { nav.navigate("stats") }
                )
            }
            composable("new") {
                NewMatchScreen(
                    onBack = { nav.popBackStack() },
                    onCreate = { match ->
                        persist(state.copy(activeMatch = match))
                        nav.navigate("active") { popUpTo("home") }
                    }
                )
            }
            composable("active") {
                val match = state.activeMatch
                if (match == null) {
                    LaunchedEffect(Unit) { nav.navigate("home") }
                } else {
                    ActiveMatchScreen(
                        match = match,
                        onBack = { nav.popBackStack() },
                        onUpdate = { updated -> persist(state.copy(activeMatch = updated)) },
                        onFinish = { finalMatch ->
                            persist(
                                state.copy(
                                    activeMatch = null,
                                    history = listOf(finalMatch.copy(finished = true)) + state.history
                                )
                            )
                            scope.launch { snackbarHost.showSnackbar("Meci salvat în istoric") }
                            nav.navigate("home") { popUpTo("home") { inclusive = true } }
                        }
                    )
                }
            }
            composable("history") {
                HistoryScreen(
                    history = state.history,
                    onBack = { nav.popBackStack() },
                    onOpen = { id -> nav.navigate("details/$id") },
                    onDelete = { id -> persist(state.copy(history = state.history.filterNot { it.id == id })) }
                )
            }
            composable("details/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { back ->
                state.history.firstOrNull { it.id == back.arguments?.getLong("id") }?.let { match ->
                    MatchDetailsScreen(match = match, onBack = { nav.popBackStack() })
                }
            }
            composable("stats") { StatsScreen(history = state.history, onBack = { nav.popBackStack() }) }
        }
    }
}

@Composable
fun HomeScreen(hasActive: Boolean, onNew: () -> Unit, onContinue: () -> Unit, onHistory: () -> Unit, onStats: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Scor REMI", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Button(onClick = onNew, modifier = Modifier.fillMaxWidth()) { Text("Meci nou") }
        if (hasActive) Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) { Text("Continuă meciul") }
        Button(onClick = onHistory, modifier = Modifier.fillMaxWidth()) { Text("Istoric meciuri") }
        Button(onClick = onStats, modifier = Modifier.fillMaxWidth()) { Text("Statistici") }
    }
}

@Composable
fun NewMatchScreen(onBack: () -> Unit, onCreate: (Match) -> Unit) {
    var name by remember { mutableStateOf("") }
    var players by remember { mutableStateOf(2) }
    val playerNames = remember { mutableStateListOf("", "", "", "") }
    var target by remember { mutableStateOf("1000") }
    val generated = listOf("Bătălia cărților rebele", "Remiul legendar de pe canapea", "Duelul asului pierdut")

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Meci nou", style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Denumire meci") }, modifier = Modifier.fillMaxWidth())
        Button(onClick = { name = generated.random() }) { Text("Generează nume") }

        Text("Număr jucători")
        Row {
            (2..4).forEach { n ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = players == n, onClick = { players = n })
                    Text("$n")
                }
            }
        }

        repeat(players) { i ->
            OutlinedTextField(
                value = playerNames[i],
                onValueChange = { playerNames[i] = it },
                label = { Text("Jucător ${i + 1}") },
                modifier = Modifier.fillMaxWidth()
            )
        }

        OutlinedTextField(value = target, onValueChange = { target = it.filter(Char::isDigit) }, label = { Text("Scor de câștig") }, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(500, 1000, 1500, 2000).forEach { v -> Button(onClick = { target = "$v" }) { Text("$v") } }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onBack) { Text("Înapoi") }
            Button(onClick = {
                val safePlayers = (0 until players).map { i -> playerNames[i].ifBlank { "Jucător ${i + 1}" } }
                val title = name.ifBlank { "Meci REMI ${SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date())}" }
                onCreate(
                    Match(
                        id = Random.nextLong(),
                        name = title,
                        startDate = System.currentTimeMillis(),
                        targetScore = target.toIntOrNull() ?: 1000,
                        players = safePlayers
                    )
                )
            }) { Text("Start") }
        }
    }
}

@Composable
fun ActiveMatchScreen(match: Match, onBack: () -> Unit, onUpdate: (Match) -> Unit, onFinish: (Match) -> Unit) {
    val totals = match.totals()
    val ranking = match.players.indices.sortedBy { totals[it] }
    val inputs = remember(match.rounds.size) { mutableStateListOf(*Array(match.players.size) { "0" }) }
    var confirmUndo by remember { mutableStateOf(false) }
    var showWinner by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }

    LaunchedEffect(match.rounds.size) {
        if (match.rounds.isNotEmpty() && totals.any { it >= match.targetScore }) showWinner = true
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(match.name, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            TextButton(onClick = { showInfo = true }) { Text("ℹ️") }
            TextButton(onClick = onBack) { Text("Acasă") }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text("Clasament")
                ranking.forEachIndexed { idx, playerIndex ->
                    val leaderBg = if (idx == 0) Color(0xFFDFF7DF) else Color.Transparent
                    Row(
                        modifier = Modifier.fillMaxWidth().background(leaderBg).padding(4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Loc ${idx + 1}: ${match.players[playerIndex]}")
                        Text("${totals[playerIndex]} pct")
                    }
                }
            }
        }

        Text("Adaugă rundă")
        match.players.forEachIndexed { idx, player ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(player, modifier = Modifier.width(90.dp))
                OutlinedTextField(
                    value = inputs[idx],
                    onValueChange = { inputs[idx] = it.filter(Char::isDigit) },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                listOf(0, 5, 10, 25, 50, 100).forEach { quick ->
                    TextButton(onClick = { inputs[idx] = "$quick" }) { Text("$quick") }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                val scores = inputs.map { it.toIntOrNull() ?: 0 }
                onUpdate(match.copy(rounds = match.rounds + MatchRound(scores)))
            }) { Text("Adaugă runda") }
            Button(onClick = { confirmUndo = true }, enabled = match.rounds.isNotEmpty()) { Text("Anulează ultima") }
            TextButton(onClick = { onFinish(match) }) { Text("Termină meci") }
        }

        Text("Istoric runde")
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(match.rounds.indices.toList()) { r ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Runda ${r + 1}")
                    Text(match.rounds[r].scores.joinToString(" | ") { score -> if (score == 0) "🟢0" else if (score >= 100) "🔴$score" else "$score" })
                }
            }
            item { Text("Total: ${totals.joinToString(" | ")}", fontWeight = FontWeight.Bold) }
        }
    }

    if (confirmUndo) {
        AlertDialog(
            onDismissRequest = { confirmUndo = false },
            confirmButton = {
                Button(onClick = {
                    onUpdate(match.copy(rounds = match.rounds.dropLast(1)))
                    confirmUndo = false
                }) { Text("Da") }
            },
            dismissButton = { TextButton(onClick = { confirmUndo = false }) { Text("Nu") } },
            title = { Text("Ștergere rundă") },
            text = { Text("Sigur dorești anularea ultimei runde?") }
        )
    }

    if (showWinner) {
        val winnerIndex = match.winnerIndex()
        AlertDialog(
            onDismissRequest = { showWinner = false },
            confirmButton = { Button(onClick = { showWinner = false }) { Text("Continuă") } },
            dismissButton = { TextButton(onClick = { onFinish(match) }) { Text("Termină") } },
            title = { Text("🏆 Avem câștigător") },
            text = { Text("${match.players[winnerIndex]} are cel mai mic scor: ${totals[winnerIndex]} puncte") }
        )
    }

    if (showInfo) {
        AlertDialog(
            onDismissRequest = { showInfo = false },
            confirmButton = { Button(onClick = { showInfo = false }) { Text("OK") } },
            title = { Text("Informații meci") },
            text = {
                Text(
                    "Nume: ${match.name}\n" +
                        "Data start: ${SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(match.startDate))}\n" +
                        "Scor de câștig: ${match.targetScore}\n" +
                        "Runde: ${match.rounds.size}\n" +
                        "Clasament: ${ranking.mapIndexed { i, p -> "${i + 1}. ${match.players[p]} (${totals[p]})" }.joinToString(", ")}"
                )
            }
        )
    }
}

@Composable
fun HistoryScreen(history: List<Match>, onBack: () -> Unit, onOpen: (Long) -> Unit, onDelete: (Long) -> Unit) {
    var deleteId by remember { mutableStateOf<Long?>(null) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row {
            Text("Istoric", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            TextButton(onClick = onBack) { Text("Înapoi") }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(history) { match ->
                val totals = match.totals()
                val winner = match.players[match.winnerIndex()]
                Card(modifier = Modifier.fillMaxWidth().clickable { onOpen(match.id) }) {
                    Column(Modifier.padding(10.dp)) {
                        Text(match.name, fontWeight = FontWeight.Bold)
                        Text(SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(match.startDate)))
                        Text(if (match.finished) "Terminat" else "În desfășurare")
                        Text(match.players.zip(totals).joinToString { "${it.first}: ${it.second}" })
                        if (match.finished) Text("Câștigător: $winner")
                        TextButton(onClick = { deleteId = match.id }) { Text("Șterge") }
                    }
                }
            }
        }
    }

    if (deleteId != null) {
        AlertDialog(
            onDismissRequest = { deleteId = null },
            confirmButton = { Button(onClick = { onDelete(deleteId!!); deleteId = null }) { Text("Șterge") } },
            dismissButton = { TextButton(onClick = { deleteId = null }) { Text("Anulează") } },
            title = { Text("Confirmare") },
            text = { Text("Ștergi meciul selectat?") }
        )
    }
}

@Composable
fun MatchDetailsScreen(match: Match, onBack: () -> Unit) {
    val totals = match.totals()
    val winner = match.players[match.winnerIndex()]

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(match.name, style = MaterialTheme.typography.headlineSmall)
        Text("Câștigător: $winner")
        Text("Scor final: ${match.players.zip(totals).joinToString { "${it.first} ${it.second}" }}")
        match.rounds.forEachIndexed { i, round ->
            Text("Runda ${i + 1}: ${round.scores.joinToString(" | ")}")
        }
        TextButton(onClick = onBack) { Text("Înapoi") }
    }
}

data class PlayerStats(
    val name: String,
    val matches: Int,
    val wins: Int,
    val rounds: Int,
    val roundWins: Int,
    val avg: Double,
    val best: Int,
    val winRate: Double
)

@Composable
fun StatsScreen(history: List<Match>, onBack: () -> Unit) {
    val stats = remember(history) {
        val finishedMatches = history.filter { it.finished }
        val byName = mutableMapOf<String, MutableList<Pair<Match, Int>>>()

        finishedMatches.forEach { match ->
            match.players.forEachIndexed { idx, playerName ->
                byName.getOrPut(playerName) { mutableListOf() }.add(match to idx)
            }
        }

        byName.map { (name, participations) ->
            val wins = participations.count { (match, idx) -> match.winnerIndex() == idx }
            val rounds = participations.sumOf { it.first.rounds.size }
            val roundWins = participations.sumOf { (m, i) -> m.rounds.count { it.scores[i] == 0 } }
            val points = participations.sumOf { (m, i) -> m.rounds.sumOf { it.scores[i] } }
            val best = participations.minOfOrNull { (m, i) -> m.rounds.sumOf { it.scores[i] } } ?: 0
            PlayerStats(
                name = name,
                matches = participations.size,
                wins = wins,
                rounds = rounds,
                roundWins = roundWins,
                avg = if (rounds == 0) 0.0 else points.toDouble() / rounds,
                best = best,
                winRate = if (participations.isEmpty()) 0.0 else wins * 100.0 / participations.size
            )
        }.sortedByDescending { it.winRate }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row {
            Text("Statistici", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            TextButton(onClick = onBack) { Text("Înapoi") }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(stats) { s ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(10.dp)) {
                        Text(s.name, fontWeight = FontWeight.Bold)
                        Text("Meciuri jucate: ${s.matches}")
                        Text("Meciuri câștigate: ${s.wins} (${"%.1f".format(s.winRate)}%)")
                        Text("Runde jucate: ${s.rounds} | Runde câștigate: ${s.roundWins}")
                        Text("Punctaj mediu per rundă: ${"%.2f".format(s.avg)}")
                        Text("Cel mai mic punctaj într-un meci: ${s.best}")
                    }
                }
            }
        }
    }
}
