package com.kashyap.wizlyai.ui.screens.flashcards

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kashyap.wizlyai.network.models.CourseConcept
import com.kashyap.wizlyai.network.models.Flashcard
import com.kashyap.wizlyai.srs.ReviewGrade
import com.kashyap.wizlyai.srs.Sm2
import com.kashyap.wizlyai.ui.components.CollapsibleTopBar
import com.kashyap.wizlyai.ui.state.UiState
import com.kashyap.wizlyai.ui.util.MathMarkdown
import com.kashyap.wizlyai.ui.viewmodel.CourseFlashcardsViewModel
import com.kashyap.wizlyai.ui.viewmodel.FlashcardReviewViewModel
import com.kashyap.wizlyai.ui.viewmodel.ReviewSession
import com.mikepenz.markdown.m3.Markdown
import kotlinx.datetime.Clock

// --- Cards tab (course detail) ------------------------------------------------------------

@Composable
fun CourseCardsTab(
    viewModel: CourseFlashcardsViewModel,
    concepts: List<CourseConcept>,
    hasReadyMaterials: Boolean,
    onStudy: () -> Unit,
    onGoToMaterials: () -> Unit,
    showMessage: (String) -> Unit,
) {
    val state by viewModel.cards.collectAsState()
    val isGenerating by viewModel.isGenerating.collectAsState()
    val isOffline by viewModel.isOffline.collectAsState()
    val message by viewModel.message.collectAsState()
    var showGenerate by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Flashcard?>(null) }
    var adding by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.load() }
    LaunchedEffect(message) {
        message?.let {
            showMessage(it)
            viewModel.clearMessage()
        }
    }

    val cards = (state as? UiState.Success)?.data.orEmpty()
    val now = Clock.System.now()
    val due = cards.count { it.isDue(now) }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
        item {
            StudyCard(
                total = cards.size,
                due = due,
                isOffline = isOffline,
                isGenerating = isGenerating,
                hasReadyMaterials = hasReadyMaterials,
                onStudy = onStudy,
                onGenerate = { showGenerate = true },
                onAdd = { adding = true },
                onGoToMaterials = onGoToMaterials,
            )
        }
        when (val s = state) {
            is UiState.Error -> item { Text(s.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            UiState.Loading, UiState.Idle -> item { Box(Modifier.fillMaxWidth().padding(24.dp)) { CircularProgressIndicator(Modifier.align(Alignment.Center)) } }
            else -> {}
        }
        items(cards, key = { it.id }) { card -> CardRow(card, now) { editing = card } }
    }

    if (showGenerate) {
        GenerateSheet(
            concepts = concepts,
            onDismiss = { showGenerate = false },
            onGenerate = { count, conceptId ->
                showGenerate = false
                viewModel.generate(count, conceptId)
            },
        )
    }
    if (adding) {
        CardEditDialog(
            title = "New card",
            card = null,
            onDismiss = { adding = false },
            onSave = { front, back, _ ->
                adding = false
                viewModel.add(front, back)
            },
        )
    }
    editing?.let { card ->
        CardEditDialog(
            title = "Edit card",
            card = card,
            onDismiss = { editing = null },
            onSave = { front, back, suspended ->
                editing = null
                viewModel.edit(card.id, front, back, suspended)
            },
            onDelete = {
                editing = null
                viewModel.delete(card.id)
            },
        )
    }
}

