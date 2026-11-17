package com.kashyap.wizlyai.ui.screens.research

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kashyap.wizlyai.deadlines.DeadlineLogic
import com.kashyap.wizlyai.network.models.CompareResult
import com.kashyap.wizlyai.network.models.MaterialSummary
import com.kashyap.wizlyai.network.models.Milestone
import com.kashyap.wizlyai.network.models.Paper
import com.kashyap.wizlyai.network.models.PaperInput
import com.kashyap.wizlyai.network.models.ProjectInput
import com.kashyap.wizlyai.network.models.ResearchProject
import com.kashyap.wizlyai.network.models.WritingFeedbackResult
import com.kashyap.wizlyai.network.models.WritingKind
import com.kashyap.wizlyai.ui.components.CollapsibleTopBar
import com.kashyap.wizlyai.ui.screens.deadlines.formatDate
import com.kashyap.wizlyai.ui.screens.today.TAB_BOTTOM_CLEARANCE
import com.kashyap.wizlyai.ui.state.UiState
import com.kashyap.wizlyai.ui.util.MathMarkdown
import com.kashyap.wizlyai.ui.viewmodel.CompareViewModel
import com.kashyap.wizlyai.ui.viewmodel.PaperViewModel
import com.kashyap.wizlyai.ui.viewmodel.ProjectViewModel
import com.kashyap.wizlyai.ui.viewmodel.ResearchHomeViewModel
import com.kashyap.wizlyai.ui.viewmodel.WritingFeedbackViewModel
import com.kashyap.wizlyai.ui.viewmodel.parseAuthors
import com.kashyap.wizlyai.util.FilePicker
import com.mikepenz.markdown.m3.Markdown
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

private val PAPER_MIME_TYPES = listOf("application/pdf", "text/plain", "text/markdown")
private val PROJECT_KINDS = listOf("THESIS" to "Thesis", "PROJECT" to "Research project", "COURSEWORK" to "Coursework")

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

