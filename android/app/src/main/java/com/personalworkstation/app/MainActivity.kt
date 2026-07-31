package com.personalworkstation.app

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Bundle
import android.provider.CalendarContract
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personalworkstation.app.core.model.BoardColumn
import com.personalworkstation.app.core.model.DashboardSummary
import com.personalworkstation.app.core.model.GithubActivity
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import org.yaml.snakeyaml.Yaml
import com.personalworkstation.app.core.model.GithubHeatmap
import com.personalworkstation.app.core.model.GithubProfile
import com.personalworkstation.app.core.model.GithubRepo
import com.personalworkstation.app.core.model.Note
import com.personalworkstation.app.core.model.NoteCreateRequest
import com.personalworkstation.app.core.model.NoteUpdateRequest
import com.personalworkstation.app.core.model.ProfileUpdateRequest
import com.personalworkstation.app.core.model.BoardCreateRequest
import com.personalworkstation.app.core.model.ColumnCreateRequest
import com.personalworkstation.app.core.model.Task
import com.personalworkstation.app.core.model.TaskCreateRequest
import com.personalworkstation.app.core.model.TaskUpdateRequest
import com.personalworkstation.app.core.model.Snippet
import com.personalworkstation.app.core.model.SnippetCreateRequest
import com.personalworkstation.app.core.model.DevLog
import com.personalworkstation.app.core.model.DevLogCalendarDay
import com.personalworkstation.app.core.model.DevLogStreakResponse
import com.personalworkstation.app.core.model.DevLogUpdateRequest
import com.personalworkstation.app.core.network.ApiClient
import com.google.zxing.integration.android.IntentIntegrator
import io.noties.markwon.Markwon
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val bg = Color(0xFF181818)
private val panel = Color(0xFF252525)
private val softPanel = Color(0xFF202020)
private val green = Color(0xFF22C55E)
private val greenLight = Color(0xFF4ADE80)
private val muted = Color(0xFF888888)
private val dim = Color(0xFF555555)
private val errorColor = Color(0xFFFF786D)
private val cardShape = RoundedCornerShape(16.dp)
private val tabs = listOf("看板", "笔记", "片段", "GitHub", "仪表盘", "工具", "日志", "设置")
private val mobileTabs = listOf("看板", "笔记", "GitHub", "仪表盘", "更多")
private val mobileIcons = listOf("▦", "▤", "◉", "▥", "•••")
private data class TaskMove(val taskId: Int, val previousColumnId: Int, val previousColumnName: String)
private val icons = listOf("▦", "▤", "</>", "◉", "▥", "⌘", "▣", "⚙")
private val colors = darkColorScheme(
    primary = green,
    onPrimary = Color.White,
    secondary = greenLight,
    background = bg,
    surface = panel,
    surfaceVariant = Color(0xFF303030),
    onBackground = Color.White,
    onSurface = Color.White,
    onSurfaceVariant = Color(0xFFB8B8B8),
    error = errorColor,
)

class MainActivity : ComponentActivity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        setContent { WorkstationApp() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkstationApp() {
    MaterialTheme(colorScheme = colors) {
        val context = LocalContext.current
        val prefs = remember { context.getSharedPreferences("workstation_preferences", Context.MODE_PRIVATE) }
        var tab by remember { mutableIntStateOf(4) }
        var host by remember { mutableStateOf(prefs.getString("host", "192.168.1.100") ?: "192.168.1.100") }
        var port by remember { mutableStateOf(prefs.getString("port", "8080") ?: "8080") }
        var displayName by remember { mutableStateOf(prefs.getString("display_name", "Liu Developer") ?: "Liu Developer") }
        var githubUsername by remember { mutableStateOf(prefs.getString("github_username", "") ?: "") }
        var notifications by remember { mutableStateOf(prefs.getBoolean("notifications", true)) }
        var githubSync by remember { mutableStateOf(prefs.getBoolean("github_sync", true)) }
        var focusMode by remember { mutableStateOf(prefs.getBoolean("focus_mode", false)) }
        var profileSyncStatus by remember { mutableStateOf<String?>(null) }
        var realtimeRevision by remember { mutableIntStateOf(0) }
        var realtimeStatus by remember { mutableStateOf("disconnected") }
        var showScratchpad by remember { mutableStateOf(false) }
        val appScope = rememberCoroutineScope()
        val client = remember(host, port) {
            ApiClient(normalizeHost(host), port.toIntOrNull()?.coerceIn(1, 65535) ?: 8080)
        }
        fun saveBoolean(key: String, value: Boolean) {
            prefs.edit().putBoolean(key, value).apply()
        }
        LaunchedEffect(client) {
            profileSyncStatus = null
            try {
                val remote = client.profile()
                if (remote.display_name.isNotBlank()) {
                    displayName = remote.display_name
                    prefs.edit().putString("display_name", remote.display_name).apply()
                }
                if (remote.github_username.isNotBlank()) {
                    githubUsername = remote.github_username
                    prefs.edit().putString("github_username", remote.github_username).apply()
                }
                profileSyncStatus = "已从电脑同步"
            } catch (_: Exception) {
                profileSyncStatus = "连接后同步失败，保留本机资料"
            }
        }
        LaunchedEffect(client) {
            client.listenRealtime(
                onEvent = { realtimeRevision += 1 },
                onStatus = { realtimeStatus = it },
            )
        }
        Scaffold(
            containerColor = bg,
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { showScratchpad = true },
                    containerColor = greenLight,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text("📝", fontSize = 20.sp)
                }
            },
            bottomBar = {
                NavigationBar(containerColor = Color(0xFF0A0A0A), tonalElevation = 0.dp) {
                    mobileTabs.forEachIndexed { index, title ->
                        val targetTab = when (index) {
                            0 -> 0
                            1 -> 1
                            2 -> 3
                            3 -> 4
                            else -> 8
                        }
                        NavigationBarItem(
                            selected = if (index == 4) tab >= 5 else tab == targetTab,
                            onClick = { tab = targetTab },
                            icon = { Text(mobileIcons[index], fontSize = if (index == 4) 18.sp else 20.sp) },
                            label = { Text(title, fontSize = 11.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = greenLight,
                                selectedTextColor = greenLight,
                                indicatorColor = Color(0x3322C55E),
                                unselectedIconColor = dim,
                                unselectedTextColor = dim,
                            ),
                        )
                    }
                }
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().background(bg).padding(padding)) {
                when (tab) {
                    0 -> KanbanScreen(client, realtimeRevision)
                    1 -> NotesScreen(client, realtimeRevision)
                    2 -> SnippetsScreen(client, realtimeRevision)
                    3 -> GithubScreen(client, realtimeRevision)
                    4 -> DashboardScreen(
                        client = client,
                        realtimeRevision = realtimeRevision,
                        displayName = displayName,
                        githubSync = githubSync,
                        focusMode = focusMode,
                        onNavigate = { tab = if (it == 4) 7 else it },
                        onFocusModeChange = {
                            focusMode = it
                            saveBoolean("focus_mode", it)
                        },
                    )
                    5 -> ToolboxScreen()
                    6 -> DevLogScreen(client, realtimeRevision)
                    8 -> MoreScreen(onNavigate = { tab = it })
                    else -> SettingsScreen(
                        client = client,
                        realtimeStatus = realtimeStatus,
                        host = host,
                        port = port,
                        onHostChange = {
                            host = it
                            prefs.edit().putString("host", it).apply()
                        },
                        onPortChange = {
                            port = it
                            prefs.edit().putString("port", it).apply()
                        },
                        displayName = displayName,
                        githubUsername = githubUsername,
                        profileSyncStatus = profileSyncStatus,
                        onScanConnection = { scanned ->
                            val parsed = Uri.parse(scanned)
                            parsed.host?.let { onHost ->
                                host = onHost
                                prefs.edit().putString("host", onHost).apply()
                                val scannedPort = parsed.port.takeIf { it > 0 } ?: 8080
                                port = scannedPort.toString()
                                prefs.edit().putString("port", port).apply()
                            }
                        },
                        onProfileSave = { name, username ->
                            displayName = name
                            githubUsername = username
                            prefs.edit().putString("display_name", name).putString("github_username", username).apply()
                            appScope.launch {
                                runCatching { client.updateProfile(ProfileUpdateRequest(name, username)) }
                            }
                        },
                        notifications = notifications,
                        onNotificationsChange = {
                            notifications = it
                            saveBoolean("notifications", it)
                        },
                        githubSync = githubSync,
                        onGithubSyncChange = {
                            githubSync = it
                            saveBoolean("github_sync", it)
                        },
                        focusMode = focusMode,
                        onFocusModeChange = {
                            focusMode = it
                            saveBoolean("focus_mode", it)
                        },
                    )
                }
            }
            if (showScratchpad) {
                ScratchpadBottomSheet(onDismiss = { showScratchpad = false })
            }
        }
    }
}
@Composable
private fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier,
        shape = cardShape,
        colors = CardDefaults.cardColors(containerColor = panel),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    ) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

