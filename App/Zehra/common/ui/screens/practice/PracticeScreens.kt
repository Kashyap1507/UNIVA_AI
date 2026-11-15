package com.kashyap.wizlyai.ui.screens.practice

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kashyap.wizlyai.network.models.CourseModule
import com.kashyap.wizlyai.network.models.PracticeAttempt
import com.kashyap.wizlyai.network.models.PracticeKind
import com.kashyap.wizlyai.network.models.ExamQuestion
import com.kashyap.wizlyai.network.models.PracticeSet
import com.kashyap.wizlyai.network.models.ReviewedQuestion
import com.kashyap.wizlyai.practice.ExamTimer
import com.kashyap.wizlyai.ui.components.CollapsibleTopBar
import com.kashyap.wizlyai.ui.state.UiState
import com.kashyap.wizlyai.ui.util.MathMarkdown
import com.kashyap.wizlyai.ui.viewmodel.CoursePracticeViewModel
import com.kashyap.wizlyai.ui.viewmodel.ExamSession
import com.kashyap.wizlyai.ui.viewmodel.ExamState
import com.kashyap.wizlyai.ui.viewmodel.ExamViewModel
import com.mikepenz.markdown.m3.Markdown
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock

private val Green = Color(0xFF16A34A)
private val Red = Color(0xFFDC2626)
private val Amber = Color(0xFFD97706)

private fun pct(value: Double?): String = value?.let { "${(it + 0.5).toInt()}%" } ?: "–"

private fun points(value: Double?): String = value?.let { if (it == it.toLong().toDouble()) it.toLong().toString() else it.toString() } ?: "0"

// --- Practice tab (course detail) ------------------------------------------------------------------

