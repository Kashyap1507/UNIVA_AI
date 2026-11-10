package com.kashyap.wizlyai.ui.screens.work

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import com.kashyap.wizlyai.network.models.Course
import com.kashyap.wizlyai.network.models.WorkFeedback
import com.kashyap.wizlyai.network.models.WorkFeedbackItem
import com.kashyap.wizlyai.ui.components.CollapsibleTopBar
import com.kashyap.wizlyai.ui.screens.courses.courseColor
import com.kashyap.wizlyai.ui.util.MathMarkdown
import com.kashyap.wizlyai.ui.viewmodel.WorkFeedbackState
import com.kashyap.wizlyai.ui.viewmodel.WorkFeedbackViewModel
import com.mikepenz.markdown.m3.Markdown

private val GOOD = Color(0xFF10B981)
private val NEEDS_WORK = Color(0xFFF59E0B)

/** Course + practice choice shown before checking scanned work. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckWorkBottomSheet(
    courses: List<Course>,
    onDismiss: () -> Unit,
    onAddCourse: () -> Unit,
    onConfirm: (courseId: String, isPractice: Boolean) -> Unit,
) {
    val active = courses.filterNot { it.isArchived }
    var selected by remember { mutableStateOf(active.firstOrNull()?.id) }
    var isPractice by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            Text("Check my work", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                "Univa AI points out where your working goes wrong and gives hints so you can fix it yourself.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Text("Course", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            if (active.isEmpty()) {
                Text("Add a course first so feedback uses your course's methods.", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onAddCourse) { Text("Add a course") }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    active.forEach { course ->
                        val isSelected = course.id == selected
                        Surface(
                            onClick = { selected = course.id },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface,
                            tonalElevation = if (isSelected) 0.dp else 1.dp,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(12.dp).background(courseColor(course.color, MaterialTheme.colorScheme.primary), CircleShape))
                                Spacer(Modifier.width(12.dp))
                                Text(course.displayTitle, style = MaterialTheme.typography.bodyLarge, fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("This is practice", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    Text(
                        if (isPractice) {
                            "Full worked solutions will be available."
                        } else {
                            "Graded or unmarked work gets hints only, never full solutions."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = isPractice, onCheckedChange = { isPractice = it })
            }
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { selected?.let { onConfirm(it, isPractice) } },
                enabled = selected != null,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Check my work") }
        }
    }
}

@Composable
fun WorkFeedbackScreen(
    viewModel: WorkFeedbackViewModel,
    onClose: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    Scaffold(
        topBar = {
            CollapsibleTopBar(title = "Feedback", isCollapsed = false, showBackButton = true, collapsible = false, onBackClick = onClose)
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val s = state) {
                WorkFeedbackState.Idle, WorkFeedbackState.Uploading, is WorkFeedbackState.Checking -> Progress(s)
                is WorkFeedbackState.Failed ->
                    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(12.dp))
                        Text(s.message, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { viewModel.retry() }) { Text("Try again") }
                        TextButton(onClick = onClose) { Text("Close") }
                    }
                is WorkFeedbackState.Ready -> Results(s.submission.feedback ?: WorkFeedback(), onClose)
            }
        }
    }
}

private val STEP_LABELS =
    listOf(
        "upload" to "Uploading your pages",
        "extract" to "Reading your working",
        "evaluate" to "Checking each step",
        "verify" to "Double-checking the feedback",
        "format" to "Preparing hints",
    )

@Composable
private fun Progress(state: WorkFeedbackState) {
    val current =
        when (state) {
            is WorkFeedbackState.Checking ->
                when (state.step) {
                    null, "queued", "loading" -> 1
                    else -> STEP_LABELS.indexOfFirst { it.first == state.step }.coerceAtLeast(1)
                }
            else -> 0
        }
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center) {
        Text("Checking your work", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text("This usually takes under a minute.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        STEP_LABELS.forEachIndexed { index, (_, label) ->
            Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                when {
                    index < current -> Icon(Icons.Default.CheckCircle, contentDescription = "Done", tint = GOOD, modifier = Modifier.size(22.dp))
                    index == current -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else -> Box(Modifier.size(22.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape))
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (index <= current) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Results(
    feedback: WorkFeedback,
    onClose: () -> Unit,
) {
    val needsWork = feedback.items.count { !it.correct }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
        item {
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(
                        when {
                            feedback.items.isEmpty() -> "Nothing to check"
                            needsWork == 0 -> "All ${feedback.items.size} correct"
                            else -> "$needsWork of ${feedback.items.size} need another look"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    if (feedback.summary.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(feedback.summary, style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (feedback.isPractice) Icons.Default.Visibility else Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (feedback.isPractice) "Practice: worked solutions available" else "Hints only (graded or unmarked work)",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        itemsIndexed(feedback.items) { _, item -> FeedbackItemCard(item) }
        item {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Done") }
        }
    }
}

@Composable
private fun FeedbackItemCard(item: WorkFeedbackItem) {
    var revealed by remember { mutableStateOf(false) }
    val accent = if (item.correct) GOOD else NEEDS_WORK
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (item.correct) Icons.Default.CheckCircle else Icons.Default.ErrorOutline, contentDescription = null, tint = accent)
                Spacer(Modifier.width(8.dp))
                Text("Problem ${item.problem}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(if (item.correct) "Correct" else "Needs work", style = MaterialTheme.typography.labelLarge, color = accent)
            }
            item.concept?.takeIf { it.isNotBlank() }?.let {
                Text("Concept: $it", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
            item.location?.takeIf { it.isNotBlank() }?.let { Labeled("Where", it) }
            item.issue?.takeIf { it.isNotBlank() }?.let { Labeled("What's wrong", it) }
            item.hint?.takeIf { it.isNotBlank() }?.let { hint ->
                Surface(color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f), shape = RoundedCornerShape(10.dp)) {
                    Row(Modifier.fillMaxWidth().padding(12.dp)) {
                        Icon(Icons.Default.Lightbulb, contentDescription = null, tint = NEEDS_WORK, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Markdown(content = MathMarkdown.prepare(hint), modifier = Modifier.weight(1f))
                    }
                }
            }
            if (!item.solution.isNullOrBlank()) {
                if (revealed) {
                    Text("Worked solution", style = MaterialTheme.typography.labelLarge)
                    Markdown(content = MathMarkdown.prepare(item.solution), modifier = Modifier.fillMaxWidth())
                } else {
                    TextButton(onClick = { revealed = true }) {
                        Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Try the hint first, then reveal the solution")
                    }
                }
            }
        }
    }
}

@Composable
private fun Labeled(
    label: String,
    value: String,
) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Markdown(content = MathMarkdown.prepare(value), modifier = Modifier.fillMaxWidth())
    }
}
