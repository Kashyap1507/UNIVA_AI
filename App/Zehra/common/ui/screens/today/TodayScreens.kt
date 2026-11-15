package com.kashyap.wizlyai.ui.screens.today

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kashyap.wizlyai.deadlines.DeadlineLogic
import com.kashyap.wizlyai.network.models.Course
import com.kashyap.wizlyai.network.models.CourseGrades
import com.kashyap.wizlyai.network.models.CourseLabel
import com.kashyap.wizlyai.network.models.Deadline
import com.kashyap.wizlyai.network.models.GradeEntry
import com.kashyap.wizlyai.network.models.GradeInput
import com.kashyap.wizlyai.network.models.StudyStreak
import com.kashyap.wizlyai.network.models.TodayData
import com.kashyap.wizlyai.network.models.TodayTask
import com.kashyap.wizlyai.ui.components.CollapsibleTopBar
import com.kashyap.wizlyai.ui.screens.courses.courseColor
import com.kashyap.wizlyai.ui.screens.deadlines.formatDate
import com.kashyap.wizlyai.ui.state.UiState
import com.kashyap.wizlyai.ui.viewmodel.CourseProgressViewModel
import com.kashyap.wizlyai.ui.viewmodel.GradesViewModel
import com.kashyap.wizlyai.ui.viewmodel.TodayViewModel
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn

/** Space kept free for the floating bottom bar on tab screens. */
val TAB_BOTTOM_CLEARANCE = 112.dp

private val Green = Color(0xFF16A34A)
private val Amber = Color(0xFFD97706)
private val Red = Color(0xFFDC2626)
private val Flame = Color(0xFFF97316)

private fun pct(value: Double?): String = value?.let { "${(it + 0.5).toInt()}%" } ?: "–"

fun masteryColor(fraction: Double): Color = if (fraction >= 0.7) Green else if (fraction >= 0.4) Amber else Red

@Composable
private fun SectionTitle(
    text: String,
    action: String? = null,
    onAction: () -> Unit = {},
) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        if (action != null) TextButton(onClick = onAction) { Text(action) }
    }
}

@Composable
private fun SurfaceCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier.fillMaxWidth(),
    ) { content() }
}

// --- Today ------------------------------------------------------------------------------------------------