@Composable
private fun StudyCard(
    total: Int,
    due: Int,
    isOffline: Boolean,
    isGenerating: Boolean,
    hasReadyMaterials: Boolean,
    onStudy: () -> Unit,
    onGenerate: () -> Unit,
    onAdd: () -> Unit,
    onGoToMaterials: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Style, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Flashcards", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (isOffline) {
                    Icon(Icons.Default.CloudOff, contentDescription = "Offline", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                when {
                    total == 0 -> "Turn your materials into cards, then review them a little each day. Univa AI schedules each card just before you'd forget it."
                    due == 0 -> "All caught up. $total card${if (total == 1) "" else "s"} in this course."
                    else -> "$due due now · $total in this course"
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(12.dp))
            if (due > 0) {
                Button(onClick = onStudy, modifier = Modifier.fillMaxWidth()) { Text("Study $due card${if (due == 1) "" else "s"}") }
                Spacer(Modifier.height(8.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when {
                    isGenerating ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f).padding(vertical = 10.dp)) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(10.dp))
                            Text("Writing cards from your materials...", style = MaterialTheme.typography.bodySmall)
                        }
                    hasReadyMaterials ->
                        OutlinedButton(onClick = onGenerate, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Generate")
                        }
                    else -> OutlinedButton(onClick = onGoToMaterials, modifier = Modifier.weight(1f)) { Text("Upload materials") }
                }
                OutlinedButton(onClick = onAdd, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add card")
                }
            }
        }
    }
}