@Composable
private fun PageTitle(title: String, subtitle: String? = null) {
    Column {
        Text(title, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        if (subtitle != null) Text(subtitle, fontSize = 13.sp, color = muted)
    }
}

@Composable
private fun DashboardScreen(
    client: ApiClient,
    realtimeRevision: Int = 0,
    displayName: String,
    githubSync: Boolean,
    focusMode: Boolean,
    onNavigate: (Int) -> Unit,
    onFocusModeChange: (Boolean) -> Unit,
) {
    var summary by remember { mutableStateOf<DashboardSummary?>(null) }
    var notes by remember { mutableStateOf<List<Note>>(emptyList()) }
    var activity by remember { mutableStateOf<List<GithubActivity>>(emptyList()) }
    var repos by remember { mutableStateOf<List<GithubRepo>>(emptyList()) }
    var heatmap by remember { mutableStateOf<GithubHeatmap?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var showFocus by remember { mutableStateOf(false) }
    var showStats by remember { mutableStateOf(false) }
    LaunchedEffect(client, githubSync, realtimeRevision) {
        try {
            summary = client.summary()
            notes = client.notes().take(3)
            activity = if (githubSync) client.githubActivity(4) else emptyList()
            repos = if (githubSync) client.githubRepos().take(6) else emptyList()
            heatmap = client.heatmap(84)
        } catch (e: Exception) {
            error = friendlyError(e)
        }
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("下午好，" + displayName.ifBlank { "Liu Developer" }, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
                    Text(dateLabel(), fontSize = 13.sp, color = muted)
                }
                Row(
                    Modifier.clip(RoundedCornerShape(20.dp)).background(Color(0x3322C55E))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(green))
                    Spacer(Modifier.size(7.dp))
                    Text("在线", fontSize = 12.sp, color = greenLight, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        item { ProgressPanel(summary) }
        if (error != null) item { Text("连接提示：" + error, color = errorColor, fontSize = 13.sp) }
        item {
            Panel(Modifier.fillMaxWidth()) {
                SectionTitle("GitHub 动态")
                if (activity.isEmpty()) EmptyText("暂无 GitHub 活动")
                activity.forEach { FeedRow(it) }
            }
        }
        if (repos.isNotEmpty()) {
            item {
                Panel(Modifier.fillMaxWidth()) {
                    SectionTitle("仓库概览")
                    repos.forEach { repo -> DashboardRepoRow(repo, onNavigate) }
                }
            }
        }
        item {
            Panel(Modifier.fillMaxWidth()) {
                SectionTitle("最近笔记")
                if (notes.isEmpty()) EmptyText("暂无笔记")
                notes.forEach { NotePreview(it) }
            }
        }
        item { HeatmapPanel(heatmap, "贡献热力图") }
        item {
            QuickTools(
                onNavigate = onNavigate,
                onOpenFocus = { showFocus = true },
                onOpenStats = { showStats = true },
            )
        }
    }
    if (showFocus) {
        FocusDialog(
            focusMode = focusMode,
            onDismiss = { showFocus = false },
            onFocusModeChange = onFocusModeChange,
        )
    }
    if (showStats) {
        StatsDialog(summary, onDismiss = { showStats = false })
    }
}

@Composable
private fun ProgressPanel(summary: DashboardSummary?) {
    val rate = (summary?.task_completion_rate ?: 0.0).coerceIn(0.0, 100.0)
    val progress = (rate / 100.0).toFloat()
    Panel(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(80.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    drawArc(
                        Color(0x2233FFFFFF), -90f, 360f, false,
                        style = Stroke(8.dp.toPx(), cap = StrokeCap.Round),
                    )
                    drawArc(
                        green, -90f, 360f * progress, false,
                        style = Stroke(8.dp.toPx(), cap = StrokeCap.Round),
                    )
                }
                Text(rate.roundToInt().toString() + "%", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.size(18.dp))
            Column(Modifier.weight(1f)) {
                Text("今日任务进度", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    (summary?.tasks_completed ?: 0).toString() + " / " +
                        (summary?.tasks_total ?: 0) + " 已完成",
                    fontSize = 12.sp,
                    color = dim,
                )
                Spacer(Modifier.height(10.dp))
                Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color(0x1AFFFFFF))) {
                    Box(Modifier.fillMaxWidth(progress).height(6.dp).background(green))
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = muted, letterSpacing = 1.sp)
}

@Composable
private fun DashboardRepoRow(repo: GithubRepo, onNavigate: (Int) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .clickable { onNavigate(3) }
            .padding(vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(languageColor(repo.language)),
        )
        Column(Modifier.weight(1f)) {
            Text(repo.name, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(
                repo.language.ifBlank { "?" } + "  ·  " + repo.stars + " ★",
                fontSize = 11.sp,
                color = muted,
            )
        }
        Text("▶", fontSize = 8.sp, color = dim)
    }
}

@Composable
private fun FeedRow(item: GithubActivity) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.size(34.dp).clip(RoundedCornerShape(17.dp)).background(Color(0xFF166534)),
            contentAlignment = Alignment.Center,
        ) {
            Text(item.actor.firstOrNull()?.uppercase() ?: "G", fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.weight(1f)) {
            Row {
                Text(item.actor.ifBlank { "GitHub" }, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.size(5.dp))
                Text(activityLabel(item.type), fontSize = 11.sp, color = muted)
            }
            Text(
                item.type.removeSuffix("Event") + " · " + relativeDate(item.created_at),
                fontSize = 12.sp,
                color = Color(0xFFD4D4D4),
                maxLines = 2,
            )
            Text(relativeDate(item.created_at), fontSize = 10.sp, color = dim)
        }
    }
}

@Composable
private fun NotePreview(note: Note) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(width = 3.dp, height = 30.dp).clip(RoundedCornerShape(2.dp)).background(green))
        Column(Modifier.weight(1f)) {
            Text(note.title, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(relativeDate(note.updated_at) + " · 已保存", fontSize = 11.sp, color = dim)
        }
    }
}

@Composable
private fun HeatmapPanel(data: GithubHeatmap?, title: String) {
    Panel(Modifier.fillMaxWidth()) {
        SectionTitle(title)
        val cells = data?.items?.takeLast(84).orEmpty()
        if (cells.isEmpty()) EmptyText("暂无提交数据")
        cells.chunked(12).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                row.forEach { item ->
                    Box(
                        Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(3.dp))
                            .background(heatColor(item.count)),
                    )
                }
            }
        }
        if (cells.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("过去 84 天", fontSize = 10.sp, color = dim)
                Text("共 " + (data?.total ?: 0) + " 次提交", fontSize = 10.sp, color = dim)
            }
        }
    }
}

@Composable
private fun QuickTools(
    onNavigate: (Int) -> Unit,
    onOpenFocus: () -> Unit,
    onOpenStats: () -> Unit,
) {
    val context = LocalContext.current
    fun openCalendar() {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW).setData(CalendarContract.CONTENT_URI))
        } catch (_: Exception) {
            Toast.makeText(context, "设备上没有可用的日历应用", Toast.LENGTH_SHORT).show()
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("快捷工具")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Tool("日程", "◷", Modifier.weight(1f), ::openCalendar)
            Tool("专注", "◎", Modifier.weight(1f), onOpenFocus)
            Tool("备忘录", "▤", Modifier.weight(1f)) { onNavigate(1) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Tool("统计", "▥", Modifier.weight(1f), onOpenStats)
            Tool("设置", "⚙", Modifier.weight(1f)) { onNavigate(4) }
            Tool("搜索", "⌕", Modifier.weight(1f)) { onNavigate(1) }
        }
    }
}

@Composable
private fun Tool(label: String, icon: String, modifier: Modifier, onClick: () -> Unit) {
    Card(
        modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(Color(0x0AFFFFFF)),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(icon, fontSize = 22.sp, color = greenLight)
            Spacer(Modifier.height(5.dp))
            Text(label, fontSize = 11.sp, color = muted)
        }
    }
}

@Composable
private fun FocusDialog(
    focusMode: Boolean,
    onDismiss: () -> Unit,
    onFocusModeChange: (Boolean) -> Unit,
) {
    var running by remember { mutableStateOf(false) }
    var seconds by remember { mutableIntStateOf(25 * 60) }
    val totalDuring = remember(seconds) { seconds }
    var sessionCount by remember { mutableIntStateOf(0) }
    var selectedPreset by remember { mutableIntStateOf(25) }
    val presets = listOf(15, 25, 45)
    LaunchedEffect(running) {
        while (running && seconds > 0) {
            delay(1000)
            seconds -= 1
        }
        if (seconds == 0 && running) {
            sessionCount++
            running = false
            onFocusModeChange(false)
        }
    }
    val progress = if (totalDuring > 0) seconds.toFloat() / totalDuring.toFloat() else 1f
    val time = String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60)
    AlertDialog(
        onDismissRequest = {
            running = false
            onFocusModeChange(false)
            onDismiss()
        },
        containerColor = Color(0xFF111111),
        shape = RoundedCornerShape(24.dp),
        title = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("🍅 番茄钟", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                if (sessionCount > 0) Text("完成 $sessionCount 次", fontSize = 12.sp, color = muted)
            }
        },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(180.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxSize()) {
                        val strokeW = 8.dp.toPx()
                        val arcSize = size.minDimension / 2f - strokeW / 2f
                        val topLeft = Offset(size.width / 2f - arcSize, size.height / 2f - arcSize)
                        val arcRect = Rect(topLeft, Size(arcSize * 2, arcSize * 2))
                        drawArc(Color(0x22FFFFFF), 0f, 360f, false, style = Stroke(strokeW, cap = StrokeCap.Round), topLeft = arcRect.topLeft, size = arcRect.size)
                        drawArc(green, -90f, -360f * progress, false, style = Stroke(strokeW, cap = StrokeCap.Round), topLeft = arcRect.topLeft, size = arcRect.size)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(time, fontSize = 42.sp, fontWeight = FontWeight.Bold, color = greenLight)
                        Text(
                            when {
                                seconds == 0 && sessionCount > 0 -> "完成！休息一下吧 ☕"
                                running -> "专注中..."
                                else -> "准备开始"
                            },
                            fontSize = 12.sp, color = muted,
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    presets.forEach { preset ->
                        FilterChip(
                            selected = selectedPreset == preset,
                            onClick = { if (!running) { selectedPreset = preset; seconds = preset * 60 } },
                            label = { Text("${preset}min", fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0x3322C55E), selectedLabelColor = greenLight),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text("选择预设时长以开始新的专注周期", fontSize = 11.sp, color = dim)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (seconds == 0) seconds = selectedPreset * 60
                    running = !running
                    onFocusModeChange(running)
                },
                colors = ButtonDefaults.buttonColors(containerColor = if (running) Color(0x55555555) else green),
                shape = RoundedCornerShape(10.dp),
            ) { Text(if (running) "暂停" else "开始专注", fontSize = 14.sp) }
        },
        dismissButton = {
            TextButton(onClick = {
                running = false
                seconds = selectedPreset * 60
                sessionCount = 0
                onFocusModeChange(false)
            }) { Text("重置", color = muted) }
        },
    )
}