@Composable
fun TodayScreen(
    viewModel: TodayViewModel,
    studentName: String,
    hasCourses: Boolean,
    onOpenTask: (TodayTask) -> Unit,
    onStudyCards: () -> Unit,
    onOpenDeadlines: () -> Unit,
    onPracticeConcept: (courseId: String) -> Unit,
    onAskTutor: (courseId: String) -> Unit,
    onCheckWork: () -> Unit,
    onOpenCourses: () -> Unit,
    onOpenProfile: () -> Unit,
) {
    val state by viewModel.today.collectAsState()
    LaunchedEffect(Unit) { viewModel.load() }
    val zone = remember { TimeZone.currentSystemDefault() }
    val now = remember { Clock.System.now().toLocalDateTime(zone) }
    val greeting =
        when (now.hour) {
            in 5..11 -> "Good morning"
            in 12..17 -> "Good afternoon"
            else -> "Good evening"
        }

    LazyColumn(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = TAB_BOTTOM_CLEARANCE),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("$greeting, $studentName", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(formatDate(now.date), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary).clickable(onClick = onOpenProfile),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(studentName.firstOrNull()?.uppercase() ?: "?", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
        when (val s = state) {
            is UiState.Error -> item { Text(s.message, color = MaterialTheme.colorScheme.error) }
            is UiState.Success -> {
                val d = s.data
                item { StreakCard(d.streak) }
                if (!hasCourses) {
                    item {
                        SurfaceCard(Modifier.clickable(onClick = onOpenCourses)) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.School, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("Add your first course", fontWeight = FontWeight.SemiBold)
                                    Text("Upload the syllabus and slides to unlock the tutor, cards and practice.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Icon(Icons.Default.ChevronRight, contentDescription = null)
                            }
                        }
                    }
                }
                if (d.planTasks.isNotEmpty()) {
                    val done = d.planTasks.count { it.done }
                    item { SectionTitle("Today's plan · $done/${d.planTasks.size}") }
                    item {
                        SurfaceCard {
                            Column(Modifier.padding(vertical = 6.dp)) {
                                d.planTasks.forEach { task -> TaskRow(task, d.courses[task.courseId], onToggle = { viewModel.setDone(task, it) }, onStart = { onOpenTask(task) }) }
                            }
                        }
                    }
                }
                if (d.cards.dueTotal > 0) {
                    item {
                        SurfaceCard(Modifier.clickable(onClick = onStudyCards)) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Style, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("${d.cards.dueTotal} flashcard${if (d.cards.dueTotal == 1) "" else "s"} due", fontWeight = FontWeight.SemiBold)
                                    Text(
                                        d.cards.dueByCourse.entries.joinToString(" · ") { (cid, n) -> "${d.courses[cid]?.short ?: "Course"} $n" },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Button(onClick = onStudyCards) { Text("Study") }
                            }
                        }
                    }
                }
                d.focus?.let { f ->
                    item {
                        SurfaceCard {
                            Column(Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.TrackChanges, contentDescription = null, tint = Amber)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Focus", style = MaterialTheme.typography.labelLarge, color = Amber, fontWeight = FontWeight.Bold)
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(f.conceptName ?: "A weak concept", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text(
                                    listOfNotNull(
                                        d.courses[f.courseId]?.short,
                                        if (f.reason == "WEAKEST") "mastery ${pct(f.mastery * 100)}" else "not practised yet",
                                        f.examTitle?.let { "before $it" },
                                    ).joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.height(10.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = { onPracticeConcept(f.courseId) }) { Text("Practice") }
                                    OutlinedButton(onClick = { onAskTutor(f.courseId) }) { Text("Ask tutor") }
                                }
                            }
                        }
                    }
                }
                val deadlines = d.overdue + d.upcoming
                item { SectionTitle("Deadlines", "See all", onOpenDeadlines) }
                item {
                    SurfaceCard(Modifier.clickable(onClick = onOpenDeadlines)) {
                        if (deadlines.isEmpty()) {
                            Text("Nothing due in the next 7 days.", modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            Column(Modifier.padding(vertical = 8.dp)) { deadlines.take(6).forEach { DeadlineLine(it, d.courses, zone) } }
                        }
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 4.dp)) {
                        QuickAction(Icons.Default.CameraAlt, "Check my work", Modifier.weight(1f), onCheckWork)
                        QuickAction(Icons.Default.Psychology, "Ask the tutor", Modifier.weight(1f)) { onAskTutor("") }
                    }
                }
            }
            else -> item { Box(Modifier.fillMaxWidth().padding(40.dp)) { CircularProgressIndicator(Modifier.align(Alignment.Center)) } }
        }
    }
}

@Composable
private fun StreakCard(streak: StudyStreak) {
    SurfaceCard {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.LocalFireDepartment, contentDescription = null, tint = if (streak.current > 0) Flame else MaterialTheme.colorScheme.outline, modifier = Modifier.size(36.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (streak.current == 0) "Start a streak today" else "${streak.current}-day streak",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    when {
                        streak.studiedToday -> "Done for today. Best: ${streak.best} days"
                        streak.current > 0 -> "Study anything today to keep it going"
                        else -> "Review cards, practise or ask the tutor"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                streak.last7Days.forEach { day ->
                    Box(Modifier.size(10.dp).clip(CircleShape).background(if (day.active) Flame else MaterialTheme.colorScheme.surfaceVariant))
                }
            }
        }
    }
}

