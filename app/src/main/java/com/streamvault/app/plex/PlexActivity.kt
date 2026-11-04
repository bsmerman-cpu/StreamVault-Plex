package com.streamvault.app.plex

import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.streamvault.app.R
import com.streamvault.core.ui.theme.StreamVaultTheme
import com.streamvault.data.security.CredentialCrypto
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.util.UUID
import javax.inject.Inject

@AndroidEntryPoint
class PlexActivity : ComponentActivity() {
    @Inject lateinit var credentialCrypto: CredentialCrypto

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("plex_account", MODE_PRIVATE)
        val clientId = prefs.getString("client_id", null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString("client_id", it).apply()
        }
        val client = PlexClient(clientId)
        setContent {
            StreamVaultTheme {
                PlexLibrary(
                    client = client,
                    savedToken = prefs.getString("token", null)?.let { encrypted ->
                        runCatching { credentialCrypto.decryptIfNeeded(encrypted) }.getOrNull()
                    },
                    onSaveToken = { token ->
                        prefs.edit().putString("token", credentialCrypto.encryptIfNeeded(token)).apply()
                    },
                    onSignOut = { prefs.edit().remove("token").apply() },
                    onClose = ::finish
                )
            }
        }
    }
}

@Composable
private fun PlexLibrary(
    client: PlexClient,
    savedToken: String?,
    onSaveToken: (String) -> Unit,
    onSignOut: () -> Unit,
    onClose: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var token by remember { mutableStateOf(savedToken) }
    var pin by remember { mutableStateOf<PlexPin?>(null) }
    var servers by remember { mutableStateOf<List<PlexServer>>(emptyList()) }
    var server by remember { mutableStateOf<PlexServer?>(null) }
    var sections by remember { mutableStateOf<List<PlexSection>>(emptyList()) }
    var section by remember { mutableStateOf<PlexSection?>(null) }
    var items by remember { mutableStateOf<List<PlexItem>>(emptyList()) }
    val path = remember { mutableStateListOf<Pair<PlexItem, List<PlexItem>>>() }
    var playing by remember { mutableStateOf<PlexItem?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun load(block: suspend () -> Unit) {
        scope.launch {
            busy = true
            error = null
            try { block() } catch (e: Exception) { error = e.localizedMessage ?: e.javaClass.simpleName }
            finally { busy = false }
        }
    }
    fun back() {
        when {
            playing != null -> playing = null
            path.isNotEmpty() -> {
                items = path.removeAt(path.lastIndex).second
            }
            section != null -> { section = null; items = emptyList() }
            server != null -> { server = null; sections = emptyList() }
            else -> onClose()
        }
    }
    BackHandler { back() }

    LaunchedEffect(token) {
        val accountToken = token ?: return@LaunchedEffect
        busy = true
        try { servers = withContext(Dispatchers.IO) { client.servers(accountToken) } }
        catch (e: Exception) { error = e.localizedMessage ?: e.javaClass.simpleName }
        finally { busy = false }
    }
    LaunchedEffect(pin) {
        val activePin = pin ?: return@LaunchedEffect
        repeat(150) {
            delay(2_000)
            val claimed = runCatching { withContext(Dispatchers.IO) { client.checkPin(activePin.id) } }.getOrNull()
            if (claimed != null) {
                onSaveToken(claimed)
                token = claimed
                pin = null
                return@LaunchedEffect
            }
        }
        pin = null
        error = null
    }

    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFF10151D)).padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = ::back) { Text(stringResource(R.string.plex_back)) }
            Text(stringResource(R.string.plex_library), style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.weight(1f))
            if (token != null) Button(onClick = {
                onSignOut(); token = null; pin = null; servers = emptyList(); server = null
                sections = emptyList(); section = null; items = emptyList(); playing = null
            }) { Text(stringResource(R.string.plex_sign_out)) }
        }
        when {
            playing != null -> {
                val currentServer = server
                val currentItem = playing
                if (currentServer != null && currentItem != null) {
                    val url = client.playbackUrl(currentServer, currentItem)
                    if (url != null) PlexPlayer(url, currentServer.token, currentItem.title)
                    else Text(stringResource(R.string.plex_playback_unavailable))
                }
            }
            token == null -> {
                if (pin == null) Button(onClick = { load { pin = withContext(Dispatchers.IO) { client.createPin() } } }) {
                    Text(stringResource(R.string.plex_link_account))
                } else {
                    Text(stringResource(R.string.plex_link_url), style = MaterialTheme.typography.titleLarge)
                    Text(pin!!.code, style = MaterialTheme.typography.displayLarge)
                    CircularProgressIndicator()
                }
            }
            server == null -> {
                Text(stringResource(R.string.plex_choose_server), style = MaterialTheme.typography.titleLarge)
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(servers) { choice ->
                        Button(onClick = { load {
                            sections = withContext(Dispatchers.IO) { client.sections(choice) }
                            server = choice
                        } }) { Text(choice.name) }
                    }
                }
            }
            section == null -> {
                Text(server!!.name, style = MaterialTheme.typography.titleLarge)
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(sections) { choice ->
                        Button(onClick = { load {
                            items = withContext(Dispatchers.IO) { client.sectionItems(server!!, choice) }
                            section = choice
                        } }) { Text(choice.title) }
                    }
                }
            }
            else -> {
                Text(path.lastOrNull()?.first?.title ?: section!!.title, style = MaterialTheme.typography.titleLarge)
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(items, key = { it.key }) { item ->
                        PlexMediaRow(client, server!!, item) {
                            if (item.type in setOf("show", "season")) load {
                                val children = withContext(Dispatchers.IO) { client.children(server!!, item) }
                                path.add(item to items)
                                items = children
                            } else playing = item
                        }
                    }
                }
            }
        }
        if (busy) CircularProgressIndicator()
        error?.let { Text(it, color = Color(0xFFFF9D8F)) }
    }
}

@Composable
private fun PlexMediaRow(client: PlexClient, server: PlexServer, item: PlexItem, onClick: () -> Unit) {
    var image by remember(server.uri, item.thumb) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(server.uri, item.thumb) {
        val bytes = withContext(Dispatchers.IO) { client.artworkBytes(server, item) }
        image = bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
    }
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            image?.let { bitmap ->
                Image(bitmap.asImageBitmap(), contentDescription = null, modifier = Modifier.size(72.dp, 96.dp), contentScale = ContentScale.Crop)
            } ?: Box(Modifier.size(72.dp, 96.dp).background(Color.DarkGray))
            Column {
                Text(item.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                item.year?.let { Text(it.toString(), style = MaterialTheme.typography.bodySmall) }
                item.parentTitle?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

@Composable
private fun PlexPlayer(url: String, token: String, title: String) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val player = remember(url, token) {
        val dataSource = OkHttpDataSource.Factory(OkHttpClient())
            .setDefaultRequestProperties(mapOf("X-Plex-Token" to token))
        ExoPlayer.Builder(context).setMediaSourceFactory(DefaultMediaSourceFactory(dataSource)).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        AndroidView(
            factory = { PlayerView(it).apply { this.player = player } },
            modifier = Modifier.fillMaxWidth().weight(1f)
        )
    }
}