@Composable
fun CoursePracticeTab(
    viewModel: CoursePracticeViewModel,
    modules: List<CourseModule>,
    hasReadyMaterials: Boolean,
    onOpenSet: (setId: String, title: String) -> Unit,
    onOpenAttempt: (attemptId: String, title: String) -> Unit,
    onGoToMaterials: () -> Unit,
    showMessage: (String) -> Unit,
) {
    val setsState by viewModel.sets.collectAsState()
    val attempts by viewModel.attempts.collectAsState()
    val message by viewModel.message.collectAsState()
    var showGenerate by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<PracticeSet?>(null) }

    LaunchedEffect(Unit) { viewModel.load() }
    LaunchedEffect(message) {
        message?.let {
            showMessage(it)
            viewModel.clearMessage()
        }
    }

    val sets = (setsState as? UiState.Success)?.data.orEmpty()
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
        item {
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Quiz, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("Practice and mock exams", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Questions are written from your materials. Answers and explanations appear after you submit, with results by concept.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(12.dp))
                    if (hasReadyMaterials) {
                        Button(onClick = { showGenerate = true }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Create a set")
                        }
                    } else {
                        OutlinedButton(onClick = onGoToMaterials, modifier = Modifier.fillMaxWidth()) { Text("Upload materials first") }
                    }
                }
            }
        }
        when (val s = setsState) {
            is UiState.Error -> item { Text(s.message, color = MaterialTheme.colorScheme.error) }
            UiState.Loading, UiState.Idle -> item { Box(Modifier.fillMaxWidth().padding(24.dp)) { CircularProgressIndicator(Modifier.align(Alignment.Center)) } }
            else -> {}
        }
        items(sets, key = { it.id }) { set ->
            SetRow(set, onOpen = { if (set.isReady) onOpenSet(set.id, set.title ?: set.practiceKind.label) }, onDelete = { confirmDelete = set })
        }
        if (attempts.isNotEmpty()) {
            item { Text("Past attempts", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp)) }
            items(attempts.take(10), key = { "a_" + it.id }) { a ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onOpenAttempt(a.id, a.title ?: "Result") }.padding(vertical = 10.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(a.title ?: PracticeKind.from(a.kind).label, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(a.finishedAt?.take(10).orEmpty() + if (a.late) " · late" else "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(pct(a.scorePct), fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }

    if (showGenerate) {
        GenerateSheet(modules, onDismiss = { showGenerate = false }) { kind, count, moduleIds, limit ->
            showGenerate = false
            viewModel.generate(kind, count, moduleIds, limit)
        }
    }
    confirmDelete?.let { set ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete this set?") },
            text = { Text("Past attempts keep their scores.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = null
                    viewModel.delete(set.id)
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SetRow(
    set: PracticeSet,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth().clickable(enabled = set.isReady, onClick = onOpen),
    ) {
        Row(Modifier.padding(start = 14.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(set.practiceKind.label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Text(set.title ?: set.practiceKind.label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                when {
                    set.isProcessing ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Writing questions from your materials...", style = MaterialTheme.typography.bodySmall)
                        }
                    set.status == "FAILED" -> Text(set.error ?: "Couldn't create this set", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    else ->
                        Text(
                            listOfNotNull(
                                "${set.questionCount} questions",
                                set.timeLimitMin?.let { "$it min" },
                                set.bestScorePct?.let { "best ${pct(it)}" },
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                }
            }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Delete set", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GenerateSheet(
    modules: List<CourseModule>,
    onDismiss: () -> Unit,
    onGenerate: (PracticeKind, Int?, List<String>, Int?) -> Unit,
) {
    var kind by remember { mutableStateOf(PracticeKind.PRACTICE) }
    var size by remember { mutableStateOf<Int?>(null) }
    var limit by remember { mutableStateOf<Int?>(null) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    val sizes =
        when (kind) {
            PracticeKind.PRACTICE -> listOf(5, 10, 20)
            PracticeKind.LECTURE_QUIZ -> listOf(5, 10)
            PracticeKind.MOCK_EXAM -> listOf(10, 20, 30)
        }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text("Create a set", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Text("Type", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PracticeKind.entries.forEach { k ->
                    FilterChip(selected = kind == k, onClick = {
                        kind = k
                        size = null
                        limit = null
                    }, label = { Text(k.label) })
                }
            }
            Text(
                when (kind) {
                    PracticeKind.PRACTICE -> "Mixed questions to check your understanding, easier first."
                    PracticeKind.LECTURE_QUIZ -> "A short quiz on the main ideas. Pick the lectures below."
                    PracticeKind.MOCK_EXAM -> "A timed exam across the scope, marked like the real thing."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Text("Questions", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                sizes.forEach { n -> FilterChip(selected = (size ?: sizes[1.coerceAtMost(sizes.size - 1)]) == n, onClick = { size = n }, label = { Text("$n") }) }
            }
            if (kind == PracticeKind.MOCK_EXAM) {
                Spacer(Modifier.height(12.dp))
                Text("Time limit", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = limit == null, onClick = { limit = null }, label = { Text("Suggested") })
                    listOf(60, 90, 120).forEach { m -> FilterChip(selected = limit == m, onClick = { limit = m }, label = { Text("$m min") }) }
                }
            }
            if (modules.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text("Scope (none selected = whole course)", style = MaterialTheme.typography.labelLarge)
                Column(Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState())) {
                    modules.forEach { m ->
                        Row(
                            Modifier.fillMaxWidth().clickable { selected = if (m.id in selected) selected - m.id else selected + m.id },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = m.id in selected, onCheckedChange = { selected = if (it) selected + m.id else selected - m.id })
                            Text(listOfNotNull(m.week?.let { "Week $it" }, m.title).joinToString(" · "), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            val count = size ?: sizes[1.coerceAtMost(sizes.size - 1)]
            Button(onClick = { onGenerate(kind, count, selected.toList(), limit) }, modifier = Modifier.fillMaxWidth()) {
                Text("Create ${kind.label.lowercase()} ($count questions)")
            }
        }
    }
}

// --- Exam screen -------------------------------------------------------------------------------------

@Composable
fun ExamScreen(
    viewModel: ExamViewModel,
    title: String,
    onClose: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    var confirmSubmit by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }
    val taking = (state as? ExamState.Taking)?.session

    Scaffold(
        topBar = {
            CollapsibleTopBar(
                title = taking?.title ?: title,
                isCollapsed = false,
                showBackButton = true,
                collapsible = false,
                onBackClick = { if (taking != null) confirmLeave = true else onClose() },
                trailingContent = { taking?.deadline?.let { TimerChip(viewModel) } },
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val s = state) {
                ExamState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                is ExamState.Failed ->
                    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(12.dp))
                        Text(s.message, textAlign = TextAlign.Center)
                        TextButton(onClick = onClose) { Text("Close") }
                    }
                is ExamState.Taking -> Taking(s.session, viewModel) { confirmSubmit = true }
                is ExamState.Result -> ResultView(s.attempt, s.regrading, s.notice, onRegrade = viewModel::regrade, onDone = onClose)
            }
        }
    }

    if (confirmSubmit && taking != null) {
        AlertDialog(
            onDismissRequest = { confirmSubmit = false },
            title = { Text("Submit your answers?") },
            text = {
                Text(
                    if (taking.unanswered > 0) {
                        "${taking.unanswered} question${if (taking.unanswered == 1) " is" else "s are"} unanswered. You can't change answers after submitting."
                    } else {
                        "You can't change answers after submitting."
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmSubmit = false
                    viewModel.submit()
                }) { Text("Submit") }
            },
            dismissButton = { TextButton(onClick = { confirmSubmit = false }) { Text("Keep working") } },
        )
    }
    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text("Leave this attempt?") },
            text = { Text("Your answers won't be saved. You can start the set again later.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmLeave = false
                    onClose()
                }) { Text("Leave", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text("Stay") } },
        )
    }
}

@Composable
private fun TimerChip(viewModel: ExamViewModel) {
    val state by viewModel.state.collectAsState()
    val deadline = (state as? ExamState.Taking)?.session?.deadline
    var left by remember { mutableLongStateOf(ExamTimer.remainingSeconds(deadline, Clock.System.now()) ?: 0) }
    LaunchedEffect(deadline) {
        while (true) {
            left = ExamTimer.remainingSeconds(deadline, Clock.System.now()) ?: 0
            viewModel.tick()
            delay(1_000)
        }
    }
    val warning = ExamTimer.isWarning(left)
    Row(
        Modifier.clip(RoundedCornerShape(50)).background((if (warning) Amber else MaterialTheme.colorScheme.primary).copy(alpha = 0.12f)).padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Timer, contentDescription = "Time left", tint = if (warning) Amber else MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Text(ExamTimer.format(left), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false, color = if (warning) Amber else MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun Taking(
    session: ExamSession,
    viewModel: ExamViewModel,
    onSubmit: () -> Unit,
) {
    val q = session.current ?: return
    Column(Modifier.fillMaxSize()) {
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(session.questions, key = { _, it -> it.id }) { i, item ->
                val current = i == session.index
                val answered = session.isAnswered(item.id)
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (current) MaterialTheme.colorScheme.primary else if (answered) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent)
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = if (answered || current) 0f else 0.4f), CircleShape)
                        .clickable { viewModel.goTo(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("${i + 1}", color = if (current) Color.White else MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        LinearProgressIndicator(progress = { session.answeredCount.toFloat() / session.questions.size.coerceAtLeast(1) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Question ${session.index + 1} of ${session.questions.size}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                Text("${points(q.points)} mark${if (q.points == 1.0) "" else "s"}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))
            Markdown(content = MathMarkdown.prepare(q.prompt), modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(16.dp))
            AnswerInput(q, session.answers[q.id], viewModel)
            session.error?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
        HorizontalDivider()
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { viewModel.goTo(session.index - 1) }, enabled = session.index > 0, modifier = Modifier.weight(1f)) { Text("Previous") }
            if (session.index < session.questions.size - 1) {
                Button(onClick = { viewModel.goTo(session.index + 1) }, modifier = Modifier.weight(1f)) { Text("Next") }
            } else {
                Button(onClick = onSubmit, enabled = !session.submitting, modifier = Modifier.weight(1f)) {
                    if (session.submitting) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                        Spacer(Modifier.width(8.dp))
                        Text("Marking...")
                    } else {
                        Text("Submit")
                    }
                }
            }
        }
    }
}

@Composable
private fun AnswerInput(
    q: ExamQuestion,
    answer: Any?,
    viewModel: ExamViewModel,
) {
    when {
        q.isChoice ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                q.options.orEmpty().forEachIndexed { i, option ->
                    val chosen = answer == i
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, if (chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                            .background(if (chosen) MaterialTheme.colorScheme.primary.copy(alpha = 0.06f) else Color.Transparent)
                            .clickable { viewModel.choose(q.id, i) }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = chosen, onClick = { viewModel.choose(q.id, i) })
                        Markdown(content = MathMarkdown.prepare(option), modifier = Modifier.weight(1f))
                    }
                }
            }
        q.isNumeric ->
            OutlinedTextField(
                value = answer as? String ?: "",
                onValueChange = { viewModel.write(q.id, it) },
                label = { Text("Your answer" + (q.unit?.let { " ($it)" } ?: "")) },
                supportingText = { Text("A number, e.g. 3.5, -2e-3 or 7/2") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                modifier = Modifier.fillMaxWidth(),
            )
        else ->
            OutlinedTextField(
                value = answer as? String ?: "",
                onValueChange = { viewModel.write(q.id, it) },
                label = {
                    Text(
                        when (q.type) {
                            "PROOF" -> "Your proof"
                            "CODE" -> "Your code"
                            else -> "Your answer"
                        },
                    )
                },
                textStyle = if (q.type == "CODE") MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace) else MaterialTheme.typography.bodyLarge,
                minLines = if (q.type == "SHORT") 4 else 8,
                modifier = Modifier.fillMaxWidth(),
            )
    }
}

// --- Results -------------------------------------------------------------------------------------------

@Composable
private fun ResultView(
    attempt: PracticeAttempt,
    regrading: Boolean,
    notice: String?,
    onRegrade: () -> Unit,
    onDone: () -> Unit,
) {
    val review = attempt.review.orEmpty()
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
        item {
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))) {
                Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(pct(attempt.scorePct), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text("${points(attempt.points)} of ${points(attempt.maxPoints)} marks", style = MaterialTheme.typography.bodyMedium)
                    if (attempt.late) {
                        Spacer(Modifier.height(4.dp))
                        Text("Submitted after the time limit", style = MaterialTheme.typography.bodySmall, color = Amber)
                    }
                    if (attempt.ungraded > 0) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "${attempt.ungraded} written answer${if (attempt.ungraded == 1) " wasn't" else "s weren't"} marked yet and ${if (attempt.ungraded == 1) "isn't" else "aren't"} in the score.",
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                        )
                        TextButton(onClick = onRegrade, enabled = !regrading) { Text(if (regrading) "Marking..." else "Mark them now") }
                        notice?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Amber, textAlign = TextAlign.Center) }
                    }
                }
            }
        }
        val concepts = attempt.perConcept.values.sortedBy { it.fraction }
        if (concepts.isNotEmpty()) {
            item { Text("By concept", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold) }
            items(concepts, key = { "c_" + it.name }) { c ->
                Column {
                    Row {
                        Text(c.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${c.correct}/${c.total}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { c.fraction },
                        color = if (c.fraction >= 0.7f) Green else if (c.fraction >= 0.4f) Amber else Red,
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    )
                }
            }
        }
        item { Text("Questions", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp)) }
        itemsIndexed(review, key = { _, q -> "r_" + q.id }) { i, q -> ReviewCard(i + 1, q) }
        item { Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Done") } }
    }
}

@Composable
private fun ReviewCard(
    number: Int,
    q: ReviewedQuestion,
) {
    val r = q.result
    val (icon, color, label) =
        when {
            !r.isGraded -> Triple(Icons.Default.HourglassTop, MaterialTheme.colorScheme.onSurfaceVariant, "Not marked yet")
            r.correct == true -> Triple(Icons.Default.CheckCircle, Green, "${points(r.pointsAwarded)}/${points(q.points)}")
            (r.pointsAwarded ?: 0.0) > 0 -> Triple(Icons.Default.RemoveCircleOutline, Amber, "${points(r.pointsAwarded)}/${points(q.points)}")
            else -> Triple(Icons.Default.ErrorOutline, Red, "0/${points(q.points)}")
        }
    Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Question $number", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(label, color = color, style = MaterialTheme.typography.labelLarge)
            }
            q.conceptName?.let { Text("Concept: $it", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp)) }
            Spacer(Modifier.height(8.dp))
            Markdown(content = MathMarkdown.prepare(q.prompt), modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Labeled("Your answer", q.responseText ?: "No answer")
            val correct =
                when (q.type) {
                    "MCQ" -> q.correctOption?.let { q.options?.getOrNull(it) }
                    "NUMERIC" -> q.numericAnswer?.let { points(it) + (q.unit?.let { u -> " $u" } ?: "") }
                    else -> q.modelAnswer
                }
            correct?.let { Labeled(if (q.type in listOf("SHORT", "PROOF", "CODE")) "Model answer" else "Correct answer", it) }
            r.feedback?.takeIf { q.responseText != null }?.let { Labeled("Feedback", it) }
            q.explanation?.takeIf { it.isNotBlank() }?.let { Labeled("Explanation", it) }
            q.source?.label?.let { Text("From $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp)) }
        }
    }
}

@Composable
private fun Labeled(
    label: String,
    value: String,
) {
    Column(Modifier.padding(top = 6.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Markdown(content = MathMarkdown.prepare(value), modifier = Modifier.fillMaxWidth())
    }
}
