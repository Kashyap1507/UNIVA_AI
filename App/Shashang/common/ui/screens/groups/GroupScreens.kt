package com.kashyap.wizlyai.ui.screens.groups

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kashyap.wizlyai.network.models.CourseMaterial
import com.kashyap.wizlyai.network.models.CreateGroupRequest
import com.kashyap.wizlyai.network.models.GroupMember
import com.kashyap.wizlyai.network.models.IngestionStatus
import com.kashyap.wizlyai.network.models.ReportReason
import com.kashyap.wizlyai.network.models.SharedNote
import com.kashyap.wizlyai.network.models.StudyGroup
import com.kashyap.wizlyai.ui.components.CollapsibleTopBar
import com.kashyap.wizlyai.ui.state.UiState
import com.kashyap.wizlyai.ui.util.MathMarkdown
import com.kashyap.wizlyai.ui.viewmodel.GroupDetailViewModel
import com.kashyap.wizlyai.ui.viewmodel.GroupsViewModel
import com.mikepenz.markdown.m3.Markdown

@Composable
private fun GroupCard(
    group: StudyGroup,
    trailing: @Composable () -> Unit = {},
    onClick: (() -> Unit)? = null,
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Groups, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                group.courseCode?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold) }
                Text(group.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull("${group.memberCount} member${if (group.memberCount == 1) "" else "s"}", "you're the owner".takeIf { group.isOwner }).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            trailing()
        }
    }
}

// --- My groups ---------------------------------------------------------------------------------------------

