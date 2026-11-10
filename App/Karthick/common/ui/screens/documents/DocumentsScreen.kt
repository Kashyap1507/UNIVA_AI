package com.kashyap.wizlyai.ui.screens.documents

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.kashyap.wizlyai.data.models.Document
import com.kashyap.wizlyai.data.models.DocumentType
import com.kashyap.wizlyai.ui.components.*
import com.kashyap.wizlyai.ui.components.DocumentsTopBar
import com.kashyap.wizlyai.ui.components.ViewMode
import com.kashyap.wizlyai.util.DateTimeFormatter
import kotlinx.coroutines.launch

/**
 * Documents Screen with grid/list view toggle and filters
 * Features: View modes, grouping, sorting, search, selection mode
 *
 * @param documentProvider Optional document provider for loading documents (null uses mock data)
 * @param userId Owner of the documents (the signed-in student)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentsScreen(
    onDocumentClick: (String) -> Unit,
    onBack: () -> Unit,
    documentProvider: DocumentProvider? = null,
    userId: String,
    modifier: Modifier = Modifier,
) {
    val coroutineScope = rememberCoroutineScope()
    // UI State
    var viewMode by remember { mutableStateOf(ViewMode.GRID) }
    var groupBy by remember { mutableStateOf(GroupBy.DATE) }
    var sortBy by remember { mutableStateOf(SortBy.DATE) }
    var searchQuery by remember { mutableStateOf("") }
    var searchVisible by remember { mutableStateOf(false) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedDocs by remember { mutableStateOf(setOf<String>()) }
    var showFilterSheet by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    // Scroll state for collapsing header
    val listScrollState = rememberLazyListState()
    val gridScrollState = rememberLazyGridState()
    val isCollapsed =
        remember {
            derivedStateOf {
                if (viewMode == ViewMode.LIST) {
                    listScrollState.firstVisibleItemScrollOffset > 100
                } else {
                    gridScrollState.firstVisibleItemScrollOffset > 100
                }
            }
        }

    // Load documents from provider or use mock data
    var documents by remember { mutableStateOf<List<Document>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var refreshTrigger by remember { mutableStateOf(0) }

    // Load documents on first composition and when refreshTrigger changes
    LaunchedEffect(documentProvider, userId, refreshTrigger) {
        // Don't show full loading spinner if this is a pull-to-refresh
        if (refreshTrigger == 0) {
            isLoading = true
        }
        loadError = null

        if (documentProvider != null) {
            // Load from real provider
            try {
                println("📚 DocumentsScreen: Loading documents for user: $userId")
                documents = documentProvider.getDocuments(userId)
                println("📚 DocumentsScreen: Loaded ${documents.size} documents")
                isLoading = false
                isRefreshing = false
            } catch (e: Exception) {
                println("❌ DocumentsScreen: Error loading documents: ${e.message}")
                e.printStackTrace()
                loadError = e.message ?: "Failed to load documents"
                isLoading = false
                isRefreshing = false
            }
        } else {
            // No local storage on this platform build
            documents = emptyList()
            isLoading = false
            isRefreshing = false
        }
    }

    // Manual refresh handler for pull-to-refresh
    val onRefresh: () -> Unit = {
        isRefreshing = true
        refreshTrigger++
        Unit
    }

    // Auto-refresh disabled - caused navigation freeze on iOS after app restart
    // Documents will only refresh on:
    // 1. First load
    // 2. Pull-to-refresh
    // 3. Manual navigation back to Documents tab
    // LaunchedEffect(Unit) {
    //     while (true) {
    //         kotlinx.coroutines.delay(2000)
    //         refreshTrigger++
    //     }
    // }

    // Filter and sort documents
    val filteredDocs =
        documents.filter {
            it.title.contains(searchQuery, ignoreCase = true)
        }

    val sortedDocs =
        when (sortBy) {
            SortBy.DATE -> filteredDocs.sortedByDescending { it.createdAt }
            SortBy.NAME -> filteredDocs.sortedBy { it.title }

        }

    val groupedDocs =
        when (groupBy) {
            GroupBy.DATE -> sortedDocs.groupBy { it.createdAt.take(10) } // Date only
            GroupBy.NONE -> mapOf("All scans" to sortedDocs)
        }

    Scaffold(
        topBar = {
            DocumentsTopBar(
                isCollapsed = isCollapsed.value,
                selectionMode = selectionMode,
                viewMode = viewMode,
                documentCount = filteredDocs.size,
                searchVisible = searchVisible,
                showBackButton = true,
                onBackClick = onBack,
                onSelectionModeToggle = {
                    selectionMode = !selectionMode
                    if (!selectionMode) selectedDocs = emptySet()
                },
                onViewModeToggle = {
                    viewMode = if (viewMode == ViewMode.GRID) ViewMode.LIST else ViewMode.GRID
                },
                onSearchToggle = {
                    searchVisible = !searchVisible
                    if (!searchVisible) searchQuery = ""
                },
                onGroupClick = {
                    showFilterSheet = true
                },
                onSortClick = {
                    showFilterSheet = true
                },
            )
        },
    ) { paddingValues ->
        val pullRefreshState = rememberPullToRefreshState()

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            state = pullRefreshState,
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
            indicator = {
                CustomPullToRefreshIndicator(
                    state = pullRefreshState,
                    isRefreshing = isRefreshing,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            },
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp), // Consistent horizontal padding
            ) {
                // Search Bar (collapsible)
                AnimatedVisibility(
                    visible = searchVisible,
                    enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
                    exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut(),
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        placeholder = { Text("Search documents...") },
                        leadingIcon = { Icon(Icons.Default.Search, "Search") },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Close, "Clear")
                                }
                            }
                        },
                        singleLine = true,
                        colors =
                            OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            ),
                    )
                }

                // Selection Actions
                AnimatedVisibility(visible = selectionMode && selectedDocs.isNotEmpty()) {
                    Card(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                        colors =
                            CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                            ),
                    ) {
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "${selectedDocs.size} selected",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                IconButton(onClick = { /* Share */ }) {
                                    Icon(Icons.Default.Share, "Share")
                                }
                                IconButton(onClick = { showDeleteDialog = true }) {
                                    Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }

                // Loading indicator
                if (isLoading) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .semantics {
                                    contentDescription = "Loading documents, please wait"
                                    liveRegion = LiveRegionMode.Polite
                                },
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                } else if (loadError != null) {
                    // Error state
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .errorSemantics(loadError ?: "Failed to load documents"),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Error,
                                contentDescription = null,
                                modifier =
                                    Modifier
                                        .size(64.dp)
                                        .decorative(),
                                tint = MaterialTheme.colorScheme.error,
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "Error loading documents",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.heading(),
                            )
                            Text(
                                loadError ?: "Unknown error",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    // Documents Grid/List
                    LazyColumn(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .semantics {
                                    contentDescription = "${sortedDocs.size} documents loaded. Grouped by ${groupBy.name.lowercase()}."
                                },
                        contentPadding = PaddingValues(bottom = 80.dp),
                    ) {
                        groupedDocs.forEach { (groupName, docs) ->
                            item {
                                Text(
                                    text = groupName ?: "Unknown",
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier =
                                        Modifier
                                            .padding(16.dp)
                                            .sectionHeading(),
                                )
                            }

                            if (viewMode == ViewMode.GRID) {
                                item {
                                    // Calculate proper height for grid: rows * (card height + spacing) + extra bottom padding
                                    val cardHeight = 220 // 120dp thumbnail + 100dp content
                                    val spacing = 12
                                    val numberOfRows = (docs.size + 1) / 2 // Ceiling division for 2 columns
                                    val totalHeight = (numberOfRows * (cardHeight + spacing) + spacing).dp

                                    LazyVerticalGrid(
                                        columns = GridCells.Fixed(2),
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .height(totalHeight),
                                        contentPadding = PaddingValues(horizontal = 16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        items(docs) { doc ->
                                            DocumentGridItem(
                                                document = doc,
                                                isSelected = selectedDocs.contains(doc.id),
                                                selectionMode = selectionMode,
                                                onClick = {
                                                    if (selectionMode) {
                                                        selectedDocs =
                                                            if (selectedDocs.contains(doc.id)) {
                                                                selectedDocs - doc.id
                                                            } else {
                                                                selectedDocs + doc.id
                                                            }
                                                    } else {
                                                        onDocumentClick(doc.id)
                                                    }
                                                },
                                            )
                                        }
                                    }
                                }
                            } else {
                                items(docs) { doc ->
                                    DocumentListItem(
                                        document = doc,
                                        isSelected = selectedDocs.contains(doc.id),
                                        selectionMode = selectionMode,
                                        onClick = {
                                            if (selectionMode) {
                                                selectedDocs =
                                                    if (selectedDocs.contains(doc.id)) {
                                                        selectedDocs - doc.id
                                                    } else {
                                                        selectedDocs + doc.id
                                                    }
                                            } else {
                                                onDocumentClick(doc.id)
                                            }
                                        },
                                    )
                                }
                            }
                        }

                        if (filteredDocs.isEmpty()) {
                            item {
                                Box(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(32.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            Icons.Default.FolderOpen,
                                            contentDescription = null,
                                            modifier = Modifier.size(64.dp),
                                            tint = MaterialTheme.colorScheme.outline,
                                        )
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Text(
                                            "No documents found",
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Delete Confirmation Dialog
        if (showDeleteDialog) {
            DeleteConfirmationDialog(
                documentCount = selectedDocs.size,
                onConfirm = {
                    // Delete selected documents
                    val docsToDelete = selectedDocs.toList()
                    showDeleteDialog = false

                    // Delete asynchronously
                    coroutineScope.launch {
                        docsToDelete.forEach { docId ->
                            documentProvider?.deleteDocument(docId)
                        }

                        // Clear selection and exit selection mode
                        selectedDocs = emptySet()
                        selectionMode = false

                        // Trigger refresh
                        refreshTrigger++
                    }
                },
                onDismiss = {
                    showDeleteDialog = false
                },
            )
        }

        // Filter Bottom Sheet
        if (showFilterSheet) {
            ModalBottomSheet(onDismissRequest = { showFilterSheet = false }) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Group By Section
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.FilterList,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Group By",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    GroupBy.values().forEach { group ->
                        FilterChip(
                            selected = groupBy == group,
                            onClick = { groupBy = group },
                            label = { Text(group.name) },
                            leadingIcon = {
                                if (groupBy == group) {
                                    Icon(Icons.Default.Check, null, Modifier.size(18.dp))
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Sort By Section
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sort,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Sort By",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    SortBy.values().forEach { sort ->
                        FilterChip(
                            selected = sortBy == sort,
                            onClick = { sortBy = sort },
                            label = { Text(sort.name) },
                            leadingIcon = {
                                if (sortBy == sort) {
                                    Icon(Icons.Default.Check, null, Modifier.size(18.dp))
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}

@Composable
private fun DocumentGridItem(
    document: Document,
    isSelected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (isSelected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
            ),
        elevation =
            CardDefaults.cardElevation(
                defaultElevation = 4.dp,
                pressedElevation = 8.dp,
            ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column {
            // Document thumbnail
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                contentAlignment = Alignment.Center,
            ) {
                // Show thumbnail image if available, otherwise show icon
                if (document.thumbnailUrl != null && document.thumbnailUrl.isNotEmpty()) {
                    val context = LocalPlatformContext.current
                    AsyncImage(
                        model =
                            ImageRequest.Builder(context)
                                .data(
                                    if (document.thumbnailUrl.startsWith("http")) {
                                        document.thumbnailUrl
                                    } else {
                                        "file://${document.thumbnailUrl}"
                                    },
                                )
                                .crossfade(true)
                                .build(),
                        contentDescription = "Thumbnail for ${document.title}",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    )
                } else {
                    Icon(
                        getDocumentTypeIcon(document.type),
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }

                // Sync status indicator (green cloud icon)
                if (!selectionMode) {
                    Icon(
                        imageVector = Icons.Default.CloudDone,
                        contentDescription = "Synced",
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier =
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                                .size(24.dp),
                    )
                }

                if (selectionMode) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = null,
                        modifier =
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp),
                    )
                }
            }

            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = document.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Default.Description,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "${document.pageCount}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        DateTimeFormatter.formatDocumentDateTime(document.createdAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun DocumentListItem(
    document: Document,
    isSelected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (isSelected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
            ),
        elevation =
            CardDefaults.cardElevation(
                defaultElevation = 2.dp,
                pressedElevation = 4.dp,
            ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = null,
                )
                Spacer(modifier = Modifier.width(8.dp))
            }

            // Show thumbnail image if available, otherwise show icon
            Box(
                modifier = Modifier.size(48.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (document.thumbnailUrl != null && document.thumbnailUrl.isNotEmpty()) {
                    val context = LocalPlatformContext.current
                    AsyncImage(
                        model =
                            ImageRequest.Builder(context)
                                .data(
                                    if (document.thumbnailUrl.startsWith("http")) {
                                        document.thumbnailUrl
                                    } else {
                                        "file://${document.thumbnailUrl}"
                                    },
                                )
                                .crossfade(true)
                                .build(),
                        contentDescription = "Thumbnail for ${document.title}",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    )
                } else {
                    Icon(
                        getDocumentTypeIcon(document.type),
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }

                // Sync status indicator (small green check badge)
                if (!selectionMode) {
                    Icon(
                        imageVector = Icons.Default.CloudDone,
                        contentDescription = "Synced",
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier =
                            Modifier
                                .align(Alignment.BottomEnd)
                                .size(16.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = document.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Default.Description,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "${document.pageCount}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    DateTimeFormatter.formatDate(document.createdAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    DateTimeFormatter.formatTime(document.createdAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            IconButton(onClick = { /* More options */ }) {
                Icon(Icons.Default.MoreVert, "More")
            }
        }
    }
}

private fun getDocumentTypeIcon(type: DocumentType): ImageVector {
    return when (type) {
        DocumentType.HOMEWORK -> Icons.Default.Assignment
        DocumentType.WORKSHEET -> Icons.Default.Description
    }
}

enum class ViewMode {
    GRID,
    LIST,
}

enum class GroupBy {
    DATE,
    NONE,
}

enum class SortBy {
    DATE,
    NAME,
}

/**
 * Delete Confirmation Dialog
 * Shows a confirmation dialog before deleting selected documents
 */
@Composable
private fun DeleteConfirmationDialog(
    documentCount: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    ThemedAlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.Delete,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(32.dp),
            )
        },
        title = {
            Text("Delete Documents?")
        },
        text = {
            Text(
                text =
                    if (documentCount == 1) {
                        "Are you sure you want to delete this document? This action cannot be undone."
                    } else {
                        "Are you sure you want to delete $documentCount documents? This action cannot be undone."
                    },
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                    ),
            ) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

/**
 * Custom Pull to Refresh Indicator
 * Shows animated icon and text that changes based on pull progress
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomPullToRefreshIndicator(
    state: PullToRefreshState,
    isRefreshing: Boolean,
    modifier: Modifier = Modifier,
) {
    val pullProgress = state.distanceFraction
    val rotation by animateFloatAsState(
        targetValue = if (pullProgress >= 1f) 180f else 0f,
        animationSpec =
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium,
            ),
        label = "pullRotation",
    )

    val alpha by animateFloatAsState(
        targetValue = if (pullProgress > 0.1f) 1f else 0f,
        animationSpec = tween(150),
        label = "pullAlpha",
    )

    if (pullProgress > 0.1f || isRefreshing) {
        Column(
            modifier =
                modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (isRefreshing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.secondary,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Syncing...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Icon(
                    imageVector = Icons.Default.CloudSync,
                    contentDescription = null,
                    modifier =
                        Modifier
                            .size(28.dp)
                            .rotate(rotation),
                    tint = MaterialTheme.colorScheme.secondary,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (pullProgress >= 1f) "Release to sync" else "Pull down to sync",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (pullProgress >= 1f) FontWeight.Bold else FontWeight.Normal,
                    color = if (pullProgress >= 1f) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
