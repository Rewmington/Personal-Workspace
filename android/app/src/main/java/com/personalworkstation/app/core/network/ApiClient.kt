package com.personalworkstation.app.core.network

import com.personalworkstation.app.core.model.Board
import com.personalworkstation.app.core.model.BoardColumn
import com.personalworkstation.app.core.model.DashboardSummary
import com.personalworkstation.app.core.model.GithubActivity
import com.personalworkstation.app.core.model.GithubHeatmap
import com.personalworkstation.app.core.model.GithubRefreshResponse
import com.personalworkstation.app.core.model.GithubRepo
import com.personalworkstation.app.core.model.HealthResponse
import com.personalworkstation.app.core.model.Note
import com.personalworkstation.app.core.model.NoteCreateRequest
import com.personalworkstation.app.core.model.Task
import com.personalworkstation.app.core.model.TaskCreateRequest
import com.personalworkstation.app.core.model.TaskUpdateRequest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

class ApiClient(host: String = "192.168.1.100", port: Int = 8080) {
    private val baseUrl = "http://$host:$port"
    private val client = HttpClient(Android) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
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

    suspend fun createNote(request: NoteCreateRequest): Note =
        client.post("$baseUrl/api/notes") {
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

    fun close() = client.close()
}
