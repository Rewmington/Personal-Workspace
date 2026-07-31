package com.personalworkstation.app.core.network

import com.personalworkstation.app.core.model.Board
import com.personalworkstation.app.core.model.BoardColumn
import com.personalworkstation.app.core.model.DashboardSummary
import com.personalworkstation.app.core.model.GithubActivity
import com.personalworkstation.app.core.model.GithubHeatmap
import com.personalworkstation.app.core.model.GithubRefreshResponse
import com.personalworkstation.app.core.model.GithubRepo
import com.personalworkstation.app.core.model.GithubProfile
import com.personalworkstation.app.core.model.GithubSettingsStatus
import com.personalworkstation.app.core.model.Profile
import com.personalworkstation.app.core.model.ProfileUpdateRequest
import com.personalworkstation.app.core.model.BackupRestoreResponse
import com.personalworkstation.app.core.model.HealthResponse
import com.personalworkstation.app.core.model.Note
import com.personalworkstation.app.core.model.NoteCreateRequest
import com.personalworkstation.app.core.model.NoteUpdateRequest
import com.personalworkstation.app.core.model.BoardCreateRequest
import com.personalworkstation.app.core.model.ColumnCreateRequest
import com.personalworkstation.app.core.model.Snippet
import com.personalworkstation.app.core.model.SnippetCreateRequest
import com.personalworkstation.app.core.model.DevLog
import com.personalworkstation.app.core.model.DevLogCalendarDay
import com.personalworkstation.app.core.model.DevLogUpdateRequest
import com.personalworkstation.app.core.model.DevLogStreakResponse
import com.personalworkstation.app.core.model.Task
import com.personalworkstation.app.core.model.TaskCreateRequest
import com.personalworkstation.app.core.model.TaskUpdateRequest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.delete
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.min
import kotlin.random.Random

