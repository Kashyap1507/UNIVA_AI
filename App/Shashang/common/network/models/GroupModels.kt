package com.kashyap.wizlyai.network.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GroupMember(
    @SerialName("user_id") val userId: String,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("role") val role: String = "MEMBER",
    @SerialName("is_me") val isMe: Boolean = false,
)

@Serializable
data class StudyGroup(
    @SerialName("id") val id: String,
    @SerialName("name") val name: String,
    @SerialName("description") val description: String? = null,
    @SerialName("course_code") val courseCode: String? = null,
    @SerialName("discoverable") val discoverable: Boolean = false,
    @SerialName("member_count") val memberCount: Int = 0,
    /** Present only for members. */
    @SerialName("role") val role: String? = null,
    @SerialName("invite_code") val inviteCode: String? = null,
    @SerialName("members") val members: List<GroupMember>? = null,
) {
    val isOwner: Boolean get() = role == "OWNER"
}

@Serializable
data class SharedNote(
    @SerialName("id") val id: String,
    @SerialName("group_id") val groupId: String,
    /** TEXT or MATERIAL. */
    @SerialName("kind") val kind: String,
    @SerialName("title") val title: String? = null,
    @SerialName("body") val body: String? = null,
    @SerialName("author_name") val authorName: String? = null,
    @SerialName("is_mine") val isMine: Boolean = false,
    @SerialName("can_remove") val canRemove: Boolean = false,
    @SerialName("material_title") val materialTitle: String? = null,
    @SerialName("material_mime") val materialMime: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class CreateGroupRequest(
    @SerialName("name") val name: String,
    @SerialName("description") val description: String? = null,
    @SerialName("course_code") val courseCode: String? = null,
    @SerialName("discoverable") val discoverable: Boolean = false,
)

@Serializable
data class UpdateGroupRequest(
    @SerialName("discoverable") val discoverable: Boolean? = null,
    @SerialName("name") val name: String? = null,
)

@Serializable
data class JoinGroupRequest(
    @SerialName("invite_code") val inviteCode: String,
)

@Serializable
data class NoteRequest(
    @SerialName("kind") val kind: String,
    @SerialName("body") val body: String? = null,
    @SerialName("title") val title: String? = null,
    @SerialName("material_id") val materialId: String? = null,
)

@Serializable
data class NoteDownload(
    @SerialName("url") val url: String,
    @SerialName("title") val title: String? = null,
    @SerialName("mime") val mime: String? = null,
)

@Serializable
data class ReportRequest(
    @SerialName("group_id") val groupId: String,
    @SerialName("target_type") val targetType: String,
    @SerialName("target_id") val targetId: String,
    @SerialName("reason") val reason: String,
    @SerialName("details") val details: String? = null,
)

@Serializable
data class ReportResult(
    @SerialName("id") val id: String,
    @SerialName("status") val status: String,
    @SerialName("hidden") val hidden: Boolean = false,
)

enum class ReportReason(val apiName: String, val label: String) {
    SPAM("SPAM", "Spam or advertising"),
    INAPPROPRIATE("INAPPROPRIATE", "Offensive or inappropriate"),
    COPYRIGHT("COPYRIGHT", "Copyrighted material"),
    ACADEMIC_INTEGRITY("ACADEMIC_INTEGRITY", "Graded work or answers (academic integrity)"),
    OTHER("OTHER", "Something else"),
}
