package com.kashyap.wizlyai.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*

/**
 * Accessibility Extensions for Compose UI Components
 *
 * Provides 20+ Modifier extensions for common accessibility patterns
 * to ensure WCAG 2.1 Level AA compliance across the app.
 */

/**
 * Mark an element as a heading for screen reader navigation
 * Usage: Text("Section Title", modifier = Modifier.heading())
 */
fun Modifier.heading(): Modifier {
    return this.semantics { heading() }
}

/**
 * Mark a decorative element that should be hidden from screen readers
 * Usage: Icon(..., modifier = Modifier.decorative())
 */
fun Modifier.decorative(): Modifier {
    return this.semantics { invisibleToUser() }
}

/**
 * Add a live region for announcing dynamic content changes
 * @param mode Polite (default) or Assertive for urgent announcements
 * Usage: Text(statusMessage, modifier = Modifier.liveRegion(LiveRegionMode.Polite))
 */
fun Modifier.liveRegion(mode: LiveRegionMode = LiveRegionMode.Polite): Modifier {
    return this.semantics { liveRegion = mode }
}

/**
 * Mark an element with button role and description
 * Usage: Box(modifier = Modifier.buttonRole("Delete item")) { ... }
 */
fun Modifier.buttonRole(description: String): Modifier {
    return this.semantics {
        role = Role.Button
        contentDescription = description
    }
}

/**
 * Accessible button with proper role and label
 * Usage: Button(modifier = Modifier.accessibleButton("Submit form", enabled = true))
 */
fun Modifier.accessibleButton(
    label: String,
    enabled: Boolean = true,
): Modifier {
    return this.semantics {
        role = Role.Button
        contentDescription = label
        if (!enabled) disabled()
    }
}

/**
 * Mark an element with image role and description
 * Usage: AsyncImage(..., modifier = Modifier.imageRole("Profile picture"))
 */
fun Modifier.imageRole(description: String): Modifier {
    return this.semantics {
        role = Role.Image
        contentDescription = description
    }
}

/**
 * Mark a checkbox with state
 * Usage: Checkbox(modifier = Modifier.checkboxRole(isChecked, "Accept terms"))
 */
fun Modifier.checkboxRole(
    checked: Boolean,
    label: String,
): Modifier {
    return this.semantics {
        role = Role.Checkbox
        contentDescription = "$label, ${if (checked) "checked" else "not checked"}"
        stateDescription = if (checked) "Checked" else "Not checked"
    }
}

/**
 * Mark a switch with state
 * Usage: Switch(modifier = Modifier.switchRole(isEnabled, "Enable notifications"))
 */
fun Modifier.switchRole(
    enabled: Boolean,
    label: String,
): Modifier {
    return this.semantics {
        role = Role.Switch
        contentDescription = "$label, ${if (enabled) "on" else "off"}"
        stateDescription = if (enabled) "On" else "Off"
    }
}

/**
 * Mark a radio button with state
 * Usage: RadioButton(modifier = Modifier.radioButtonRole(isSelected, "Option 1"))
 */
fun Modifier.radioButtonRole(
    selected: Boolean,
    label: String,
): Modifier {
    return this.semantics {
        role = Role.RadioButton
        contentDescription = "$label, ${if (selected) "selected" else "not selected"}"
        stateDescription = if (selected) "Selected" else "Not selected"
    }
}

/**
 * Mark a tab with selection state
 * Usage: Tab(modifier = Modifier.tabRole(isSelected, "Overview tab"))
 */
fun Modifier.tabRole(
    selected: Boolean,
    label: String,
): Modifier {
    return this.semantics {
        role = Role.Tab
        contentDescription = "$label tab, ${if (selected) "selected" else "not selected"}"
        stateDescription = if (selected) "Selected" else "Not selected"
    }
}

/**
 * Add content description for a card with optional details
 * Usage: Card(modifier = Modifier.cardDescription("Week 3 problem set", "COMP-8117, 4 pages"))
 */
fun Modifier.cardDescription(
    title: String,
    details: String? = null,
): Modifier {
    return this.semantics(mergeDescendants = true) {
        contentDescription =
            if (details != null) {
                "$title. $details. Double tap to view details"
            } else {
                "$title. Double tap to view details"
            }
    }
}

/**
 * Add position announcement for list items
 * Usage: LazyColumn { itemsIndexed(items) { index, item ->
 *   Card(modifier = Modifier.listItemPosition(index + 1, items.size, item.title))
 * }}
 */
fun Modifier.listItemPosition(
    position: Int,
    total: Int,
    itemLabel: String,
): Modifier {
    return this.semantics {
        contentDescription = "$itemLabel. Item $position of $total"
    }
}

/**
 * Add error semantics for error states
 * Usage: Text(errorMessage, modifier = Modifier.errorSemantics(errorMessage))
 */
