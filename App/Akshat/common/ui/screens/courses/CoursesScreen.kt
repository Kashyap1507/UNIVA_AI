package com.kashyap.wizlyai.ui.screens.courses

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.Grade
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.datetime.todayIn
import com.kashyap.wizlyai.network.models.Course
import com.kashyap.wizlyai.network.models.CreateCourseRequest
import com.kashyap.wizlyai.ui.components.CollapsibleTopBar
import com.kashyap.wizlyai.ui.state.UiState
import com.kashyap.wizlyai.ui.viewmodel.CoursesViewModel

/** Colours offered when creating a course (stored as hex on the server). */
val COURSE_COLORS = listOf("#3B82F6", "#8B5CF6", "#EC4899", "#F59E0B", "#10B981", "#06B6D4", "#EF4444", "#64748B")

fun courseColor(hex: String?, fallback: Color): Color =
    hex?.removePrefix("#")?.takeIf { it.length == 6 }?.toLongOrNull(16)?.let { Color(0xFF000000 or it) } ?: fallback

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoursesScreen(
    viewModel: CoursesViewModel,
    onBack: (() -> Unit)?,
    onOpenCourse: (String) -> Unit,
    progress: Map<String, com.kashyap.wizlyai.network.models.CourseProgress> = emptyMap(),
    onOpenGrades: (() -> Unit)? = null,
    onOpenDeadlines: (() -> Unit)? = null,
    onOpenGroups: (() -> Unit)? = null,
) {
    // As a tab the screen sits under the floating bottom bar
    val bottomClearance = if (onBack == null) com.kashyap.wizlyai.ui.screens.today.TAB_BOTTOM_CLEARANCE else 0.dp
    val today = remember { kotlinx.datetime.Clock.System.todayIn(kotlinx.datetime.TimeZone.currentSystemDefault()) }
    val coursesState by viewModel.courses.collectAsState()
    val isOffline by viewModel.isOffline.collectAsState()
    val message by viewModel.message.collectAsState()
    var showAddSheet by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { viewModel.load() }
    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            CollapsibleTopBar(
                title = "Courses",
                isCollapsed = false,
                showBackButton = onBack != null,
                collapsible = false,
                onBackClick = { onBack?.invoke() },
                trailingContent = {
                    onOpenDeadlines?.let { IconButton(onClick = it) { Icon(Icons.Default.EventNote, contentDescription = "Deadlines") } }
                    onOpenGrades?.let { IconButton(onClick = it) { Icon(Icons.Default.Grade, contentDescription = "Grades") } }
                    onOpenGroups?.let { IconButton(onClick = it) { Icon(Icons.Default.Groups, contentDescription = "Study groups") } }
                },
            )
        },
        floatingActionButton = {
            // The empty state has its own "Add course" button
            val hasCourses = (coursesState as? UiState.Success)?.data?.isNotEmpty() == true
            if (hasCourses) ExtendedFloatingActionButton(
                onClick = { showAddSheet = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Add course") },
                modifier = Modifier.navigationBarsPadding().padding(bottom = bottomClearance),
            )
        },
        snackbarHost = { SnackbarHost(snackbar, modifier = Modifier.padding(bottom = bottomClearance)) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val state = coursesState) {
                is UiState.Loading, UiState.Idle -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                is UiState.Error ->
                    CenteredMessage(
                        title = "Couldn't load your courses",
                        body = state.message,
                        action = "Try again",
                        onAction = { viewModel.load() },
                    )
                is UiState.Success ->
                    if (state.data.isEmpty()) {
                        CenteredMessage(
                            title = "Add your first course",
                            body = "Create a course for each class you're taking, then upload the syllabus, slides and notes. Univa AI builds an outline and a searchable knowledge base from them.",
                            action = "Add course",
                            onAction = { showAddSheet = true },
                        )
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp + bottomClearance),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            if (isOffline) {
                                item { OfflineBanner() }
                            }
                            items(state.data, key = { it.id }) { course ->
                                CourseCard(
                                    course = course,
                                    onClick = { onOpenCourse(course.id) },
                                    progressLine = progress[course.id]?.let { com.kashyap.wizlyai.ui.screens.today.progressLine(it, today) },
                                    mastery = progress[course.id]?.concepts?.masteryAvg,
                                )
                            }
                        }
                    }
            }
        }
    }

    if (showAddSheet) {
        AddCourseSheet(
            onDismiss = { showAddSheet = false },
            onCreate = { request ->
                showAddSheet = false
                viewModel.createCourse(request) { onOpenCourse(it.id) }
            },
        )
    }
}

