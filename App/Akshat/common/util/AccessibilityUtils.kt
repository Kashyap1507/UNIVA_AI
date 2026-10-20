package com.kashyap.wizlyai.util

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * UX-026: Accessibility Utilities
 *
 * Helper functions and constants for implementing WCAG 2.1 AA compliant accessibility.
 * Provides semantic labels, state announcements, and touch target validations.
 */
object AccessibilityUtils {
    /**
     * Minimum touch target size (WCAG 2.1 AA requirement)
     */
    val MIN_TOUCH_TARGET_SIZE: Dp = 48.dp

    /**
     * Recommended touch target size for better usability
     */
    val RECOMMENDED_TOUCH_TARGET_SIZE: Dp = 56.dp

    /**
     * Add content description for screen readers
     */
    fun Modifier.contentDescription(description: String): Modifier {
        return this.semantics {
            contentDescription = description
        }
    }

    /**
     * Mark element as heading for screen reader navigation
     */
    fun Modifier.heading(): Modifier {
        return this.semantics {
            heading()
        }
    }

    /**
     * Announce state changes to screen readers
     * Use for dynamic content that changes without user interaction
     */
    fun Modifier.liveRegion(polite: Boolean = true): Modifier {
        return this.semantics {
            if (polite) {
                liveRegion = LiveRegionMode.Polite
            } else {
                liveRegion = LiveRegionMode.Assertive
            }
        }
    }

    /**
     * Mark element as a button for proper role announcement
     */
    fun Modifier.buttonRole(label: String? = null): Modifier {
        return this.semantics {
            role = Role.Button
            label?.let { contentDescription = it }
        }
    }

    /**
     * Mark element as a checkbox with state
     */
    fun Modifier.checkboxRole(
        checked: Boolean,
        label: String,
    ): Modifier {
        return this.semantics {
            role = Role.Checkbox
            contentDescription = label
            stateDescription = if (checked) "Checked" else "Unchecked"
        }
    }

    /**
     * Mark element as a switch with state
     */
    fun Modifier.switchRole(
        enabled: Boolean,
        label: String,
    ): Modifier {
        return this.semantics {
            role = Role.Switch
            contentDescription = label
            stateDescription = if (enabled) "On" else "Off"
        }
    }

    /**
     * Mark element as an image with description
     */
    fun Modifier.imageRole(description: String): Modifier {
        return this.semantics {
            role = Role.Image
            contentDescription = description
        }
    }

    /**
     * Ensure minimum touch target size
     */
    fun Modifier.minimumTouchTarget(): Modifier {
        return this.size(MIN_TOUCH_TARGET_SIZE)
    }

    /**
     * Group related content for screen reader navigation
     */
    fun Modifier.accessibilityGroup(label: String? = null): Modifier {
        return this.semantics(mergeDescendants = true) {
            label?.let { contentDescription = it }
        }
    }

    /**
     * Hide decorative element from screen readers
     */
    fun Modifier.decorative(): Modifier {
        return this.semantics {
            invisibleToUser()
        }
    }

    /**
     * Announce loading state to screen readers
     */
    fun loadingAnnouncement(message: String = "Loading"): String {
        return "$message. Please wait."
    }

    /**
     * Announce error state to screen readers
     */
    fun errorAnnouncement(error: String): String {
        return "Error: $error"
    }

    /**
     * Announce success state to screen readers
     */
    fun successAnnouncement(message: String): String {
        return "Success: $message"
    }

    /**
     * Format progress for screen reader announcement
     */
    fun progressAnnouncement(
        current: Int,
        total: Int,
    ): String {
        return "Progress: $current of $total"
    }

    /**
     * Format percentage for screen reader announcement
     */
    fun percentageAnnouncement(value: Int): String {
        return "$value percent"
    }

    /**
     * Format count for screen reader announcement
     */
    fun countAnnouncement(
        count: Int,
        itemType: String,
    ): String {
        return "$count $itemType${if (count != 1) "s" else ""}"
    }

    /**
     * Create navigation hint for screen readers
     */
    fun navigationHint(action: String): String {
        return "Double tap to $action"
    }

    /**
     * Create list item description with position information
     * @param position Current position (1-based)
     * @param total Total number of items
     * @param itemContent Description of the item content
     */
    fun listItemDescription(
        position: Int,
        total: Int,
        itemContent: String,
    ): String {
        return "$itemContent. Item $position of $total"
    }

    /**
     * Create page description for paginated content
     * @param currentPage Current page number (1-based)
     * @param totalPages Total number of pages
     */
    fun pageDescription(
        currentPage: Int,
        totalPages: Int,
    ): String {
        return "Page $currentPage of $totalPages"
    }

    /**
     * Create selection state description
     * @param isSelected Whether the item is selected
     * @param itemName Name of the item
     */
    fun selectionDescription(
        isSelected: Boolean,
        itemName: String,
    ): String {
        return "$itemName, ${if (isSelected) "selected" else "not selected"}"
    }

