package com.kashyap.wizlyai.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade

/**
 * Platform-agnostic image loader using Coil 3.0
 * Displays images from file paths with loading indicator
 */
@Composable
fun ImageLoader(
    imagePath: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    showLoading: Boolean = true,
    onLoading: ((Boolean) -> Unit)? = null,
    onError: ((Throwable) -> Unit)? = null,
) {
    val context = LocalPlatformContext.current
    var isLoading by remember { mutableStateOf(true) }

    Box(modifier = modifier) {
        AsyncImage(
            model =
                ImageRequest.Builder(context)
                    .data("file://$imagePath")
                    .crossfade(true)
                    .build(),
            contentDescription = contentDescription,
            modifier = Modifier.fillMaxSize(),
            contentScale = contentScale,
            onState = { state ->
                when (state) {
                    is AsyncImagePainter.State.Loading -> {
                        isLoading = true
                        onLoading?.invoke(true)
                    }
                    is AsyncImagePainter.State.Success -> {
                        isLoading = false
                        onLoading?.invoke(false)
                    }
                    is AsyncImagePainter.State.Error -> {
                        isLoading = false
                        onLoading?.invoke(false)
                        onError?.invoke(state.result.throwable)
                    }
                    is AsyncImagePainter.State.Empty -> {
                        isLoading = false
                        onLoading?.invoke(false)
                    }
                }
            },
        )

        // Show loading indicator only while actually loading
        if (showLoading && isLoading) {
            CircularProgressIndicator(
                modifier =
                    Modifier
                        .size(48.dp)
                        .align(Alignment.Center),
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