@Composable
private fun StatsDialog(summary: DashboardSummary?, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("工作统计") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                StatLine("任务总数", (summary?.tasks_total ?: 0).toString())
                StatLine("已完成", (summary?.tasks_completed ?: 0).toString())
                StatLine("笔记", (summary?.notes_total ?: 0).toString())
                StatLine("近 30 天提交", (summary?.github_commits_30d ?: 0).toString())
                StatLine("活跃仓库", (summary?.github_repositories ?: 0).toString())
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("知道了") } },
    )
}

@Composable
private fun StatLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = muted)
        Text(value, fontWeight = FontWeight.Bold, color = greenLight)
    }
}
@Composable
private fun KanbanScreen(client: ApiClient, realtimeRevision: Int = 0) {
    var boardName by remember { mutableStateOf("Sprint") }
    var boards by remember { mutableStateOf<List<com.personalworkstation.app.core.model.Board>>(emptyList()) }
    var selectedBoardId by remember { mutableIntStateOf(0) }
    var columns by remember { mutableStateOf<List<BoardColumn>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var selectedColumn by remember { mutableStateOf<Int?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var showFocus by remember { mutableStateOf(false) }
    var showStats by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }
    var showBoardAdd by remember { mutableStateOf(false) }
    var showColumnAdd by remember { mutableStateOf(false) }
    var undoMoves by remember { mutableStateOf<List<TaskMove>>(emptyList()) }
    var newTitle by remember { mutableStateOf("") }
    var newDescription by remember { mutableStateOf("") }
    var newDueDate by remember { mutableStateOf("") }
    var newPriority by remember { mutableStateOf("medium") }
    var editingTaskId by remember { mutableStateOf<Int?>(null) }
    var boardDraftName by remember { mutableStateOf("") }
    var columnDraftName by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    fun reload() {
        scope.launch {
            loading = true
            error = null
            try {
                boards = client.boards()
                if (boards.isEmpty()) throw IllegalStateException("还没有看板")
                if (selectedBoardId == 0 || boards.none { it.id == selectedBoardId }) selectedBoardId = boards.first().id
                val board = boards.first { it.id == selectedBoardId }
                boardName = board.name
                columns = client.boardTasks(board.id)
            } catch (e: Exception) {
                error = friendlyError(e)
            } finally {
                loading = false
            }
        }
    }

fun moveTask(task: Task, target: BoardColumn) {
        val previousName = columns.firstOrNull { it.id == task.column_id }?.name ?: "上一列"
        scope.launch {
            try {
                client.updateTask(task.id, TaskUpdateRequest(column_id = target.id))
                undoMoves = undoMoves + TaskMove(task.id, task.column_id, previousName)
                reload()
            } catch (e: Exception) {
                error = friendlyError(e)
            }
        }
    }

    fun undoMove() {
        val previous = undoMoves.lastOrNull() ?: return
        scope.launch {
            try {
                client.updateTask(previous.taskId, TaskUpdateRequest(column_id = previous.previousColumnId))
                undoMoves = undoMoves.dropLast(1)
                reload()
            } catch (e: Exception) {
                error = friendlyError(e)
            }
        }
    }

    fun editTask(task: Task) {
        editingTaskId = task.id
        newTitle = task.title
        newDescription = task.description
        newDueDate = task.due_date.orEmpty()
        newPriority = task.priority
        showAdd = true
    }

    LaunchedEffect(client, realtimeRevision) { reload() }
    val total = columns.sumOf { it.tasks.size }
    val visible = columns.filter { selectedColumn == null || it.id == selectedColumn }

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(boardName, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text("当前看板 · " + total + " 个任务", fontSize = 12.sp, color = muted)
                }
                if (undoMoves.isNotEmpty()) {
                    TextButton(onClick = { undoMove() }) { Text("撤销", color = greenLight) }
                }
                TextButton(onClick = { columnDraftName = ""; showColumnAdd = true }) { Text("＋列", color = greenLight) }
                Button(
                    onClick = { newTitle = ""; newDescription = ""; showAdd = true },
                    modifier = Modifier.size(42.dp),
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(0.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = green),
                ) { Text("+", fontSize = 24.sp) }
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(boards) { board ->
                    FilterChip(
                        selected = board.id == selectedBoardId,
                        onClick = { selectedBoardId = board.id; reload() },
                        label = { Text(board.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    )
                }
                item { TextButton(onClick = { boardDraftName = ""; showBoardAdd = true }) { Text("＋看板", color = greenLight) } }
            }
            SearchField(query, { query = it }, "搜索任务、描述...")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = selectedColumn == null,
                        onClick = { selectedColumn = null },
                        label = { Text("全部 " + total) },
                    )
                }
                items(columns) { column ->
                    FilterChip(
                        selected = selectedColumn == column.id,
                        onClick = {
                            selectedColumn = if (selectedColumn == column.id) null else column.id
                        },
                        label = { Text(column.name + " " + column.tasks.size) },
                    )
                }
            }
        }
        if (loading) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = green)
            }
        } else {
            LazyColumn(
                Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (error != null) item { Text(error ?: "", color = errorColor) }
                items(visible) { column ->
                    KanbanColumn(column, columns, query, client, ::moveTask, ::editTask, ::reload)
                }
            }
        }
    }

    if (showAdd) {
        AlertDialog(
            onDismissRequest = { showAdd = false; editingTaskId = null },
            title = { Text(if (editingTaskId == null) "新建任务" else "编辑任务") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        newTitle,
                        { newTitle = it },
                        Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("任务标题") },
                        colors = fieldColors(),
                    )
                    OutlinedTextField(
                        newDescription,
                        { newDescription = it },
                        Modifier.fillMaxWidth(),
                        minLines = 3,
                        label = { Text("任务描述") },
                        colors = fieldColors(),
                    )
                    OutlinedTextField(newDueDate, { newDueDate = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("截止日期 YYYY-MM-DD") }, colors = fieldColors())
                    OutlinedTextField(newPriority, { newPriority = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("优先级 low / medium / high") }, colors = fieldColors())
                }
            },
            confirmButton = {
                TextButton(
                    enabled = newTitle.isNotBlank() && columns.isNotEmpty(),
                    onClick = {
                        scope.launch {
                            try {
                                val dueDate = newDueDate.trim().takeIf { it.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) }
                                val priority = newPriority.trim().lowercase().takeIf { it in setOf("low", "medium", "high") } ?: "medium"
                                if (editingTaskId == null) client.createTask(TaskCreateRequest(columns.first().id, newTitle.trim(), newDescription.trim(), priority, dueDate))
                                else client.updateTask(editingTaskId!!, TaskUpdateRequest(title = newTitle.trim(), description = newDescription.trim(), priority = priority, due_date = dueDate))
                                showAdd = false
                                editingTaskId = null
                                reload()
                            } catch (e: Exception) {
                                error = friendlyError(e)
                            }
                        }
                    },
                ) { Text("创建") }
            },
            dismissButton = { TextButton(onClick = { showAdd = false; editingTaskId = null }) { Text("取消") } },
        )
    }
    if (showBoardAdd) {
        AlertDialog(
            onDismissRequest = { showBoardAdd = false },
            title = { Text("新建看板") },
            text = { OutlinedTextField(boardDraftName, { boardDraftName = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("看板名称") }, colors = fieldColors()) },
            confirmButton = { TextButton(enabled = boardDraftName.isNotBlank(), onClick = { scope.launch { try { val board = client.createBoard(BoardCreateRequest(boardDraftName.trim())); selectedBoardId = board.id; showBoardAdd = false; reload() } catch (e: Exception) { error = friendlyError(e) } } }) { Text("创建") } },
            dismissButton = { TextButton(onClick = { showBoardAdd = false }) { Text("取消") } },
        )
    }
    if (showColumnAdd) {
        AlertDialog(
            onDismissRequest = { showColumnAdd = false },
            title = { Text("新建任务列") },
            text = { OutlinedTextField(columnDraftName, { columnDraftName = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("列名称") }, colors = fieldColors()) },
            confirmButton = { TextButton(enabled = columnDraftName.isNotBlank() && selectedBoardId != 0, onClick = { scope.launch { try { client.createColumn(selectedBoardId, ColumnCreateRequest(columnDraftName.trim())); showColumnAdd = false; reload() } catch (e: Exception) { error = friendlyError(e) } } }) { Text("创建") } },
            dismissButton = { TextButton(onClick = { showColumnAdd = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun KanbanColumn(
    column: BoardColumn,
    columns: List<BoardColumn>,
    query: String,
    client: ApiClient,
    moveTask: (Task, BoardColumn) -> Unit,
    editTask: (Task) -> Unit,
    reload: () -> Unit,
) {
    val tasks = column.tasks.filter {
        query.isBlank() ||
            it.title.contains(query, ignoreCase = true) ||
            it.description.contains(query, ignoreCase = true)
    }
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).clip(RoundedCornerShape(4.dp)).background(green))
            Spacer(Modifier.size(7.dp))
            Text(column.name.uppercase(), fontSize = 12.sp, color = muted, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.size(6.dp))
            Text(tasks.size.toString(), fontSize = 11.sp, color = dim)
        }
        if (tasks.isEmpty()) EmptyText("暂无匹配任务")
        tasks.forEach { TaskCard(it, columns, client, moveTask, editTask, reload) }
    }
}

@Composable
private fun TaskCard(task: Task, columns: List<BoardColumn>, client: ApiClient, moveTask: (Task, BoardColumn) -> Unit, editTask: (Task) -> Unit, reload: () -> Unit) {
    val scope = rememberCoroutineScope()
    Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(panel)) {
        Row(Modifier.fillMaxWidth()) {
            Box(
                Modifier.width(3.dp).height(100.dp).background(priorityColor(task.priority)),
            )
            Column(Modifier.padding(12.dp).weight(1f)) {
                Text(task.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
                if (task.description.isNotBlank()) {
                    Text(
                        task.description,
                        fontSize = 12.sp,
                        color = muted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(priorityLabel(task.priority), fontSize = 11.sp, color = priorityColor(task.priority))
                        if (!task.due_date.isNullOrBlank()) Text("截止 " + task.due_date, fontSize = 10.sp, color = if ((task.due_date ?: "") < LocalDate.now().toString()) errorColor else muted)
                    }
                    Row {
                        val index = columns.indexOfFirst { it.id == task.column_id }
                        val previous = columns.getOrNull(index - 1)
                        val next = columns.getOrNull(index + 1)
                        if (previous != null) {
                            TextButton(onClick = { moveTask(task, previous) }) { Text("↑", fontSize = 16.sp) }
                        }
                        if (next != null) {
                            TextButton(onClick = { moveTask(task, next) }) { Text("↓", fontSize = 16.sp) }
                        }
                        TextButton(onClick = { editTask(task) }) { Text("编辑", fontSize = 11.sp, color = greenLight) }
                        TextButton(onClick = {
                            scope.launch {
                                client.deleteTask(task.id)
                                reload()
                            }
                        }) { Text("删除", color = errorColor, fontSize = 11.sp) }
                    }
                }
            }
        }
    }
}

@Composable
private fun MoreScreen(onNavigate: (Int) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { PageTitle("更多", "工具、日志和连接设置") }
        item { Panel(Modifier.fillMaxWidth()) { MoreEntry("开发工具", "JSON/YAML/Base64/URL/时间戳/JWT/正则/Markdown", "⌘") { onNavigate(5) }; MoreEntry("每日开发日志", "记录进展、问题和下一步计划", "▣") { onNavigate(6) }; MoreEntry("连接设置", "服务器、二维码和个人资料", "⚙") { onNavigate(7) } } }
    }
}

