package com.kashyap.wizlyai.ui.screens.deadlines

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kashyap.wizlyai.calendar.CalendarProvider
import com.kashyap.wizlyai.deadlines.DeadlineLogic
import com.kashyap.wizlyai.deadlines.DeadlineSection
import com.kashyap.wizlyai.network.models.Course
import com.kashyap.wizlyai.network.models.Deadline
import com.kashyap.wizlyai.network.models.DeadlineInput
import com.kashyap.wizlyai.network.models.DeadlineType
import com.kashyap.wizlyai.network.models.StudyPlan
import com.kashyap.wizlyai.ui.components.CollapsibleTopBar
import com.kashyap.wizlyai.ui.state.UiState
import com.kashyap.wizlyai.ui.viewmodel.DeadlinePreview
import com.kashyap.wizlyai.ui.viewmodel.DeadlinesViewModel
import com.kashyap.wizlyai.ui.viewmodel.StudyPlanViewModel
import com.kashyap.wizlyai.util.CALENDAR_MIME_TYPES
import com.kashyap.wizlyai.util.FilePicker
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn

private fun typeColor(type: DeadlineType): Color =
    when (type) {
        DeadlineType.EXAM -> Color(0xFFDC2626)
        DeadlineType.QUIZ -> Color(0xFFD97706)
        DeadlineType.ASSIGNMENT -> Color(0xFF2563EB)
        DeadlineType.LAB -> Color(0xFF0D9488)
        DeadlineType.PRESENTATION -> Color(0xFF7C3AED)
        DeadlineType.OTHER -> Color(0xFF6B7280)
    }

private val BOTTOM_BAR_CLEARANCE = 96.dp

private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
private val WEEKDAYS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

fun formatDate(date: LocalDate): String = "${WEEKDAYS[date.dayOfWeek.ordinal]} ${date.dayOfMonth} ${MONTHS[date.monthNumber - 1]}"

// --- Deadlines tab --------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeadlinesScreen(
    viewModel: DeadlinesViewModel,
    filePicker: FilePicker?,
    calendarProvider: CalendarProvider?,
    onOpenPlan: (StudyPlan) -> Unit,
    onBack: (() -> Unit)? = null,
) {
    val state by viewModel.deadlines.collectAsState()
    val courses by viewModel.courses.collectAsState()
    val filter by viewModel.filter.collectAsState()
    val preview by viewModel.preview.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val message by viewModel.message.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var menuOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Deadline?>(null) }
    var adding by remember { mutableStateOf(false) }
    var askUrl by remember { mutableStateOf(false) }
    var planFor by remember { mutableStateOf<Deadline?>(null) }

    LaunchedEffect(Unit) { viewModel.load() }
    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    val zone = remember { TimeZone.currentSystemDefault() }
    val today = remember { Clock.System.todayIn(zone) }
    val byId = courses.associateBy { it.id }
    val all = (state as? UiState.Success)?.data.orEmpty()
    val shown = if (filter == null) all else all.filter { it.courseId == filter }

    Scaffold(
        topBar = {
            CollapsibleTopBar(
                title = "Deadlines",
                isCollapsed = false,
                showBackButton = onBack != null,
                collapsible = false,
                onBackClick = { onBack?.invoke() },
                trailingContent = {
                    if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Box {
                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Import") }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            if (filePicker != null) {
                                DropdownMenuItem(text = { Text("Import calendar file (.ics)") }, onClick = {
                                    menuOpen = false
                                    filePicker.pickDocument(onPicked = { viewModel.importFile(it) }, onCancelled = {}, mimeTypes = CALENDAR_MIME_TYPES)
                                })
                            }
                            DropdownMenuItem(text = { Text("Add Canvas / Moodle feed") }, onClick = {
                                menuOpen = false
                                askUrl = true
                            })
                            if (calendarProvider != null) {
                                DropdownMenuItem(text = { Text("Import from device calendar") }, onClick = {
                                    menuOpen = false
                                    viewModel.previewDeviceCalendar(calendarProvider)
                                })
                            }
                        }
                    }
                },
            )
        },
        // Tab screens sit under the floating bottom bar, so lift the button and snackbars above it
        floatingActionButton = {
            FloatingActionButton(onClick = { adding = true }, modifier = Modifier.padding(bottom = BOTTOM_BAR_CLEARANCE)) {
                Icon(Icons.Default.Add, contentDescription = "Add deadline")
            }
        },
        snackbarHost = { SnackbarHost(snackbar, modifier = Modifier.padding(bottom = BOTTOM_BAR_CLEARANCE)) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = BOTTOM_BAR_CLEARANCE + 80.dp)) {
            if (courses.isNotEmpty()) {
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item { FilterChip(selected = filter == null, onClick = { viewModel.setFilter(null) }, label = { Text("All") }) }
                        items(courses, key = { it.id }) { c ->
                            FilterChip(selected = filter == c.id, onClick = { viewModel.setFilter(c.id) }, label = { Text(c.code?.takeIf { it.isNotBlank() } ?: c.name) })
                        }
                    }
                }
            }
            when (val s = state) {
                is UiState.Error -> item { Text(s.message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }
                UiState.Loading, UiState.Idle -> item { Box(Modifier.fillMaxWidth().padding(32.dp)) { CircularProgressIndicator(Modifier.align(Alignment.Center)) } }
                else -> {}
            }
            if (state is UiState.Success && shown.isEmpty()) {
                item { EmptyDeadlines() }
            }
            DeadlineLogic.group(shown, today, zone).forEach { (section, items) ->
                item(key = "h_${section.name}") {
                    Text(
                        "${section.label} · ${items.size}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (section == DeadlineSection.OVERDUE) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 6.dp),
                    )
                }
                items(items, key = { it.id }) { d ->
                    DeadlineRow(
                        deadline = d,
                        course = d.courseId?.let { byId[it] },
                        today = today,
                        zone = zone,
                        onToggle = { viewModel.setCompleted(d, !d.completed) },
                        onClick = { editing = d },
                        onPlan = if (d.deadlineType == DeadlineType.EXAM && d.courseId != null && !d.completed && (d.localDate(zone)?.let { it > today } == true)) {
                            { planFor = d }
                        } else {
                            null
                        },
                    )
                }
            }
        }
    }

    if (adding || editing != null) {
        DeadlineEditDialog(
            existing = editing,
            courses = courses,
            defaultCourseId = filter,
            onDismiss = {
                adding = false
                editing = null
            },
            onSave = { input ->
                viewModel.save(editing, input)
                adding = false
                editing = null
            },
            onDelete = editing?.let { d ->
                {
                    viewModel.delete(d)
                    editing = null
                }
            },
        )
    }
    if (askUrl) {
        FeedUrlDialog(onDismiss = { askUrl = false }) {
            askUrl = false
            viewModel.importUrl(it)
        }
    }
    preview?.let { p ->
        PreviewSheet(
            preview = p,
            courses = courses,
            onToggle = viewModel::toggle,
            onCourse = viewModel::setPreviewCourse,
            onSave = viewModel::savePreview,
            onDismiss = viewModel::dismissPreview,
        )
    }
    planFor?.let { d ->
        PlanSetupDialog(d, onDismiss = { planFor = null }) { minutes ->
            planFor = null
            viewModel.generatePlan(d, minutes, onOpenPlan)
        }
    }
}

