package com.shaheem.yt.ui.screens.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.shaheem.yt.LocalPlayerAwareWindowInsets
import com.shaheem.yt.LocalPlayerConnection
import com.shaheem.yt.R
import com.shaheem.yt.constants.*
import com.shaheem.yt.extensions.togglePlayPause
import com.shaheem.yt.together.*
import com.shaheem.yt.utils.rememberPreference
import kotlinx.coroutines.flow.MutableStateFlow

/** Musik's own room UI, using the join/create interaction pattern rather than external app code. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusikJamScreen(navController: NavController, scrollBehavior: TopAppBarScrollBehavior,
    topLevel: Boolean = false, incomingInvite: String = "") {
    val context = LocalContext.current
    val connection = LocalPlayerConnection.current
    val (serverUrl, _) = rememberPreference(TogetherServerUrlKey, "")
    val (serverToken, _) = rememberPreference(TogetherServerTokenKey, "")
    val (savedName, saveName) = rememberPreference(TogetherDisplayNameKey, "Listener")
    val (savedInvite, saveInvite) = rememberPreference(TogetherLastJoinLinkKey, "")
    var name by rememberSaveable(savedName) { mutableStateOf(savedName) }
    var creating by rememberSaveable { mutableStateOf(false) }
    var collaborative by rememberSaveable { mutableStateOf(true) }
    var joinInput by rememberSaveable { mutableStateOf(savedInvite) }
    var advanced by rememberSaveable { mutableStateOf(false) }
    var notice by rememberSaveable { mutableStateOf<String?>(null) }
    val flow = remember(connection) { connection?.service?.togetherSessionState ?: MutableStateFlow<TogetherSessionState>(TogetherSessionState.Idle) }
    val session by flow.collectAsState()
    val host = session is TogetherSessionState.HostingOnline || session is TogetherSessionState.Hosting
    val joining = session is TogetherSessionState.JoiningOnline || session is TogetherSessionState.Joining
    val joined = session as? TogetherSessionState.Joined
    val active = host || joined != null
    val room = when (val s = session) {
        is TogetherSessionState.HostingOnline -> s.roomState
        is TogetherSessionState.Hosting -> s.roomState
        is TogetherSessionState.Joined -> s.roomState
        else -> null
    }
    val settings = room?.settings ?: (session as? TogetherSessionState.HostingOnline)?.settings
    val pending = joined != null && room?.participants?.any { it.id == joined.selfParticipantId && it.isPending } == true
    val canControl = room != null && !pending && (host || settings?.allowGuestsToControlPlayback == true)
    val invite = MusikJamInvite.parse(joinInput)
    val joinServer = invite?.serverUrl ?: TogetherOnlineEndpoint.normalizeBaseUrl(serverUrl)
    val hostingReady = TogetherOnlineEndpoint.normalizeBaseUrl(serverUrl) != null && serverToken.length >= 16

    LaunchedEffect(incomingInvite) {
        if (incomingInvite.isNotBlank() && MusikJamInvite.parse(incomingInvite) != null) {
            joinInput = incomingInvite
            creating = false
            notice = null
        }
    }
    fun changeMode(value: Boolean) {
        collaborative = value
        if (host && settings != null) connection?.service?.updateTogetherSettings(settings.copy(
            allowGuestsToAddTracks = value, allowGuestsToControlPlayback = value,
        ))
    }
    fun connect() {
        notice = null
        if (creating && !hostingReady || !creating && joinServer == null) {
            advanced = true
            notice = context.getString(R.string.jam_configure_once)
            return
        }
        val displayName = name.trim().ifBlank { "Listener" }.take(100)
        saveName(displayName)
        if (creating) {
            connection?.service?.startTogetherOnlineHost(displayName, TogetherRoomSettings(
                allowGuestsToAddTracks = collaborative, allowGuestsToControlPlayback = collaborative,
            ))
        } else if (invite != null) {
            saveInvite(joinInput.trim())
            connection?.service?.joinTogetherOnline(invite.code, displayName, serverUrlOverride = joinServer)
        }
    }

    Column(Modifier.fillMaxSize().windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
        .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.jam_title), style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            SuggestionChip(onClick = {}, label = { Text(stringResource(if (active) R.string.jam_live else R.string.jam_listen_together)) })
        }

        if (!active) {
            TabRow(selectedTabIndex = if (creating) 1 else 0, containerColor = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth()) {
                Tab(selected = !creating, onClick = { if (!joining) { creating = false; notice = null } },
                    text = { Text(stringResource(R.string.jam_join_room)) })
                Tab(selected = creating, onClick = { if (!joining) { creating = true; notice = null } },
                    text = { Text(stringResource(R.string.jam_create_room)) })
            }
            OutlinedTextField(value = name, onValueChange = { name = it.take(100) }, singleLine = true,
                enabled = !joining, label = { Text(stringResource(R.string.jam_display_name)) },
                leadingIcon = { Icon(painterResource(R.drawable.person), null) }, modifier = Modifier.fillMaxWidth())
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (creating) {
                        Text(stringResource(R.string.jam_permissions), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        JamModeOption(true, collaborative, !joining) { collaborative = true }
                        JamModeOption(false, !collaborative, !joining) { collaborative = false }
                    } else {
                        OutlinedTextField(value = joinInput, onValueChange = { joinInput = it.take(2000); notice = null },
                            enabled = !joining, label = { Text(stringResource(R.string.jam_code_or_invite)) },
                            supportingText = { Text(stringResource(R.string.jam_join_hint)) },
                            modifier = Modifier.fillMaxWidth(), maxLines = 3)
                        if (invite?.serverUrl != null) Text(invite.serverUrl, style = MaterialTheme.typography.bodySmall)
                    }
                    if (session is TogetherSessionState.Error) Text((session as TogetherSessionState.Error).message,
                        color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    notice?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Button(onClick = ::connect, enabled = !joining && connection != null && (creating || invite != null),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                        if (joining) { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)) }
                        Text(stringResource(if (joining) R.string.connecting else if (creating) R.string.jam_start_session else R.string.jam_join_room))
                    }
                    if (!creating) Text(stringResource(R.string.jam_guest_token_free), style = MaterialTheme.typography.bodySmall)
                }
            }
            TextButton(onClick = { advanced = !advanced }, modifier = Modifier.fillMaxWidth(), enabled = !joining) {
                Icon(painterResource(R.drawable.settings), null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.jam_custom_server), Modifier.weight(1f))
                Icon(painterResource(if (advanced) R.drawable.close else R.drawable.expand_more), null)
            }
            if (advanced) {
                TogetherServerSettingsCard(serverUrl, serverToken, enabled = !joining, compact = true)
                TextButton(onClick = { navController.navigate("settings/music_together_lan") }) {
                    Text(stringResource(R.string.jam_lan_advanced))
                }
            }
        } else {
            val code = (session as? TogetherSessionState.HostingOnline)?.code
                ?: MusikJamInvite.parse(savedInvite)?.code.orEmpty()
            val lanLink = (session as? TogetherSessionState.Hosting)?.joinLink
            val shareLink = lanLink ?: code.takeIf { it.isNotBlank() }?.let {
                MusikJamInvite(it, (session as? TogetherSessionState.HostingOnline)?.let { TogetherOnlineEndpoint.normalizeBaseUrl(serverUrl) }
                    ?: MusikJamInvite.parse(savedInvite)?.serverUrl ?: TogetherOnlineEndpoint.normalizeBaseUrl(serverUrl)).toLink()
            }
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(stringResource(R.string.jam_room_code), style = MaterialTheme.typography.labelLarge)
                    Text(code.ifBlank { if (lanLink != null) "LAN" else "—" }, fontFamily = FontFamily.Monospace,
                        fontSize = 36.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp, color = MaterialTheme.colorScheme.primary)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(enabled = code.isNotBlank() || lanLink != null, onClick = {
                            context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("Musik room", code.ifBlank { lanLink.orEmpty() }))
                            Toast.makeText(context, R.string.copied, Toast.LENGTH_SHORT).show()
                        }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.jam_copy_code)) }
                        Button(enabled = shareLink != null, onClick = {
                            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, "Join my Musik Jam: ${code.ifBlank { "LAN" }}\n$shareLink")
                            }, null))
                        }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.jam_share_invite)) }
                    }
                    val currentMode = settings?.allowGuestsToControlPlayback == true
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = currentMode, onClick = { changeMode(true) }, enabled = host,
                            label = { Text(stringResource(R.string.jam_collaborative)) })
                        FilterChip(selected = !currentMode, onClick = { changeMode(false) }, enabled = host,
                            label = { Text(stringResource(R.string.jam_host_only)) })
                    }
                    if (pending) Text(stringResource(R.string.together_waiting_approval))
                }
            }
            val track = room?.queue?.getOrNull(room.currentIndex)
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(track?.title ?: stringResource(R.string.jam_no_track), fontWeight = FontWeight.SemiBold,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(track?.artists?.joinToString().orEmpty().ifBlank { context.getString(R.string.jam_sync) },
                            style = MaterialTheme.typography.bodySmall)
                    }
                    IconButton(enabled = canControl && track != null, onClick = {
                        if (host) connection?.player?.togglePlayPause()
                        else connection?.service?.requestTogetherControl(if (room?.isPlaying == true) ControlAction.Pause else ControlAction.Play)
                    }) { Icon(painterResource(if (room?.isPlaying == true) R.drawable.pause else R.drawable.play), stringResource(R.string.jam_play_pause)) }
                    IconButton(enabled = canControl && track != null, onClick = { connection?.seekToNext() }) {
                        Icon(painterResource(R.drawable.skip_next), stringResource(R.string.jam_next))
                    }
                }
            }
            Text(stringResource(R.string.jam_up_next), style = MaterialTheme.typography.labelLarge)
            val upcoming = room?.queue.orEmpty().withIndex().filter { it.index > (room?.currentIndex ?: -1) }
            if (upcoming.isEmpty()) Text(stringResource(R.string.jam_queue_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
            upcoming.take(30).forEach { (index, queued) ->
                ListItem(headlineContent = { Text(queued.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = { Text(queued.artists.joinToString(), maxLines = 1) },
                    modifier = Modifier.clickable(enabled = canControl) {
                        if (host) connection?.player?.apply { seekTo(index, 0); prepare(); playWhenReady = true }
                        else connection?.service?.requestTogetherControl(ControlAction.SeekToIndex(index))
                    })
            }
            Text(stringResource(R.string.jam_listeners, room?.participants?.size ?: 1), style = MaterialTheme.typography.titleMedium)
            room?.participants.orEmpty().forEach { listener ->
                ListItem(leadingContent = { Icon(painterResource(R.drawable.person), null) },
                    headlineContent = { Text(listener.name) },
                    trailingContent = { Text(if (listener.isHost) "HOST" else if (listener.isPending) "PENDING" else "LISTENER",
                        style = MaterialTheme.typography.labelSmall) })
            }
            Button(onClick = { connection?.service?.leaveTogether() }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer)) {
                Text(stringResource(if (host) R.string.jam_end_session else R.string.jam_leave_session))
            }
        }
        Text(stringResource(R.string.together_server_note), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (!topLevel) TopAppBar(title = {}, navigationIcon = {
        IconButton(onClick = { navController.navigateUp() }) { Icon(painterResource(R.drawable.arrow_back), stringResource(R.string.back_button_desc)) }
    }, scrollBehavior = scrollBehavior)
}

@Composable
private fun JamModeOption(collaborative: Boolean, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    OutlinedCard(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth(),
        border = androidx.compose.foundation.BorderStroke(if (selected) 2.dp else 1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected, onClick = onClick, enabled = enabled)
            Column(Modifier.weight(1f)) {
                Text(stringResource(if (collaborative) R.string.jam_collaborative else R.string.jam_host_only), fontWeight = FontWeight.SemiBold)
                Text(stringResource(if (collaborative) R.string.jam_collaborative_description else R.string.jam_host_only_description),
                    style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