@Composable
private fun MoreEntry(title: String, subtitle: String, icon: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(Color(0x3322C55E)), contentAlignment = Alignment.Center) { Text(icon, color = greenLight, fontSize = 18.sp) }
        Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.SemiBold); Text(subtitle, color = muted, fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp)) }
        Text("›", color = muted, fontSize = 22.sp)
    }
}

@Composable
private fun ToolboxScreen() {
    var input by remember { mutableStateOf("") }
    var output by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf("JSON") }
    var base64Dir by remember { mutableStateOf("编码") }
    var urlDir by remember { mutableStateOf("编码") }
    var tsDir by remember { mutableStateOf("戳→日期") }
    val context = LocalContext.current
    var history by remember { mutableStateOf(loadToolHistory(context)) }
    val modes = listOf("JSON", "JSON↔YAML", "Base64", "URL", "时间戳", "JWT", "正则", "Markdown")

    @Composable
    fun ToolButton(label: String, onClickAction: () -> Unit) {
        Button(onClick = onClickAction, colors = ButtonDefaults.buttonColors(containerColor = green)) { Text(label) }
    }
    @Composable
    fun OutputBar() {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = {
                    if (output.isNotBlank()) {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("tool_output", output))
                        Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("📋 复制", fontSize = 12.sp, color = greenLight) }
                TextButton(onClick = {
                    if (output.isNotBlank()) {
                        val h = history.toMutableList()
                        h.removeAll { it == output }
                        h.add(0, output)
                        if (h.size > 10) h.removeAt(h.size - 1)
                        history = h
                        saveToolHistory(context, h)
                        Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("💾 存历史", fontSize = 12.sp, color = greenLight) }
            }
            if (history.isNotEmpty()) {
                TextButton(onClick = {
                    if (history.isNotEmpty()) { input = history.first(); history = history.drop(1); saveToolHistory(context, history) }
                }) { Text("📜 取历史(${history.size})", fontSize = 12.sp, color = muted) }
            }
        }
    }

    fun process(): String {
        val json = kotlinx.serialization.json.Json { prettyPrint = true; ignoreUnknownKeys = true }
        return when (mode) {
            "JSON" -> json.encodeToString(kotlinx.serialization.json.JsonElement.serializer(), json.parseToJsonElement(input))
            "JSON↔YAML" -> {
                val y = Yaml()
                if (input.trimStart().startsWith("{")) {
                    y.dump(y.load(input))
                } else {
                    fun toJsonElement(v: Any?): kotlinx.serialization.json.JsonElement = when (v) {
                        is Map<*, *> -> kotlinx.serialization.json.buildJsonObject { v.forEach { (k, value) -> put(k.toString(), toJsonElement(value)) } }
                        is List<*> -> kotlinx.serialization.json.buildJsonArray { v.forEach { add(toJsonElement(it)) } }
                        is String -> kotlinx.serialization.json.JsonPrimitive(v)
                        is Number -> kotlinx.serialization.json.JsonPrimitive(v)
                        is Boolean -> kotlinx.serialization.json.JsonPrimitive(v)
                        null -> kotlinx.serialization.json.JsonNull
                        else -> kotlinx.serialization.json.JsonPrimitive(v.toString())
                    }
                    json.encodeToString(kotlinx.serialization.json.JsonElement.serializer(), toJsonElement(y.load(input)))
                }
            }
            "Base64" -> if (base64Dir == "编码") android.util.Base64.encodeToString(input.toByteArray(), android.util.Base64.NO_WRAP)
            else String(android.util.Base64.decode(input, android.util.Base64.NO_WRAP))
            "URL" -> if (urlDir == "编码") java.net.URLEncoder.encode(input, "UTF-8") else java.net.URLDecoder.decode(input, "UTF-8")
            "时间戳" -> if (tsDir == "戳→日期") java.time.Instant.ofEpochSecond(input.toLong()).toString()
            else (java.time.Instant.parse(input).epochSecond.toString())
            "JWT" -> {
                val parts = input.split(".")
                if (parts.size != 3) "无效 JWT：期望 header.payload.signature 格式"
                else {
                    fun decodeB64(s: String): String {
                        val padded = s.padEnd(s.length + (4 - s.length % 4) % 4, '=')
                        return String(android.util.Base64.decode(padded, android.util.Base64.URL_SAFE))
                    }
                    val header = json.encodeToString(kotlinx.serialization.json.JsonElement.serializer(), json.parseToJsonElement(decodeB64(parts[0])))
                    val payload = json.encodeToString(kotlinx.serialization.json.JsonElement.serializer(), json.parseToJsonElement(decodeB64(parts[1])))
                    "Header:\n$header\n\nPayload:\n$payload"
                }
            }
            "正则" -> {
                val lines = input.lines()
                if (lines.size < 2) "第一行输入正则表达式\n第二行起为测试文本"
                else {
                    val pattern = lines.first()
                    val text = lines.drop(1).joinToString("\n")
                    Regex(pattern).findAll(text).joinToString("\n") { "匹配: ${it.value} (位置: ${it.range.first})" }.ifEmpty { "无匹配" }
                }
            }
            "Markdown" -> input
            else -> "未知模式"
        }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { PageTitle("开发工具", "JSON格式化/互转、编解码、时间戳、JWT、正则、Markdown预览") }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(modes.size) { idx ->
                    FilterChip(selected = mode == modes[idx], onClick = { mode = modes[idx] }, label = { Text(modes[idx], fontSize = 12.sp) })
                }
            }
        }
        if (mode == "Base64") item { Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("编码", "解码").forEach { d -> FilterChip(selected = base64Dir == d, onClick = { base64Dir = d; output = "" }, label = { Text(d, fontSize = 11.sp) }) } } }
        if (mode == "URL") item { Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("编码", "解码").forEach { d -> FilterChip(selected = urlDir == d, onClick = { urlDir = d; output = "" }, label = { Text(d, fontSize = 11.sp) }) } } }
        if (mode == "时间戳") item { Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("戳→日期", "日期→戳").forEach { d -> FilterChip(selected = tsDir == d, onClick = { tsDir = d; output = "" }, label = { Text(d, fontSize = 11.sp) }) } } }
        item {
            Panel(Modifier.fillMaxWidth()) {
                OutlinedTextField(input, { input = it }, Modifier.fillMaxWidth(), minLines = if (mode == "Markdown") 10 else 5, label = { Text(if (mode == "正则") "首行正则 | 其余为测试文本" else "输入") }, colors = fieldColors())
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ToolButton(if (mode == "Markdown") "渲染" else "处理") { output = runCatching { process() }.getOrElse { "错误：${it.message}" } }
                    TextButton(onClick = { input = ""; output = "" }) { Text("清空", color = muted, fontSize = 12.sp) }
                    TextButton(onClick = { output = "" }) { Text("清输出", color = muted, fontSize = 12.sp) }
                }
                Spacer(Modifier.height(8.dp))
                if (mode == "Markdown") {
                    if (output.isNotBlank()) MarkdownPreview(output, Modifier.fillMaxWidth().heightIn(min = 120.dp).clip(RoundedCornerShape(8.dp)).background(softPanel).padding(12.dp))
                } else {
                    OutlinedTextField(output, {}, Modifier.fillMaxWidth(), minLines = 8, readOnly = true, label = { Text("输出") }, colors = fieldColors())
                }
                Spacer(Modifier.height(8.dp))
                OutputBar()
            }
        }
    }
}

