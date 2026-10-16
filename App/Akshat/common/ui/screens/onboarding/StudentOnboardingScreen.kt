package com.kashyap.wizlyai.ui.screens.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kashyap.wizlyai.data.repository.StudentProfileRepository
import com.kashyap.wizlyai.network.models.DegreeLevel
import com.kashyap.wizlyai.session.UserSessionManager
import com.kashyap.wizlyai.ui.components.GlassmorphismCard
import com.kashyap.wizlyai.ui.components.GlowLoadingButton
import com.kashyap.wizlyai.ui.components.decorative
import com.kashyap.wizlyai.ui.components.dropdownSemantics
import com.kashyap.wizlyai.ui.components.errorSemantics
import com.kashyap.wizlyai.ui.components.heading
import com.kashyap.wizlyai.ui.components.textFieldSemantics
import com.kashyap.wizlyai.ui.theme.BluePrimary
import com.kashyap.wizlyai.ui.theme.Foreground
import com.kashyap.wizlyai.ui.theme.authBackgroundGradient
import kotlinx.coroutines.launch

/**
 * Student onboarding: name, university, degree level, program and semester.
 * Saves to `/api/student/profile`; [onComplete] runs once the profile is complete.
 *
 * With [isEditing] the same form edits an existing profile from the Profile tab:
 * the secondary action becomes "Cancel" ([onCancel]) instead of "Sign out".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentOnboardingScreen(
    onComplete: () -> Unit,
    onSignOut: () -> Unit = {},
    isEditing: Boolean = false,
    onCancel: () -> Unit = {},
    repository: StudentProfileRepository = remember { StudentProfileRepository() },
) {
    val session = UserSessionManager.instance
    var form by remember {
        mutableStateOf(StudentOnboardingForm.from(session.studentProfile.value, session.userName, session.userEmail))
    }
    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showSemesterPicker by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val isDarkTheme = isSystemInDarkTheme()
    val fieldColors = onboardingFieldColors(isDarkTheme)
    val labelColor = if (isDarkTheme) Color.White.copy(alpha = 0.9f) else MaterialTheme.colorScheme.onSurface

    fun save() {
        if (!form.isValid || isSaving) return
        isSaving = true
        errorMessage = null
        scope.launch {
            repository.updateProfile(form.toUpdate()).fold(
                onSuccess = { data ->
                    isSaving = false
                    if (!data.needsOnboarding) onComplete() else errorMessage = "Please fill in all required fields."
                },
                onFailure = { error ->
                    isSaving = false
                    errorMessage = error.message ?: "Couldn't save your profile. Check your connection and try again."
                },
            )
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().background(authBackgroundGradient()),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            GlassmorphismCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 24.dp) {
                Box(
                    modifier =
                        Modifier
                            .size(72.dp)
                            .background(BluePrimary.copy(alpha = 0.15f), CircleShape)
                            .decorative()
                            .align(Alignment.CenterHorizontally),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.School, contentDescription = null, tint = BluePrimary, modifier = Modifier.size(36.dp))
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = if (isEditing) "Edit your study profile" else "Set up your study profile",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isDarkTheme) Color.White else MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().heading(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Univa AI uses this to tailor explanations and practice to your program.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isDarkTheme) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(24.dp))

                OnboardingTextField(
                    label = "Your name",
                    value = form.name,
                    placeholder = "e.g. Priya Shah",
                    error = form.nameError,
                    colors = fieldColors,
                    labelColor = labelColor,
                    capitalization = KeyboardCapitalization.Words,
                    onValueChange = { form = form.copy(name = it) },
                )

                Spacer(modifier = Modifier.height(16.dp))

                OnboardingTextField(
                    label = "University",
                    value = form.university,
                    placeholder = "e.g. University of Windsor",
                    error = form.universityError,
                    colors = fieldColors,
                    labelColor = labelColor,
                    capitalization = KeyboardCapitalization.Words,
                    onValueChange = { form = form.copy(university = it) },
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text("Degree level", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = labelColor)
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    DegreeLevel.entries.forEach { level ->
                        FilterChip(
                            selected = form.degreeLevel == level,
                            onClick = { form = form.copy(degreeLevel = level) },
                            label = { Text(level.label) },
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                OnboardingTextField(
                    label = "Program",
                    value = form.program,
                    placeholder = "e.g. Master of Applied Computing",
                    error = form.programError,
                    colors = fieldColors,
                    labelColor = labelColor,
                    capitalization = KeyboardCapitalization.Words,
                    onValueChange = { form = form.copy(program = it) },
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text("Current semester (optional)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = labelColor)
                Spacer(modifier = Modifier.height(8.dp))
                ExposedDropdownMenuBox(
                    expanded = showSemesterPicker,
                    onExpandedChange = { showSemesterPicker = it },
                    modifier = Modifier.fillMaxWidth().dropdownSemantics("Select current semester", showSemesterPicker),
                ) {
                    OutlinedTextField(
                        value = form.currentSemester?.let { "Semester $it" } ?: "Not set",
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showSemesterPicker) },
                        shape = RoundedCornerShape(14.dp),
                        colors = fieldColors,
                        textStyle = MaterialTheme.typography.bodyLarge,
                    )
                    ExposedDropdownMenu(expanded = showSemesterPicker, onDismissRequest = { showSemesterPicker = false }) {
                        StudentOnboardingForm.SEMESTERS.forEach { semester ->
                            DropdownMenuItem(
                                text = { Text("Semester $semester") },
                                onClick = {
                                    form = form.copy(currentSemester = semester)
                                    showSemesterPicker = false
                                },
                            )
                        }
                    }
                }

                errorMessage?.let { message ->
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.errorSemantics(message),
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                GlowLoadingButton(
                    text = if (isEditing) "Save" else "Continue",
                    onClick = { save() },
                    isLoading = isSaving,
                    loadingText = "Saving your profile...",
                    enabled = form.isValid,
                    backgroundColor = BluePrimary,
                    contentColor = Color.White,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(8.dp))

                TextButton(
                    onClick = if (isEditing) onCancel else onSignOut,
                    enabled = !isSaving,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Text(
                        if (isEditing) "Cancel" else "Sign out",
                        color = if (isDarkTheme) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun OnboardingTextField(
    label: String,
    value: String,
    placeholder: String,
    error: String?,
    colors: TextFieldColors,
    labelColor: Color,
    capitalization: KeyboardCapitalization,
    onValueChange: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
        Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = labelColor)
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth().textFieldSemantics(label = label, value = value, hint = placeholder, error = error),
            placeholder = { Text(placeholder) },
            singleLine = true,
            isError = error != null,
            shape = RoundedCornerShape(14.dp),
            colors = colors,
            textStyle = MaterialTheme.typography.bodyLarge,
            keyboardOptions = KeyboardOptions(capitalization = capitalization, imeAction = ImeAction.Next),
        )
        if (error != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.errorSemantics(error),
            )
        }
    }
}

@Composable
private fun onboardingFieldColors(isDarkTheme: Boolean): TextFieldColors =
    OutlinedTextFieldDefaults.colors(
        focusedBorderColor = if (isDarkTheme) Color.White.copy(alpha = 0.5f) else MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = if (isDarkTheme) Color.White.copy(alpha = 0.2f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
        focusedContainerColor = if (isDarkTheme) Color.White.copy(alpha = 0.05f) else Color.White,
        unfocusedContainerColor = if (isDarkTheme) Color.White.copy(alpha = 0.05f) else Color.White,
        focusedTextColor = if (isDarkTheme) Color.White else Foreground,
        unfocusedTextColor = if (isDarkTheme) Color.White else Foreground,
        cursorColor = if (isDarkTheme) Color.White else MaterialTheme.colorScheme.primary,
    )
