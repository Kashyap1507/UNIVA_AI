package com.kashyap.wizlyai.ui.screens.courses

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kashyap.wizlyai.network.models.Course
import com.kashyap.wizlyai.network.models.CourseConcept
import com.kashyap.wizlyai.network.models.CourseMaterial
import com.kashyap.wizlyai.network.models.CourseModule
import com.kashyap.wizlyai.network.models.IngestionStatus
import com.kashyap.wizlyai.ui.components.CollapsibleTopBar
import com.kashyap.wizlyai.ui.state.UiState
import com.kashyap.wizlyai.ui.viewmodel.CourseDetailViewModel
import com.kashyap.wizlyai.util.FilePicker

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseDetailScreen(
    viewModel: CourseDetailViewModel,
    filePicker: FilePicker?,
    onBack: () -> Unit,
    onDeleted: () -> Unit,
    onOpenTutor: (courseId: String, courseName: String) -> Unit = { _, _ -> },
    onOpenFeedback: (submissionId: String) -> Unit = {},
    flashcardsViewModel: com.kashyap.wizlyai.ui.viewmodel.CourseFlashcardsViewModel? = null,
    onStudyCards: (courseId: String, courseName: String) -> Unit = { _, _ -> },
    initialTab: Int = 0,
    onTabChange: (Int) -> Unit = {},
    practiceViewModel: com.kashyap.wizlyai.ui.viewmodel.CoursePracticeViewModel? = null,
    onOpenPracticeSet: (setId: String, title: String) -> Unit = { _, _ -> },
    onOpenPracticeAttempt: (attemptId: String, title: String) -> Unit = { _, _ -> },
    syllabusViewModel: com.kashyap.wizlyai.ui.viewmodel.SyllabusImportViewModel? = null,
    progressViewModel: com.kashyap.wizlyai.ui.viewmodel.CourseProgressViewModel? = null,
    onOpenGrades: () -> Unit = {},
) {
    val courseState by viewModel.course.collectAsState()
    val materialsState by viewModel.materials.collectAsState()
    val modules by viewModel.modules.collectAsState()
    val concepts by viewModel.concepts.collectAsState()
    val submissions by viewModel.submissions.collectAsState()
    val isUploading by viewModel.isUploading.collectAsState()
    val message by viewModel.message.collectAsState()
    var tab by remember { mutableIntStateOf(initialTab) }
    LaunchedEffect(tab) { onTabChange(tab) }
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { viewModel.load() }
    DisposableEffect(Unit) { onDispose { viewModel.stop() } }
    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    val course = (courseState as? UiState.Success)?.data
    val pickFile: () -> Unit = {
        filePicker?.pickDocument(onPicked = { viewModel.upload(it) }, onCancelled = {})
    }

    Scaffold(
        topBar = {
            CollapsibleTopBar(
                title = course?.code?.takeIf { it.isNotBlank() } ?: course?.name ?: "Course",
                isCollapsed = false,
                showBackButton = true,
                collapsible = false,
                onBackClick = onBack,
                trailingContent = {
                    Box {
                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Course options") }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            if (syllabusViewModel != null) {
                                DropdownMenuItem(
                                    text = { Text("Find deadlines in syllabus") },
                                    leadingIcon = { Icon(Icons.Default.AutoAwesome, contentDescription = null) },
                                    onClick = {
                                        menuOpen = false
                                        syllabusViewModel.extract()
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Delete course") },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    confirmDelete = true
                                },
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            course?.let {
                ExtendedFloatingActionButton(
                    onClick = { onOpenTutor(it.id, it.name) },
                    icon = { Icon(Icons.Default.Psychology, contentDescription = null) },
                    text = { Text("Ask tutor") },
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            when (val state = courseState) {
                is UiState.Error -> {
                    CenteredMessage("Couldn't load this course", state.message, "Try again") { viewModel.load() }
                    return@Column
                }
                !is UiState.Success -> {
                    Box(Modifier.fillMaxSize()) { CircularProgressIndicator(Modifier.align(Alignment.Center)) }
                    return@Column
                }
                else -> {}
            }
            course!!
            CourseHeader(course)
            ScrollableTabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background, edgePadding = 8.dp) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Outline") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Materials") })
                Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("Cards") })
                Tab(selected = tab == 3, onClick = { tab = 3 }, text = { Text("Practice") })
                Tab(selected = tab == 4, onClick = { tab = 4 }, text = { Text("Progress") })
                Tab(selected = tab == 5, onClick = { tab = 5 }, text = { Text("Feedback") })
            }
            when (tab) {
                0 ->
                    OutlineTab(
                        course = course,
                        modules = modules,
                        concepts = concepts,
                        hasReadyMaterials = (materialsState as? UiState.Success)?.data?.any { it.status == IngestionStatus.READY } == true,
                        onGenerate = { viewModel.generateOutline() },
                        onGoToMaterials = { tab = 1 },
                    )
                5 -> FeedbackTab(submissions, onOpenFeedback)
                4 -> progressViewModel?.let { com.kashyap.wizlyai.ui.screens.today.CourseProgressTab(it, onOpenGrades) }
                3 ->
                    practiceViewModel?.let { practiceVm ->
                        com.kashyap.wizlyai.ui.screens.practice.CoursePracticeTab(
                            viewModel = practiceVm,
                            modules = modules,
                            hasReadyMaterials = (materialsState as? UiState.Success)?.data?.any { it.status == IngestionStatus.READY } == true,
                            onOpenSet = onOpenPracticeSet,
                            onOpenAttempt = onOpenPracticeAttempt,
                            onGoToMaterials = { tab = 1 },
                            showMessage = { viewModel.showMessage(it) },
                        )
                    }
                2 ->
                    flashcardsViewModel?.let { cardsVm ->
                        com.kashyap.wizlyai.ui.screens.flashcards.CourseCardsTab(
                            viewModel = cardsVm,
                            concepts = concepts,
                            hasReadyMaterials = (materialsState as? UiState.Success)?.data?.any { it.status == IngestionStatus.READY } == true,
                            onStudy = { onStudyCards(course.id, course.code?.takeIf { it.isNotBlank() } ?: course.name) },
                            onGoToMaterials = { tab = 1 },
                            showMessage = { viewModel.showMessage(it) },
                        )
                    }
                else ->
                    MaterialsTab(
                        state = materialsState,
                        isUploading = isUploading,
                        canPick = filePicker != null,
                        onUpload = pickFile,
                        onDelete = { viewModel.deleteMaterial(it) },
                        onRetry = { viewModel.retryMaterial(it) },
                        onSummarise = { viewModel.summarise(it) },
                    )
            }
        }
    }

    val summaryView by viewModel.summaryView.collectAsState()
    summaryView?.let { (material, summary) ->
        com.kashyap.wizlyai.ui.screens.research.MaterialSummaryDialog(
            title = material.title,
            summary = summary,
            loading = summary == null,
            onDismiss = viewModel::closeSummary,
        )
    }

    syllabusViewModel?.let { svm ->
        val preview by svm.preview.collectAsState()
        val busy by svm.busy.collectAsState()
        val syllabusMessage by svm.message.collectAsState()
        LaunchedEffect(syllabusMessage) {
            syllabusMessage?.let {
                viewModel.showMessage(it)
                svm.clearMessage()
            }
        }
        if (busy) {
            AlertDialog(
                onDismissRequest = {},
                confirmButton = {},
                title = { Text("Reading the syllabus") },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text("Finding exams, assignments and their dates...")
                    }
                },
            )
        }
        preview?.let { p ->
            com.kashyap.wizlyai.ui.screens.deadlines.PreviewSheet(
                preview = p,
                courses = emptyList(),
                onToggle = svm::toggle,
                onCourse = null,
                onSave = svm::save,
                onDismiss = svm::dismiss,
            )
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this course?") },
            text = { Text("Its outline and search index are removed. Uploaded files can't be recovered.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.deleteCourse(onDeleted)
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun CourseHeader(course: Course) {
    val accent = courseColor(course.color, MaterialTheme.colorScheme.primary)
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(course.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        val line =
            listOfNotNull(
                course.instructor?.takeIf { it.isNotBlank() },
                course.examDates.takeIf { it.isNotEmpty() }?.let { "Exams: ${it.joinToString(", ")}" },
            ).joinToString(" · ")
        if (line.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(line, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.width(48.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(accent))
    }
}

// --- Outline tab ----------------------------------------------------------------------

@Composable
private fun OutlineTab(
    course: Course,
    modules: List<CourseModule>,
    concepts: List<CourseConcept>,
    hasReadyMaterials: Boolean,
    onGenerate: () -> Unit,
    onGoToMaterials: () -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            GenerateOutlineCard(
                course = course,
                hasModules = modules.isNotEmpty(),
                hasReadyMaterials = hasReadyMaterials,
                onGenerate = onGenerate,
                onGoToMaterials = onGoToMaterials,
            )
        }
        items(modules, key = { it.id }) { module ->
            ModuleCard(module, concepts.filter { it.moduleId == module.id })
        }
    }
}

@Composable
private fun GenerateOutlineCard(
    course: Course,
    hasModules: Boolean,
    hasReadyMaterials: Boolean,
    onGenerate: () -> Unit,
    onGoToMaterials: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Course outline", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(8.dp))
            when {
                course.isGeneratingOutline -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text("Reading your materials and building modules, concepts and deadlines...", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                !hasReadyMaterials -> {
                    Text(
                        "Upload the syllabus or lecture slides first. Univa AI uses them to build the outline.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = onGoToMaterials) { Text("Go to materials") }
                }
                else -> {
                    course.structureError?.takeIf { course.structureStatus == "FAILED" }?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(8.dp))
                    }
                    Text(
                        if (hasModules) {
                            "Univa AI updates the outline each time you add a file. Your practice history is kept."
                        } else {
                            "Build modules, key concepts and deadlines from your uploaded materials."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onGenerate) { Text(if (hasModules) "Regenerate outline" else "Generate outline") }
                }
            }
        }
    }
}

@Composable
private fun ModuleCard(
    module: CourseModule,
    concepts: List<CourseConcept>,
) {
    var expanded by remember { mutableStateOf(false) }
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    module.week?.let {
                        Text("Week $it", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                    Text(module.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${concepts.size} concept${if (concepts.size == 1) "" else "s"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                )
            }
            AnimatedVisibility(expanded) {
                Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    module.summary?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    concepts.forEach { concept ->
                        Column {
                            Text(concept.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                            concept.summary?.takeIf { it.isNotBlank() }?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- Feedback tab -------------------------------------------------------------------------

@Composable
private fun FeedbackTab(
    submissions: List<com.kashyap.wizlyai.network.models.WorkSubmission>,
    onOpen: (String) -> Unit,
) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
        if (submissions.isEmpty()) {
            item {
                Text(
                    "No checked work yet. Scan your working with the + button and choose this course to get hints.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(submissions, key = { it.id }) { sub ->
            val items = sub.feedback?.items.orEmpty()
            val status =
                when (sub.status) {
                    "READY" -> if (items.isEmpty()) "No attempt found" else "${items.count { it.correct }}/${items.size} correct"
                    "FAILED" -> "Couldn't be checked"
                    else -> "Checking..."
                }
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth().clickable { onOpen(sub.id) },
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(sub.title ?: "Checked work", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(status, if (sub.isPractice) "Practice" else "Hints only", sub.createdAt?.take(10)).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

// --- Materials tab ------------------------------------------------------------------------

@Composable
private fun MaterialsTab(
    state: UiState<List<CourseMaterial>>,
    isUploading: Boolean,
    canPick: Boolean,
    onUpload: () -> Unit,
    onDelete: (String) -> Unit,
    onRetry: (String) -> Unit,
    onSummarise: (CourseMaterial) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            Button(onClick = onUpload, enabled = canPick && !isUploading, modifier = Modifier.fillMaxWidth()) {
                if (isUploading) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(8.dp))
                    Text("Uploading...")
                } else {
                    Icon(Icons.Default.UploadFile, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Upload file")
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "PDF, PowerPoint (.pptx), text, Markdown, JPEG or PNG, up to 50 MB.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        when (state) {
            is UiState.Success -> {
                if (state.data.isEmpty()) {
                    item {
                        Text(
                            "No files yet. Start with the syllabus, then add lecture slides and notes.",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 16.dp),
                        )
                    }
                }
                items(state.data, key = { it.id }) { MaterialRow(it, onDelete, onRetry, onSummarise) }
            }
            is UiState.Error -> item { Text(state.message, color = MaterialTheme.colorScheme.error) }
            else -> item { Box(Modifier.fillMaxWidth().padding(24.dp)) { CircularProgressIndicator(Modifier.align(Alignment.Center)) } }
        }
    }
}

@Composable
private fun MaterialRow(
    material: CourseMaterial,
    onDelete: (String) -> Unit,
    onRetry: (String) -> Unit,
    onSummarise: (CourseMaterial) -> Unit,
) {
    var confirm by remember { mutableStateOf(false) }
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(material.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                StatusLine(material)
            }
            if (material.status == IngestionStatus.READY) {
                IconButton(onClick = { onSummarise(material) }) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = if (material.summary != null) "Show summary" else "Summarise ${material.title}",
                        tint = if (material.summary != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (material.status == IngestionStatus.FAILED) {
                IconButton(onClick = { onRetry(material.id) }) { Icon(Icons.Default.Refresh, contentDescription = "Retry processing") }
            }
            IconButton(onClick = { confirm = true }) { Icon(Icons.Default.Delete, contentDescription = "Delete ${material.title}") }
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Delete ${material.title}?") },
            text = { Text("It will no longer be used for the outline or search.") },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    onDelete(material.id)
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun StatusLine(material: CourseMaterial) {
    val (label, color) =
        when (material.status) {
            IngestionStatus.PENDING -> "Waiting to process" to MaterialTheme.colorScheme.onSurfaceVariant
            IngestionStatus.PROCESSING -> "Processing..." to MaterialTheme.colorScheme.primary
            IngestionStatus.READY ->
                listOfNotNull(
                    material.kind.lowercase().replaceFirstChar { it.uppercase() },
                    material.pages?.let { "$it page${if (it == 1) "" else "s"}" },
                    "Ready",
                ).joinToString(" · ") to Color(0xFF10B981)
            IngestionStatus.FAILED -> (material.error ?: "Processing failed") to MaterialTheme.colorScheme.error
        }
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (material.isInProgress) {
            CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 1.5.dp)
            Spacer(Modifier.width(6.dp))
        }
        Text(label, style = MaterialTheme.typography.bodySmall, color = color, maxLines = 2)
    }
}