class ApiClient(host: String = "192.168.1.100", port: Int = 8080) {
    private val baseUrl = "http://$host:$port"
    private val client = HttpClient(Android) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        install(WebSockets)
    }

    suspend fun health(): HealthResponse = client.get("$baseUrl/api/health").body()
    suspend fun summary(): DashboardSummary = client.get("$baseUrl/api/dashboard/summary").body()
    suspend fun heatmap(days: Int = 84): GithubHeatmap =
        client.get("$baseUrl/api/dashboard/github-heatmap?days=$days").body()

    suspend fun boards(): List<Board> = client.get("$baseUrl/api/boards").body()
    suspend fun boardTasks(boardId: Int): List<BoardColumn> =
        client.get("$baseUrl/api/boards/$boardId/tasks").body()

    suspend fun notes(search: String? = null): List<Note> {
        val query = search?.takeIf { it.isNotBlank() }?.let { "?search=" + it } ?: ""
        return client.get("$baseUrl/api/notes$query").body()
    }

    suspend fun note(noteId: Int): Note = client.get("$baseUrl/api/notes/$noteId").body()

    suspend fun createNote(request: NoteCreateRequest): Note =
        client.post("$baseUrl/api/notes") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

    suspend fun updateNote(noteId: Int, request: NoteUpdateRequest): Note =
        client.put("$baseUrl/api/notes/$noteId") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

    suspend fun deleteNote(noteId: Int) {
        client.delete("$baseUrl/api/notes/$noteId")
    }

    suspend fun githubRepos(): List<GithubRepo> =
        client.get("$baseUrl/api/github/repos").body()

    suspend fun githubActivity(limit: Int = 20): List<GithubActivity> =
        client.get("$baseUrl/api/github/activity?limit=$limit").body()

    suspend fun refreshGithub(): GithubRefreshResponse =
        client.post("$baseUrl/api/github/refresh").body()

    suspend fun githubProfile(): GithubProfile =
        client.get("$baseUrl/api/github/profile").body()

    suspend fun githubSettings(): GithubSettingsStatus =
        client.get("$baseUrl/api/github/settings").body()

    suspend fun profile(): Profile = client.get("$baseUrl/api/profile").body()

    suspend fun updateProfile(request: ProfileUpdateRequest): Profile =
        client.put("$baseUrl/api/profile") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

    suspend fun exportJsonBackup(): ByteArray =
        client.post("$baseUrl/api/backup/export?format=json").body()

    suspend fun importJsonBackup(filename: String, bytes: ByteArray): BackupRestoreResponse =
        client.post("$baseUrl/api/backup/import?mode=merge") {
            setBody(MultiPartFormDataContent(formData {
                append("file", bytes, Headers.build {
                    append(HttpHeaders.ContentDisposition, "form-data; name=\"file\"; filename=\"$filename\"")
                    append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                })
            }))
        }.body()

    suspend fun createTask(request: TaskCreateRequest): Task =
        client.post("$baseUrl/api/tasks") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

    suspend fun updateTask(taskId: Int, request: TaskUpdateRequest): Task =
        client.put("$baseUrl/api/tasks/$taskId") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

    suspend fun deleteTask(taskId: Int) {
        client.delete("$baseUrl/api/tasks/$taskId")
    }

    suspend fun snippets(query: String? = null): List<Snippet> = client.get("$baseUrl/api/snippets" + (query?.takeIf { it.isNotBlank() }?.let { "?q=" + java.net.URLEncoder.encode(it, "UTF-8") } ?: "")).body()
    suspend fun createSnippet(request: SnippetCreateRequest): Snippet = client.post("$baseUrl/api/snippets") { contentType(ContentType.Application.Json); setBody(request) }.body()
    suspend fun deleteSnippet(id: Int) { client.delete("$baseUrl/api/snippets/$id") }
    suspend fun todayLog(): DevLog = client.get("$baseUrl/api/logs/today").body()
    suspend fun updateLog(id: Int, request: DevLogUpdateRequest): DevLog = client.put("$baseUrl/api/logs/$id") { contentType(ContentType.Application.Json); setBody(request) }.body()
    suspend fun logCalendar(year: Int, month: Int): List<DevLogCalendarDay> = client.get("$baseUrl/api/logs/calendar?year=$year&month=$month").body()
    suspend fun logByDate(date: String): DevLog = client.get("$baseUrl/api/logs?date=$date").body()
    suspend fun logStreak(): DevLogStreakResponse = client.get("$baseUrl/api/logs/streak").body()

    suspend fun createBoard(request: BoardCreateRequest): Board =
        client.post("$baseUrl/api/boards") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

    suspend fun createColumn(boardId: Int, request: ColumnCreateRequest): BoardColumn =
        client.post("$baseUrl/api/boards/$boardId/columns") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

    fun close() = client.close()

    suspend fun listenRealtime(onEvent: suspend () -> Unit, onStatus: (String) -> Unit) {
        val websocketUrl = baseUrl.replaceFirst("http://", "ws://").replaceFirst("https://", "wss://") + "/ws"
        var retryCount = 0
        var lastSeq = 0L
        while (currentCoroutineContext().isActive) {
            try {
                onStatus("reconnecting")
                client.webSocket(websocketUrl) {
                    onStatus("connected")
                    send(Frame.Text("{\"type\":\"sync_request\",\"last_seq\":$lastSeq}"))
                    for (frame in incoming) {
                        if (frame is Frame.Text) {
                            val text = frame.readText()
                            val json = runCatching { Json.parseToJsonElement(text).jsonObject }.getOrNull() ?: continue
                            json["seq"]?.toString()?.toLongOrNull()?.let { lastSeq = maxOf(lastSeq, it) }
                            when (json["type"]?.toString()?.trim('"')) {
                                "ping" -> send(Frame.Text("{\"type\":\"pong\"}"))
                                "sync_state", "task_created", "task_updated", "task_deleted", "note_created", "note_updated", "note_deleted", "snippet_created", "snippet_updated", "snippet_deleted", "board_created", "column_created" -> onEvent()
                            }
                        }
                    }
                }
                retryCount = 0
            } catch (_: Exception) {
                onStatus("reconnecting")
            }
            val delayMs = (min(30_000L, 1_000L * (1L shl retryCount.coerceAtMost(5))) * (0.75 + Random.nextDouble() * 0.5)).toLong()
            retryCount += 1
            delay(delayMs)
        }
    }
}