fun Modifier.errorSemantics(errorMessage: String): Modifier {
    return this.semantics {
        contentDescription = "Error: $errorMessage"
        error(errorMessage)
    }
}

/**
 * Mark loading state with announcement
 * Usage: CircularProgressIndicator(modifier = Modifier.loadingSemantics("Loading documents"))
 */
fun Modifier.loadingSemantics(message: String = "Loading"): Modifier {
    return this.semantics {
        contentDescription = "$message, please wait"
        liveRegion = LiveRegionMode.Polite
    }
}

/**
 * Add progress announcement for progress indicators
 * Usage: LinearProgressIndicator(modifier = Modifier.progressSemantics(50, 100))
 */
fun Modifier.progressSemantics(
    current: Int,
    total: Int,
): Modifier {
    return this.semantics {
        contentDescription = "Progress: $current of $total"
        stateDescription = "${(current * 100 / total)}%"
    }
}

/**
 * Add pagination semantics for pageable content
 * Usage: HorizontalPager(modifier = Modifier.paginationSemantics(currentPage, totalPages))
 */
fun Modifier.paginationSemantics(
    currentPage: Int,
    totalPages: Int,
): Modifier {
    return this.semantics {
        contentDescription = "Page $currentPage of $totalPages"
    }
}

/**
 * Group related elements for screen reader navigation
 * Usage: Column(modifier = Modifier.accessibilityGroup("User profile section"))
 */
fun Modifier.accessibilityGroup(label: String): Modifier {
    return this.semantics(mergeDescendants = true) {
        contentDescription = label
    }
}

/**
 * Add text field semantics with label, value, hint, and error
 * Usage: TextField(modifier = Modifier.textFieldSemantics("Email", email, "Enter email", emailError))
 */
fun Modifier.textFieldSemantics(
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
                    append(", current value: $value")
                }
                hint?.let { append(", $it") }
                error?.let { append(", error: $it") }
            }
        if (error != null) {
            error(error)
        }
    }
}

/**
 * Add dialog semantics with title
 * Usage: AlertDialog(modifier = Modifier.dialogSemantics("Confirm deletion"))
 */
fun Modifier.dialogSemantics(title: String): Modifier {
    return this.semantics {
        contentDescription = "Dialog: $title"
    }
}

/**
 * Add bottom sheet semantics
 * Usage: ModalBottomSheet(modifier = Modifier.bottomSheetSemantics("Filter options"))
 */
fun Modifier.bottomSheetSemantics(title: String): Modifier {
    return this.semantics {
        contentDescription = "Bottom sheet: $title"
    }
}

/**
 * Add swipeable item semantics
 * Usage: SwipeableItem(modifier = Modifier.swipeableSemantics("Swipe to delete"))
 */
fun Modifier.swipeableSemantics(hint: String): Modifier {
    return this.semantics {
        customActions =
            listOf(
                CustomAccessibilityAction(hint) { true },
            )
    }
}

/**
 * Add dropdown menu semantics
 * Usage: DropdownMenu(modifier = Modifier.dropdownSemantics("Select course", isExpanded))
 */
fun Modifier.dropdownSemantics(
    label: String,
    expanded: Boolean,
): Modifier {
    return this.semantics {
        contentDescription = "$label, ${if (expanded) "expanded" else "collapsed"}"
        stateDescription = if (expanded) "Expanded" else "Collapsed"
    }
}

/**
 * Add section heading semantics (combines heading and group)
 * Usage: Text("Study Collections", modifier = Modifier.sectionHeading())
 */
fun Modifier.sectionHeading(): Modifier {
    return this.semantics { heading() }
}

/**
 * Add link semantics
 * Usage: Text("View all", modifier = Modifier.linkSemantics("View all study collections"))
 */
fun Modifier.linkSemantics(description: String): Modifier {
    return this.semantics {
        role = Role.Button // Compose doesn't have a Link role, use Button
        contentDescription = "$description, link"
    }
}

/**
 * Add selection state for selectable items
 * Usage: Card(modifier = Modifier.selectionSemantics(isSelected, "Document"))
 */
fun Modifier.selectionSemantics(
    isSelected: Boolean,
    itemType: String,
): Modifier {
    return this.semantics {
        contentDescription = "$itemType, ${if (isSelected) "selected" else "not selected"}"
        stateDescription = if (isSelected) "Selected" else "Not selected"
        selected = isSelected
    }
}

/**
 * Add disabled state semantics
 * Usage: Button(modifier = Modifier.disabledSemantics(isDisabled, "Submit"))
 */
fun Modifier.disabledSemantics(
    isDisabled: Boolean,
    label: String,
): Modifier {
    return this.semantics {
        contentDescription = "$label${if (isDisabled) ", disabled" else ""}"
        if (isDisabled) {
            disabled()
        }
    }
}
