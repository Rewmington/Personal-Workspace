package com.personalworkstation.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.CalendarContract
import android.widget.Toast
import androidx.activity.ComponentActivity
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personalworkstation.app.core.model.BoardColumn
import com.personalworkstation.app.core.model.DashboardSummary
import com.personalworkstation.app.core.model.GithubActivity
import com.personalworkstation.app.core.model.GithubHeatmap
import com.personalworkstation.app.core.model.GithubRepo
import com.personalworkstation.app.core.model.Note
import com.personalworkstation.app.core.model.NoteCreateRequest
import com.personalworkstation.app.core.model.Task
import com.personalworkstation.app.core.model.TaskCreateRequest
import com.personalworkstation.app.core.model.TaskUpdateRequest
import com.personalworkstation.app.core.network.ApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
private val tabs = listOf("看板", "笔记", "GitHub", "仪表盘", "设置")
private data class TaskMove(val taskId: Int, val previousColumnId: Int, val previousColumnName: String)
private val icons = listOf("▦", "▤", "◉", "▥", "⚙")
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

@Composable
private fun WorkstationApp() {
    MaterialTheme(colorScheme = colors) {
        val context = LocalContext.current
        val prefs = remember { context.getSharedPreferences("workstation_preferences", Context.MODE_PRIVATE) }
        var tab by remember { mutableIntStateOf(3) }
        var host by remember { mutableStateOf(prefs.getString("host", "192.168.1.100") ?: "192.168.1.100") }
        var port by remember { mutableStateOf(prefs.getString("port", "8080") ?: "8080") }
        var displayName by remember { mutableStateOf(prefs.getString("display_name", "Liu Developer") ?: "Liu Developer") }
        var githubUsername by remember { mutableStateOf(prefs.getString("github_username", "") ?: "") }
        var notifications by remember { mutableStateOf(prefs.getBoolean("notifications", true)) }
        var githubSync by remember { mutableStateOf(prefs.getBoolean("github_sync", true)) }
        var focusMode by remember { mutableStateOf(prefs.getBoolean("focus_mode", false)) }
        val client = remember(host, port) {
            ApiClient(normalizeHost(host), port.toIntOrNull()?.coerceIn(1, 65535) ?: 8080)
        }
        fun saveBoolean(key: String, value: Boolean) {
            prefs.edit().putBoolean(key, value).apply()
        }
        Scaffold(
            containerColor = bg,
            bottomBar = {
                NavigationBar(containerColor = Color(0xFF0A0A0A), tonalElevation = 0.dp) {
                    tabs.forEachIndexed { index, title ->
                        NavigationBarItem(
                            selected = tab == index,
                            onClick = { tab = index },
                            icon = { Text(icons[index], fontSize = 20.sp) },
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
                    0 -> KanbanScreen(client)
                    1 -> NotesScreen(client)
                    2 -> GithubScreen(client)
                    3 -> DashboardScreen(
                        client = client,
                        displayName = displayName,
                        githubSync = githubSync,
                        focusMode = focusMode,
                        onNavigate = { tab = it },
                        onFocusModeChange = {
                            focusMode = it
                            saveBoolean("focus_mode", it)
                        },
                    )
                    else -> SettingsScreen(
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
                        onProfileSave = { name, username ->
                            displayName = name
                            githubUsername = username
                            prefs.edit().putString("display_name", name).putString("github_username", username).apply()
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
    displayName: String,
    githubSync: Boolean,
    focusMode: Boolean,
    onNavigate: (Int) -> Unit,
    onFocusModeChange: (Boolean) -> Unit,
) {
    var summary by remember { mutableStateOf<DashboardSummary?>(null) }
    var notes by remember { mutableStateOf<List<Note>>(emptyList()) }
    var activity by remember { mutableStateOf<List<GithubActivity>>(emptyList()) }
    var heatmap by remember { mutableStateOf<GithubHeatmap?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var showFocus by remember { mutableStateOf(false) }
    var showStats by remember { mutableStateOf(false) }
    LaunchedEffect(client, githubSync) {
        try {
            summary = client.summary()
            notes = client.notes().take(3)
            activity = if (githubSync) client.githubActivity(4) else emptyList()
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
    LaunchedEffect(running) {
        while (running && seconds > 0) {
            delay(1000)
            seconds -= 1
        }
        if (seconds == 0) {
            running = false
            onFocusModeChange(false)
        }
    }
    val time = String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60)
    AlertDialog(
        onDismissRequest = {
            running = false
            onFocusModeChange(false)
            onDismiss()
        },
        title = { Text("专注模式") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(time, fontSize = 42.sp, fontWeight = FontWeight.Bold, color = greenLight)
                Spacer(Modifier.height(8.dp))
                Text(
                    if (running) "专注计时进行中" else if (focusMode) "专注模式已开启" else "准备开始 25 分钟专注",
                    color = muted,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    running = !running
                    onFocusModeChange(running)
                },
                colors = ButtonDefaults.buttonColors(containerColor = green),
            ) { Text(if (running) "暂停" else "开始专注") }
        },
        dismissButton = {
            TextButton(onClick = {
                running = false
                seconds = 25 * 60
                onFocusModeChange(false)
            }) { Text("重置") }
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
private fun KanbanScreen(client: ApiClient) {
    var boardName by remember { mutableStateOf("Sprint") }
    var columns by remember { mutableStateOf<List<BoardColumn>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var selectedColumn by remember { mutableStateOf<Int?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var showFocus by remember { mutableStateOf(false) }
    var showStats by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }
    var undoMoves by remember { mutableStateOf<List<TaskMove>>(emptyList()) }
    var newTitle by remember { mutableStateOf("") }
    var newDescription by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    fun reload() {
        scope.launch {
            loading = true
            error = null
            try {
                val board = client.boards().firstOrNull() ?: throw IllegalStateException("还没有看板")
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

    LaunchedEffect(client) { reload() }
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
                Button(
                    onClick = { newTitle = ""; newDescription = ""; showAdd = true },
                    modifier = Modifier.size(42.dp),
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(0.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = green),
                ) { Text("+", fontSize = 24.sp) }
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
                    KanbanColumn(column, columns, query, client, ::moveTask, ::reload)
                }
            }
        }
    }

    if (showAdd) {
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("新建任务") },
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
                }
            },
            confirmButton = {
                TextButton(
                    enabled = newTitle.isNotBlank() && columns.isNotEmpty(),
                    onClick = {
                        scope.launch {
                            try {
                                client.createTask(
                                    TaskCreateRequest(
                                        columns.first().id,
                                        newTitle.trim(),
                                        newDescription.trim(),
                                    ),
                                )
                                showAdd = false
                                reload()
                            } catch (e: Exception) {
                                error = friendlyError(e)
                            }
                        }
                    },
                ) { Text("创建") }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("取消") } },
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
        tasks.forEach { TaskCard(it, columns, client, moveTask, reload) }
    }
}

@Composable
private fun TaskCard(task: Task, columns: List<BoardColumn>, client: ApiClient, moveTask: (Task, BoardColumn) -> Unit, reload: () -> Unit) {
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
                    Text(priorityLabel(task.priority), fontSize = 11.sp, color = priorityColor(task.priority))
                    Row {
                        val index = columns.indexOfFirst { it.id == task.column_id }
                        val previous = columns.getOrNull(index - 1)
                        val next = columns.getOrNull(index + 1)
                        if (previous != null) {
                            TextButton(onClick = { moveTask(task, previous) }) { Text("←", fontSize = 16.sp) }
                        }
                        if (next != null) {
                            TextButton(onClick = { moveTask(task, next) }) { Text("→", fontSize = 16.sp) }
                        }
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
private fun NotesScreen(client: ApiClient) {
    var notes by remember { mutableStateOf<List<Note>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var showFocus by remember { mutableStateOf(false) }
    var showStats by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf("") }
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

    LaunchedEffect(client, query) { reload() }

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                PageTitle("笔记", "本地知识与想法")
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = { title = ""; content = ""; tags = ""; showAdd = true },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = green),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                ) { Text("+ 新建", fontSize = 12.sp) }
            }
            SearchField(query, { query = it }, "搜索笔记内容...")
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
                if (notes.isEmpty()) item { EmptyText("还没有笔记，先记录一个想法吧") }
                items(notes) { note ->
                    NoteCard(note) {
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
            onDismissRequest = { showAdd = false },
            title = { Text("新建笔记") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("标题") }, colors = fieldColors())
                    OutlinedTextField(content, { content = it }, Modifier.fillMaxWidth(), minLines = 5, label = { Text("内容") }, colors = fieldColors())
                    OutlinedTextField(tags, { tags = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("标签，用逗号分隔") }, colors = fieldColors())
                }
            },
            confirmButton = {
                TextButton(enabled = title.isNotBlank(), onClick = {
                    scope.launch {
                        try {
                            client.createNote(
                                NoteCreateRequest(
                                    title.trim(),
                                    content.trim(),
                                    tags.split(",").map { it.trim() }.filter { it.isNotBlank() },
                                ),
                            )
                            showAdd = false
                            reload()
                        } catch (e: Exception) {
                            error = friendlyError(e)
                        }
                    }
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun NoteCard(note: Note, onDelete: () -> Unit) {
    Panel(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(note.title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(5.dp))
                Text(note.content.ifBlank { "暂无内容" }, fontSize = 13.sp, color = Color(0xFFD4D4D4), maxLines = 3, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(8.dp))
                Text(note.tags.joinToString(" · ").ifBlank { relativeDate(note.updated_at) }, fontSize = 11.sp, color = greenLight)
            }
            TextButton(onClick = onDelete) { Text("删除", color = errorColor, fontSize = 11.sp) }
        }
    }
}
@Composable
private fun GithubScreen(client: ApiClient) {
    var repos by remember { mutableStateOf<List<GithubRepo>>(emptyList()) }
    var activity by remember { mutableStateOf<List<GithubActivity>>(emptyList()) }
    var heatmap by remember { mutableStateOf<GithubHeatmap?>(null) }
    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showFocus by remember { mutableStateOf(false) }
    var showStats by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun reload() {
        scope.launch {
            loading = true
            error = null
            try {
                repos = client.githubRepos()
                activity = client.githubActivity()
                heatmap = client.heatmap(84)
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

    LaunchedEffect(client) { reload() }

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
                item { GithubProfile(repos, activity) }
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
                        repos.take(8).forEach { RepoRow(it) }
                    }
                }
            }
        }
    }
}

@Composable
private fun GithubProfile(repos: List<GithubRepo>, activity: List<GithubActivity>) {
    Panel(Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
            Box(
                Modifier.size(58.dp).clip(RoundedCornerShape(29.dp)).background(Color(0xFF166534)),
                contentAlignment = Alignment.Center,
            ) { Text("L", fontSize = 22.sp, fontWeight = FontWeight.Bold) }
            Column(Modifier.weight(1f)) {
                Text("Liu Developer", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("GitHub 本地缓存", fontSize = 13.sp, color = muted)
                Text("关注个人项目进展与近期活动", fontSize = 12.sp, color = Color(0xFFD4D4D4))
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

@Composable
private fun RepoRow(repo: GithubRepo) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 9.dp),
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

@Composable
private fun SettingsScreen(
    host: String,
    port: String,
    onHostChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    displayName: String,
    githubUsername: String,
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
    val scope = rememberCoroutineScope()

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
                if (status != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(status ?: "", color = if (status == "连接成功") greenLight else errorColor, fontSize = 12.sp)
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
            }
        }
    }
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






