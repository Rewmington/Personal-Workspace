# Android 视觉重做（indigo）+ 通知接收 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 Android 客户端从绿色系旧视觉换成与 Web 一致的 **indigo 蓝紫设计语言**（配色/主题/关键组件），并新增**接收服务端 `notify` 事件、弹系统通知**的能力（为"电脑通知→手机提醒"打通 Android 侧）。

**Architecture:** 改动集中在 `android/app/src/main/java/com/personalworkstation/app/MainActivity.kt`（主题常量 + darkColorScheme + 关键组件色 + 通知逻辑）、`core/network/ApiClient.kt`（`listenRealtime` 增加 `notify` 事件与回调）、`AndroidManifest.xml`（加 `POST_NOTIFICATIONS`）。视觉聚焦"统一 indigo 语言"而非逐行替换全部硬编码。

**Tech Stack:** Kotlin + Jetpack Compose（Material3）+ Ktor client（已含 WebSockets）+ framework NotificationManager。minSdk 26 / targetSdk 35。

**Spec:** `docs/superpowers/specs/2026-09-04-personal-workstation-beautify-design.md`（§2 视觉 + §3 通知接力 + §4 硬约束）

## Global Constraints

- **纯视觉 + 通知新增**，不改任何业务逻辑/API 契约；不破坏现有各 Screen 功能。
- indigo 配色逐字用 spec：`--accent #6E7BF2 / --accent-lt #98A5F8 / --bg #0B0D11 / --surface #14161C / --elevated #191C24 / --text #ECEEF2 / --text-2 #9AA3B0 / --text-3 #626A76 / --success #3ECF8E / --warning #E8A93D / --danger #EF6B6B`（Android 用 Color(0xFF…) 表示）。
- 卡片圆角统一 `RoundedCornerShape(16.dp)`；保留标签/胶囊圆角。
- **零云/局域网**：通知仅在手机连着工作台（同一 WiFi、App 运行）时收到；不加第三方推送。
- 通知渠道 id = `workstation_notify`，IMPORTANCE_HIGH；Android 13+ 运行时申请 `POST_NOTIFICATIONS`。
- `listenRealtime` 现有行为（reconnect/seq/ping）保持不变，仅为新增 `notify` 分支扩展。
- 验收：`gradlew assembleDebug` 编译通过（若本机无 Android SDK/JDK，则报告说明，视觉改动以代码层为准）；真机验收通知为最终确认。

---

### Task 1: 主题与配色 → indigo

**Files:**
- Modify: `android/app/src/main/java/com/personalworkstation/app/MainActivity.kt:143-168`（颜色常量 + cardShape + darkColorScheme）

**Interfaces:**
- Consumes: 无。
- Produces: `bg/panel/softPanel/green/greenLight/muted/dim/errorColor` 重新定义为 indigo 系；`colors = darkColorScheme(...)` 用 indigo。下游组件引用这些常量自动生效。

- [ ] **Step 1: 替换颜色常量（L143-151）**

把：
```kotlin
private val bg = Color(0xFF181818)
private val panel = Color(0xFF252525)
private val softPanel = Color(0xFF202020)
private val green = Color(0xFF22C55E)
private val greenLight = Color(0xFF4ADE80)
private val muted = Color(0xFF888888)
private val dim = Color(0xFF555555)
private val errorColor = Color(0xFFFF786D)
```
替换为：
```kotlin
private val bg = Color(0xFF0B0D11)
private val panel = Color(0xFF14161C)
private val softPanel = Color(0xFF191C24)
private val green = Color(0xFF6E7BF2)        // indigo accent（做 primary）
private val greenLight = Color(0xFF98A5F8)   // indigo light
private val muted = Color(0xFF9AA3B0)        // text-2
private val dim = Color(0xFF626A76)          // text-3
private val errorColor = Color(0xFFEF6B6B)   // danger
```
（保留变量名 `green/greenLight` 以免破坏下游引用；其语义变为 indigo。）

- [ ] **Step 2: 更新 darkColorScheme（L157-168）**

