package com.kashyap.wizlyai.ui.viewmodel

import com.kashyap.wizlyai.data.repository.CourseRepository
import com.kashyap.wizlyai.data.repository.StudyGroupRepository
import com.kashyap.wizlyai.network.models.Course
import com.kashyap.wizlyai.network.models.CourseMaterial
import com.kashyap.wizlyai.network.models.CreateGroupRequest
import com.kashyap.wizlyai.network.models.NoteRequest
import com.kashyap.wizlyai.network.models.ReportReason
import com.kashyap.wizlyai.network.models.ReportRequest
import com.kashyap.wizlyai.network.models.SharedNote
import com.kashyap.wizlyai.network.models.StudyGroup
import com.kashyap.wizlyai.network.models.UpdateGroupRequest
import com.kashyap.wizlyai.ui.state.UiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** My groups, create, join by code and find by course code. */
class GroupsViewModel(
    private val viewModelScope: CoroutineScope,
    private val repository: StudyGroupRepository = StudyGroupRepository(),
) {
    private val _groups = MutableStateFlow<UiState<List<StudyGroup>>>(UiState.Idle)
    val groups: StateFlow<UiState<List<StudyGroup>>> = _groups.asStateFlow()

    private val _found = MutableStateFlow<List<StudyGroup>?>(null)
    val found: StateFlow<List<StudyGroup>?> = _found.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun load() {
        viewModelScope.launch {
            if (_groups.value !is UiState.Success) _groups.value = UiState.Loading
            repository.myGroups().fold(
                onSuccess = { _groups.value = UiState.Success(it) },
                onFailure = { if (_groups.value !is UiState.Success) _groups.value = UiState.Error(it.message ?: "Couldn't load your groups", it) },
            )
        }
    }

    fun create(
        request: CreateGroupRequest,
        onCreated: (StudyGroup) -> Unit,
    ) {
        viewModelScope.launch {
            repository.create(request).fold(onSuccess = {
                load()
                onCreated(it)
            }, onFailure = { _message.value = it.message ?: "Couldn't create the group" })
        }
    }

    fun join(
        code: String,
        onJoined: (StudyGroup) -> Unit,
    ) {
        viewModelScope.launch {
            repository.joinByCode(code.trim()).fold(onSuccess = {
                load()
                onJoined(it)
            }, onFailure = { _message.value = it.message ?: "Couldn't join" })
        }
    }

    fun find(courseCode: String) {
        viewModelScope.launch {
            repository.discover(courseCode.trim()).fold(
                onSuccess = {
                    _found.value = it
                    if (it.isEmpty()) _message.value = "No open groups for ${courseCode.trim().uppercase()} yet. Create one and invite classmates."
                },
                onFailure = { _message.value = it.message ?: "Couldn't search" },
            )
        }
    }

    fun joinFound(
        group: StudyGroup,
        onJoined: (StudyGroup) -> Unit,
    ) {
        viewModelScope.launch {
            repository.joinDiscoverable(group.id).fold(onSuccess = {
                _found.value = _found.value?.filterNot { f -> f.id == group.id }
                load()
                onJoined(it)
            }, onFailure = { _message.value = it.message ?: "Couldn't join" })
        }
    }

    fun clearFound() {
        _found.value = null
    }

    fun clearMessage() {
        _message.value = null
    }
}