private fun loadToolHistory(ctx: Context): List<String> {
    val raw = ctx.getSharedPreferences("tool_history", Context.MODE_PRIVATE).getString("items", null) ?: return emptyList()
    return runCatching { raw.split("|||HIST_SPLIT|||") }.getOrDefault(emptyList())
}
private fun saveToolHistory(ctx: Context, items: List<String>) {
    ctx.getSharedPreferences("tool_history", Context.MODE_PRIVATE).edit().putString("items", items.joinToString("|||HIST_SPLIT|||")).apply()
}

@Composable
private fun DevLogScreen(client: ApiClient, realtimeRevision: Int = 0) {
    var log by remember { mutableStateOf<DevLog?>(null) }
    var text by remember { mutableStateOf("") }
    var mood by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf("") }
    var selectedDate by remember { mutableStateOf(java.time.LocalDate.now()) }
    var calendarDays by remember { mutableStateOf<List<DevLogCalendarDay>>(emptyList()) }
    var streak by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val moodOptions = listOf("😄", "😊", "😐", "😔", "😤", "🔥", "💡", "🚀", "☕", "🐛")

    fun loadDate(dateStr: String) {
        scope.launch {
            loading = true
            runCatching { client.logByDate(dateStr) }.onSuccess {
                log = it; text = it.content; mood = it.mood; tags = it.tags.joinToString(", ")
            }
            loading = false
        }
    }

    LaunchedEffect(client, realtimeRevision) {
        loadDate(java.time.LocalDate.now().toString())
        runCatching { client.logCalendar(java.time.LocalDate.now().year, java.time.LocalDate.now().monthValue) }.onSuccess { calendarDays = it }
        runCatching { client.logStreak() }.onSuccess { streak = it.streak }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PageTitle("每日开发日志", "记录进展、问题与解决思路")
            Spacer(Modifier.weight(1f))
            Text("🔥 连续 $streak 天", fontSize = 12.sp, color = greenLight, fontWeight = FontWeight.Medium)
        }

        if (loading) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = green)
            }
        } else {
            LazyColumn(
                Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Calendar heatmap
                item {
                    Panel(Modifier.fillMaxWidth()) {
                        SectionTitle("月历")
                        Spacer(Modifier.height(8.dp))
                        val now = java.time.LocalDate.now()
                        val year = now.year
                        val month = now.monthValue
                        val firstDay = java.time.LocalDate.of(year, month, 1)
                        val daysInMonth = java.time.LocalDate.of(year, month, 1).lengthOfMonth()
                        val startDayOfWeek = (firstDay.dayOfWeek.value % 7) // Mon=1 -> 0
                        val dayMap = calendarDays.associate { it.date to it.length }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            listOf("一", "二", "三", "四", "五", "六", "日").forEach { Text(it, fontSize = 10.sp, color = dim, modifier = Modifier.weight(1f)) }
                        }
                        val calendar = mutableListOf<Int?>()
                        repeat(startDayOfWeek) { calendar.add(null) }
                        for (d in 1..daysInMonth) calendar.add(d)
                        val rows = calendar.chunked(7)
                        rows.forEachIndexed { ri, row ->
                            val fullRow = if (row.size < 7) row + List(7 - row.size) { null } else row
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                fullRow.forEachIndexed { ci, day ->
                                    val dateStr = if (day != null) "${year}-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}" else ""
                                    val len = dayMap[dateStr] ?: 0
                                    val isToday = day != null && day == now.dayOfMonth
                                    val bg = when {
                                        day == null -> Color.Transparent
                                        len > 200 -> green
                                        len > 0 -> green.copy(alpha = 0.4f + (len / 200f) * 0.6f)
                                        else -> Color(0x0AFFFFFF)
                                    }
                                    val boxMod = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .padding(2.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(bg)
                                        .let { if (day != null && !isToday) it.clickable { selectedDate = java.time.LocalDate.of(year, month, day); loadDate(selectedDate.toString()) } else it }
                                    if (day != null) Box(boxMod, contentAlignment = Alignment.Center) {
                                        if (isToday) {
                                            Canvas(Modifier.fillMaxSize()) {
                                                drawCircle(green, radius = 2.dp.toPx())
                                            }
                                        }
                                        Text(day.toString(), fontSize = 11.sp, color = if (isToday) greenLight else if (len > 0) Color.White else dim)
                                    } else Spacer(boxMod)
                                }
                            }
                        }
                    }
                }

                // Mood selector
                item {
                    Panel(Modifier.fillMaxWidth()) {
                        SectionTitle("心情")
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            moodOptions.forEach { emoji ->
                                Box(
                                    Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (mood == emoji) Color(0x3322C55E) else Color(0x0AFFFFFF))
                                        .clickable { mood = emoji },
                                    contentAlignment = Alignment.Center,
                                ) { Text(emoji, fontSize = 20.sp) }
                            }
                        }
                    }
                }

                // Tag input
                item {
                    Panel(Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = tags,
                            onValueChange = { tags = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("标签（用逗号分隔）") },
                            placeholder = { Text("前端, Bug修复, 重构", color = dim) },
                            colors = fieldColors(),
                            textStyle = TextStyle(fontSize = 14.sp),
                        )
                    }
                }

                // Date selector
                item {
                    Panel(Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("📅 ", fontSize = 14.sp)
                            Text(selectedDate.toString(), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = greenLight)
                            Spacer(Modifier.weight(1f))
                            Box(Modifier.size(32.dp).clip(RoundedCornerShape(6.dp)).clickable {
                                val prev = selectedDate.minusDays(1)
                                selectedDate = prev; loadDate(prev.toString())
                            }, contentAlignment = Alignment.Center) { Text("◀", fontSize = 12.sp, color = muted) }
                            Box(Modifier.size(32.dp).clip(RoundedCornerShape(6.dp)).clickable {
                                val next = selectedDate.plusDays(1)
                                selectedDate = next; loadDate(next.toString())
                            }, contentAlignment = Alignment.Center) { Text("▶", fontSize = 12.sp, color = muted) }
                        }
                    }
                }

                // Content editor
                item {
                    Panel(Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = text,
                            onValueChange = { text = it },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp),
                            label = { Text("日志内容（支持 Markdown）") },
                            placeholder = { Text("记录今天的开发进展、遇到的问题、解决思路...", color = dim) },
                            colors = fieldColors(),
                            textStyle = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
                        )
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = {
                                saving = true
                                log?.let { current ->
                                    scope.launch {
                                        runCatching {
                                            client.updateLog(
                                                current.id,
                                                DevLogUpdateRequest(
                                                    content = text,
                                                    mood = mood,
                                                    tags = tags.split(",").map { it.trim() }.filter { it.isNotBlank() },
                                                )
                                            )
                                            Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show()
                                        }
                                        saving = false
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = green),
                            shape = RoundedCornerShape(10.dp),
                            enabled = !saving,
                        ) { Text(if (saving) "保存中..." else "💾 保存", fontSize = 14.sp) }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SnippetsScreen(client: ApiClient, realtimeRevision: Int = 0) {
    var items by remember { mutableStateOf<List<Snippet>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var language by remember { mutableStateOf("plain") }
    var showEditor by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    LaunchedEffect(client, realtimeRevision, query) { runCatching { items = client.snippets(query) } }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { PageTitle("代码片段", "搜索、复制和复用常用代码") }
        item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) { SearchField(query, { query = it }, "搜索标题或代码"); Button(onClick = { showEditor = true }, colors = ButtonDefaults.buttonColors(containerColor = green)) { Text("新建") } } }
        if (items.isEmpty()) item { EmptyText("还没有匹配的代码片段") }
        items(items, key = { it.id }) { item ->
            Panel(Modifier.fillMaxWidth()) { Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(item.title, fontWeight = FontWeight.Bold); Text(item.language, fontSize = 11.sp, color = greenLight); Text(item.code.take(500), fontSize = 11.sp, color = muted, maxLines = 6, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp)) }; TextButton(onClick = { val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager; clipboard.setPrimaryClip(android.content.ClipData.newPlainText("snippet", item.code)); Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show() }) { Text("复制", color = greenLight) } } }
        }
    }
    if (showEditor) AlertDialog(onDismissRequest = { showEditor = false }, title = { Text("新建代码片段") }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(title, { title = it }, label = { Text("标题") }, singleLine = true, colors = fieldColors()); OutlinedTextField(language, { language = it }, label = { Text("语言") }, singleLine = true, colors = fieldColors()); OutlinedTextField(code, { code = it }, label = { Text("代码") }, minLines = 6, colors = fieldColors()) } }, confirmButton = { TextButton(onClick = { if (title.isNotBlank() && code.isNotBlank()) scope.launch { client.createSnippet(SnippetCreateRequest(title, code, language)); title=""; code=""; showEditor=false; items=client.snippets(query) } }) { Text("保存", color = greenLight) } }, dismissButton = { TextButton(onClick = { showEditor = false }) { Text("取消") } })
}