把 `darkColorScheme(primary = green, secondary = greenLight, background = bg, surface = panel, surfaceVariant = Color(0xFF303030), …)` 的 `surfaceVariant` 改为 `Color(0xFF24262E)`、`onSurfaceVariant` 改为 `Color(0xFFECEEF2)`（text），其余引用常量即可。

- [ ] **Step 3: 验证**

Run: `grep -n "0xFF181818\|0xFF22C55E\|0xFF4ADE80\|0xFFFF786D" android/app/src/main/java/com/personalworkstation/app/MainActivity.kt`
Expected: 无命中（旧绿系色值已清）。

- [ ] **Step 4: 提交**

```bash
git add android/app/src/main/java/com/personalworkstation/app/MainActivity.kt
git commit -m "style(android): 主题配色换 indigo"
```

---

### Task 2: 关键组件统一 indigo（导航/按钮/FAB/热力图/进度/心情）

**Files:**
- Modify: `MainActivity.kt`（导航 indicator/文字、FAB、FilterChip 选中、热力图 4 级、进度环、Button 颜色、mood 选中）

**Interfaces:**
- Consumes: Task 1 的 `green/greenLight/bg/panel`。
- Produces: 各 Screen 关键组件回到 indigo 色调。

- [ ] **Step 1: 底导航（L238-258）`NavigationBarItemDefaults.colors`**

把 `indicatorColor = Color(0x3322C55E)` → `Color(0x336E7BF2)`；`unselectedTextColor = dim` 保留；selected 用 `greenLight`（已=indigo light）。

- [ ] **Step 2: FAB（L228-236）** `containerColor = greenLight`（=indigo light）保留即可。

- [ ] **Step 3: 热力图梯度（L2440-2444）与 mood 选中（L1310-1345, L1424）中硬编码绿**

把 `Color(0xDD22C55E)/0x9922C55E/0x6622C55E/0x3322C55E/0xFF22C55E`（热力图 4 级 + 选中）替换为 indigo 4 级不透明度 `0xDD6E7BF2/0x996E7BF2/0x666E7BF2/0x336E7BF2`（或对应的 `green.copy(alpha=…)` 已用 alpha 的保留，只改 `green` 颜色本身）；`Color(0xFFF0BF63)`（mood 高亮黄）→ `Color(0xFFE8A93D)`。

- [ ] **Step 4: 进度环描边（L691 `drawArc` 的 `Color(0x22FFFFFF)` 等）** 保持中性即可；确认主描边用 `green`（已 indigo）。

- [ ] **Step 5: 验证**

Run: `grep -n "22C55E\|4ADE80\|F0BF63" android/app/src/main/java/com/personalworkstation/app/MainActivity.kt`
Expected: 0 命中（旧绿/黄已清，或仅剩已改为 indigo 的等价）。

- [ ] **Step 6: 提交**

```bash
git add android/app/src/main/java/com/personalworkstation/app/MainActivity.kt
git commit -m "style(android): 关键组件统一 indigo（导航/FAB/热力图/进度/心情）"
```

---

### Task 3: 接收 `notify` 事件 → 系统通知

**Files:**
- Modify: `android/app/src/main/java/com/personalworkstation/app/core/network/ApiClient.kt:192-222`（listenRealtime 增加 onNotify）
- Modify: `android/app/src/main/java/com/personalworkstation/app/MainActivity.kt`（通知渠道 + 权限 + onNotify→弹通知；`WorkstationApp` 里调用 `client.listenRealtime`）
- Modify: `android/app/src/main/AndroidManifest.xml`（加 `POST_NOTIFICATIONS`）

**Interfaces:**
- Consumes: `listenRealtime` 现有签名 `(onEvent: suspend () -> Unit, onStatus: (String) -> Unit)`。
- Produces: `listenRealtime(onEvent, onNotify: suspend (title: String, message: String, type: String) -> Unit, onStatus)`；MainActivity 用它弹通知。

- [ ] **Step 1: ApiClient.listenRealtime 增加 onNotify 与 notify 分支**

