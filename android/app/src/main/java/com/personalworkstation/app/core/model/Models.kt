package com.personalworkstation.app.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class HealthResponse(val status: String, val service: String)

@Serializable
data class DashboardSummary(
    val tasks_total: Int = 0,
    val tasks_completed: Int = 0,
    val task_completion_rate: Double = 0.0,
    val notes_total: Int = 0,
    val github_commits_30d: Int = 0,
    val github_repositories: Int = 0,
)

@Serializable
data class GithubHeatmapItem(
    val date: String,
    val count: Int = 0,
)

@Serializable
data class GithubHeatmap(
    val days: Int = 0,
    val total: Int = 0,
    val items: List<GithubHeatmapItem> = emptyList(),
)

@Serializable
data class Note(
    val id: Int,
    val title: String,
    val content: String = "",
    val tags: List<String> = emptyList(),
    val created_at: String = "",
    val updated_at: String = "",
)

@Serializable
data class NoteCreateRequest(
    val title: String,
    val content: String = "",
    val tags: List<String> = emptyList(),
)

@Serializable
data class GithubRepo(
    val id: Int,
    val name: String,
    val owner: String,
    val html_url: String = "",
    val description: String = "",
    val language: String = "",
    val stars: Int = 0,
    val open_issues: Int = 0,
    val pushed_at: String? = null,
    val last_fetch_at: String = "",
)

@Serializable
data class GithubActivity(
    val id: Int,
    val type: String,
    val actor: String = "",
    val payload: Map<String, JsonElement> = emptyMap(),
    val created_at: String = "",
)

@Serializable
data class GithubRefreshResponse(
    val ok: Boolean = false,
    val fetched_repositories: Int = 0,
    val fetched_commits: Int = 0,
    val fetched_events: Int = 0,
    val message: String = "",
)

@Serializable
data class BoardColumn(
    val id: Int,
    val board_id: Int,
    val name: String,
    val position: Int,
    val tasks: List<Task> = emptyList(),
)

@Serializable
data class Task(
    val id: Int,
    val column_id: Int,
    val title: String,
    val description: String = "",
    val priority: String = "medium",
    val due_date: String? = null,
    val position: Int = 0,
    val created_at: String = "",
    val updated_at: String = "",
)

@Serializable
data class Board(
    val id: Int,
    val name: String,
    val position: Int = 0,
    val created_at: String = "",
)

@Serializable
data class TaskCreateRequest(
    val column_id: Int,
    val title: String,
    val description: String = "",
    val priority: String = "medium",
)

@Serializable
data class TaskUpdateRequest(
    val column_id: Int? = null,
    val title: String? = null,
    val description: String? = null,
    val priority: String? = null,
    val position: Int? = null,
)