@Composable
private fun NotesScreen(client: ApiClient, realtimeRevision: Int = 0) {
    var notes by remember { mutableStateOf<List<Note>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var selectedTag by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var showFocus by remember { mutableStateOf(false) }
    var showStats by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }
    var editingNoteId by remember { mutableStateOf<Int?>(null) }
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf("") }
    var noteMode by remember { mutableStateOf("edit") }
    val scope = rememberCoroutineScope()

    fun reload() {
        scope.launch {
            loading = true
            error = null
            try {
                notes = client.notes(query)
            } catch (e: Exception) {
                error = friendlyError(e)
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(client, query, realtimeRevision) { reload() }
    val availableTags = notes.flatMap { it.tags }.distinct().sorted()
    val visibleNotes = notes.filter { selectedTag == null || selectedTag in it.tags }

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                PageTitle("笔记", "本地知识与想法")
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = { title = ""; content = ""; tags = ""; noteMode = "edit"; showAdd = true },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = green),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                ) { Text("+ 新建", fontSize = 12.sp) }
            }
            SearchField(query, { query = it }, "搜索笔记内容...")
            if (availableTags.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = selectedTag == null,
                            onClick = { selectedTag = null },
                            label = { Text("全部") },
                        )
                    }
                    items(availableTags) { tag ->
                        FilterChip(
                            selected = selectedTag == tag,
                            onClick = { selectedTag = if (selectedTag == tag) null else tag },
                            label = { Text(tag, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        )
                    }
                }
            }
        }
        if (loading) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = green)
            }
        } else {
            LazyColumn(
                Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (error != null) item { Text(error ?: "", color = errorColor) }
                if (visibleNotes.isEmpty()) item { EmptyText(if (notes.isEmpty()) "还没有笔记，先记录一个想法吧" else "没有匹配该标签的笔记") }
                items(visibleNotes) { note ->
                    NoteCard(note, onEdit = {
                        editingNoteId = note.id
                        title = note.title
                        content = note.content
                        tags = note.tags.joinToString(", ")
                        noteMode = "edit"
                        showAdd = true
                    }) {
                        scope.launch {
                            client.deleteNote(note.id)
                            reload()
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        AlertDialog(
            onDismissRequest = { showAdd = false; editingNoteId = null },
            title = { Text(if (editingNoteId == null) "新建笔记" else "编辑笔记") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("标题") }, colors = fieldColors())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("edit" to "编辑", "preview" to "预览", "split" to "分栏").forEach { (mode, label) ->
                            FilterChip(selected = noteMode == mode, onClick = { noteMode = mode }, label = { Text(label) })
                        }
                    }
                    when (noteMode) {
                        "preview" -> MarkdownPreview(content, Modifier.fillMaxWidth().heightIn(min = 140.dp, max = 280.dp))
                        "split" -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(content, { content = it }, Modifier.fillMaxWidth(), minLines = 4, label = { Text("Markdown 原文") }, colors = fieldColors())
                            MarkdownPreview(content, Modifier.fillMaxWidth().heightIn(min = 100.dp, max = 220.dp))
                        }
                        else -> OutlinedTextField(content, { content = it }, Modifier.fillMaxWidth(), minLines = 5, label = { Text("Markdown 内容") }, colors = fieldColors())
                    }
                    OutlinedTextField(tags, { tags = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("标签，用逗号分隔") }, colors = fieldColors())
                }
            },
            confirmButton = {
                TextButton(enabled = title.isNotBlank(), onClick = {
                    scope.launch {
                        try {
                            val tagList = tags.split(",").map { it.trim() }.filter { it.isNotBlank() }
                            if (editingNoteId == null) client.createNote(NoteCreateRequest(title.trim(), content.trim(), tagList))
                            else client.updateNote(editingNoteId!!, NoteUpdateRequest(title = title.trim(), content = content.trim(), tags = tagList))
                            showAdd = false
                            editingNoteId = null
                            reload()
                        } catch (e: Exception) {
                            error = friendlyError(e)
                        }
                    }
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { showAdd = false; editingNoteId = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun NoteCard(note: Note, onEdit: () -> Unit, onDelete: () -> Unit) {
    Panel(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(note.title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(5.dp))
                if (note.content.isBlank()) Text("暂无内容", fontSize = 13.sp, color = Color(0xFFD4D4D4))
                else MarkdownPreview(note.content, Modifier.fillMaxWidth().heightIn(max = 120.dp))
                Spacer(Modifier.height(8.dp))
                Text(note.tags.joinToString(" · ").ifBlank { relativeDate(note.updated_at) }, fontSize = 11.sp, color = greenLight)
            }
            Column {
                TextButton(onClick = onEdit) { Text("编辑", color = greenLight, fontSize = 11.sp) }
                TextButton(onClick = onDelete) { Text("删除", color = errorColor, fontSize = 11.sp) }
            }
        }
    }
}

@Composable
private fun GithubProfileCard(
    profile: GithubProfile?,
    settings: com.personalworkstation.app.core.model.GithubSettingsStatus?,
    repos: List<GithubRepo>,
    activity: List<GithubActivity>,
) {
    val displayName = profile?.name?.ifBlank { profile.login }?.ifBlank { "未配置 GitHub" } ?: "未配置 GitHub"
    val username = profile?.login?.ifBlank { settings?.username.orEmpty() }.orEmpty()
    Panel(Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
            Box(
                Modifier.size(58.dp).clip(RoundedCornerShape(29.dp)).background(Color(0xFF166534)),
                contentAlignment = Alignment.Center,
            ) { Text(displayName.firstOrNull()?.uppercase() ?: "G", fontSize = 22.sp, fontWeight = FontWeight.Bold) }
            Column(Modifier.weight(1f)) {
                Text(displayName, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(if (username.isBlank()) "尚未配置用户名" else "@$username", fontSize = 13.sp, color = muted)
                Text(profile?.bio?.ifBlank { "关注个人项目进展与近期活动" } ?: "关注个人项目进展与近期活动", fontSize = 12.sp, color = Color(0xFFD4D4D4), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
            StatItem(repos.size.toString(), "仓库")
            StatItem(activity.size.toString(), "活动")
            StatItem(repos.sumOf { it.stars }.toString(), "Stars")
        }
    }
}

@Composable
private fun StatItem(number: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(number, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(label, fontSize = 10.sp, color = muted)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GithubScreen(client: ApiClient, realtimeRevision: Int = 0) {
    var repos by remember { mutableStateOf<List<GithubRepo>>(emptyList()) }
    var activity by remember { mutableStateOf<List<GithubActivity>>(emptyList()) }
    var heatmap by remember { mutableStateOf<GithubHeatmap?>(null) }
    var profile by remember { mutableStateOf<GithubProfile?>(null) }
    var settings by remember { mutableStateOf<com.personalworkstation.app.core.model.GithubSettingsStatus?>(null) }
    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showFocus by remember { mutableStateOf(false) }
    var showStats by remember { mutableStateOf(false) }
    var selectedRepo by remember { mutableStateOf<GithubRepo?>(null) }
    val scope = rememberCoroutineScope()

    fun reload() {
        scope.launch {
            loading = true
            error = null
            try {
                repos = client.githubRepos()
                activity = client.githubActivity()
                heatmap = client.heatmap(84)
                profile = client.githubProfile()
                settings = client.githubSettings()
            } catch (e: Exception) {
                error = friendlyError(e)
            } finally {
                loading = false
            }
        }
    }

    fun refresh() {
        scope.launch {
            refreshing = true
            error = null
            try {
                client.refreshGithub()
                reload()
            } catch (e: Exception) {
                error = friendlyError(e)
            } finally {
                refreshing = false
            }
        }
    }

    LaunchedEffect(client, realtimeRevision) { reload() }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PageTitle("GitHub", "仓库、提交与活动追踪")
            Spacer(Modifier.weight(1f))
            TextButton(enabled = !refreshing, onClick = { refresh() }) {
                Text(if (refreshing) "同步中" else "刷新", color = greenLight)
            }
        }
        if (loading) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = green)
            }
        } else {
            LazyColumn(
                Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (error != null) item { Text(error ?: "", color = errorColor) }
                item { GithubProfileCard(profile, settings, repos, activity) }
                item { HeatmapPanel(heatmap, "提交热力图") }
                item {
                    Panel(Modifier.fillMaxWidth()) {
                        SectionTitle("最新动态")
                        if (activity.isEmpty()) EmptyText("暂无 GitHub 活动")
                        activity.take(6).forEach { FeedRow(it) }
                    }
                }
                item {
                    Panel(Modifier.fillMaxWidth()) {
                        SectionTitle("个人仓库")
                        if (repos.isEmpty()) EmptyText("暂无仓库缓存")
                        repos.take(8).forEach { repo ->
                            RepoRow(repo, onClick = { selectedRepo = repo })
                        }
                    }
                }
            }
        }
    }
    if (selectedRepo != null) {
        RepoDetailSheet(repo = selectedRepo!!, onDismiss = { selectedRepo = null })
    }
}

@Composable
private fun RepoRow(repo: GithubRepo, onClick: () -> Unit = {}) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.size(9.dp).clip(RoundedCornerShape(5.dp)).background(languageColor(repo.language)))
        Column(Modifier.weight(1f)) {
            Text(repo.owner + " / " + repo.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(
                repo.description.ifBlank { "暂无仓库描述" },
                fontSize = 11.sp,
                color = muted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                repo.language.ifBlank { "未知语言" } + " · " + repo.stars + " stars · " +
                    repo.open_issues + " issues",
                fontSize = 10.sp,
                color = dim,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RepoDetailSheet(repo: GithubRepo, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = panel,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
        ) {
            // Header
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(12.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(languageColor(repo.language)),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        repo.language.ifBlank { "Unknown" },
                        fontSize = 13.sp,
                        color = muted,
                    )
                }
                TextButton(onClick = onDismiss) { Text("关闭", color = greenLight) }
            }

            Spacer(Modifier.height(10.dp))

            // Repo name
            Text(
                repo.owner + " / " + repo.name,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )

            Spacer(Modifier.height(8.dp))

            // Description
            Text(
                repo.description.ifBlank { "暂无仓库描述" },
                fontSize = 14.sp,
                color = Color(0xFFD4D4D4),
                lineHeight = 20.sp,
            )

            Spacer(Modifier.height(16.dp))

            // Stats cards
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                RepoStatCard(repo.stars.toString(), "Stars", "⭐")
                RepoStatCard(repo.open_issues.toString(), "Issues", "📋")
                if (repo.pushed_at != null) {
                    RepoStatCard(relativeDate(repo.pushed_at), "最后推送", "⏰")
                }
            }

            Spacer(Modifier.height(20.dp))

            // Info rows
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0x0AFFFFFF)),
                shape = RoundedCornerShape(12.dp),
            ) {
                Column(Modifier.padding(14.dp)) {
                    InfoRow("📦 仓库名", repo.name)
                    InfoRow("👤 所有者", repo.owner)
                    InfoRow("🌐 URL", repo.html_url)
                    if (repo.pushed_at != null) {
                        InfoRow("🕐 最后推送", repo.pushed_at.take(19).replace("T", " "))
                    }
                    InfoRow("📅 上次同步", repo.last_fetch_at.take(19).replace("T", " "))
                }
            }

            Spacer(Modifier.height(16.dp))

            // Open on GitHub button
            Button(
                onClick = {
                    try {
                        val intent = android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse(repo.html_url)
                        )
                        context.startActivity(intent)
                    } catch (_: Exception) {}
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF238636)),
                shape = RoundedCornerShape(10.dp),
            ) {
                Text("🔗 在 GitHub 中打开", fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun RepoStatCard(value: String, label: String, icon: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(icon, fontSize = 18.sp)
        Spacer(Modifier.height(4.dp))
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(label, fontSize = 10.sp, color = muted)
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Text(label, fontSize = 12.sp, color = dim, modifier = Modifier.width(100.dp))
        Text(
            value,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SettingsScreen(
    client: ApiClient,
    realtimeStatus: String,
    host: String,
    port: String,
    onHostChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    displayName: String,
    githubUsername: String,
    profileSyncStatus: String?,
    onScanConnection: (String) -> Unit,
    onProfileSave: (String, String) -> Unit,
    notifications: Boolean,
    onNotificationsChange: (Boolean) -> Unit,
    githubSync: Boolean,
    onGithubSyncChange: (Boolean) -> Unit,
    focusMode: Boolean,
    onFocusModeChange: (Boolean) -> Unit,
) {
    var status by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }
    var localDisplayName by remember(displayName) { mutableStateOf(displayName) }
    var localGithubUsername by remember(githubUsername) { mutableStateOf(githubUsername) }
    var profileStatus by remember { mutableStateOf<String?>(null) }
    var backupStatus by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    // mDNS 局域网发现
    var discovering by remember { mutableStateOf(false) }
    val discoveredServices = remember { mutableStateListOf<DiscoveredService>() }
    var discoveryError by remember { mutableStateOf<String?>(null) }
    val nsdManager = remember { context.getSystemService(Context.NSD_SERVICE) as NsdManager }
    val wifiLock = remember {
        (context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager)
            .createMulticastLock("workstation-mdns")
    }
    // UDP 广播回退发现（当 NsdManager 不可用时）
    val startUdpDiscovery: suspend () -> Unit = {
        var socket: DatagramSocket? = null
        try {
            val request = "WORKSTATION_DISCOVER".toByteArray()
            val broadcastAddr = InetAddress.getByName("255.255.255.255")
            socket = withContext(Dispatchers.IO) {
                val s = DatagramSocket()
                s.broadcast = true
                s.soTimeout = 3000
                s.send(DatagramPacket(request, request.size, broadcastAddr, 5354))
                s
            }
            val buffer = ByteArray(1024)
            val deadline = System.currentTimeMillis() + 3000
            while (System.currentTimeMillis() < deadline) {
                try {
                    val response = DatagramPacket(buffer, buffer.size)
                    withContext(Dispatchers.IO) { socket.receive(response) }
                    val json = String(response.data, 0, response.length, Charsets.UTF_8)
                    val obj = JSONObject(json)
                    if (obj.optString("service") != "personal-workstation") continue
                    val svc = DiscoveredService(
                        name = obj.optString("name", "个人工作台"),
                        host = obj.optString("host", response.address.hostAddress ?: ""),
                        port = obj.optInt("port", 8080),
                    )
                    if (svc.host.isNotBlank() && discoveredServices.none { it.host == svc.host && it.port == svc.port }) {
                        withContext(Dispatchers.Main) { discoveredServices.add(svc) }
                    }
                } catch (_: java.net.SocketTimeoutException) {
                    break
                }
            }
            if (discoveredServices.isEmpty()) {
                discovering = false
                discoveryError = "未发现工作台，请确保电脑和手机在同一 WiFi"
            } else {
                discovering = false
                discoveryError = null
            }
        } catch (e: Exception) {
            discovering = false
            discoveryError = "UDP 扫描失败: " + (e.message ?: "未知错误")
        } finally {
            try { socket?.close() } catch (_: Exception) {}
        }
    }
    val discoveryListener = remember {
        object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) { discovering = true; discoveryError = null }
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                if (errorCode == NsdManager.FAILURE_INTERNAL_ERROR) {
                    discoveryError = "mDNS 不可用，正在 UDP 广播搜索…"
                    scope.launch { startUdpDiscovery() }
                } else {
                    discovering = false
                    discoveryError = when (errorCode) {
                        NsdManager.FAILURE_ALREADY_ACTIVE -> "已在扫描中"
                        else -> "启动扫描失败 (错误码: $errorCode)"
                    }
                }
            }
            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                    override fun onServiceResolved(resolved: NsdServiceInfo) {
                        val svc = DiscoveredService(
                            name = resolved.serviceName ?: "个人工作台",
                            host = resolved.host?.hostAddress ?: return,
                            port = resolved.port.takeIf { it in 1..65535 } ?: return,
                        )
                        if (discoveredServices.none { it.host == svc.host && it.port == svc.port }) {
                            discoveredServices.add(svc)
                        }
                    }
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
                })
            }
            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                discoveredServices.removeAll { it.host == serviceInfo.host?.hostAddress }
            }
            override fun onDiscoveryStopped(serviceType: String) { discovering = false }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            try { nsdManager.stopServiceDiscovery(discoveryListener) } catch (_: Exception) {}
            try { if (wifiLock.isHeld) wifiLock.release() } catch (_: Exception) {}
        }
    }
    val activity = context as? Activity
    val backupExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { target ->
        if (target != null) {
            scope.launch {
                try {
                    val bytes = client.exportJsonBackup()
                    context.contentResolver.openOutputStream(target)?.use { it.write(bytes) }
                        ?: throw IllegalStateException("无法写入所选文件")
                    backupStatus = "JSON 备份已导出"
                } catch (e: Exception) {
                    backupStatus = "导出失败：" + friendlyError(e)
                }
            }
        }
    }
    val backupImport = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { source ->
        if (source != null) {
            scope.launch {
                try {
                    val bytes = context.contentResolver.openInputStream(source)?.use { it.readBytes() }
                        ?: throw IllegalStateException("无法读取所选文件")
                    val result = client.importJsonBackup(source.lastPathSegment ?: "workstation-backup.json", bytes)
                    backupStatus = "已合并恢复，安全备份：" + result.safety_backup
                } catch (e: Exception) {
                    backupStatus = "恢复失败：" + friendlyError(e)
                }
            }
        }
    }
    val scanner = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val scan = IntentIntegrator.parseActivityResult(result.resultCode, result.data)
        val value = scan?.contents
        if (!value.isNullOrBlank()) {
            val parsed = Uri.parse(value)
            if (parsed.scheme == "http" || parsed.scheme == "https") {
                onScanConnection(value)
                status = "已读取二维码，请测试连接"
            } else {
                status = "二维码不是工作台连接地址"
            }
        }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { PageTitle("设置", "连接、偏好与个人信息") }
        item {
            Panel(Modifier.fillMaxWidth()) {
                SectionTitle("连接状态")
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(52.dp).clip(RoundedCornerShape(26.dp)).background(Color(0xFF166534)),
                        contentAlignment = Alignment.Center,
                    ) { Text(localDisplayName.firstOrNull()?.uppercase() ?: "L", fontSize = 18.sp, fontWeight = FontWeight.Bold) }
                    Column {
                        Text(localDisplayName.ifBlank { "Liu Developer" }, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (status == "连接成功") "在线 · 刚刚" else "等待连接测试",
                            fontSize = 12.sp,
                            color = if (status == "连接成功") greenLight else muted,
                        )
                        Text(
                            when (realtimeStatus) {
                                "connected" -> "实时同步已连接"
                                "reconnecting" -> "实时同步重连中"
                                else -> "实时同步未连接"
                            },
                            fontSize = 11.sp,
                            color = if (realtimeStatus == "connected") greenLight else muted,
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    host,
                    onHostChange,
                    Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    label = { Text("服务器 IP") },
                    placeholder = { Text("例如 192.168.1.34") },
                    colors = fieldColors(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    port,
                    onPortChange,
                    Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    label = { Text("端口") },
                    placeholder = { Text("8080") },
                    colors = fieldColors(),
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !testing,
                    onClick = {
                        val cleanHost = normalizeHost(host)
                        val parsedPort = port.trim().toIntOrNull()
                        if (cleanHost.isBlank()) {
                            status = "请输入服务器 IP"
                        } else if (parsedPort == null || parsedPort !in 1..65535) {
                            status = "端口应为 1 到 65535"
                        } else {
                            val targetPort = parsedPort
                            onHostChange(cleanHost)
                            onPortChange(targetPort.toString())
                            testing = true
                            scope.launch {
                                val checkClient = ApiClient(cleanHost, targetPort)
                                status = try {
                                    checkClient.health()
                                    "连接成功"
                                } catch (e: Exception) {
                                    "连接失败：" + friendlyError(e)
                                } finally {
                                    checkClient.close()
                                    testing = false
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = green),
                ) { Text(if (testing) "测试中…" else "测试连接") }
                TextButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        activity?.let { scanner.launch(IntentIntegrator(it).createScanIntent()) }
                    },
                ) { Text("扫描电脑二维码", color = greenLight) }
                Spacer(Modifier.height(4.dp))
                // 局域网扫描按钮
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !discovering && !testing,
                    onClick = {
                        discoveredServices.clear()
                        discoveryError = null
                        try {
                            wifiLock.acquire()
                            nsdManager.discoverServices(
                                "_personal-workstation._tcp.local.",
                                NsdManager.PROTOCOL_DNS_SD,
                                discoveryListener,
                            )
                        } catch (e: Exception) {
                            discoveryError = "mDNS 不可用，正在 UDP 广播搜索…"
                            discovering = true
                            scope.launch { startUdpDiscovery() }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                ) {
                    if (discovering) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (discovering) "正在扫描局域网…" else "扫描局域网")
                }
                // 发现的服务列表
                if (discoveryError != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(discoveryError ?: "", color = errorColor, fontSize = 12.sp)
                }
                if (discoveredServices.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("发现 ${discoveredServices.size} 个工作台", fontSize = 11.sp, color = muted)
                    Spacer(Modifier.height(4.dp))
                    discoveredServices.forEach { svc ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onHostChange(svc.host)
                                    onPortChange(svc.port.toString())
                                    status = "已填入 ${svc.host}:${svc.port}，请测试连接"
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(svc.name, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                Text("${svc.host}:${svc.port}", fontSize = 11.sp, color = muted)
                            }
                            Text("▸", color = greenLight, fontSize = 16.sp)
                        }
                    }
                }
                if (status != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(status ?: "", color = if (status == "连接成功") greenLight else errorColor, fontSize = 12.sp)
                }
            }
        }
        item {
            Panel(Modifier.fillMaxWidth()) {
                SectionTitle("数据管理")
                Text("备份数据保存在你选择的位置；导入会以合并方式恢复，并由电脑端自动创建安全备份。", fontSize = 11.sp, color = muted)
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { backupExport.launch("personal-workstation-backup.json") },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = green),
                    ) { Text("导出 JSON", fontSize = 12.sp) }
                    TextButton(
                        onClick = { backupImport.launch(arrayOf("application/json", "text/json")) },
                        modifier = Modifier.weight(1f),
                    ) { Text("导入并合并", color = greenLight, fontSize = 12.sp) }
                }
                if (backupStatus != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(backupStatus ?: "", fontSize = 11.sp, color = if (backupStatus?.startsWith("恢复失败") == true || backupStatus?.startsWith("导出失败") == true) errorColor else greenLight)
                }
            }
        }
        item {
            Panel(Modifier.fillMaxWidth()) {
                SectionTitle("偏好设置")
                SettingToggle("推送通知", "接收任务更新和 GitHub 动态", notifications, onNotificationsChange)
                SettingToggle("GitHub 实时同步", "仪表盘自动拉取最新动态", githubSync, onGithubSyncChange)
                SettingToggle("专注模式", "隐藏非紧急通知并保留专注状态", focusMode, onFocusModeChange)
                Text("偏好会自动保存在本机，重启应用后仍然保留。", fontSize = 11.sp, color = dim, modifier = Modifier.padding(top = 8.dp))
            }
        }
        item {
            Panel(Modifier.fillMaxWidth()) {
                SectionTitle("个人信息")
                OutlinedTextField(
                    localDisplayName,
                    { localDisplayName = it; profileStatus = null },
                    Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("显示名称") },
                    placeholder = { Text("例如 Liu Developer") },
                    colors = fieldColors(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    localGithubUsername,
                    { localGithubUsername = it; profileStatus = null },
                    Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("GitHub 用户名") },
                    placeholder = { Text("例如 octocat") },
                    colors = fieldColors(),
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = localDisplayName.isNotBlank(),
                    onClick = {
                        onProfileSave(localDisplayName.trim(), localGithubUsername.trim())
                        profileStatus = "个人信息已保存"
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = green),
                ) { Text("保存个人信息") }
                if (profileStatus != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(profileStatus ?: "", color = greenLight, fontSize = 12.sp)
                }
                if (profileSyncStatus != null) {
                    Spacer(Modifier.height(5.dp))
                    Text(profileSyncStatus, color = if (profileSyncStatus == "已从电脑同步") greenLight else muted, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun MarkdownPreview(content: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val markwon = remember(context) {
        Markwon.builder(context)
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(TablePlugin.create(context))
            .usePlugin(TaskListPlugin.create(context))
            .build()
    }
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            android.widget.TextView(ctx).apply {
                setTextColor(android.graphics.Color.rgb(212, 212, 212))
                textSize = 13f
                setLineSpacing(0f, 1.15f)
            }
        },
        update = { view -> markwon.setMarkdown(view, content) },
    )
}
@Composable
private fun SettingToggle(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, fontSize = 11.sp, color = muted)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = green,
                uncheckedThumbColor = Color(0xFFB8B8B8),
                uncheckedTrackColor = Color(0x33111111),
            ),
        )
    }
}