@Composable
private fun EmptyDeadlines() {
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.EventNote, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(12.dp))
        Text("No deadlines yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(
            "Add them with +, find them in a course syllabus, or import your Canvas / Moodle calendar from the ⋮ menu.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DeadlineRow(
    deadline: Deadline,
    course: Course?,
    today: LocalDate,
    zone: TimeZone,
    onToggle: () -> Unit,
    onClick: () -> Unit,
    onPlan: (() -> Unit)?,
) {
    val type = deadline.deadlineType
    val date = deadline.localDate(zone)
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(end = 12.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = deadline.completed, onCheckedChange = { onToggle() })
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        type.label.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = typeColor(type),
                        modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(typeColor(type).copy(alpha = 0.1f)).padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                    course?.let {
                        Spacer(Modifier.width(8.dp))
                        Text(it.code?.takeIf { c -> c.isNotBlank() } ?: it.name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, maxLines = 1)
                    }
                    deadline.weightPct?.let {
                        Spacer(Modifier.width(8.dp))
                        Text("${(it + 0.5).toInt()}%", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    deadline.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textDecoration = if (deadline.completed) TextDecoration.LineThrough else null,
                )
                Text(
                    listOfNotNull(date?.let { formatDate(it) }, deadline.localTime(zone), DeadlineLogic.countdown(date, today)).joinToString(" · ").ifEmpty { "No date" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (onPlan != null) {
                TextButton(onClick = onPlan) { Text("Plan") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeadlineEditDialog(
    existing: Deadline?,
    courses: List<Course>,
    defaultCourseId: String?,
    onDismiss: () -> Unit,
    onSave: (DeadlineInput) -> Unit,
    onDelete: (() -> Unit)?,
) {
    val zone = remember { TimeZone.currentSystemDefault() }
    var title by remember { mutableStateOf(existing?.title.orEmpty()) }
    var type by remember { mutableStateOf(existing?.deadlineType ?: DeadlineType.ASSIGNMENT) }
    var date by remember { mutableStateOf(existing?.localDate(zone) ?: Clock.System.todayIn(zone)) }
    var time by remember { mutableStateOf(existing?.localTime(zone).orEmpty()) }
    var courseId by remember { mutableStateOf(existing?.courseId ?: defaultCourseId) }
    var weight by remember { mutableStateOf(existing?.weightPct?.let { if (it == it.toLong().toDouble()) it.toLong().toString() else it.toString() }.orEmpty()) }
    var pickDate by remember { mutableStateOf(false) }
    var courseMenu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val timeValid = time.isBlank() || Regex("^([01]?\\d|2[0-3]):[0-5]\\d$").matches(time.trim())
    val weightValue = weight.trim().toDoubleOrNull()
    val weightValid = weight.isBlank() || (weightValue != null && weightValue in 0.0..100.0)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "New deadline" else "Edit deadline") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it.take(200) }, label = { Text("Title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(DeadlineType.entries) { t -> FilterChip(selected = type == t, onClick = { type = t }, label = { Text(t.label) }) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { pickDate = true }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(formatDate(date))
                    }
                    OutlinedTextField(
                        value = time,
                        onValueChange = { time = it.take(5) },
                        label = { Text("Time") },
                        placeholder = { Text("23:59") },
                        isError = !timeValid,
                        singleLine = true,
                        modifier = Modifier.width(96.dp),
                    )
                }
                Box {
                    OutlinedButton(onClick = { courseMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.School, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(courses.firstOrNull { it.id == courseId }?.let { it.code?.takeIf { c -> c.isNotBlank() } ?: it.name } ?: "No course", maxLines = 1)
                    }
                    DropdownMenu(expanded = courseMenu, onDismissRequest = { courseMenu = false }) {
                        DropdownMenuItem(text = { Text("No course") }, onClick = {
                            courseId = null
                            courseMenu = false
                        })
                        courses.forEach { c ->
                            DropdownMenuItem(text = { Text(listOfNotNull(c.code?.takeIf { it.isNotBlank() }, c.name).joinToString(" · ")) }, onClick = {
                                courseId = c.id
                                courseMenu = false
                            })
                        }
                    }
                }
                OutlinedTextField(
                    value = weight,
                    onValueChange = { weight = it.take(5) },
                    label = { Text("Weight % (optional)") },
                    isError = !weightValid,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (onDelete != null) {
                    TextButton(onClick = { confirmDelete = true }) { Text("Delete deadline", color = MaterialTheme.colorScheme.error) }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank() && timeValid && weightValid,
                onClick = {
                    val due =
                        if (time.isBlank()) {
                            date.toString()
                        } else {
                            LocalDateTime(date, LocalTime.parse(time.trim().padStart(5, '0'))).toInstant(zone).toString()
                        }
                    onSave(DeadlineInput(title = title.trim(), type = type.apiName, dueAt = due, courseId = courseId, weightPct = weightValue))
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )

    if (pickDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = date.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds())
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { date = Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.UTC).date }
                    pickDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text("Cancel") } },
        ) { DatePicker(state = state) }
    }
    if (confirmDelete && onDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this deadline?") },
            confirmButton = { TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun FeedUrlDialog(
    onDismiss: () -> Unit,
    onImport: (String) -> Unit,
) {
    var url by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import a calendar feed") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Canvas: Calendar → Calendar Feed. Moodle: Calendar → Export calendar → Get calendar URL. Paste the https:// or webcal:// link.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(value = url, onValueChange = { url = it.trim().take(2000) }, label = { Text("Feed URL") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("Importing again later updates changed dates.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            TextButton(enabled = url.startsWith("https://") || url.startsWith("webcal://"), onClick = { onImport(url) }) { Text("Import") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Checklist of deadlines to save (syllabus or device calendar). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreviewSheet(
    preview: DeadlinePreview,
    courses: List<Course>,
    onToggle: (Int) -> Unit,
    onCourse: ((String?) -> Unit)?,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    val zone = remember { TimeZone.currentSystemDefault() }
    var courseMenu by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text(preview.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Tick the ones to save.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (onCourse != null && courses.isNotEmpty()) {
                Box(Modifier.padding(top = 8.dp)) {
                    val current = preview.items.firstOrNull()?.courseId
                    OutlinedButton(onClick = { courseMenu = true }) {
                        Text("Course: " + (courses.firstOrNull { it.id == current }?.let { it.code?.takeIf { c -> c.isNotBlank() } ?: it.name } ?: "none"))
                    }
                    DropdownMenu(expanded = courseMenu, onDismissRequest = { courseMenu = false }) {
                        DropdownMenuItem(text = { Text("No course") }, onClick = {
                            onCourse(null)
                            courseMenu = false
                        })
                        courses.forEach { c ->
                            DropdownMenuItem(text = { Text(c.code?.takeIf { it.isNotBlank() } ?: c.name) }, onClick = {
                                onCourse(c.id)
                                courseMenu = false
                            })
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                preview.items.forEachIndexed { i, item ->
                    val savable = item.dueAt != null
                    Row(
                        Modifier.fillMaxWidth().clickable(enabled = savable) { onToggle(i) }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = i in preview.selected, onCheckedChange = { onToggle(i) }, enabled = savable)
                        Column(Modifier.weight(1f)) {
                            Text(item.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            val date =
                                item.dueAt?.let {
                                    if (it.length == 10) runCatching { LocalDate.parse(it) }.getOrNull() else runCatching { Instant.parse(it).toLocalDateTime(zone).date }.getOrNull()
                                }
                            Text(
                                listOfNotNull(DeadlineType.from(item.type).label, date?.let { formatDate(it) }, item.weightPct?.let { "${(it + 0.5).toInt()}%" }, preview.notes[i]).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (preview.notes.containsKey(i)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Button(onClick = onSave, enabled = preview.selected.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                Text("Save ${preview.selected.size} deadline${if (preview.selected.size == 1) "" else "s"}")
            }
        }
    }
}

@Composable
private fun PlanSetupDialog(
    deadline: Deadline,
    onDismiss: () -> Unit,
    onCreate: (Int) -> Unit,
) {
    var minutes by remember { mutableStateOf(60) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Study plan for ${deadline.title}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "A day-by-day plan until the exam: your weakest concepts first, daily flashcards, practice quizzes and a mock exam a few days before.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text("Time per day", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(30, 60, 90, 120).forEach { m -> FilterChip(selected = minutes == m, onClick = { minutes = m }, label = { Text(if (m < 60) "$m min" else "${m / 60}${if (m % 60 == 30) ".5" else ""} h") }) }
                }
                Text("Making a new plan replaces the old one for this exam.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = { onCreate(minutes) }) { Text("Make plan") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// --- Study plan ----------------------------------------------------------------------------------------------

@Composable
fun StudyPlanScreen(
    viewModel: StudyPlanViewModel,
    onBack: () -> Unit,
    onOpenTask: (type: String, courseId: String) -> Unit,
) {
    val state by viewModel.plan.collectAsState()
    val today = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()) }
    val listState = rememberLazyListState()
    val plan = (state as? UiState.Success)?.data
    LaunchedEffect(plan?.id) {
        plan?.let { p ->
            val index = StudyPlanViewModel.todayIndex(p, today)
            if (index > 0) listState.scrollToItem(index + 1)
        }
    }
    Scaffold(
        topBar = { CollapsibleTopBar(title = plan?.title ?: "Study plan", isCollapsed = false, showBackButton = true, collapsible = false, onBackClick = onBack) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val s = state) {
                is UiState.Error -> Text(s.message, color = MaterialTheme.colorScheme.error, modifier = Modifier.align(Alignment.Center).padding(24.dp))
                is UiState.Success -> {
                    val p = s.data
                    val exam = runCatching { LocalDate.parse(p.examDate) }.getOrNull()
                    LazyColumn(state = listState, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        item {
                            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))) {
                                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                                    Text(
                                        listOfNotNull(exam?.let { "Exam ${formatDate(it)}" }, DeadlineLogic.countdown(exam, today)).joinToString(" · "),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text("${p.minutesPerDay} min a day · ${p.tasksDone} of ${p.tasksTotal} tasks done", style = MaterialTheme.typography.bodyMedium)
                                    Spacer(Modifier.height(8.dp))
                                    LinearProgressIndicator(progress = { if (p.tasksTotal == 0) 0f else p.tasksDone.toFloat() / p.tasksTotal }, modifier = Modifier.fillMaxWidth())
                                }
                            }
                        }
                        itemsIndexed(p.days.orEmpty(), key = { _, d -> d.date }) { _, day ->
                            val date = runCatching { LocalDate.parse(day.date) }.getOrNull()
                            val isToday = date == today
                            val past = date != null && date < today
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = if (isToday) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                            ) {
                                Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                                    Text(
                                        (if (isToday) "Today · " else "") + (date?.let { formatDate(it) } ?: day.date),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isToday) MaterialTheme.colorScheme.primary else if (past) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                    )
                                    day.tasks.forEach { task ->
                                        Row(Modifier.fillMaxWidth().padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Checkbox(checked = task.done, onCheckedChange = { viewModel.setDone(task.id, it) })
                                            Column(Modifier.weight(1f)) {
                                                Text(
                                                    task.title,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    textDecoration = if (task.done) TextDecoration.LineThrough else null,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                                Text("${task.minutes} min", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            if (!task.done && (isToday || past)) {
                                                TextButton(onClick = { onOpenTask(task.type, p.courseId) }) { Text("Start") }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                else -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
        }
    }
}
