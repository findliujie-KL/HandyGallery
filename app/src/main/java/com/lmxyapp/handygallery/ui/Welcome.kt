package com.lmxyapp.handygallery.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lmxyapp.handygallery.R
import kotlinx.coroutines.launch

/**
 * Three pages shown once, on the very first start, before the app asks for access to photos: what the
 * app is for, the Front Row, and where the tools and help are. [onDone] is called on Skip or at the end.
 */
@Composable
fun WelcomeScreen(onDone: () -> Unit) {
    BackHandler(onBack = onDone)
    val pager = rememberPagerState { 3 }
    val scope = rememberCoroutineScope()
    val last = pager.currentPage == 2
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.End) {
                if (!last) TextButton(onClick = onDone) { Text(stringResource(R.string.welcome_skip)) }
            }
            HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth()) { page ->
                Column(
                    Modifier.fillMaxSize().padding(horizontal = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    val modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth().aspectRatio(16f / 10f).clip(RoundedCornerShape(20.dp))
                    when (page) {
                        // In a tinted frame, as on the Help page, so the white of the animation stands out.
                        0, 1 -> Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(12.dp)) {
                            DemoView(if (page == 0) Demo.Share else Demo.FrontRow, Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp)))
                        }
                        else -> Row(
                            modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Badge(FrsIcons.Toolbox)
                            Badge(FrsIcons.Help)
                        }
                    }
                    Text(
                        stringResource(listOf(R.string.welcome_1_title, R.string.welcome_2_title, R.string.welcome_3_title)[page]),
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 28.dp),
                    )
                    Text(
                        stringResource(listOf(R.string.welcome_1_text, R.string.welcome_2_text, R.string.welcome_3_text)[page]),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.widthIn(max = 420.dp).padding(top = 12.dp),
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(3) { i ->
                        val colour by animateColorAsState(
                            if (i == pager.currentPage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                            label = "dot",
                        )
                        Box(Modifier.size(8.dp).clip(CircleShape).background(colour))
                    }
                }
                Button(onClick = { if (last) onDone() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } }) {
                    Text(stringResource(if (last) R.string.welcome_start else R.string.welcome_next))
                }
            }
        }
    }
}

@Composable
private fun Badge(icon: ImageVector) {
    Box(
        Modifier.size(72.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(36.dp))
    }
}