@Composable
private fun TaskRow(
    task: TodayTask,
    course: CourseLabel?,
    onToggle: (Boolean) -> Unit,
    onStart: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = task.done, onCheckedChange = onToggle)
        Column(Modifier.weight(1f)) {
            Text(task.title, style = MaterialTheme.typography.bodyMedium, textDecoration = if (task.done) TextDecoration.LineThrough else null, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(listOfNotNull(course?.short, "${task.minutes} min").joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (!task.done) TextButton(onClick = onStart) { Text("Start") }
    }
}

@Composable
private fun DeadlineLine(
    d: Deadline,
    courses: Map<String, CourseLabel>,
    zone: TimeZone,
) {
    val today = Clock.System.todayIn(zone)
    val date = d.localDate(zone)
    val overdue = date != null && date < today
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(d.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(listOfNotNull(d.courseId?.let { courses[it]?.short }, d.deadlineType.label, date?.let { formatDate(it) }).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(DeadlineLogic.countdown(date, today) ?: "", style = MaterialTheme.typography.labelLarge, color = if (overdue) Red else MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun QuickAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text(label, fontWeight = FontWeight.Medium)
        }
    }
}

// --- Course progress tab --------------------------------------------------------------------------------------

@Composable
fun CourseProgressTab(
    viewModel: CourseProgressViewModel,
    onOpenGrades: () -> Unit,
) {
    val state by viewModel.detail.collectAsState()
    LaunchedEffect(Unit) { viewModel.load() }
    when (val s = state) {
        is UiState.Error -> Text(s.message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
        is UiState.Success -> {
            val d = s.data
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Stat("Mastery", d.conceptStats.masteryAvg?.let { pct(it * 100) } ?: "–", "${d.conceptStats.practised}/${d.conceptStats.total} practised", Modifier.weight(1f))
                        Stat("Practice", pct(d.practice.averagePct), "${d.practice.attempts} attempt${if (d.practice.attempts == 1) "" else "s"}", Modifier.weight(1f))
                        Stat("Grade", pct(d.grades?.currentPct), d.grades?.letter ?: "no marks yet", Modifier.weight(1f), onClick = onOpenGrades)
                    }
                }
                item {
                    SurfaceCard {
                        Column(Modifier.padding(16.dp)) {
                            Text("Flashcard reviews, last 14 days", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text("${d.cards.total} cards · ${d.cards.due} due · ${d.cards.mature} learned well", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(10.dp))
                            Bars(d.reviewsPerDay.map { it.count.toFloat() })
                        }
                    }
                }
                if (d.practiceHistory.isNotEmpty()) {
                    item {
                        SurfaceCard {
                            Column(Modifier.padding(16.dp)) {
                                Text("Practice scores", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.height(10.dp))
                                Bars(d.practiceHistory.map { (it.scorePct ?: 0.0).toFloat() }, maxValue = 100f, colorFor = { masteryColor(it / 100.0) })
                            }
                        }
                    }
                }
                item { Text("Concepts", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold) }
                if (d.concepts.isEmpty()) {
                    item { Text("Generate the course outline to see concepts here.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium) }
                }
                items(d.concepts, key = { it.id }) { c ->
                    Column {
                        Row {
                            Text(c.name ?: "Concept", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(if (c.practised) pct(c.mastery * 100) else "not practised", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { c.mastery.toFloat() },
                            color = masteryColor(c.mastery),
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                        )
                    }
                }
            }
        }
        else -> Box(Modifier.fillMaxSize()) { CircularProgressIndicator(Modifier.align(Alignment.Center)) }
    }
}

@Composable
private fun Stat(
    label: String,
    value: String,
    caption: String,
    modifier: Modifier,
    onClick: (() -> Unit)? = null,
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
        modifier = modifier.then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun Bars(
    values: List<Float>,
    maxValue: Float = (values.maxOrNull() ?: 0f).coerceAtLeast(1f),
    colorFor: (Float) -> Color = { Color(0xFF3B82F6) },
) {
    Row(Modifier.fillMaxWidth().height(64.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
        values.forEach { v ->
            val fraction = (v / maxValue).coerceIn(0f, 1f)
            Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.BottomCenter) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(if (v > 0f) fraction.coerceAtLeast(0.06f) else 0.03f)
                        .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                        .background(if (v > 0f) colorFor(v) else MaterialTheme.colorScheme.surfaceVariant),
                )
            }
        }
    }
}

// --- Grades ---------------------------------------------------------------------------------------------------------

@Composable
fun GradesScreen(
    viewModel: GradesViewModel,
    courses: List<Course>,
    onBack: () -> Unit,
) {
    val state by viewModel.grades.collectAsState()
    val message by viewModel.message.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var editing by remember { mutableStateOf<Pair<String, GradeEntry?>?>(null) }
    LaunchedEffect(Unit) { viewModel.load() }
    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMessage()
        }
    }
    Scaffold(
        topBar = { CollapsibleTopBar(title = "Grades", isCollapsed = false, showBackButton = true, collapsible = false, onBackClick = onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (courses.isNotEmpty()) {
                FloatingActionButton(onClick = { editing = courses.first().id to null }) { Icon(Icons.Default.Add, contentDescription = "Add a mark") }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        when (val s = state) {
            is UiState.Error -> Text(s.message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(padding).padding(16.dp))
            is UiState.Success -> {
                val g = s.data
                LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        SurfaceCard {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("Estimated GPA", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(g.gpa?.let { ((it * 100 + 0.5).toInt() / 100.0).toString() } ?: "–", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                }
                                Text(
                                    "4.0 scale, weighted by course credits, from marks entered so far. Your university's scale may differ.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1.4f),
                                )
                            }
                        }
                    }
                    items(g.courses, key = { it.courseId }) { c ->
                        CourseGradesCard(c, onAdd = { editing = c.courseId to null }, onEdit = { editing = c.courseId to it })
                    }
                }
            }
            else -> Box(Modifier.fillMaxSize().padding(padding)) { CircularProgressIndicator(Modifier.align(Alignment.Center)) }
        }
    }
    editing?.let { (courseId, entry) ->
        GradeDialog(
            courses = courses,
            courseId = courseId,
            entry = entry,
            onDismiss = { editing = null },
            onSave = {
                viewModel.save(entry, it)
                editing = null
            },
            onDelete =
                entry?.let {
                    {
                        viewModel.delete(it)
                        editing = null
                    }
                },
        )
    }
}

@Composable
private fun CourseGradesCard(
    c: CourseGrades,
    onAdd: () -> Unit,
    onEdit: (GradeEntry) -> Unit,
) {
    var target by remember { mutableFloatStateOf(80f) }
    SurfaceCard {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(c.courseCode?.takeIf { it.isNotBlank() } ?: c.courseName ?: "Course", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("${(c.gradedWeight + 0.5).toInt()}% of the grade marked · ${c.credits} credits", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(pct(c.currentPct), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = c.currentPct?.let { masteryColor(it / 100) } ?: MaterialTheme.colorScheme.onSurfaceVariant)
                    c.letter?.let { Text(it, style = MaterialTheme.typography.labelMedium) }
                }
            }
            c.entries.forEach { e ->
                Row(Modifier.fillMaxWidth().clickable { onEdit(e) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(e.name, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${fmt(e.score)}/${fmt(e.max)} · ${fmt(e.weightPct)}%", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            TextButton(onClick = onAdd) { Text("Add a mark") }
            if (c.gradedWeight > 0 && c.remainingWeight > 0) {
                val needed = c.neededFor(target.toDouble())
                Text("To finish on ${target.toInt()}%", style = MaterialTheme.typography.labelLarge)
                Slider(value = target, onValueChange = { target = (it / 5).toInt() * 5f }, valueRange = 50f..100f)
                Text(
                    when {
                        needed == null -> ""
                        needed <= 0 -> "You're already there, even with zeros on the remaining ${fmt(c.remainingWeight)}%."
                        needed > 100 -> "Out of reach: you'd need ${pct(needed)} on the remaining ${fmt(c.remainingWeight)}%."
                        else -> "You need an average of ${pct(needed)} on the remaining ${fmt(c.remainingWeight)}%."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

private fun fmt(v: Double): String = if (v == v.toLong().toDouble()) v.toLong().toString() else ((v * 10 + 0.5).toLong() / 10.0).toString()

@Composable
private fun GradeDialog(
    courses: List<Course>,
    courseId: String,
    entry: GradeEntry?,
    onDismiss: () -> Unit,
    onSave: (GradeInput) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var course by remember { mutableStateOf(courseId) }
    var name by remember { mutableStateOf(entry?.name.orEmpty()) }
    var score by remember { mutableStateOf(entry?.score?.let(::fmt).orEmpty()) }
    var max by remember { mutableStateOf(entry?.max?.let(::fmt) ?: "100") }
    var weight by remember { mutableStateOf(entry?.weightPct?.let(::fmt).orEmpty()) }
    var menu by remember { mutableStateOf(false) }
    val s = score.toDoubleOrNull()
    val m = max.toDoubleOrNull()
    val w = weight.toDoubleOrNull()
    val valid = name.isNotBlank() && s != null && s >= 0 && m != null && m > 0 && s <= m * 1.5 && w != null && w in 0.0..100.0
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (entry == null) "Add a mark" else "Edit mark") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (entry == null) {
                    Box {
                        OutlinedButton(onClick = { menu = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(courses.firstOrNull { it.id == course }?.let { it.code?.takeIf { c -> c.isNotBlank() } ?: it.name } ?: "Course")
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            courses.forEach { c ->
                                DropdownMenuItem(text = { Text(listOfNotNull(c.code?.takeIf { it.isNotBlank() }, c.name).joinToString(" · ")) }, onClick = {
                                    course = c.id
                                    menu = false
                                })
                            }
                        }
                    }
                }
                OutlinedTextField(value = name, onValueChange = { name = it.take(120) }, label = { Text("What was it? e.g. Midterm") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = score, onValueChange = { score = it.take(8) }, label = { Text("Score") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = max, onValueChange = { max = it.take(8) }, label = { Text("Out of") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                OutlinedTextField(value = weight, onValueChange = { weight = it.take(6) }, label = { Text("Weight in final grade (%)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (onDelete != null) TextButton(onClick = onDelete) { Text("Delete mark", color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton(enabled = valid, onClick = { onSave(GradeInput(course, name.trim(), s!!, m!!, w!!)) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// --- Tutor tab -----------------------------------------------------------------------------------------------------

@Composable
fun TutorHomeScreen(
    courses: UiState<List<Course>>,
    onOpenTutor: (courseId: String, courseName: String) -> Unit,
    onOpenCourses: () -> Unit,
) {
    Scaffold(
        topBar = { CollapsibleTopBar(title = "Tutor", isCollapsed = false, showBackButton = false, collapsible = false) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = TAB_BOTTOM_CLEARANCE),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    "Pick a course. The tutor answers from that course's materials and shows where each answer comes from. It gives hints, not answers, for graded work.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            when (courses) {
                is UiState.Success -> {
                    val active = courses.data.filterNot { it.isArchived }
                    if (active.isEmpty()) {
                        item {
                            Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("No courses yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.height(8.dp))
                                Button(onClick = onOpenCourses) { Text("Add a course") }
                            }
                        }
                    }
                    items(active, key = { it.id }) { c ->
                        val accent = courseColor(c.color, MaterialTheme.colorScheme.primary)
                        SurfaceCard(Modifier.clickable { onOpenTutor(c.id, c.name) }) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(40.dp).clip(CircleShape).background(accent.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Psychology, contentDescription = null, tint = accent)
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    c.code?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = accent, fontWeight = FontWeight.SemiBold) }
                                    Text(c.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Icon(Icons.Default.ChevronRight, contentDescription = null)
                            }
                        }
                    }
                }
                is UiState.Error -> item { Text(courses.message, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center) }
                else -> item { Box(Modifier.fillMaxWidth().padding(32.dp)) { CircularProgressIndicator(Modifier.align(Alignment.Center)) } }
            }
        }
    }
}

/** Short summary line for a course card on the Courses tab. */
fun progressLine(
    p: com.kashyap.wizlyai.network.models.CourseProgress,
    today: LocalDate,
): String =
    listOfNotNull(
        p.concepts.masteryAvg?.let { "Mastery ${pct(it * 100)}" },
        p.cards.due.takeIf { it > 0 }?.let { "$it cards due" },
        p.gradePct?.let { "Grade ${pct(it)}" },
        p.nextDeadline?.let { d -> d.localDate()?.let { date -> "${d.title} ${DeadlineLogic.countdown(date, today)}" } },
    ).joinToString(" · ")
