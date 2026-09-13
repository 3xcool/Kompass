@file:OptIn(
    androidx.compose.animation.ExperimentalSharedTransitionApi::class,
    com.tekmoon.kompass.ExperimentalKompassSharedTransitionApi::class,
)

package com.tekmoon.samples

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tekmoon.kompass.BackStackEntry
import com.tekmoon.kompass.Destination
import com.tekmoon.kompass.KompassBackHandler
import com.tekmoon.kompass.KompassSharedTransitionHost
import com.tekmoon.kompass.LocalKompassAnimatedVisibilityScope
import com.tekmoon.kompass.LocalKompassSharedTransitionScope
import com.tekmoon.kompass.NavController
import com.tekmoon.kompass.NavigationGraph
import com.tekmoon.kompass.rememberNavController
import com.tekmoon.kompass.toBackStackEntry
import com.tekmoon.kompass.util.BackPressedChannel
import kompasskmp.samples.generated.resources.Res
import kompasskmp.samples.generated.resources.sample10_back
import kompasskmp.samples.generated.resources.sample10_detail_body
import kompasskmp.samples.generated.resources.sample10_detail_heading
import kompasskmp.samples.generated.resources.sample10_detail_title
import kompasskmp.samples.generated.resources.sample10_home_body
import kompasskmp.samples.generated.resources.sample10_home_title
import kompasskmp.samples.generated.resources.sample10_monogram
import kompasskmp.samples.generated.resources.sample10_open
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.compose.resources.stringResource

private enum class Sample10Destination : Destination {
    List,
    Detail;

    override val id: String = "kompass/sample10/$name"
}

private object Sample10Graph : NavigationGraph {
    override fun canResolveDestination(destinationId: String): Boolean =
        Sample10Destination.entries.any { it.id == destinationId }

    override fun resolveDestination(destinationId: String, args: String?): Destination =
        Sample10Destination.entries.first { it.id == destinationId }

    @Composable
    override fun Content(
        entry: BackStackEntry,
        destination: Destination,
        navController: NavController,
    ) {
        when (destination) {
            Sample10Destination.List -> Sample10ListScreen(navController)
            Sample10Destination.Detail -> Sample10DetailScreen(navController)
        }
    }
}

@Composable
fun Sample10_SharedElementTransition(
    backPressedChannel: BackPressedChannel?,
    onDismiss: () -> Unit = {},
): Unit {
    val navController = rememberNavController(Sample10Destination.List)

    KompassBackHandler(backPressedChannel = backPressedChannel) {
        navController.popIfCan(onFailure = onDismiss)
    }

    KompassSharedTransitionHost(
        navController = navController,
        graphs = persistentListOf(Sample10Graph),
    )
}

@Composable
private fun Sample10ListScreen(navController: NavController): Unit {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
    ) {
        Text(
            text = stringResource(Res.string.sample10_home_title),
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = stringResource(Res.string.sample10_home_body),
            style = MaterialTheme.typography.bodyMedium,
        )
        Card(
            onClick = { navController.navigate(Sample10Destination.Detail.toBackStackEntry()) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(20.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Sample10HeroArtwork(
                    modifier = Modifier
                        .size(80.dp)
                        .sample10SharedElement(Sample10HeroKey),
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(Res.string.sample10_detail_title),
                        modifier = Modifier.sample10SharedBounds(Sample10TitleKey),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        text = stringResource(Res.string.sample10_open),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

@Composable
private fun Sample10DetailScreen(navController: NavController): Unit {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
    ) {
        Sample10HeroArtwork(
            modifier = Modifier
                .size(220.dp)
                .sample10SharedElement(Sample10HeroKey),
        )
        Text(
            // A different string on purpose. sharedBounds animates the box and cross-fades two
            // different contents, so the title must not be identical on the two screens.
            text = stringResource(Res.string.sample10_detail_heading),
            modifier = Modifier.sample10SharedBounds(Sample10TitleKey),
            style = MaterialTheme.typography.headlineLarge,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(Res.string.sample10_detail_body),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Button(onClick = { navController.pop() }) {
            Text(stringResource(Res.string.sample10_back))
        }
    }
}

@Composable
private fun Sample10HeroArtwork(modifier: Modifier): Unit {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(Res.string.sample10_monogram),
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            style = MaterialTheme.typography.displayMedium,
        )
    }
}

@Composable
private fun Modifier.sample10SharedElement(key: String): Modifier {
    val sharedTransitionScope = LocalKompassSharedTransitionScope.current ?: return this
    val animatedVisibilityScope = LocalKompassAnimatedVisibilityScope.current ?: return this
    return with(sharedTransitionScope) {
        this@sample10SharedElement.sharedElement(
            sharedContentState = rememberSharedContentState(key),
            animatedVisibilityScope = animatedVisibilityScope,
        )
    }
}

@Composable
private fun Modifier.sample10SharedBounds(key: String): Modifier {
    val sharedTransitionScope = LocalKompassSharedTransitionScope.current ?: return this
    val animatedVisibilityScope = LocalKompassAnimatedVisibilityScope.current ?: return this
    return with(sharedTransitionScope) {
        this@sample10SharedBounds.sharedBounds(
            sharedContentState = rememberSharedContentState(key),
            animatedVisibilityScope = animatedVisibilityScope,
        )
    }
}

private const val Sample10HeroKey = "sample10-hero"
private const val Sample10TitleKey = "sample10-title"
