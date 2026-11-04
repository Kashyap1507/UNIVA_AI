package com.kashyap.wizlyai.ui.screens.tutor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.ScrollableDefaults
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kashyap.wizlyai.network.models.TutorCitation
import com.kashyap.wizlyai.network.models.TutorMessage
import com.kashyap.wizlyai.ui.components.CollapsibleTopBar
import com.kashyap.wizlyai.ui.util.MathMarkdown
import com.kashyap.wizlyai.ui.viewmodel.StreamingAnswer
import com.kashyap.wizlyai.ui.viewmodel.TutorViewModel
import com.mikepenz.markdown.m3.Markdown

private val STARTER_QUESTIONS =
    listOf(
        "Summarise the key ideas of the latest lecture",
        "Explain the hardest concept in this course with an example",
        "What should I review before the next exam?",
    )

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TutorChatScreen(
    viewModel: TutorViewModel,
    courseName: String,
    onBack: () -> Unit,
) {
    val messages by viewModel.messages.collectAsState()
    val streaming by viewModel.streaming.collectAsState()
    val sessions by viewModel.sessions.collectAsState()
    val currentSession by viewModel.currentSession.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val restoredDraft by viewModel.restoredDraft.collectAsState()
    var input by remember { mutableStateOf("") }
    var showHistory by remember { mutableStateOf(false) }
    var openCitation by remember { mutableStateOf<TutorCitation?>(null) }
    val listState = rememberLazyListState()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { viewModel.start() }
    LaunchedEffect(error) {
        error?.let {
            snackbar.showSnackbar(it)
            viewModel.clearError()
        }
    }
    LaunchedEffect(restoredDraft) {
        viewModel.consumeRestoredDraft()?.let { if (input.isBlank()) input = it }
    }
    // When a question is asked, bring it to the top once. The answer then fills in below it
    // from its first line, and the list stays put while it streams so the student reads at
    // their own pace. Opening a past conversation jumps to its last question the same way.
    val questionCount = messages.count { it.isUser }
    LaunchedEffect(questionCount) {
        val index = messages.indexOfLast { it.isUser }
        if (index < 0) return@LaunchedEffect
        if (streaming != null) listState.animateScrollToItem(index) else listState.scrollToItem(index)
    }
    val gentleFling = rememberGentleFling()

    fun send(text: String) {
        if (text.isBlank() || streaming != null) return
        viewModel.ask(text)
        input = ""
    }

    Scaffold(
        topBar = {
            CollapsibleTopBar(
                title = "Tutor · $courseName",
                isCollapsed = false,
                showBackButton = true,
                collapsible = false,
                onBackClick = onBack,
                trailingContent = {
                    IconButton(onClick = { showHistory = true }) { Icon(Icons.Default.History, contentDescription = "Past conversations") }
                    IconButton(onClick = { viewModel.newConversation() }, enabled = streaming == null) {
                        Icon(Icons.Default.AddComment, contentDescription = "New conversation")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            InputBar(
                value = input,
                onValueChange = { input = it.take(4000) },
                enabled = streaming == null,
                onSend = { send(input) },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                isLoading && messages.isEmpty() -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                messages.isEmpty() && streaming == null -> EmptyTutor(courseName, onPick = { send(it) })
                else ->
                    LazyColumn(
                        state = listState,
                        flingBehavior = gentleFling,
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(messages, key = { it.id }) { message ->
                            if (message.isUser) UserBubble(message.content) else AssistantBubble(message, onCitation = { openCitation = it })
                        }
                        streaming?.let { item(key = "streaming") { StreamingBubble(it) } }
                    }
            }
        }
    }

    if (showHistory) {
        ModalBottomSheet(onDismissRequest = { showHistory = false }) {
            Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                Text("Conversations", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
                if (sessions.isEmpty()) {
                    Text("No conversations yet.", modifier = Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                sessions.forEach { session ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                showHistory = false
                                viewModel.openSession(session.id)
                            }
                            .padding(horizontal = 24.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                session.title,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (session.id == currentSession?.id) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text("${session.messageCount} messages", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { viewModel.deleteSession(session.id) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete conversation ${session.title}")
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }

    openCitation?.let { citation ->
        AlertDialog(
            onDismissRequest = { openCitation = null },
            icon = { Icon(Icons.Default.MenuBook, contentDescription = null) },
            title = { Text("Source [${citation.n}]") },
            text = { Text(listOfNotNull(citation.title, citation.pageLabel).joinToString(" · ")) },
            confirmButton = { TextButton(onClick = { openCitation = null }) { Text("OK") } },
        )
    }
}

@Composable
private fun EmptyTutor(
    courseName: String,
    onPick: (String) -> Unit,
) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(72.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Psychology, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text("Ask about $courseName", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            "Answers come from your uploaded course materials, with numbered sources. If something isn't covered, the tutor tells you.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        STARTER_QUESTIONS.forEach { question ->
            Surface(
                onClick = { onPick(question) },
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            ) {
                Text(question, modifier = Modifier.padding(14.dp), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun UserBubble(text: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Surface(
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 4.dp),
            modifier = Modifier.widthIn(max = 320.dp),
        ) {
            Text(text, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun AssistantBubble(
    message: TutorMessage,
    onCitation: (TutorCitation) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp),
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Markdown(content = MathMarkdown.prepare(message.content), modifier = Modifier.fillMaxWidth())
            if (message.citations.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text("Sources", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    message.citations.forEach { citation ->
                        AssistChip(
                            onClick = { onCitation(citation) },
                            label = {
                                Text(
                                    listOfNotNull("[${citation.n}] ${citation.title}", citation.pageLabel).joinToString(" · "),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StreamingBubble(answer: StreamingAnswer) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp),
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            if (answer.text.isBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.size(10.dp))
                    Text(
                        if (answer.grounded == null) "Searching your course materials..." else "Writing an answer...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Markdown(content = MathMarkdown.prepare(answer.text), modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun InputBar(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    onSend: () -> Unit,
) {
    Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth().navigationBarsPadding().imePadding()) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = { Text("Ask about your course...") },
                modifier = Modifier.weight(1f),
                maxLines = 5,
                shape = RoundedCornerShape(20.dp),
            )
            IconButton(onClick = onSend, enabled = enabled && value.isNotBlank()) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send question")
            }
        }
    }
}

/** The default fling, started at a lower speed so long answers are easier to read through. */
@Composable
private fun rememberGentleFling(): FlingBehavior {
    val base = ScrollableDefaults.flingBehavior()
    return remember(base) {
        object : FlingBehavior {
            override suspend fun ScrollScope.performFling(initialVelocity: Float): Float =
                with(base) { performFling(initialVelocity * FLING_SPEED) }
        }
    }
}

private const val FLING_SPEED = 0.6f