@Composable
private fun SearchField(value: String, onValueChange: (String) -> Unit, placeholder: String) {
    OutlinedTextField(
        value,
        onValueChange,
        Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text("搜索") },
        placeholder = { Text(placeholder) },
        colors = fieldColors(),
    )
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    disabledTextColor = muted,
    focusedContainerColor = softPanel,
    unfocusedContainerColor = softPanel,
    disabledContainerColor = softPanel,
    focusedBorderColor = green,
    unfocusedBorderColor = Color(0xFF505050),
    disabledBorderColor = Color(0xFF404040),
    focusedLabelColor = greenLight,
    unfocusedLabelColor = Color(0xFFB8B8B8),
)

@Composable
private fun EmptyText(text: String) {
    Text(text, fontSize = 12.sp, color = muted, modifier = Modifier.padding(vertical = 6.dp))
}

private fun dateLabel(): String {
    val today = LocalDate.now()
    val names = listOf("一", "二", "三", "四", "五", "六", "日")
    return "周" + names[today.dayOfWeek.value - 1] + " · " +
        today.format(DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA))
}

private fun relativeDate(value: String): String {
    if (value.isBlank()) return "刚刚"
    return value.replace("T", " ").take(16)
}

private fun activityLabel(type: String): String = when {
    type.contains("Push", true) -> "提交了代码"
    type.contains("PullRequest", true) -> "更新了 PR"
    type.contains("Issues", true) -> "更新了 Issue"
    type.contains("Create", true) -> "创建了分支"
    else -> "产生了活动"
}