@Composable
fun GroupsScreen(
    viewModel: GroupsViewModel,
    onOpenGroup: (StudyGroup) -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.groups.collectAsState()
    val found by viewModel.found.collectAsState()
    val message by viewModel.message.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var creating by remember { mutableStateOf(false) }
    var joining by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { viewModel.load() }
    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMessage()
        }
    }
    Scaffold(
        topBar = { CollapsibleTopBar(title = "Study groups", isCollapsed = false, showBackButton = true, collapsible = false, onBackClick = onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = { creating = true }, modifier = Modifier.weight(1f)) { Text("Create a group") }
                    OutlinedButton(onClick = { joining = true }, modifier = Modifier.weight(1f)) { Text("Join with a code") }
                }
            }
            item {
                OutlinedTextField(
                    value = search,
                    onValueChange = {
                        search = it.take(30)
                        if (it.isBlank()) viewModel.clearFound()
                    },
                    label = { Text("Find open groups by course code") },
                    placeholder = { Text("COMP-3300") },
                    singleLine = true,
                    trailingIcon = { IconButton(onClick = { viewModel.find(search) }, enabled = search.isNotBlank()) { Icon(Icons.Default.Search, contentDescription = "Search") } },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Only groups at your university that chose to be findable show up.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            found?.takeIf { it.isNotEmpty() }?.let { list ->
                items(list, key = { "f_" + it.id }) { g ->
                    GroupCard(g, trailing = { TextButton(onClick = { viewModel.joinFound(g, onOpenGroup) }) { Text("Join") } })
                }
            }
            item { Text("My groups", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp)) }
            when (val s = state) {
                is UiState.Success -> {
                    if (s.data.isEmpty()) {
                        item {
                            Text(
                                "Study with classmates: share notes and slides, plan sessions and keep each other on track. Groups are private unless you make them findable.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    items(s.data, key = { it.id }) { g -> GroupCard(g, onClick = { onOpenGroup(g) }) }
                }
                is UiState.Error -> item { Text(s.message, color = MaterialTheme.colorScheme.error) }
                else -> item { Box(Modifier.fillMaxWidth().padding(24.dp)) { CircularProgressIndicator(Modifier.align(Alignment.Center)) } }
            }
        }
    }
    if (creating) {
        CreateGroupDialog(onDismiss = { creating = false }) {
            creating = false
            viewModel.create(it, onOpenGroup)
        }
    }
    if (joining) {
        var code by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { joining = false },
            title = { Text("Join a group") },
            text = {
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.uppercase().filter { c -> c.isLetterOrDigit() }.take(8) },
                    label = { Text("Invite code") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
                )
            },
            confirmButton = {
                TextButton(enabled = code.length == 8, onClick = {
                    joining = false
                    viewModel.join(code, onOpenGroup)
                }) { Text("Join") }
            },
            dismissButton = { TextButton(onClick = { joining = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun CreateGroupDialog(
    onDismiss: () -> Unit,
    onCreate: (CreateGroupRequest) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var discoverable by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New study group") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it.take(80) }, label = { Text("Name") }, placeholder = { Text("OS midterm crew") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = code, onValueChange = { code = it.uppercase().take(30) }, label = { Text("Course code (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = description, onValueChange = { description = it.take(500) }, label = { Text("What's it for? (optional)") }, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Findable by classmates")
                        Text("Students at your university can find it by course code. Off: invite code only.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = discoverable, onCheckedChange = { discoverable = it }, enabled = code.isNotBlank())
                }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = {
                onCreate(CreateGroupRequest(name.trim(), description.trim().ifBlank { null }, code.trim().ifBlank { null }, discoverable && code.isNotBlank()))
            }) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// --- One group -------------------------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupDetailScreen(
    viewModel: GroupDetailViewModel,
    onBack: () -> Unit,
    onLeft: () -> Unit,
) {
    val state by viewModel.group.collectAsState()
    val notes by viewModel.notes.collectAsState()
    val shareable by viewModel.shareable.collectAsState()
    val message by viewModel.message.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val clipboard = LocalClipboardManager.current
    val uriHandler = LocalUriHandler.current
    var draft by remember { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    var showMembers by remember { mutableStateOf(false) }
    var reporting by remember { mutableStateOf<Triple<String, String, String>?>(null) }
    var confirmLeave by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { viewModel.load() }
    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMessage()
        }
    }
    val group = (state as? UiState.Success)?.data
    Scaffold(
        topBar = {
            CollapsibleTopBar(
                title = group?.name ?: "Group",
                isCollapsed = false,
                showBackButton = true,
                collapsible = false,
                onBackClick = onBack,
                trailingContent = {
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Group options") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Members") }, onClick = {
                                menu = false
                                showMembers = true
                            })
                            if (group?.isOwner == true) {
                                DropdownMenuItem(text = { Text("New invite code") }, onClick = {
                                    menu = false
                                    viewModel.resetCode()
                                })
                            } else if (group != null) {
                                DropdownMenuItem(text = { Text("Report group") }, onClick = {
                                    menu = false
                                    reporting = Triple("GROUP", group.id, group.name)
                                })
                            }
                            DropdownMenuItem(text = { Text("Leave group", color = MaterialTheme.colorScheme.error) }, onClick = {
                                menu = false
                                confirmLeave = true
                            })
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (group != null) {
                Row(
                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).navigationBarsPadding().imePadding().padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { viewModel.loadShareable() }) { Icon(Icons.Default.AttachFile, contentDescription = "Share a file") }
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it.take(5000) },
                        placeholder = { Text("Post to the group") },
                        maxLines = 4,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(enabled = draft.isNotBlank(), onClick = {
                        viewModel.post(draft)
                        draft = ""
                    }) { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Post") }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        when (val s = state) {
            is UiState.Error -> Text(s.message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(padding).padding(24.dp))
            is UiState.Success -> {
                val g = s.data
                LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item {
                        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))) {
                            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                                g.description?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                                Text(
                                    listOfNotNull(g.courseCode, "${g.memberCount} members", if (g.discoverable) "findable" else "invite only").joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                g.inviteCode?.let { code ->
                                    Spacer(Modifier.height(8.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("Invite code ", style = MaterialTheme.typography.bodyMedium)
                                        Text(code, style = MaterialTheme.typography.titleMedium, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                        IconButton(onClick = { clipboard.setText(AnnotatedString(code)) }) {
                                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy invite code")
                                        }
                                    }
                                }
                                if (g.isOwner && g.courseCode != null) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("Findable by classmates at your university", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                        Switch(checked = g.discoverable, onCheckedChange = { viewModel.setDiscoverable(it) })
                                    }
                                }
                                Text(
                                    "Share your own notes and slides. Don't post graded work or answers to assessments.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 6.dp),
                                )
                            }
                        }
                    }
                    if (notes.isEmpty()) {
                        item { Text("No posts yet. Say hi, or share a file with the attach button.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    items(notes, key = { it.id }) { note ->
                        NoteCard(
                            note = note,
                            onOpen = { viewModel.open(note) { url -> uriHandler.openUri(url) } },
                            onRemove = { viewModel.remove(note) },
                            onReport = { reporting = Triple("NOTE", note.id, note.title ?: note.body?.let { MathMarkdown.plain(it).take(40) } ?: "this post") },
                        )
                    }
                }
            }
            else -> Box(Modifier.fillMaxSize().padding(padding)) { CircularProgressIndicator(Modifier.align(Alignment.Center)) }
        }
    }

    if (showMembers && group != null) {
        MembersSheet(
            members = group.members.orEmpty(),
            isOwner = group.isOwner,
            onDismiss = { showMembers = false },
            onRemove = { viewModel.removeMember(it.userId) },
            onReport = {
                showMembers = false
                reporting = Triple("MEMBER", it.userId, it.displayName ?: "this member")
            },
        )
    }
    shareable?.let { list -> ShareSheet(list, onDismiss = viewModel::closeShare, onShare = viewModel::share) }
    reporting?.let { (type, id, label) ->
        ReportDialog(label, onDismiss = { reporting = null }) { reason, details ->
            reporting = null
            viewModel.report(type, id, reason, details)
        }
    }
    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text("Leave this group?") },
            text = { Text(if (group?.isOwner == true) "Ownership passes to the longest-standing member. If you're the last one, the group closes." else "You can rejoin later with the invite code.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmLeave = false
                    viewModel.leave(onLeft)
                }) { Text("Leave", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text("Stay") } },
        )
    }
}