把 `fun` 声明与事件处理改为（其余不动，保持 reconnect/seq/ping）：
```kotlin
suspend fun listenRealtime(
    onEvent: suspend () -> Unit,
    onStatus: (String) -> Unit,
    onNotify: suspend (title: String, message: String, type: String) -> Unit = { _, _, _ -> },
) {
    ...
    when (json["type"]?.toString()?.trim('"')) {
        "ping" -> send(Frame.Text("{\"type\":\"pong\"}"))
        "notify" -> {
            val data = json["data"]?.jsonObject
            onNotify(
                data?.get("title")?.primitive?.content ?: "通知",
                data?.get("message")?.primitive?.content ?: "",
                data?.get("type")?.primitive?.content ?: "info",
            )
        }
        else -> { /* 现有 sync_state/task_*/...*/note_*/sequence */ 
            // 仅对既有业务事件调用 onEvent；未知类型忽略
        }
    }
    ...
}
```
具体做法：保留原事件列表在 `else` 里判 `onEvent()`（移到 `isEvent` 判断），新增 `"notify"` 分支。确保 `import kotlinx.serialization.json.jsonObject`、`.primitive` 已可用（当前已 import jsonObject；`primitive` 是 JsonPrimitive 的扩展，需引入，或改用 `data?.get("title")?.toString()?.trim('"')` 简化）。

- [ ] **Step 2: MainActivity 通知渠道 + 权限**

在 `MainActivity` 内的 `WorkstationApp()` 顶部（或 onCreate）：
```kotlin
// 通知渠道（幂等）
val channelId = "workstation_notify"
val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
    nm.createNotificationChannel(NotificationChannel(channelId, "工作台提醒", NotificationManager.IMPORTANCE_HIGH))
}
// Android 13+ 运行时申请 POST_NOTIFICATIONS
val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
LaunchedEffect(Unit) {
    if (Build.VERSION.SDK_INT >= 33 &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
        notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
```
（需 import `android.app.NotificationChannel/NotificationManager`、`android.os.Build`、`androidx.core.content.ContextCompat`、`android.Manifest`、`android.content.pm.PackageManager`；`context` 已有 `LocalContext.current`。）

- [ ] **Step 3: 监听里接上 onNotify → 弹通知**

把现有 `client.listenRealtime(onEvent = { realtimeRevision += 1 }, onStatus = { realtimeStatus = it })`（L220-224）改为：
```kotlin
client.listenRealtime(
    onEvent = { realtimeRevision += 1 },
    onStatus = { realtimeStatus = it },
    onNotify = { title, message, _ ->
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return@listenRealtime
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val pi = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        val notif = Notification.Builder(context, channelId)
            .setContentTitle(title).setContentText(message).setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pi).setAutoCancel(true).build()
        nm.notify(System.currentTimeMillis().toInt(), notif)
    },
)
```
（`nm`、`channelId` 用 Step 2 定义的；需 `android.app.Notification`、`android.app.PendingIntent`。）

- [ ] **Step 4: Manifest 加权限**

在 `<manifest>` 下、`<uses-permission android:name="android.permission.INTERNET" />` 附近加：
```xml
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```
（`androidx.core:core-ktx` 若未在依赖里，`ContextCompat` 需补依赖 `implementation("androidx.core:core-ktx:1.13.1")`，否则改用 `requireActivity().checkSelfPermission`。）

- [ ] **Step 5: 验证**

Run: `grep -n "POST_NOTIFICATIONS\|workstation_notify\|onNotify" android/app/src/main/AndroidManifest.xml android/app/src/main/java/com/personalworkstation/app/MainActivity.kt android/app/src/main/java/com/personalworkstation/app/core/network/ApiClient.kt`
Expected: 三者均有命中。

- [ ] **Step 6: 提交**

```bash
git add android/
git commit -m "feat(android): 接收 notify 事件弹系统通知 + 通知权限"
```

---

### Task 4: 编译验证

**Files:** 无（验证）

- [ ] **Step 1: 编译**

Run: `cd android && ./gradlew assembleDebug`（需本机 JDK17 + Android SDK。若报"SDK 未找到/Java 版本不符"，如实报告：Android 视觉/通知改动以代码层为准，编译待有 SDK 时验证。）
Expected: BUILD SUCCESSFUL；或在无 SDK 环境给出说明。

- [ ] **Step 2: 提交（若有 micro 调整）**

```bash
git add -A && git commit -m "fix(android): 编译调整"
```