@Composable
private fun Section(
    title: String,
    body: String?,
) {
    if (body.isNullOrBlank()) return
    Column(Modifier.padding(top = 10.dp)) {
        if (title.isNotEmpty()) Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
        Markdown(content = MathMarkdown.prepare(body), modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun Bullets(
    title: String,
    items: List<String>,
) {
    if (items.isEmpty()) return
    Section(title, items.joinToString("\n") { "- $it" })
}

private fun parseDate(value: String?): LocalDate? = value?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() }

// --- Research tab ---------------------------------------------------------------------------------------------

@Composable
fun ResearchHomeScreen(
    viewModel: ResearchHomeViewModel,
    onOpenProject: (ResearchProject) -> Unit,
    onOpenWriting: () -> Unit,
) {
    val state by viewModel.projects.collectAsState()
    val message by viewModel.message.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var creating by remember { mutableStateOf(false) }
    val today = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()) }
    LaunchedEffect(Unit) { viewModel.load() }
    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMessage()
        }
    }
    Scaffold(
        topBar = { CollapsibleTopBar(title = "Research", isCollapsed = false, showBackButton = false, collapsible = false) },
        snackbarHost = { SnackbarHost(snackbar, modifier = Modifier.padding(bottom = TAB_BOTTOM_CLEARANCE)) },
        floatingActionButton = {
            FloatingActionButton(onClick = { creating = true }, modifier = Modifier.padding(bottom = TAB_BOTTOM_CLEARANCE)) { Icon(Icons.Default.Add, contentDescription = "New project") }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = TAB_BOTTOM_CLEARANCE + 72.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenWriting),
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.EditNote, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Writing feedback", fontWeight = FontWeight.SemiBold)
                            Text("Paste a draft section for comments on structure, argument, evidence and style.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            item { Text("Projects", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp)) }
            when (val s = state) {
                is UiState.Success -> {
                    if (s.data.isEmpty()) {
                        item {
                            Text(
                                "Create a project for your thesis or a research paper. Keep its papers, get summaries and a literature review table, export references and plan milestones.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    items(s.data, key = { it.id }) { p ->
                        SurfaceCard(Modifier.clickable { onOpenProject(p) }) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Science, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(PROJECT_KINDS.firstOrNull { it.first == p.kind }?.second ?: p.kind, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                    Text(p.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        listOfNotNull(
                                            "${p.paperCount} paper${if (p.paperCount == 1) "" else "s"}",
                                            p.milestonesTotal.takeIf { it > 0 }?.let { "${p.milestonesDone}/$it milestones" },
                                            parseDate(p.dueDate)?.let { "due ${DeadlineLogic.countdown(it, today)?.lowercase()}" },
                                        ).joinToString(" · "),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
                is UiState.Error -> item { Text(s.message, color = MaterialTheme.colorScheme.error) }
                else -> item { Box(Modifier.fillMaxWidth().padding(24.dp)) { CircularProgressIndicator(Modifier.align(Alignment.Center)) } }
            }
        }
    }
    if (creating) {
        var title by remember { mutableStateOf("") }
        var kind by remember { mutableStateOf("THESIS") }
        var due by remember { mutableStateOf("") }
        val dueValid = due.isBlank() || parseDate(due) != null
        AlertDialog(
            onDismissRequest = { creating = false },
            title = { Text("New project") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = title, onValueChange = { title = it.take(200) }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth())
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(PROJECT_KINDS) { (k, label) -> FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text(label) }) }
                    }
                    OutlinedTextField(value = due, onValueChange = { due = it.take(10) }, label = { Text("Due date (YYYY-MM-DD, optional)") }, isError = !dueValid, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(enabled = title.isNotBlank() && dueValid, onClick = {
                    creating = false
                    viewModel.create(ProjectInput(title.trim(), kind, due.trim().ifBlank { null }), onOpenProject)
                }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { creating = false }) { Text("Cancel") } },
        )
    }
}

// --- Project ----------------------------------------------------------------------------------------------------

@Composable
fun ProjectScreen(
    viewModel: ProjectViewModel,
    filePicker: FilePicker?,
    onBack: () -> Unit,
    onOpenPaper: (Paper) -> Unit,
    onCompare: (List<String>) -> Unit,
) {
    val state by viewModel.project.collectAsState()
    val papers by viewModel.papers.collectAsState()
    val milestones by viewModel.milestones.collectAsState()
    val selected by viewModel.selected.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val export by viewModel.export.collectAsState()
    val message by viewModel.message.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var tab by remember { mutableIntStateOf(0) }
    var adding by remember { mutableStateOf(false) }
    var planning by remember { mutableStateOf(false) }
    var addingMilestone by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { viewModel.load() }
    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMessage()
        }
    }
    val project = (state as? UiState.Success)?.data
    Scaffold(
        topBar = { CollapsibleTopBar(title = project?.title ?: "Project", isCollapsed = false, showBackButton = true, collapsible = false, onBackClick = onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (tab == 0 && selected.size >= 2) {
                Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).navigationBarsPadding().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${selected.size} selected", modifier = Modifier.weight(1f))
                    TextButton(onClick = viewModel::clearSelection) { Text("Clear") }
                    Button(onClick = { onCompare(selected.toList()) }) { Text("Compare") }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Papers") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Milestones") })
            }
            if (tab == 0) {
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { filePicker?.pickDocument(onPicked = viewModel::upload, onCancelled = {}, mimeTypes = PAPER_MIME_TYPES) }, enabled = filePicker != null && ProjectViewModel.UPLOADING !in busy, modifier = Modifier.weight(1f)) {
                                if (ProjectViewModel.UPLOADING in busy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Upload PDF")
                            }
                            OutlinedButton(onClick = { adding = true }, modifier = Modifier.weight(1f)) { Text("Add details") }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                            TextButton(onClick = { viewModel.exportAs("bibtex") }, enabled = papers.isNotEmpty()) { Text("Export BibTeX") }
                            TextButton(onClick = { viewModel.exportAs("apa") }, enabled = papers.isNotEmpty()) { Text("Export APA") }
                        }
                        if (papers.size >= 2) Text("Tick two or more analysed papers to build a literature review table.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (papers.isEmpty()) {
                        item { Text("No papers yet. Upload a PDF to get its details and a structured summary.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    items(papers, key = { it.id }) { p -> PaperRow(p, p.id in selected, p.id in busy, onToggle = { viewModel.toggle(p.id) }, onOpen = { onOpenPaper(p) }, onAnalyse = { viewModel.analyse(p) }) }
                }
            } else {
                MilestonesTab(project, milestones, onPlan = { planning = true }, onAdd = { addingMilestone = true }, onCycle = viewModel::cycle, onDelete = viewModel::deleteMilestone)
            }
        }
    }
    if (adding) PaperDetailsDialog(title = "Add a paper", initial = null, onDismiss = { adding = false }) {
        adding = false
        viewModel.add(it)
    }
    export?.let { e -> ExportDialog(e.format, e.text, onDismiss = viewModel::closeExport) }
    if (planning) {
        var due by remember { mutableStateOf(project?.dueDate.orEmpty()) }
        AlertDialog(
            onDismissRequest = { planning = false },
            title = { Text("Plan milestones") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Univa AI spreads the usual ${project?.kind?.lowercase() ?: "thesis"} milestones from today to your due date. Your own milestones and ones you've started stay.", style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(value = due, onValueChange = { due = it.take(10) }, label = { Text("Final due date (YYYY-MM-DD)") }, singleLine = true, isError = due.isNotBlank() && parseDate(due) == null)
                }
            },
            confirmButton = {
                TextButton(enabled = parseDate(due) != null, onClick = {
                    planning = false
                    viewModel.suggestMilestones(due)
                }) { Text("Make plan") }
            },
            dismissButton = { TextButton(onClick = { planning = false }) { Text("Cancel") } },
        )
    }
    if (addingMilestone) {
        var title by remember { mutableStateOf("") }
        var due by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { addingMilestone = false },
            title = { Text("New milestone") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = title, onValueChange = { title = it.take(200) }, label = { Text("Milestone") }, singleLine = true)
                    OutlinedTextField(value = due, onValueChange = { due = it.take(10) }, label = { Text("Due (YYYY-MM-DD)") }, singleLine = true, isError = due.isNotBlank() && parseDate(due) == null)
                }
            },
            confirmButton = {
                TextButton(enabled = title.isNotBlank() && parseDate(due) != null, onClick = {
                    addingMilestone = false
                    viewModel.addMilestone(title, due)
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { addingMilestone = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun PaperRow(
    paper: Paper,
    selected: Boolean,
    busy: Boolean,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    onAnalyse: () -> Unit,
) {
    SurfaceCard(Modifier.clickable(onClick = onOpen)) {
        Row(Modifier.padding(start = 4.dp, end = 12.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = selected, onCheckedChange = { onToggle() }, enabled = paper.isAnalysed)
            Column(Modifier.weight(1f)) {
                Text(paper.citation ?: "Untitled", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(paper.title ?: "Untitled", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                paper.summary?.tldr?.takeIf { it.isNotBlank() }?.let { Text(MathMarkdown.plain(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                if (paper.status == "NO_TEXT") Text("No text in the PDF: add details by hand", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            when {
                busy -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                !paper.isAnalysed && paper.hasText -> TextButton(onClick = onAnalyse) { Text("Analyse") }
                paper.isAnalysed -> Icon(Icons.Default.CheckCircle, contentDescription = "Analysed", tint = Color(0xFF16A34A), modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun MilestonesTab(
    project: ResearchProject?,
    milestones: List<Milestone>,
    onPlan: () -> Unit,
    onAdd: () -> Unit,
    onCycle: (Milestone) -> Unit,
    onDelete: (Milestone) -> Unit,
) {
    val today = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()) }
    var confirm by remember { mutableStateOf<Milestone?>(null) }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onPlan, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Plan milestones")
                }
                OutlinedButton(onClick = onAdd, modifier = Modifier.weight(1f)) { Text("Add one") }
            }
            parseDate(project?.dueDate)?.let { Text("Due ${formatDate(it)} (${DeadlineLogic.countdown(it, today)}). Tap a milestone's circle to move it along.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp)) }
        }
        items(milestones, key = { it.id }) { m ->
            val date = parseDate(m.dueAt)
            val late = m.status != "DONE" && date != null && date < today
            SurfaceCard {
                Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { onCycle(m) }) {
                        Icon(
                            when (m.status) {
                                "DONE" -> Icons.Default.CheckCircle
                                "IN_PROGRESS" -> Icons.Default.Schedule
                                else -> Icons.Default.RadioButtonUnchecked
                            },
                            contentDescription = "Status ${m.status.lowercase().replace('_', ' ')}",
                            tint = when (m.status) {
                                "DONE" -> Color(0xFF16A34A)
                                "IN_PROGRESS" -> Color(0xFFD97706)
                                else -> MaterialTheme.colorScheme.outline
                            },
                        )
                    }
                    Column(Modifier.weight(1f).clickable { confirm = m }) {
                        Text(m.title, fontWeight = FontWeight.Medium)
                        Text(
                            listOfNotNull(date?.let { formatDate(it) }, date?.let { DeadlineLogic.countdown(it, today) }, "late".takeIf { late }).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (late) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
    confirm?.let { m ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("Delete \"${m.title}\"?") },
            confirmButton = {
                TextButton(onClick = {
                    confirm = null
                    onDelete(m)
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun PaperDetailsDialog(
    title: String,
    initial: Paper?,
    onDismiss: () -> Unit,
    onSave: (PaperInput) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.title.orEmpty()) }
    var authors by remember { mutableStateOf(initial?.authors?.joinToString("; ").orEmpty()) }
    var year by remember { mutableStateOf(initial?.year?.toString().orEmpty()) }
    var venue by remember { mutableStateOf(initial?.venue.orEmpty()) }
    var doi by remember { mutableStateOf(initial?.doi.orEmpty()) }
    var type by remember { mutableStateOf(initial?.type ?: "ARTICLE") }
    val yearValid = year.isBlank() || year.toIntOrNull()?.let { it in 1500..2100 } == true
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it.take(500) }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = authors, onValueChange = { authors = it.take(2000) }, label = { Text("Authors (separate with ;)") }, placeholder = { Text("Ada Lovelace; Alan Turing") }, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = year, onValueChange = { year = it.filter(Char::isDigit).take(4) }, label = { Text("Year") }, isError = !yearValid, singleLine = true, modifier = Modifier.width(100.dp))
                    OutlinedTextField(value = venue, onValueChange = { venue = it.take(300) }, label = { Text("Journal / conference") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                OutlinedTextField(value = doi, onValueChange = { doi = it.trim().take(200) }, label = { Text("DOI (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(listOf("ARTICLE" to "Journal", "CONFERENCE" to "Conference", "BOOK" to "Book", "THESIS" to "Thesis", "PREPRINT" to "Preprint")) { (k, label) ->
                        FilterChip(selected = type == k, onClick = { type = k }, label = { Text(label) })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank() && yearValid, onClick = {
                onSave(PaperInput(title = name.trim(), authors = parseAuthors(authors), year = year.toIntOrNull(), venue = venue.trim().ifBlank { null }, type = type, doi = doi.ifBlank { null }))
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ExportDialog(
    format: String,
    text: String,
    onDismiss: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (format == "bibtex") "BibTeX" else "APA 7 references") },
        text = {
            Box(Modifier.heightIn(max = 420.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(10.dp).verticalScroll(rememberScrollState())) {
                SelectionContainer { Text(text.ifBlank { "No papers yet." }, fontFamily = if (format == "bibtex") FontFamily.Monospace else FontFamily.Default, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                clipboard.setText(AnnotatedString(text))
                onDismiss()
            }) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Copy")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

// --- Paper -------------------------------------------------------------------------------------------------------

@Composable
fun PaperScreen(
    viewModel: PaperViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.paper.collectAsState()
    val analysing by viewModel.analysing.collectAsState()
    val message by viewModel.message.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val uriHandler = LocalUriHandler.current
    var editing by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { viewModel.load() }
    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMessage()
        }
    }
    Scaffold(
        topBar = { CollapsibleTopBar(title = "Paper", isCollapsed = false, showBackButton = true, collapsible = false, onBackClick = onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        when (val s = state) {
            is UiState.Error -> Text(s.message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(padding).padding(24.dp))
            is UiState.Success -> {
                val p = s.data
                LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item {
                        Text(p.title ?: "Untitled", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(p.authors.joinToString(", ").ifBlank { "Authors not set" }, style = MaterialTheme.typography.bodyMedium)
                        Text(listOfNotNull(p.venue, p.year?.toString(), p.doi).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                            OutlinedButton(onClick = { editing = true }) { Text("Edit details") }
                            if (p.fileName != null) {
                                OutlinedButton(onClick = { viewModel.open { uriHandler.openUri(it) } }) {
                                    Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Open file")
                                }
                            }
                        }
                    }
                    val summary = p.summary
                    if (summary == null) {
                        item {
                            SurfaceCard {
                                Column(Modifier.padding(16.dp)) {
                                    Text(
                                        if (p.hasText) "Get a structured summary: question, methods, data, findings, limitations and contributions." else "Upload the PDF to get a summary. Papers added by details can still be cited and exported.",
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    if (p.hasText) {
                                        Spacer(Modifier.height(10.dp))
                                        Button(onClick = viewModel::analyse, enabled = !analysing) {
                                            if (analysing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White) else Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text(if (analysing) "Reading the paper..." else "Analyse")
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        item {
                            SurfaceCard {
                                Column(Modifier.padding(16.dp)) {
                                    Section("In short", summary.tldr)
                                    Section("Research question", summary.researchQuestion)
                                    Section("Methods", summary.methods)
                                    Section("Data", summary.data)
                                    Bullets("Findings", summary.findings)
                                    Bullets("Limitations", summary.limitations)
                                    Section("Contributions", summary.contributions)
                                    if (summary.keywords.isNotEmpty()) Text(summary.keywords.joinToString(" · "), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 10.dp))
                                    Text("AI summary: check it against the paper before citing.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontStyle = FontStyle.Italic, modifier = Modifier.padding(top = 10.dp))
                                }
                            }
                        }
                    }
                    item { TextButton(onClick = { confirmDelete = true }) { Text("Remove paper", color = MaterialTheme.colorScheme.error) } }
                }
                if (editing) PaperDetailsDialog("Edit details", p, onDismiss = { editing = false }) {
                    editing = false
                    viewModel.save(it)
                }
                if (confirmDelete) {
                    AlertDialog(
                        onDismissRequest = { confirmDelete = false },
                        title = { Text("Remove this paper?") },
                        confirmButton = {
                            TextButton(onClick = {
                                confirmDelete = false
                                viewModel.delete(onBack)
                            }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
                        },
                        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
                    )
                }
            }
            else -> Box(Modifier.fillMaxSize().padding(padding)) { CircularProgressIndicator(Modifier.align(Alignment.Center)) }
        }
    }
}

// --- Compare ------------------------------------------------------------------------------------------------------

@Composable
fun CompareScreen(
    viewModel: CompareViewModel,
    paperIds: List<String>,
    onBack: () -> Unit,
) {
    val state by viewModel.result.collectAsState()
    var focus by remember { mutableStateOf("") }
    LaunchedEffect(paperIds) { viewModel.run(paperIds, null) }
    Scaffold(
        topBar = { CollapsibleTopBar(title = "Literature review", isCollapsed = false, showBackButton = true, collapsible = false, onBackClick = onBack) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = focus, onValueChange = { focus = it.take(200) }, label = { Text("Focus (optional), e.g. efficiency") }, singleLine = true, modifier = Modifier.weight(1f))
                    TextButton(onClick = { viewModel.run(paperIds, focus) }, enabled = state !is UiState.Loading) { Text("Redo") }
                }
            }
            when (val s = state) {
                is UiState.Success -> {
                    val r = s.data
                    item {
                        ReviewTable(r)
                        Text("Swipe the table sideways for methods, data, findings and limitations.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                    }
                    r.note?.let { item { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
                    r.synthesis?.let { syn ->
                        item {
                            SurfaceCard {
                                Column(Modifier.padding(16.dp)) {
                                    Text("Synthesis", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                    Section("", syn.paragraph)
                                    Bullets("Themes", syn.themes)
                                    Bullets("Agreements", syn.agreements)
                                    Bullets("Disagreements", syn.disagreements)
                                    Bullets("Gaps", syn.gaps)
                                    Text("A starting point: write the review in your own words and check each claim.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontStyle = FontStyle.Italic, modifier = Modifier.padding(top = 10.dp))
                                }
                            }
                        }
                    }
                }
                is UiState.Error -> item { Text(s.message, color = MaterialTheme.colorScheme.error) }
                else -> item { Box(Modifier.fillMaxWidth().padding(32.dp)) { CircularProgressIndicator(Modifier.align(Alignment.Center)) } }
            }
        }
    }
}

@Composable
private fun ReviewTable(result: CompareResult) {
    val columns = listOf("Paper" to 150.dp, "Question" to 220.dp, "Methods" to 220.dp, "Data" to 180.dp, "Findings" to 260.dp, "Limitations" to 220.dp)
    Column(Modifier.clip(RoundedCornerShape(12.dp)).border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)).horizontalScroll(rememberScrollState())) {
        Row(Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))) {
            columns.forEach { (label, w) -> Text(label, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(w).padding(10.dp)) }
        }
        result.rows.forEach { row ->
            Row(Modifier.border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)) {
                listOf("[${row.number}] ${row.citation}", row.researchQuestion, row.methods, row.data, row.findings, row.limitations).zip(columns).forEach { (value, col) ->
                    Text(value.ifBlank { "–" }, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(col.second).padding(10.dp))
                }
            }
        }
    }
}

// --- Writing feedback ----------------------------------------------------------------------------------------------

@Composable
fun WritingFeedbackScreen(
    viewModel: WritingFeedbackViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.result.collectAsState()
    var text by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(WritingKind.LIT_REVIEW) }
    Scaffold(
        topBar = { CollapsibleTopBar(title = "Writing feedback", isCollapsed = false, showBackButton = true, collapsible = false, onBackClick = onBack) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            when (val s = state) {
                is UiState.Success -> {
                    val f = s.data
                    item { FeedbackView(f) }
                    item { OutlinedButton(onClick = viewModel::reset, modifier = Modifier.fillMaxWidth()) { Text("Review another section") } }
                }
                else -> {
                    item {
                        Text(
                            "Paste a section of your own writing. You'll get comments on structure, argument, evidence and style. Univa AI won't rewrite it for you.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(WritingKind.entries) { k -> FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text(k.label) }) }
                        }
                    }
                    item {
                        OutlinedTextField(
                            value = text,
                            onValueChange = { text = it.take(WritingFeedbackViewModel.MAX_CHARS) },
                            label = { Text("Your ${kind.label.lowercase()}") },
                            minLines = 10,
                            supportingText = { Text("${text.length} characters · at least ${WritingFeedbackViewModel.MIN_CHARS}") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (s is UiState.Error) item { Text(s.message, color = MaterialTheme.colorScheme.error) }
                    item {
                        Button(onClick = { viewModel.submit(text, kind) }, enabled = text.trim().length >= WritingFeedbackViewModel.MIN_CHARS && s !is UiState.Loading, modifier = Modifier.fillMaxWidth()) {
                            if (s is UiState.Loading) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                                Spacer(Modifier.width(8.dp))
                                Text("Reading your draft...")
                            } else {
                                Text("Get feedback")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FeedbackView(f: WritingFeedbackResult) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SurfaceCard {
            Column(Modifier.padding(16.dp)) {
                Text("Overall", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(f.overall, style = MaterialTheme.typography.bodyMedium)
                Bullets("Strengths", f.strengths)
                Bullets("Structure", f.structureNotes)
            }
        }
        f.issues.forEachIndexed { i, issue ->
            SurfaceCard {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${i + 1}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text(issue.category.lowercase().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                    if (issue.quote.isNotBlank()) Text("“${issue.quote}”", fontStyle = FontStyle.Italic, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp))
                    Text(issue.problem, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp))
                    if (issue.suggestion.isNotBlank()) Text("→ ${issue.suggestion}", style = MaterialTheme.typography.bodyMedium, color = Color(0xFF15803D), modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}

// --- Lecture summary (course materials) ------------------------------------------------------------------------------

@Composable
fun MaterialSummaryDialog(
    title: String,
    summary: MaterialSummary?,
    loading: Boolean,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            if (loading || summary == null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("Summarising...")
                }
            } else {
                Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                    Section("Overview", summary.overview)
                    Bullets("Key points", summary.keyPoints)
                    Bullets("Terms", summary.terms.map { "**${it.term}**: ${it.definition}" })
                    Bullets("Possible exam questions", summary.examQuestions)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