@Composable
private fun NoteCard(
    note: SharedNote,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
    onReport: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Column(Modifier.fillMaxWidth().padding(start = 14.dp, top = 10.dp, bottom = 12.dp, end = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    (note.authorName ?: "Student") + (note.createdAt?.take(10)?.let { " · $it" } ?: ""),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Post options") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        if (note.canRemove) {
                            DropdownMenuItem(text = { Text("Remove") }, onClick = {
                                menu = false
                                onRemove()
                            })
                        }
                        if (!note.isMine) {
                            DropdownMenuItem(text = { Text("Report") }, onClick = {
                                menu = false
                                onReport()
                            })
                        }
                    }
                }
            }
            if (note.kind == "MATERIAL") {
                Row(
                    Modifier.fillMaxWidth().padding(end = 10.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)).clickable(onClick = onOpen).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(note.materialTitle ?: note.title ?: "File", fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("Tap to open", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            note.body?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(6.dp))
                Box(Modifier.padding(end = 10.dp)) { Markdown(content = MathMarkdown.prepare(it), modifier = Modifier.fillMaxWidth()) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MembersSheet(
    members: List<GroupMember>,
    isOwner: Boolean,
    onDismiss: () -> Unit,
    onRemove: (GroupMember) -> Unit,
    onReport: (GroupMember) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text("Members", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            members.forEach { m ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text((m.displayName ?: "Student") + if (m.isMe) " (you)" else "", modifier = Modifier.weight(1f))
                    if (m.role == "OWNER") Text("Owner", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    if (!m.isMe && m.role != "OWNER" && isOwner) TextButton(onClick = { onRemove(m) }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
                    if (!m.isMe) TextButton(onClick = { onReport(m) }) { Text("Report") }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShareSheet(
    options: List<Pair<com.kashyap.wizlyai.network.models.Course, List<CourseMaterial>>>,
    onDismiss: () -> Unit,
    onShare: (CourseMaterial, String) -> Unit,
) {
    var chosen by remember { mutableStateOf<CourseMaterial?>(null) }
    var comment by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text("Share a file", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Only the file you pick is shared, and only with this group.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            if (options.isEmpty()) {
                Text("Upload files to one of your courses first.", modifier = Modifier.padding(vertical = 16.dp))
            }
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                options.forEach { (course, materials) ->
                    Text(course.code?.takeIf { it.isNotBlank() } ?: course.name, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
                    materials.forEach { m ->
                        Row(Modifier.fillMaxWidth().clickable { chosen = m }.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = chosen?.id == m.id, onClick = { chosen = m })
                            Column(Modifier.weight(1f)) {
                                Text(m.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (m.status == IngestionStatus.PENDING || m.status == IngestionStatus.PROCESSING) {
                                    Text("Still processing", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
            OutlinedTextField(value = comment, onValueChange = { comment = it.take(500) }, label = { Text("Add a note (optional)") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            Spacer(Modifier.height(12.dp))
            Button(enabled = chosen != null, onClick = { chosen?.let { onShare(it, comment) } }, modifier = Modifier.fillMaxWidth()) { Text("Share") }
        }
    }
}

@Composable
private fun ReportDialog(
    label: String,
    onDismiss: () -> Unit,
    onSend: (ReportReason, String) -> Unit,
) {
    var reason by remember { mutableStateOf<ReportReason?>(null) }
    var details by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Report") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("What's wrong with \"${label.take(60)}\"?", style = MaterialTheme.typography.bodyMedium)
                ReportReason.entries.forEach { r ->
                    Row(Modifier.fillMaxWidth().clickable { reason = r }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = reason == r, onClick = { reason = r })
                        Text(r.label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                OutlinedTextField(value = details, onValueChange = { details = it.take(1000) }, label = { Text("Details (optional)") }, modifier = Modifier.fillMaxWidth())
                Text("Reports are reviewed by the Univa AI team. Posts reported by several members are hidden until then.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(enabled = reason != null, onClick = { reason?.let { onSend(it, details) } }) { Text("Send report") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