/** One group: members, notes, posting, sharing, reporting. */
class GroupDetailViewModel(
    private val groupId: String,
    private val viewModelScope: CoroutineScope,
    private val repository: StudyGroupRepository = StudyGroupRepository(),
    private val courses: CourseRepository = CourseRepository(),
) {
    private val _group = MutableStateFlow<UiState<StudyGroup>>(UiState.Idle)
    val group: StateFlow<UiState<StudyGroup>> = _group.asStateFlow()

    private val _notes = MutableStateFlow<List<SharedNote>>(emptyList())
    val notes: StateFlow<List<SharedNote>> = _notes.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /** My courses and their processed materials, for "Share a file". */
    private val _shareable = MutableStateFlow<List<Pair<Course, List<CourseMaterial>>>?>(null)
    val shareable: StateFlow<List<Pair<Course, List<CourseMaterial>>>?> = _shareable.asStateFlow()

    fun load() {
        viewModelScope.launch {
            if (_group.value !is UiState.Success) _group.value = UiState.Loading
            repository.group(groupId).fold(
                onSuccess = { _group.value = UiState.Success(it) },
                onFailure = { _group.value = UiState.Error(it.message ?: "Couldn't open the group", it) },
            )
            repository.notes(groupId).onSuccess { _notes.value = it }
        }
    }

    fun post(body: String) {
        viewModelScope.launch {
            repository.postNote(groupId, NoteRequest(kind = "TEXT", body = body.trim())).fold(
                onSuccess = { _notes.value = listOf(it) + _notes.value },
                onFailure = { _message.value = it.message ?: "Couldn't post" },
            )
        }
    }

    fun loadShareable() {
        viewModelScope.launch {
            val list = mutableListOf<Pair<Course, List<CourseMaterial>>>()
            courses.getCourses().onSuccess { (all, _) ->
                for (c in all.filterNot { it.isArchived }) {
                    courses.getMaterials(c.id).onSuccess { (materials, _) -> if (materials.isNotEmpty()) list += c to materials }
                }
            }
            _shareable.value = list
        }
    }

    fun share(
        material: CourseMaterial,
        comment: String,
    ) {
        _shareable.value = null
        viewModelScope.launch {
            repository.postNote(groupId, NoteRequest(kind = "MATERIAL", materialId = material.id, body = comment.trim().ifBlank { null })).fold(
                onSuccess = {
                    _notes.value = listOf(it) + _notes.value
                    _message.value = "Shared ${material.title}"
                },
                onFailure = { _message.value = it.message ?: "Couldn't share the file" },
            )
        }
    }

    fun closeShare() {
        _shareable.value = null
    }

    fun open(
        note: SharedNote,
        openUrl: (String) -> Unit,
    ) {
        viewModelScope.launch {
            repository.download(groupId, note.id).fold(onSuccess = { openUrl(it.url) }, onFailure = { _message.value = it.message ?: "Couldn't open the file" })
        }
    }

    fun remove(note: SharedNote) {
        viewModelScope.launch {
            repository.deleteNote(groupId, note.id).fold(
                onSuccess = { _notes.value = _notes.value.filterNot { it.id == note.id } },
                onFailure = { _message.value = it.message ?: "Couldn't remove it" },
            )
        }
    }

    fun report(
        targetType: String,
        targetId: String,
        reason: ReportReason,
        details: String,
    ) {
        viewModelScope.launch {
            repository.report(ReportRequest(groupId, targetType, targetId, reason.apiName, details.trim().ifBlank { null })).fold(
                onSuccess = {
                    _message.value = "Thanks. We'll review it" + if (it.hidden) " (hidden for now)." else "."
                    if (it.hidden) _notes.value = _notes.value.filterNot { n -> n.id == targetId }
                },
                onFailure = { _message.value = it.message ?: "Couldn't send the report" },
            )
        }
    }

    fun setDiscoverable(value: Boolean) {
        viewModelScope.launch {
            repository.update(groupId, UpdateGroupRequest(discoverable = value)).fold(
                onSuccess = { updated -> (group.value as? UiState.Success)?.data?.let { _group.value = UiState.Success(it.copy(discoverable = updated.discoverable)) } },
                onFailure = { _message.value = it.message ?: "Couldn't update the group" },
            )
        }
    }

    fun resetCode() {
        viewModelScope.launch {
            repository.resetCode(groupId).fold(
                onSuccess = { updated -> (group.value as? UiState.Success)?.data?.let { _group.value = UiState.Success(it.copy(inviteCode = updated.inviteCode)) } },
                onFailure = { _message.value = it.message ?: "Couldn't make a new code" },
            )
        }
    }

    fun removeMember(userId: String) {
        viewModelScope.launch {
            repository.removeMember(groupId, userId).fold(onSuccess = { load() }, onFailure = { _message.value = it.message ?: "Couldn't remove the member" })
        }
    }

    fun leave(onLeft: () -> Unit) {
        viewModelScope.launch {
            repository.leave(groupId).fold(onSuccess = { onLeft() }, onFailure = { _message.value = it.message ?: "Couldn't leave the group" })
        }
    }

    fun clearMessage() {
        _message.value = null
    }
}