@Composable
private fun CardRow(
    card: Flashcard,
    now: kotlinx.datetime.Instant,
    onClick: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(MathMarkdown.plain(card.front), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Text(MathMarkdown.plain(card.back), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(6.dp))
            Text(
                listOfNotNull(dueLabel(card, now), card.source?.label).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = if (card.isDue(now)) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun dueLabel(
    card: Flashcard,
    now: kotlinx.datetime.Instant,
): String {
    if (card.suspended) return "Paused"
    if (card.srs.isNew) return "New"
    val due = card.srs.dueInstant ?: return "Due"
    val days = ((due - now).inWholeHours + 23) / 24
    return if (days <= 0) "Due" else "Next in ${Sm2.describeInterval(days.toInt())}"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GenerateSheet(
    concepts: List<CourseConcept>,
    onDismiss: () -> Unit,
    onGenerate: (count: Int, conceptId: String?) -> Unit,
) {
    var count by remember { mutableStateOf(10) }
    var conceptId by remember { mutableStateOf<String?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text("Generate flashcards", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                "Cards are written from your uploaded materials and link back to the page they came from.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Text("How many", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(5, 10, 20).forEach { n -> FilterChip(selected = count == n, onClick = { count = n }, label = { Text("$n") }) }
            }
            if (concepts.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text("Focus", style = MaterialTheme.typography.labelLarge)
                Column(Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState())) {
                    ConceptOption("Whole course", conceptId == null) { conceptId = null }
                    concepts.forEach { c -> ConceptOption(c.name, conceptId == c.id) { conceptId = c.id } }
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(onClick = { onGenerate(count, conceptId) }, modifier = Modifier.fillMaxWidth()) { Text("Generate $count cards") }
        }
    }
}

@Composable
private fun ConceptOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.material3.RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun CardEditDialog(
    title: String,
    card: Flashcard?,
    onDismiss: () -> Unit,
    onSave: (front: String, back: String, suspended: Boolean) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    var front by remember { mutableStateOf(card?.front.orEmpty()) }
    var back by remember { mutableStateOf(card?.back.orEmpty()) }
    var suspended by remember { mutableStateOf(card?.suspended ?: false) }
    var confirmDelete by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = front, onValueChange = { front = it.take(300) }, label = { Text("Front (question)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = back, onValueChange = { back = it.take(1000) }, label = { Text("Back (answer)") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                Text("Maths: write LaTeX between \$ signs, e.g. \$x^2\$.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (card != null) {
                    card.source?.label?.let { Text("From $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Pause reviews", modifier = Modifier.weight(1f))
                        Switch(checked = suspended, onCheckedChange = { suspended = it })
                    }
                    if (onDelete != null) {
                        TextButton(onClick = { confirmDelete = true }) { Text("Delete card", color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(front, back, suspended) }, enabled = front.isNotBlank() && back.isNotBlank()) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
    if (confirmDelete && onDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this card?") },
            text = { Text("Its review history is kept, but the card won't come up again.") },
            confirmButton = { TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

// --- Review session ----------------------------------------------------------------------------

@Composable
fun FlashcardReviewScreen(
    viewModel: FlashcardReviewViewModel,
    title: String,
    onClose: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val pending by viewModel.pending.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    Scaffold(
        topBar = { CollapsibleTopBar(title = title, isCollapsed = false, showBackButton = true, collapsible = false, onBackClick = onClose) },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val s = state) {
                is UiState.Error ->
                    Centered {
                        Text(s.message, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(12.dp))
                        TextButton(onClick = onClose) { Text("Close") }
                    }
                is UiState.Success -> {
                    val session = s.data
                    when {
                        session.queue.isEmpty() ->
                            Centered {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF16A34A), modifier = Modifier.size(56.dp))
                                Spacer(Modifier.height(12.dp))
                                Text("Nothing due right now", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(4.dp))
                                Text("Come back later, or add cards from a course.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(16.dp))
                                Button(onClick = onClose) { Text("Done") }
                            }
                        session.isFinished -> Finished(session, pending, onClose, viewModel::syncNow)
                        else -> Reviewing(session, pending, viewModel)
                    }
                }
                else -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
        }
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) { content() }
}

@Composable
private fun Reviewing(
    session: ReviewSession,
    pending: Int,
    viewModel: FlashcardReviewViewModel,
) {
    val card = session.current ?: return
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${session.remaining} left", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            if (session.offline || pending > 0) {
                Icon(Icons.Default.CloudOff, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(4.dp))
                Text("Saved on this device", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { if (session.queue.isEmpty()) 0f else session.position.toFloat() / session.queue.size },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clickable(enabled = !session.revealed) { viewModel.reveal() },
        ) {
            Column(Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState())) {
                Text("QUESTION", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Markdown(content = MathMarkdown.prepare(card.front), modifier = Modifier.fillMaxWidth())
                AnimatedVisibility(session.revealed) {
                    Column {
                        Spacer(Modifier.height(20.dp))
                        Box(Modifier.fillMaxWidth().height(1.dp).padding(horizontal = 0.dp)) {
                            androidx.compose.material3.HorizontalDivider()
                        }
                        Spacer(Modifier.height(20.dp))
                        Text("ANSWER", style = MaterialTheme.typography.labelSmall, color = Color(0xFF16A34A), fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Markdown(content = MathMarkdown.prepare(card.back), modifier = Modifier.fillMaxWidth())
                        card.source?.label?.let {
                            Spacer(Modifier.height(12.dp))
                            Text("From $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        if (!session.revealed) {
            Button(onClick = { viewModel.reveal() }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Show answer") }
        } else {
            Text(
                "How well did you know it?",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ReviewGrade.entries.forEach { grade ->
                    GradeButton(grade, session.preview(grade), Modifier.weight(1f)) { viewModel.grade(grade) }
                }
            }
        }
    }
}

@Composable
private fun GradeButton(
    grade: ReviewGrade,
    preview: String,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val color =
        when (grade) {
            ReviewGrade.AGAIN -> Color(0xFFDC2626)
            ReviewGrade.HARD -> Color(0xFFD97706)
            ReviewGrade.GOOD -> Color(0xFF16A34A)
            ReviewGrade.EASY -> Color(0xFF2563EB)
        }
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = color.copy(alpha = 0.12f), contentColor = color),
        contentPadding = PaddingValues(vertical = 10.dp, horizontal = 4.dp),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(grade.label, fontWeight = FontWeight.SemiBold)
            Text(if (grade == ReviewGrade.AGAIN) "again today" else preview, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun Finished(
    session: ReviewSession,
    pending: Int,
    onClose: () -> Unit,
    onSync: () -> Unit,
) {
    Centered {
        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF16A34A), modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(12.dp))
        Text("Session complete", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        val distinct = session.queue.map { it.id }.distinct().size
        Text(
            "$distinct card${if (distinct == 1) "" else "s"} reviewed" + if (session.again > 0) " · ${session.again} relearned" else "",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (pending > 0) {
            Spacer(Modifier.height(12.dp))
            Text(
                "$pending review${if (pending == 1) "" else "s"} saved on this device will sync when you're online.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            TextButton(onClick = onSync) { Text("Sync now") }
        }
        Spacer(Modifier.height(16.dp))
        Button(onClick = onClose) { Text("Done") }
    }
}