@Composable
fun CourseCard(
    course: Course,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    progressLine: String? = null,
    mastery: Double? = null,
) {
    val accent = courseColor(course.color, MaterialTheme.colorScheme.primary)
    Card(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(Modifier.fillMaxWidth().height(88.dp)) {
            Box(Modifier.width(6.dp).fillMaxHeight().background(accent))
            Column(
                Modifier.weight(1f).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                if (!course.code.isNullOrBlank()) {
                    Text(course.code, style = MaterialTheme.typography.labelMedium, color = accent, fontWeight = FontWeight.SemiBold)
                }
                Text(
                    course.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val details =
                    listOfNotNull(
                        course.instructor?.takeIf { it.isNotBlank() },
                        course.examDates.firstOrNull()?.let { "Exam $it" },
                        "Archived".takeIf { course.isArchived },
                    ).joinToString(" · ")
                val line = progressLine?.takeIf { it.isNotBlank() } ?: details
                if (line.isNotEmpty()) {
                    Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                mastery?.let {
                    Spacer(Modifier.height(6.dp))
                    androidx.compose.material3.LinearProgressIndicator(
                        progress = { it.toFloat() },
                        color = com.kashyap.wizlyai.ui.screens.today.masteryColor(it),
                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                    )
                }
            }
        }
    }
}

@Composable
fun OfflineBanner() {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Default.CloudOff, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("You're offline. Showing saved courses.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun CenteredMessage(
    title: String,
    body: String,
    action: String? = null,
    onAction: () -> Unit = {},
) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(72.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.School, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.height(20.dp))
            Button(onClick = onAction) { Text(action) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddCourseSheet(
    onDismiss: () -> Unit,
    onCreate: (CreateCourseRequest) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var instructor by remember { mutableStateOf("") }
    var color by remember { mutableStateOf(COURSE_COLORS.first()) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            Text("Add course", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(120) },
                label = { Text("Course name") },
                placeholder = { Text("e.g. Machine Learning") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.take(30).uppercase() },
                    label = { Text("Code (optional)") },
                    placeholder = { Text("COMP-8740") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = instructor,
                    onValueChange = { instructor = it.take(120) },
                    label = { Text("Instructor (optional)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(16.dp))
            Text("Colour", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                COURSE_COLORS.forEach { hex ->
                    val selected = hex == color
                    Box(
                        Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(courseColor(hex, Color.Gray))
                            .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                            .clickable { color = hex }
                            .semantics { contentDescription = if (selected) "Colour $hex, selected" else "Colour $hex" },
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Spacer(Modifier.width(8.dp))
                Button(
                    enabled = name.isNotBlank(),
                    onClick = {
                        onCreate(
                            CreateCourseRequest(
                                name = name.trim(),
                                code = code.trim().ifBlank { null },
                                instructor = instructor.trim().ifBlank { null },
                                color = color,
                            ),
                        )
                    },
                ) { Text("Create") }
            }
        }
    }
}

/** Home section: up to three courses, "See all", or a prompt to add the first one. */
@Composable
fun CoursesSection(
    courses: UiState<List<Course>>,
    onOpenCourse: (String) -> Unit,
    onSeeAll: () -> Unit,
    modifier: Modifier = Modifier,
    flashcardsDue: Int = 0,
    onStudyFlashcards: () -> Unit = {},
    upcomingDeadlines: List<com.kashyap.wizlyai.network.models.Deadline> = emptyList(),
    onOpenDeadlines: () -> Unit = {},
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.School, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Your courses", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text("Materials, outlines and search", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = onSeeAll) { Text("See all") }
        }
        UpcomingDeadlines(upcomingDeadlines, (courses as? UiState.Success)?.data.orEmpty(), onOpenDeadlines)
        if (flashcardsDue > 0) {
            Card(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onStudyFlashcards),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Style, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "$flashcardsDue flashcard${if (flashcardsDue == 1) "" else "s"} due",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text("A few minutes now keeps them fresh for the exam", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("Study", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        when (courses) {
            is UiState.Success -> {
                val active = courses.data.filterNot { it.isArchived }
                if (active.isEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable(onClick = onSeeAll),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Add your first course", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Upload the syllabus and slides to get an outline and course-grounded search.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    active.take(3).forEach { CourseCard(it, onClick = { onOpenCourse(it.id) }) }
                }
            }
            is UiState.Error -> Text(courses.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            else -> Box(Modifier.fillMaxWidth().height(88.dp)) { CircularProgressIndicator(Modifier.align(Alignment.Center)) }
        }
    }
}

/** The next few deadlines (within two weeks) on Home. */
@Composable
private fun UpcomingDeadlines(
    deadlines: List<com.kashyap.wizlyai.network.models.Deadline>,
    courses: List<Course>,
    onOpen: () -> Unit,
) {
    val zone = kotlinx.datetime.TimeZone.currentSystemDefault()
    val today = kotlinx.datetime.Clock.System.todayIn(zone)
    val soon =
        deadlines
            .mapNotNull { d -> d.localDate(zone)?.let { d to it } }
            .filter { (_, date) -> date >= today && com.kashyap.wizlyai.deadlines.DeadlineLogic.daysUntil(today, date) <= 14 }
            .sortedBy { it.second }
            .take(3)
    if (soon.isEmpty()) return
    val byId = courses.associateBy { it.id }
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Coming up", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            soon.forEach { (d, date) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(d.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            listOfNotNull(d.courseId?.let { byId[it] }?.let { it.code?.takeIf { c -> c.isNotBlank() } ?: it.name }, d.deadlineType.label).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        com.kashyap.wizlyai.deadlines.DeadlineLogic.countdown(date, today) ?: "",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (com.kashyap.wizlyai.deadlines.DeadlineLogic.daysUntil(today, date) <= 2) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}