private fun heatColor(count: Int): Color = when {
    count >= 4 -> Color(0xDD22C55E)
    count == 3 -> Color(0x9922C55E)
    count == 2 -> Color(0x6622C55E)
    count == 1 -> Color(0x3322C55E)
    else -> Color(0x0AFFFFFF)
}

private fun priorityColor(priority: String): Color = when (priority) {
    "high" -> errorColor
    "low" -> green
    else -> Color(0xFFF0BF63)
}

private fun priorityLabel(priority: String): String = when (priority) {
    "high" -> "高优先级"
    "low" -> "低优先级"
    else -> "进行中"
}

private fun languageColor(language: String): Color = when (language.lowercase(Locale.ROOT)) {
    "kotlin" -> Color(0xFFA97BFF)
    "python" -> Color(0xFF3572A5)
    "javascript", "typescript" -> Color(0xFFF1E05A)
    "java" -> Color(0xFFB07219)
    else -> green
}

private fun normalizeHost(value: String): String {
    return value.trim().removePrefix("http://").removePrefix("https://")
        .substringBefore('/').substringBefore(':').trim()
}

private fun friendlyError(exception: Exception): String {
    val message = exception.message.orEmpty()
    return when {
        message.contains("parse url", true) -> "服务器地址格式不正确，请检查 IP 和端口"
        message.contains("connect", true) -> "无法连接服务器，请确认电脑服务已启动且手机与电脑在同一 Wi-Fi"
        else -> message.lineSequence().firstOrNull()?.take(160) ?: "请求失败"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScratchpadBottomSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("scratchpad_prefs", Context.MODE_PRIVATE) }
    var text by remember { mutableStateOf(prefs.getString("scratchpad_content", "") ?: "") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = panel,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("📝 快捷便签", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Row {
                    TextButton(onClick = {
                        text = ""
                        prefs.edit().remove("scratchpad_content").apply()
                    }) { Text("清空", color = muted) }
                    TextButton(onClick = { onDismiss() }) { Text("完成", color = greenLight) }
                }
            }

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = text,
                onValueChange = { newText ->
                    text = newText
                    prefs.edit().putString("scratchpad_content", newText).apply()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 200.dp, max = 460.dp),
                placeholder = { Text("随时记录想法、备忘、灵感...", color = muted) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = greenLight,
                    unfocusedBorderColor = Color(0xFF333333),
                ),
                textStyle = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
            )

            Spacer(Modifier.height(8.dp))
            Text(
                "共 ${text.length} 字符 · 自动保存",
                fontSize = 11.sp,
                color = muted,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.End,
            )
        }
    }
}

/** mDNS 发现的服务 */
data class DiscoveredService(
    val name: String,
    val host: String,
    val port: Int,
)
