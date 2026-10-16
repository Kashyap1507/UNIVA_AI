package com.kashyap.wizlyai.ui.screens.auth

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kashyap.wizlyai.ui.components.*
import com.kashyap.wizlyai.ui.theme.BluePrimary
import com.kashyap.wizlyai.ui.theme.authBackgroundGradient

/**
 * Onboarding screen - Redesigned per Wizly-Web specification
 * Blue-to-teal gradient background, 3 slides, auto-scroll every 3 seconds
 * Animated progress bars, slide transitions, auto-navigate to Welcome screen
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    onSkip: () -> Unit,
) {
    val pagerState = rememberPagerState(pageCount = { 3 })
    val coroutineScope = rememberCoroutineScope()

    val pages =
        remember {
            listOf(
                OnboardingPage(
                    icon = Icons.Filled.School,
                    title = "Build your courses",
                    description = "Upload your syllabus, slides and notes, and Univa AI organises them into modules and concepts",
                ),
                OnboardingPage(
                    icon = Icons.Filled.Assessment,
                    title = "Ask, practise, review",
                    description = "A tutor that cites your own material, flashcards with spaced repetition, and practice exams",
                ),
                OnboardingPage(
                    icon = Icons.Filled.CameraAlt,
                    title = "Check your work",
                    description = "Snap your working and get hints first, so you still do the thinking",
                ),
            )
        }

    // Auto-scroll timer (3 seconds per slide)
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(3000)
            val nextPage = pagerState.currentPage + 1
            if (nextPage <= pages.lastIndex) {
                pagerState.animateScrollToPage(
                    page = nextPage,
                    animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
                )
            } else {
                // Auto-navigate to Welcome screen after last slide
                kotlinx.coroutines.delay(500) // Brief pause on last slide
                onComplete()
                break
            }
        }
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(authBackgroundGradient()), // Gradient background for onboarding
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding() // UX-027: Respect iOS notch/Dynamic Island
                    .navigationBarsPadding() // UX-027: Respect iOS home indicator
                    .padding(horizontal = 24.dp),
        ) {
            Spacer(modifier = Modifier.height(16.dp)) // Reduced from 48dp (safe area handled above)

            // Skip button only (top right)
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
            ) {
                TextButton(
                    onClick = onSkip,
                    modifier =
                        Modifier
                            .align(Alignment.CenterEnd)
                            .linkSemantics("Skip onboarding and continue to app"),
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.White.copy(alpha = 0.8f)),
                ) {
                    Text(
                        text = "Skip",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Main content area (slides)
            HorizontalPager(
                state = pagerState,
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
            ) { pageIndex ->
                OnboardingPageContent(
                    page = pages[pageIndex],
                    isCurrentPage = pagerState.currentPage == pageIndex,
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Animated progress dots (active bar expands, others stay as dots)
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 32.dp)
                        .paginationSemantics(
                            currentPage = pagerState.currentPage + 1,
                            totalPages = pages.size,
                        ),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(pages.size) { index ->
                    val isActive = index == pagerState.currentPage

                    // Only active slide expands to bar, all others are dots
                    val width by animateDpAsState(
                        targetValue = if (isActive) 32.dp else 8.dp,
                        animationSpec =
                            if (isActive) {
                                tween(durationMillis = 3000, easing = LinearEasing) // 3s fill animation
                            } else {
                                tween(durationMillis = 400, easing = FastOutSlowInEasing)
                            },
                    )

                    val alpha by animateFloatAsState(
                        targetValue = if (isActive) 1f else 0.6f,
                        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
                    )

                    Box(
                        modifier =
                            Modifier
                                .padding(horizontal = 4.dp)
                                .size(width = width, height = 8.dp)
                                .alpha(alpha)
                                .clip(RoundedCornerShape(50))
                                .background(
                                    if (isActive) Color.White else Color.White.copy(alpha = 0.4f),
                                ),
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun OnboardingPageContent(
    page: OnboardingPage,
    isCurrentPage: Boolean,
) {
    val contentAlpha by animateFloatAsState(
        targetValue = if (isCurrentPage) 1f else 0f,
        animationSpec =
            tween(
                durationMillis = 400,
                easing = FastOutSlowInEasing,
            ),
    )
    val contentOffset by animateDpAsState(
        targetValue = if (isCurrentPage) 0.dp else 20.dp,
        animationSpec =
            tween(
                durationMillis = 400,
                easing = FastOutSlowInEasing,
            ),
    )

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .alpha(contentAlpha)
                .offset(y = contentOffset),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // White rounded card with icon
        Box(
            modifier =
                Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .decorative(),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = page.icon,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = BluePrimary,
            )
        }

        Spacer(modifier = Modifier.height(64.dp))

        Text(
            text = page.title,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold, // Bold for emphasis on onboarding slides
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier.heading(),
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = page.description,
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White.copy(alpha = 0.9f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

data class OnboardingPage(
    val icon: ImageVector,
    val title: String,
    val description: String,
)