    /**
     * Create expanded state description for collapsible content
     * @param isExpanded Whether the content is expanded
     * @param label Label for the expandable item
     */
    fun expandedDescription(
        isExpanded: Boolean,
        label: String,
    ): String {
        return "$label, ${if (isExpanded) "expanded" else "collapsed"}"
    }

    /**
     * Create score description for accessibility
     * @param score Current score
     * @param total Total possible score
     */
    fun scoreDescription(
        score: Int,
        total: Int,
    ): String {
        val percentage = if (total > 0) (score * 100 / total) else 0
        return "Score: $score out of $total, $percentage percent"
    }

    /**
     * Create difficulty level description
     * @param difficulty Difficulty level (e.g., "easy", "medium", "hard")
     */
    fun difficultyDescription(difficulty: String): String {
        return "Difficulty: $difficulty"
    }

    /**
     * Create date description for accessibility
     * @param date Date string to announce
     */
    fun dateDescription(date: String): String {
        return "Date: $date"
    }

    /**
     * Validate if size meets minimum touch target requirement
     */
    fun isTouchTargetValid(size: Dp): Boolean {
        return size >= MIN_TOUCH_TARGET_SIZE
    }

    /**
     * Get accessible label for common UI elements
     */
    object Labels {
        const val BACK_BUTTON = "Navigate back"
        const val CLOSE_BUTTON = "Close"
        const val MENU_BUTTON = "Open menu"
        const val SEARCH_BUTTON = "Search"
        const val FILTER_BUTTON = "Filter"
        const val SORT_BUTTON = "Sort"
        const val SETTINGS_BUTTON = "Open settings"
        const val REFRESH_BUTTON = "Refresh"
        const val MORE_OPTIONS = "More options"
        const val EDIT_BUTTON = "Edit"
        const val DELETE_BUTTON = "Delete"
        const val SAVE_BUTTON = "Save"
        const val CANCEL_BUTTON = "Cancel"
        const val CONFIRM_BUTTON = "Confirm"
        const val ADD_BUTTON = "Add"
        const val SHARE_BUTTON = "Share"
        const val DOWNLOAD_BUTTON = "Download"
    }

    /**
     * Get accessible hint for common actions
     */
    object Hints {
        const val TAP_TO_OPEN = "Double tap to open"
        const val TAP_TO_CLOSE = "Double tap to close"
        const val TAP_TO_SELECT = "Double tap to select"
        const val TAP_TO_EDIT = "Double tap to edit"
        const val TAP_TO_DELETE = "Double tap to delete"
        const val SWIPE_TO_DISMISS = "Swipe to dismiss"
        const val LONG_PRESS_FOR_OPTIONS = "Long press for options"
    }

    /**
     * State descriptions for common UI states
     */
    object States {
        const val LOADING = "Loading"
        const val ERROR = "Error"
        const val EMPTY = "Empty"
        const val SELECTED = "Selected"
        const val UNSELECTED = "Not selected"
        const val EXPANDED = "Expanded"
        const val COLLAPSED = "Collapsed"
        const val ENABLED = "Enabled"
        const val DISABLED = "Disabled"
        const val CHECKED = "Checked"
        const val UNCHECKED = "Unchecked"
    }
}

/**
 * Extension functions for common accessibility patterns
 */

/**
 * Make a card accessible with proper role and description
 */
fun Modifier.accessibleCard(
    title: String,
    description: String? = null,
): Modifier {
    return this.semantics(mergeDescendants = true) {
        contentDescription =
            if (description != null) {
                "$title. $description"
            } else {
                title
            }
    }
}

/**
 * Make a list item accessible
 */
fun Modifier.accessibleListItem(
    position: Int,
    total: Int,
    label: String,
): Modifier {
    return this.semantics {
        contentDescription = "$label. Item $position of $total"
    }
}

/**
 * Make an input field accessible with label and hint
 */
fun Modifier.accessibleTextField(
    label: String,
    value: String,
    hint: String? = null,
    error: String? = null,
): Modifier {
    return this.semantics {
        contentDescription =
            buildString {
                append(label)
                if (value.isNotEmpty()) {
                    append(". Current value: $value")
                }
                hint?.let { append(". $it") }
                error?.let { append(". Error: $it") }
            }
    }
}

/**
 * Make a button accessible with action description
 */
fun Modifier.accessibleButton(
    label: String,
    enabled: Boolean = true,
    loading: Boolean = false,
): Modifier {
    return this.semantics {
        role = Role.Button
        contentDescription =
            buildString {
                append(label)
                when {
                    loading -> append(". Loading")
                    !enabled -> append(". Disabled")
                }
            }
        if (!enabled) {
            disabled()
        }
    }
}
