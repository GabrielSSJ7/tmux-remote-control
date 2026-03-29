# Android App -- Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a native Android app (Kotlin + Jetpack Compose) that connects to the Rust backend, renders terminal sessions in real-time, and manages a command library.

**Architecture:** Single-activity Compose app with Navigation. Retrofit for REST, OkHttp for WebSocket. Custom terminal emulator (grid buffer + ANSI parser) rendered on Compose Canvas. DataStore for local preferences.

**Tech Stack:** Kotlin, Jetpack Compose, Material3, Retrofit, OkHttp, DataStore, JUnit, MockK

---

## File Structure

```
android/
├── build.gradle.kts                          (project-level)
├── settings.gradle.kts
├── gradle.properties
├── app/
│   ├── build.gradle.kts
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   └── kotlin/com/remotecontrol/
│       │       ├── App.kt
│       │       ├── MainActivity.kt
│       │       ├── navigation/
│       │       │   └── NavGraph.kt
│       │       ├── data/
│       │       │   ├── model/
│       │       │   │   ├── Session.kt
│       │       │   │   └── Command.kt
│       │       │   ├── api/
│       │       │   │   ├── ApiClient.kt
│       │       │   │   ├── SessionsApi.kt
│       │       │   │   └── CommandsApi.kt
│       │       │   ├── websocket/
│       │       │   │   ├── Protocol.kt
│       │       │   │   └── TerminalSocket.kt
│       │       │   └── settings/
│       │       │       └── SettingsStore.kt
│       │       ├── terminal/
│       │       │   ├── TerminalEmulator.kt
│       │       │   └── TerminalRenderer.kt
│       │       ├── ui/
│       │       │   ├── theme/
│       │       │   │   └── Theme.kt
│       │       │   ├── sessions/
│       │       │   │   ├── SessionsScreen.kt
│       │       │   │   └── SessionsViewModel.kt
│       │       │   ├── terminal/
│       │       │   │   ├── TerminalScreen.kt
│       │       │   │   ├── TerminalViewModel.kt
│       │       │   │   └── ExtraKeysBar.kt
│       │       │   ├── commands/
│       │       │   │   ├── CommandsSheet.kt
│       │       │   │   └── CommandsViewModel.kt
│       │       │   └── settings/
│       │       │       ├── SettingsScreen.kt
│       │       │       └── SettingsViewModel.kt
│       │       └── util/
│       │           └── Reconnector.kt
│       └── test/
│           └── kotlin/com/remotecontrol/
│               ├── data/websocket/ProtocolTest.kt
│               ├── terminal/TerminalEmulatorTest.kt
│               ├── ui/sessions/SessionsViewModelTest.kt
│               ├── ui/commands/CommandsViewModelTest.kt
│               └── util/ReconnectorTest.kt
```

---

### Task 1: Project Scaffold

**Files:**
- Create: `android/settings.gradle.kts`
- Create: `android/build.gradle.kts`
- Create: `android/gradle.properties`
- Create: `android/app/build.gradle.kts`
- Create: `android/app/src/main/AndroidManifest.xml`
- Create: `android/app/src/main/kotlin/com/remotecontrol/App.kt`
- Create: `android/app/src/main/kotlin/com/remotecontrol/MainActivity.kt`

- [ ] **Step 1: Create settings.gradle.kts**

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "RemoteControl"
include(":app")
```

- [ ] **Step 2: Create project-level build.gradle.kts**

```kotlin
plugins {
    id("com.android.application") version "8.2.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false
}
```

- [ ] **Step 3: Create gradle.properties**

```properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
android.nonTransitiveRClass=true
```

- [ ] **Step 4: Create app/build.gradle.kts**

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.remotecontrol"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.remotecontrol"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.10"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.02.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")

    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.google.code.gson:gson:2.10.1")

    implementation("androidx.datastore:datastore-preferences:1.0.0")
    implementation("androidx.core:core-ktx:1.12.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.0")
    testImplementation("io.mockk:mockk:1.13.9")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
```

- [ ] **Step 5: Create AndroidManifest.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.INTERNET" />

    <application
        android:name=".App"
        android:allowBackup="false"
        android:label="Remote Control"
        android:supportsRtl="true"
        android:theme="@style/Theme.Material3.DayNight.NoActionBar">
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:windowSoftInputMode="adjustResize">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

- [ ] **Step 6: Create App.kt and MainActivity.kt**

`App.kt`:

```kotlin
package com.remotecontrol

import android.app.Application

class App : Application()
```

`MainActivity.kt`:

```kotlin
package com.remotecontrol

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Text("Remote Control")
        }
    }
}
```

- [ ] **Step 7: Initialize Gradle wrapper and verify build**

Run:
```bash
cd android && gradle wrapper --gradle-version 8.5 && ./gradlew assembleDebug
```

- [ ] **Step 8: Commit**

```bash
git add android/ && git commit -m "feat(android): project scaffold with Compose dependencies"
```

---

### Task 2: Data Models

**Files:**
- Create: `android/app/src/main/kotlin/com/remotecontrol/data/model/Session.kt`
- Create: `android/app/src/main/kotlin/com/remotecontrol/data/model/Command.kt`

- [ ] **Step 1: Create Session.kt**

```kotlin
package com.remotecontrol.data.model

data class Session(
    val id: String,
    val name: String,
    val createdAt: String,
    val attached: Boolean,
)
```

- [ ] **Step 2: Create Command.kt**

```kotlin
package com.remotecontrol.data.model

data class Command(
    val id: String,
    val name: String,
    val command: String,
    val description: String?,
    val category: String,
    val icon: String?,
    val createdAt: String,
    val updatedAt: String,
)

data class CreateCommand(
    val name: String,
    val command: String,
    val description: String? = null,
    val category: String,
    val icon: String? = null,
)

data class UpdateCommand(
    val name: String? = null,
    val command: String? = null,
    val description: String? = null,
    val category: String? = null,
    val icon: String? = null,
)
```

- [ ] **Step 3: Verify build**

Run: `cd android && ./gradlew compileDebugKotlin`

- [ ] **Step 4: Commit**

```bash
git add android/ && git commit -m "feat(android): data models for Session and Command"
```

---

### Task 3: Binary Protocol

**Files:**
- Create: `android/app/src/main/kotlin/com/remotecontrol/data/websocket/Protocol.kt`
- Create: `android/app/src/test/kotlin/com/remotecontrol/data/websocket/ProtocolTest.kt`

- [ ] **Step 1: Write Protocol tests first**

```kotlin
package com.remotecontrol.data.websocket

import org.junit.Assert.*
import org.junit.Test

class ProtocolTest {

    @Test
    fun dataFrameRoundtrip() {
        val frame = Frame.Data("hello terminal".toByteArray())
        val encoded = frame.encode()
        assertEquals(0x00.toByte(), encoded[0])
        val decoded = Frame.decode(encoded)
        assertTrue(decoded is Frame.Data)
        assertArrayEquals("hello terminal".toByteArray(), (decoded as Frame.Data).payload)
    }

    @Test
    fun resizeFrameRoundtrip() {
        val frame = Frame.Resize(cols = 120, rows = 40)
        val encoded = frame.encode()
        assertEquals(0x01.toByte(), encoded[0])
        assertEquals(5, encoded.size)
        val decoded = Frame.decode(encoded)
        assertTrue(decoded is Frame.Resize)
        assertEquals(120, (decoded as Frame.Resize).cols)
        assertEquals(40, decoded.rows)
    }

    @Test
    fun pingPongRoundtrip() {
        assertEquals(Frame.Ping, Frame.decode(Frame.Ping.encode()))
        assertEquals(Frame.Pong, Frame.decode(Frame.Pong.encode()))
    }

    @Test
    fun sessionEventRoundtrip() {
        val frame = Frame.SessionEvent(eventType = "ended", sessionId = "rc-abc")
        val encoded = frame.encode()
        assertEquals(0x04.toByte(), encoded[0])
        val decoded = Frame.decode(encoded) as Frame.SessionEvent
        assertEquals("ended", decoded.eventType)
        assertEquals("rc-abc", decoded.sessionId)
    }

    @Test(expected = IllegalArgumentException::class)
    fun emptyFrameThrows() {
        Frame.decode(byteArrayOf())
    }

    @Test(expected = IllegalArgumentException::class)
    fun unknownTypeThrows() {
        Frame.decode(byteArrayOf(0xFF.toByte()))
    }

    @Test(expected = IllegalArgumentException::class)
    fun resizeTooShortThrows() {
        Frame.decode(byteArrayOf(0x01, 0x00))
    }
}
```

- [ ] **Step 2: Run tests to see them fail**

Run: `cd android && ./gradlew test --tests "com.remotecontrol.data.websocket.ProtocolTest"`
Expected: FAIL (class not found)

- [ ] **Step 3: Implement Protocol.kt**

```kotlin
package com.remotecontrol.data.websocket

import com.google.gson.Gson
import java.nio.ByteBuffer

sealed class Frame {
    data class Data(val payload: ByteArray) : Frame() {
        override fun equals(other: Any?) = other is Data && payload.contentEquals(other.payload)
        override fun hashCode() = payload.contentHashCode()
    }

    data class Resize(val cols: Int, val rows: Int) : Frame()
    data object Ping : Frame()
    data object Pong : Frame()
    data class SessionEvent(val eventType: String, val sessionId: String) : Frame()

    fun encode(): ByteArray = when (this) {
        is Data -> byteArrayOf(TYPE_DATA) + payload
        is Resize -> {
            val buf = ByteBuffer.allocate(5)
            buf.put(TYPE_RESIZE)
            buf.putShort(cols.toShort())
            buf.putShort(rows.toShort())
            buf.array()
        }
        is Ping -> byteArrayOf(TYPE_PING)
        is Pong -> byteArrayOf(TYPE_PONG)
        is SessionEvent -> {
            val json = gson.toJson(mapOf("event_type" to eventType, "session_id" to sessionId))
            byteArrayOf(TYPE_SESSION_EVENT) + json.toByteArray()
        }
    }

    companion object {
        private const val TYPE_DATA: Byte = 0x00
        private const val TYPE_RESIZE: Byte = 0x01
        private const val TYPE_PING: Byte = 0x02
        private const val TYPE_PONG: Byte = 0x03
        private const val TYPE_SESSION_EVENT: Byte = 0x04
        private val gson = Gson()

        fun decode(data: ByteArray): Frame {
            require(data.isNotEmpty()) { "Empty frame" }
            return when (data[0]) {
                TYPE_DATA -> Data(data.copyOfRange(1, data.size))
                TYPE_RESIZE -> {
                    require(data.size >= 5) { "Resize frame too short" }
                    val buf = ByteBuffer.wrap(data, 1, 4)
                    Resize(cols = buf.short.toInt() and 0xFFFF, rows = buf.short.toInt() and 0xFFFF)
                }
                TYPE_PING -> Ping
                TYPE_PONG -> Pong
                TYPE_SESSION_EVENT -> {
                    val json = String(data, 1, data.size - 1)
                    @Suppress("UNCHECKED_CAST")
                    val map = gson.fromJson(json, Map::class.java) as Map<String, String>
                    SessionEvent(
                        eventType = map["event_type"] ?: "",
                        sessionId = map["session_id"] ?: "",
                    )
                }
                else -> throw IllegalArgumentException("Unknown frame type: 0x${String.format("%02x", data[0])}")
            }
        }
    }
}
```

- [ ] **Step 4: Run tests**

Run: `cd android && ./gradlew test --tests "com.remotecontrol.data.websocket.ProtocolTest"`
Expected: PASS (7 tests)

- [ ] **Step 5: Commit**

```bash
git add android/ && git commit -m "feat(android): binary WebSocket protocol encode/decode"
```

---

### Task 4: Reconnector

**Files:**
- Create: `android/app/src/main/kotlin/com/remotecontrol/util/Reconnector.kt`
- Create: `android/app/src/test/kotlin/com/remotecontrol/util/ReconnectorTest.kt`

- [ ] **Step 1: Write tests first**

```kotlin
package com.remotecontrol.util

import org.junit.Assert.*
import org.junit.Test

class ReconnectorTest {

    @Test
    fun firstDelayIsInitial() {
        val r = Reconnector(initialDelayMs = 1000, maxDelayMs = 30000)
        assertEquals(1000L, r.nextDelay())
    }

    @Test
    fun delayDoublesEachAttempt() {
        val r = Reconnector(initialDelayMs = 1000, maxDelayMs = 30000)
        assertEquals(1000L, r.nextDelay())
        assertEquals(2000L, r.nextDelay())
        assertEquals(4000L, r.nextDelay())
        assertEquals(8000L, r.nextDelay())
    }

    @Test
    fun delayCapAtMax() {
        val r = Reconnector(initialDelayMs = 1000, maxDelayMs = 5000)
        r.nextDelay()
        r.nextDelay()
        r.nextDelay()
        r.nextDelay()
        assertEquals(5000L, r.nextDelay())
    }

    @Test
    fun resetRestartsSequence() {
        val r = Reconnector(initialDelayMs = 1000, maxDelayMs = 30000)
        r.nextDelay()
        r.nextDelay()
        r.reset()
        assertEquals(1000L, r.nextDelay())
    }

    @Test
    fun attemptCountIncrements() {
        val r = Reconnector(initialDelayMs = 1000, maxDelayMs = 30000)
        assertEquals(0, r.attempts)
        r.nextDelay()
        assertEquals(1, r.attempts)
        r.nextDelay()
        assertEquals(2, r.attempts)
    }
}
```

- [ ] **Step 2: Implement Reconnector.kt**

```kotlin
package com.remotecontrol.util

import kotlin.math.min

class Reconnector(
    private val initialDelayMs: Long = 1000,
    private val maxDelayMs: Long = 30000,
) {
    var attempts: Int = 0
        private set

    fun nextDelay(): Long {
        val delay = min(initialDelayMs * (1L shl attempts), maxDelayMs)
        attempts++
        return delay
    }

    fun reset() {
        attempts = 0
    }
}
```

- [ ] **Step 3: Run tests**

Run: `cd android && ./gradlew test --tests "com.remotecontrol.util.ReconnectorTest"`
Expected: PASS (5 tests)

- [ ] **Step 4: Commit**

```bash
git add android/ && git commit -m "feat(android): exponential backoff reconnector"
```

---

### Task 5: API Client

**Files:**
- Create: `android/app/src/main/kotlin/com/remotecontrol/data/api/ApiClient.kt`
- Create: `android/app/src/main/kotlin/com/remotecontrol/data/api/SessionsApi.kt`
- Create: `android/app/src/main/kotlin/com/remotecontrol/data/api/CommandsApi.kt`

- [ ] **Step 1: Create SessionsApi interface**

```kotlin
package com.remotecontrol.data.api

import com.remotecontrol.data.model.Session
import retrofit2.http.*

interface SessionsApi {
    @GET("sessions")
    suspend fun list(): List<Session>

    @POST("sessions")
    suspend fun create(@Body body: Map<String, String?>): Session

    @DELETE("sessions/{id}")
    suspend fun delete(@Path("id") id: String)

    @GET("sessions/{id}/status")
    suspend fun status(@Path("id") id: String): Session

    @POST("sessions/{id}/exec")
    suspend fun exec(@Path("id") id: String, @Body body: Map<String, String>)
}
```

- [ ] **Step 2: Create CommandsApi interface**

```kotlin
package com.remotecontrol.data.api

import com.remotecontrol.data.model.Command
import com.remotecontrol.data.model.CreateCommand
import com.remotecontrol.data.model.UpdateCommand
import retrofit2.http.*

interface CommandsApi {
    @GET("commands")
    suspend fun list(): List<Command>

    @POST("commands")
    suspend fun create(@Body body: CreateCommand): Command

    @PUT("commands/{id}")
    suspend fun update(@Path("id") id: String, @Body body: UpdateCommand): Command

    @DELETE("commands/{id}")
    suspend fun delete(@Path("id") id: String)
}
```

- [ ] **Step 3: Create ApiClient.kt**

```kotlin
package com.remotecontrol.data.api

import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class ApiClient(
    private val baseUrl: String,
    private val token: String,
) {
    private val gson = GsonBuilder()
        .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
        .create()

    private val authInterceptor = Interceptor { chain ->
        val request = chain.request().newBuilder()
            .addHeader("Authorization", "Bearer $token")
            .build()
        chain.proceed(request)
    }

    private val client = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()

    val sessions: SessionsApi = retrofit.create(SessionsApi::class.java)
    val commands: CommandsApi = retrofit.create(CommandsApi::class.java)

    fun okHttpClient(): OkHttpClient = client
}
```

- [ ] **Step 4: Verify build**

Run: `cd android && ./gradlew compileDebugKotlin`
Expected: compiles

- [ ] **Step 5: Commit**

```bash
git add android/ && git commit -m "feat(android): Retrofit API client with auth interceptor"
```

---

### Task 6: WebSocket Manager

**Files:**
- Create: `android/app/src/main/kotlin/com/remotecontrol/data/websocket/TerminalSocket.kt`

- [ ] **Step 1: Implement TerminalSocket.kt**

```kotlin
package com.remotecontrol.data.websocket

import com.remotecontrol.util.Reconnector
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.*
import okio.ByteString
import okio.ByteString.Companion.toByteString

enum class ConnectionState { DISCONNECTED, CONNECTING, CONNECTED }

class TerminalSocket(
    private val client: OkHttpClient,
    private val scope: CoroutineScope,
) {
    private var webSocket: WebSocket? = null
    private val reconnector = Reconnector()
    private var reconnectJob: Job? = null
    private var pingJob: Job? = null
    private var consecutivePingFailures = 0

    private val _state = MutableStateFlow(ConnectionState.DISCONNECTED)
    val state: StateFlow<ConnectionState> = _state

    private val _incoming = MutableSharedFlow<Frame>(extraBufferCapacity = 256)
    val incoming: SharedFlow<Frame> = _incoming

    private var currentUrl: String? = null

    fun connect(baseUrl: String, sessionId: String, token: String) {
        disconnect()
        val wsUrl = baseUrl.replace("http", "ws") + "sessions/$sessionId/terminal?token=$token"
        currentUrl = wsUrl
        reconnector.reset()
        doConnect(wsUrl)
    }

    private fun doConnect(url: String) {
        _state.value = ConnectionState.CONNECTING
        val request = Request.Builder().url(url).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                _state.value = ConnectionState.CONNECTED
                reconnector.reset()
                consecutivePingFailures = 0
                startPing()
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                try {
                    val frame = Frame.decode(bytes.toByteArray())
                    if (frame is Frame.Pong) {
                        consecutivePingFailures = 0
                        return
                    }
                    _incoming.tryEmit(frame)
                } catch (_: Exception) {}
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                _state.value = ConnectionState.DISCONNECTED
                stopPing()
                scheduleReconnect()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                _state.value = ConnectionState.DISCONNECTED
                stopPing()
            }
        })
    }

    fun send(frame: Frame) {
        webSocket?.send(frame.encode().toByteString())
    }

    fun disconnect() {
        reconnectJob?.cancel()
        stopPing()
        webSocket?.close(1000, "bye")
        webSocket = null
        _state.value = ConnectionState.DISCONNECTED
    }

    private fun startPing() {
        pingJob = scope.launch {
            while (isActive) {
                delay(15_000)
                send(Frame.Ping)
                consecutivePingFailures++
                if (consecutivePingFailures >= 3) {
                    webSocket?.cancel()
                    break
                }
            }
        }
    }

    private fun stopPing() {
        pingJob?.cancel()
        pingJob = null
    }

    private fun scheduleReconnect() {
        val url = currentUrl ?: return
        reconnectJob = scope.launch {
            delay(reconnector.nextDelay())
            doConnect(url)
        }
    }
}
```

- [ ] **Step 2: Verify build**

Run: `cd android && ./gradlew compileDebugKotlin`

- [ ] **Step 3: Commit**

```bash
git add android/ && git commit -m "feat(android): WebSocket manager with auto-reconnect and ping keepalive"
```

---

### Task 7: Settings Store

**Files:**
- Create: `android/app/src/main/kotlin/com/remotecontrol/data/settings/SettingsStore.kt`

- [ ] **Step 1: Implement SettingsStore.kt**

```kotlin
package com.remotecontrol.data.settings

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsStore(context: Context) {
    private val store = context.dataStore

    val serverUrl: Flow<String> = store.data.map { it[KEY_SERVER_URL] ?: "" }
    val token: Flow<String> = store.data.map { it[KEY_TOKEN] ?: "" }
    val fontSize: Flow<Int> = store.data.map { it[KEY_FONT_SIZE] ?: 14 }
    val darkMode: Flow<Boolean> = store.data.map { it[KEY_DARK_MODE] ?: true }
    val scrollbackLines: Flow<Int> = store.data.map { it[KEY_SCROLLBACK] ?: 10000 }

    suspend fun setServerUrl(url: String) {
        store.edit { it[KEY_SERVER_URL] = url }
    }

    suspend fun setToken(token: String) {
        store.edit { it[KEY_TOKEN] = token }
    }

    suspend fun setFontSize(size: Int) {
        store.edit { it[KEY_FONT_SIZE] = size }
    }

    suspend fun setDarkMode(dark: Boolean) {
        store.edit { it[KEY_DARK_MODE] = dark }
    }

    suspend fun setScrollbackLines(lines: Int) {
        store.edit { it[KEY_SCROLLBACK] = lines }
    }

    companion object {
        private val KEY_SERVER_URL = stringPreferencesKey("server_url")
        private val KEY_TOKEN = stringPreferencesKey("token")
        private val KEY_FONT_SIZE = intPreferencesKey("font_size")
        private val KEY_DARK_MODE = booleanPreferencesKey("dark_mode")
        private val KEY_SCROLLBACK = intPreferencesKey("scrollback_lines")
    }
}
```

- [ ] **Step 2: Verify build**

Run: `cd android && ./gradlew compileDebugKotlin`

- [ ] **Step 3: Commit**

```bash
git add android/ && git commit -m "feat(android): DataStore settings for server, theme, and terminal prefs"
```

---

### Task 8: Theme + Navigation + MainActivity

**Files:**
- Create: `android/app/src/main/kotlin/com/remotecontrol/ui/theme/Theme.kt`
- Create: `android/app/src/main/kotlin/com/remotecontrol/navigation/NavGraph.kt`
- Modify: `android/app/src/main/kotlin/com/remotecontrol/MainActivity.kt`
- Modify: `android/app/src/main/kotlin/com/remotecontrol/App.kt`

- [ ] **Step 1: Create Theme.kt**

```kotlin
package com.remotecontrol.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFF80CBC4),
    surface = androidx.compose.ui.graphics.Color(0xFF1E1E1E),
    background = androidx.compose.ui.graphics.Color(0xFF121212),
    onSurface = androidx.compose.ui.graphics.Color(0xFFE0E0E0),
)

private val LightColorScheme = lightColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFF00897B),
    surface = androidx.compose.ui.graphics.Color(0xFFFAFAFA),
    background = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
    onSurface = androidx.compose.ui.graphics.Color(0xFF212121),
)

@Composable
fun RemoteControlTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        content = content,
    )
}
```

- [ ] **Step 2: Create NavGraph.kt**

```kotlin
package com.remotecontrol.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.remotecontrol.App
import com.remotecontrol.ui.sessions.SessionsScreen
import com.remotecontrol.ui.settings.SettingsScreen
import com.remotecontrol.ui.terminal.TerminalScreen

@Composable
fun NavGraph(app: App) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "sessions") {
        composable("sessions") {
            SessionsScreen(
                app = app,
                onSessionClick = { sessionName ->
                    navController.navigate("terminal/$sessionName")
                },
                onSettingsClick = { navController.navigate("settings") },
            )
        }
        composable(
            route = "terminal/{sessionId}",
            arguments = listOf(navArgument("sessionId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getString("sessionId") ?: return@composable
            TerminalScreen(
                app = app,
                sessionId = sessionId,
                onBack = { navController.popBackStack() },
            )
        }
        composable("settings") {
            SettingsScreen(
                app = app,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
```

- [ ] **Step 3: Update App.kt with dependencies**

```kotlin
package com.remotecontrol

import android.app.Application
import com.remotecontrol.data.api.ApiClient
import com.remotecontrol.data.settings.SettingsStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class App : Application() {
    lateinit var settings: SettingsStore
        private set
    var apiClient: ApiClient? = null
        private set

    override fun onCreate() {
        super.onCreate()
        settings = SettingsStore(this)
    }

    fun updateApiClient(serverUrl: String, token: String) {
        if (serverUrl.isNotBlank() && token.isNotBlank()) {
            apiClient = ApiClient(baseUrl = serverUrl, token = token)
        }
    }
}
```

- [ ] **Step 4: Update MainActivity.kt**

```kotlin
package com.remotecontrol

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.remotecontrol.navigation.NavGraph
import com.remotecontrol.ui.theme.RemoteControlTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as App
        setContent {
            val darkMode by app.settings.darkMode.collectAsState(initial = true)
            RemoteControlTheme(darkTheme = darkMode) {
                NavGraph(app = app)
            }
        }
    }
}
```

- [ ] **Step 5: Create stub screens so navigation compiles**

Create `android/app/src/main/kotlin/com/remotecontrol/ui/sessions/SessionsScreen.kt`:

```kotlin
package com.remotecontrol.ui.sessions

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.remotecontrol.App

@Composable
fun SessionsScreen(app: App, onSessionClick: (String) -> Unit, onSettingsClick: () -> Unit) {
    Text("Sessions")
}
```

Create `android/app/src/main/kotlin/com/remotecontrol/ui/terminal/TerminalScreen.kt`:

```kotlin
package com.remotecontrol.ui.terminal

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.remotecontrol.App

@Composable
fun TerminalScreen(app: App, sessionId: String, onBack: () -> Unit) {
    Text("Terminal: $sessionId")
}
```

Create `android/app/src/main/kotlin/com/remotecontrol/ui/settings/SettingsScreen.kt`:

```kotlin
package com.remotecontrol.ui.settings

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.remotecontrol.App

@Composable
fun SettingsScreen(app: App, onBack: () -> Unit) {
    Text("Settings")
}
```

- [ ] **Step 6: Verify build**

Run: `cd android && ./gradlew compileDebugKotlin`

- [ ] **Step 7: Commit**

```bash
git add android/ && git commit -m "feat(android): theme, navigation, and activity wiring"
```

---

### Task 9: Sessions Screen

**Files:**
- Create: `android/app/src/main/kotlin/com/remotecontrol/ui/sessions/SessionsViewModel.kt`
- Modify: `android/app/src/main/kotlin/com/remotecontrol/ui/sessions/SessionsScreen.kt`
- Create: `android/app/src/test/kotlin/com/remotecontrol/ui/sessions/SessionsViewModelTest.kt`

- [ ] **Step 1: Write ViewModel tests**

```kotlin
package com.remotecontrol.ui.sessions

import com.remotecontrol.data.api.SessionsApi
import com.remotecontrol.data.model.Session
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val api = mockk<SessionsApi>()

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun loadSessionsFetchesFromApi() = runTest {
        val sessions = listOf(
            Session(id = "$1", name = "main", createdAt = "2024-01-01", attached = false)
        )
        coEvery { api.list() } returns sessions

        val vm = SessionsViewModel(api)
        vm.loadSessions()
        advanceUntilIdle()

        assertEquals(sessions, vm.sessions.value)
        assertFalse(vm.isLoading.value)
    }

    @Test
    fun loadSessionsSetsErrorOnFailure() = runTest {
        coEvery { api.list() } throws RuntimeException("Network error")

        val vm = SessionsViewModel(api)
        vm.loadSessions()
        advanceUntilIdle()

        assertNotNull(vm.error.value)
        assertTrue(vm.error.value!!.contains("Network error"))
    }

    @Test
    fun createSessionCallsApiAndReloads() = runTest {
        coEvery { api.list() } returns emptyList()
        coEvery { api.create(any()) } returns Session("$2", "rc-abc", "2024-01-01", false)

        val vm = SessionsViewModel(api)
        vm.createSession()
        advanceUntilIdle()

        coVerify { api.create(any()) }
        coVerify(atLeast = 1) { api.list() }
    }

    @Test
    fun deleteSessionCallsApiAndReloads() = runTest {
        coEvery { api.list() } returns emptyList()
        coEvery { api.delete("main") } returns Unit

        val vm = SessionsViewModel(api)
        vm.deleteSession("main")
        advanceUntilIdle()

        coVerify { api.delete("main") }
    }
}
```

- [ ] **Step 2: Implement SessionsViewModel.kt**

```kotlin
package com.remotecontrol.ui.sessions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.remotecontrol.data.api.SessionsApi
import com.remotecontrol.data.model.Session
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SessionsViewModel(private val api: SessionsApi) : ViewModel() {
    private val _sessions = MutableStateFlow<List<Session>>(emptyList())
    val sessions: StateFlow<List<Session>> = _sessions

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun loadSessions() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                _sessions.value = api.list()
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to load sessions"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun createSession(name: String? = null) {
        viewModelScope.launch {
            try {
                api.create(mapOf("name" to name))
                loadSessions()
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to create session"
            }
        }
    }

    fun deleteSession(name: String) {
        viewModelScope.launch {
            try {
                api.delete(name)
                loadSessions()
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to delete session"
            }
        }
    }
}
```

- [ ] **Step 3: Run ViewModel tests**

Run: `cd android && ./gradlew test --tests "com.remotecontrol.ui.sessions.SessionsViewModelTest"`
Expected: PASS (4 tests)

- [ ] **Step 4: Implement full SessionsScreen.kt**

```kotlin
package com.remotecontrol.ui.sessions

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.remotecontrol.App
import com.remotecontrol.data.model.Session

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionsScreen(
    app: App,
    onSessionClick: (String) -> Unit,
    onSettingsClick: () -> Unit,
) {
    val api = app.apiClient?.sessions
    if (api == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Server not configured")
                Spacer(Modifier.height(16.dp))
                Button(onClick = onSettingsClick) { Text("Open Settings") }
            }
        }
        return
    }

    val vm = remember { SessionsViewModel(api) }
    val sessions by vm.sessions.collectAsState()
    val isLoading by vm.isLoading.collectAsState()
    val error by vm.error.collectAsState()

    LaunchedEffect(Unit) { vm.loadSessions() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sessions") },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { vm.createSession() }) {
                Icon(Icons.Default.Add, contentDescription = "New session")
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (isLoading && sessions.isEmpty()) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            } else if (sessions.isEmpty()) {
                Text("No active sessions", Modifier.align(Alignment.Center))
            } else {
                LazyColumn {
                    items(sessions, key = { it.name }) { session ->
                        SessionItem(
                            session = session,
                            onClick = { onSessionClick(session.name) },
                            onDelete = { vm.deleteSession(session.name) },
                        )
                    }
                }
            }

            error?.let {
                Snackbar(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                    action = { TextButton(onClick = { vm.loadSessions() }) { Text("Retry") } },
                ) { Text(it) }
            }
        }
    }
}

@Composable
private fun SessionItem(session: Session, onClick: () -> Unit, onDelete: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(session.name) },
        supportingContent = {
            Text(if (session.attached) "attached" else "detached")
        },
        leadingContent = {
            val color = if (session.attached) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outline
            }
            Box(
                Modifier.size(12.dp).background(color, shape = androidx.compose.foundation.shape.CircleShape)
            )
        },
        trailingContent = {
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete")
            }
        },
    )
}
```

- [ ] **Step 5: Verify build**

Run: `cd android && ./gradlew compileDebugKotlin`

- [ ] **Step 6: Commit**

```bash
git add android/ && git commit -m "feat(android): sessions screen with list, create, and delete"
```

---

### Task 10: Terminal Emulator

**Files:**
- Create: `android/app/src/main/kotlin/com/remotecontrol/terminal/TerminalEmulator.kt`
- Create: `android/app/src/main/kotlin/com/remotecontrol/terminal/TerminalRenderer.kt`
- Create: `android/app/src/test/kotlin/com/remotecontrol/terminal/TerminalEmulatorTest.kt`

- [ ] **Step 1: Write emulator tests**

```kotlin
package com.remotecontrol.terminal

import org.junit.Assert.*
import org.junit.Test

class TerminalEmulatorTest {

    @Test
    fun writePlainText() {
        val emu = TerminalEmulator(cols = 10, rows = 5)
        emu.process("Hello".toByteArray())
        assertEquals('H', emu.getCell(0, 0).char)
        assertEquals('o', emu.getCell(0, 4).char)
        assertEquals(0, emu.cursorRow)
        assertEquals(5, emu.cursorCol)
    }

    @Test
    fun newlineMovesToNextRow() {
        val emu = TerminalEmulator(cols = 10, rows = 5)
        emu.process("AB\nCD".toByteArray())
        assertEquals('A', emu.getCell(0, 0).char)
        assertEquals('C', emu.getCell(1, 0).char)
    }

    @Test
    fun carriageReturnResetsColumn() {
        val emu = TerminalEmulator(cols = 10, rows = 5)
        emu.process("ABCDE\rXY".toByteArray())
        assertEquals('X', emu.getCell(0, 0).char)
        assertEquals('Y', emu.getCell(0, 1).char)
        assertEquals('C', emu.getCell(0, 2).char)
    }

    @Test
    fun lineWrapAtEndOfRow() {
        val emu = TerminalEmulator(cols = 5, rows = 3)
        emu.process("ABCDEFGH".toByteArray())
        assertEquals('F', emu.getCell(1, 0).char)
    }

    @Test
    fun sgrSetsForegroundColor() {
        val emu = TerminalEmulator(cols = 10, rows = 5)
        emu.process("\u001b[31mR".toByteArray())
        val cell = emu.getCell(0, 0)
        assertEquals('R', cell.char)
        assertEquals(0xFFCC0000.toInt(), cell.fg)
    }

    @Test
    fun sgrResetClearsStyles() {
        val emu = TerminalEmulator(cols = 10, rows = 5)
        emu.process("\u001b[1;31mA\u001b[0mB".toByteArray())
        assertTrue(emu.getCell(0, 0).bold)
        assertFalse(emu.getCell(0, 1).bold)
    }

    @Test
    fun cursorMovementCsiH() {
        val emu = TerminalEmulator(cols = 10, rows = 5)
        emu.process("\u001b[3;5H*".toByteArray())
        assertEquals('*', emu.getCell(2, 4).char)
    }

    @Test
    fun eraseDisplayCsiJ() {
        val emu = TerminalEmulator(cols = 5, rows = 3)
        emu.process("AAAAA\nBBBBB\nCCCCC".toByteArray())
        emu.process("\u001b[1;1H\u001b[2J".toByteArray())
        assertEquals(' ', emu.getCell(0, 0).char)
        assertEquals(' ', emu.getCell(2, 4).char)
    }

    @Test
    fun resizePreservesContent() {
        val emu = TerminalEmulator(cols = 10, rows = 5)
        emu.process("Hello".toByteArray())
        emu.resize(20, 10)
        assertEquals(20, emu.cols)
        assertEquals(10, emu.rows)
        assertEquals('H', emu.getCell(0, 0).char)
    }
}
```

- [ ] **Step 2: Implement TerminalEmulator.kt**

```kotlin
package com.remotecontrol.terminal

data class TerminalCell(
    var char: Char = ' ',
    var fg: Int = DEFAULT_FG,
    var bg: Int = DEFAULT_BG,
    var bold: Boolean = false,
    var underline: Boolean = false,
) {
    companion object {
        const val DEFAULT_FG = 0xFFE0E0E0.toInt()
        const val DEFAULT_BG = 0xFF1E1E1E.toInt()
    }
}

class TerminalEmulator(
    var cols: Int = 80,
    var rows: Int = 24,
) {
    private var buffer: Array<Array<TerminalCell>> = makeBuffer(rows, cols)
    var cursorRow = 0
        private set
    var cursorCol = 0
        private set
    private var currentFg = TerminalCell.DEFAULT_FG
    private var currentBg = TerminalCell.DEFAULT_BG
    private var currentBold = false
    private var currentUnderline = false
    private var state = ParseState.NORMAL
    private val csiParams = StringBuilder()

    var version = 0L
        private set

    private enum class ParseState { NORMAL, ESCAPE, CSI }

    fun getCell(row: Int, col: Int): TerminalCell {
        if (row in 0 until rows && col in 0 until cols) return buffer[row][col]
        return TerminalCell()
    }

    fun process(data: ByteArray) {
        for (b in data) {
            val c = (b.toInt() and 0xFF).toChar()
            when (state) {
                ParseState.NORMAL -> processNormal(c)
                ParseState.ESCAPE -> processEscape(c)
                ParseState.CSI -> processCsi(c)
            }
        }
        version++
    }

    fun resize(newCols: Int, newRows: Int) {
        val newBuf = makeBuffer(newRows, newCols)
        for (r in 0 until minOf(rows, newRows)) {
            for (c in 0 until minOf(cols, newCols)) {
                newBuf[r][c] = buffer[r][c]
            }
        }
        buffer = newBuf
        cols = newCols
        rows = newRows
        cursorRow = cursorRow.coerceIn(0, rows - 1)
        cursorCol = cursorCol.coerceIn(0, cols - 1)
        version++
    }

    private fun processNormal(c: Char) {
        when (c) {
            '\u001b' -> state = ParseState.ESCAPE
            '\n' -> {
                cursorRow++
                if (cursorRow >= rows) scrollUp()
            }
            '\r' -> cursorCol = 0
            '\b' -> if (cursorCol > 0) cursorCol--
            '\t' -> cursorCol = minOf(cursorCol + (8 - cursorCol % 8), cols - 1)
            '\u0007' -> {}
            else -> if (c >= ' ') {
                if (cursorCol >= cols) {
                    cursorCol = 0
                    cursorRow++
                    if (cursorRow >= rows) scrollUp()
                }
                buffer[cursorRow][cursorCol] = TerminalCell(
                    char = c, fg = currentFg, bg = currentBg,
                    bold = currentBold, underline = currentUnderline,
                )
                cursorCol++
            }
        }
    }

    private fun processEscape(c: Char) {
        when (c) {
            '[' -> {
                state = ParseState.CSI
                csiParams.clear()
            }
            else -> state = ParseState.NORMAL
        }
    }

    private fun processCsi(c: Char) {
        if (c in '0'..'9' || c == ';') {
            csiParams.append(c)
            return
        }
        state = ParseState.NORMAL
        val params = csiParams.toString().split(';').mapNotNull { it.toIntOrNull() }
        when (c) {
            'm' -> processSgr(params)
            'H', 'f' -> {
                cursorRow = ((params.getOrElse(0) { 1 }) - 1).coerceIn(0, rows - 1)
                cursorCol = ((params.getOrElse(1) { 1 }) - 1).coerceIn(0, cols - 1)
            }
            'A' -> cursorRow = maxOf(0, cursorRow - maxOf(1, params.getOrElse(0) { 1 }))
            'B' -> cursorRow = minOf(rows - 1, cursorRow + maxOf(1, params.getOrElse(0) { 1 }))
            'C' -> cursorCol = minOf(cols - 1, cursorCol + maxOf(1, params.getOrElse(0) { 1 }))
            'D' -> cursorCol = maxOf(0, cursorCol - maxOf(1, params.getOrElse(0) { 1 }))
            'J' -> eraseDisplay(params.getOrElse(0) { 0 })
            'K' -> eraseLine(params.getOrElse(0) { 0 })
        }
    }

    private fun processSgr(params: List<Int>) {
        val ps = params.ifEmpty { listOf(0) }
        var i = 0
        while (i < ps.size) {
            when (ps[i]) {
                0 -> { currentFg = TerminalCell.DEFAULT_FG; currentBg = TerminalCell.DEFAULT_BG; currentBold = false; currentUnderline = false }
                1 -> currentBold = true
                4 -> currentUnderline = true
                22 -> currentBold = false
                24 -> currentUnderline = false
                in 30..37 -> currentFg = ANSI_COLORS[ps[i] - 30]
                39 -> currentFg = TerminalCell.DEFAULT_FG
                in 40..47 -> currentBg = ANSI_COLORS[ps[i] - 40]
                49 -> currentBg = TerminalCell.DEFAULT_BG
                in 90..97 -> currentFg = ANSI_BRIGHT_COLORS[ps[i] - 90]
                in 100..107 -> currentBg = ANSI_BRIGHT_COLORS[ps[i] - 100]
                38 -> {
                    if (i + 2 < ps.size && ps[i + 1] == 5) {
                        currentFg = color256(ps[i + 2]); i += 2
                    }
                }
                48 -> {
                    if (i + 2 < ps.size && ps[i + 1] == 5) {
                        currentBg = color256(ps[i + 2]); i += 2
                    }
                }
            }
            i++
        }
    }

    private fun eraseDisplay(mode: Int) {
        when (mode) {
            0 -> {
                for (c in cursorCol until cols) buffer[cursorRow][c] = TerminalCell()
                for (r in cursorRow + 1 until rows) for (c in 0 until cols) buffer[r][c] = TerminalCell()
            }
            1 -> {
                for (r in 0 until cursorRow) for (c in 0 until cols) buffer[r][c] = TerminalCell()
                for (c in 0..cursorCol) buffer[cursorRow][c] = TerminalCell()
            }
            2 -> for (r in 0 until rows) for (c in 0 until cols) buffer[r][c] = TerminalCell()
        }
    }

    private fun eraseLine(mode: Int) {
        when (mode) {
            0 -> for (c in cursorCol until cols) buffer[cursorRow][c] = TerminalCell()
            1 -> for (c in 0..cursorCol) buffer[cursorRow][c] = TerminalCell()
            2 -> for (c in 0 until cols) buffer[cursorRow][c] = TerminalCell()
        }
    }

    private fun scrollUp() {
        for (r in 1 until rows) buffer[r - 1] = buffer[r]
        buffer[rows - 1] = Array(cols) { TerminalCell() }
        cursorRow = rows - 1
    }

    companion object {
        private fun makeBuffer(rows: Int, cols: Int) = Array(rows) { Array(cols) { TerminalCell() } }

        val ANSI_COLORS = intArrayOf(
            0xFF000000.toInt(), 0xFFCC0000.toInt(), 0xFF00CC00.toInt(), 0xFFCCCC00.toInt(),
            0xFF0000CC.toInt(), 0xFFCC00CC.toInt(), 0xFF00CCCC.toInt(), 0xFFCCCCCC.toInt(),
        )
        val ANSI_BRIGHT_COLORS = intArrayOf(
            0xFF555555.toInt(), 0xFFFF5555.toInt(), 0xFF55FF55.toInt(), 0xFFFFFF55.toInt(),
            0xFF5555FF.toInt(), 0xFFFF55FF.toInt(), 0xFF55FFFF.toInt(), 0xFFFFFFFF.toInt(),
        )

        fun color256(n: Int): Int {
            if (n < 8) return ANSI_COLORS[n]
            if (n < 16) return ANSI_BRIGHT_COLORS[n - 8]
            if (n < 232) {
                val idx = n - 16
                val r = (idx / 36) * 51
                val g = ((idx / 6) % 6) * 51
                val b = (idx % 6) * 51
                return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
            val gray = 8 + (n - 232) * 10
            return (0xFF shl 24) or (gray shl 16) or (gray shl 8) or gray
        }
    }
}
```

- [ ] **Step 3: Run emulator tests**

Run: `cd android && ./gradlew test --tests "com.remotecontrol.terminal.TerminalEmulatorTest"`
Expected: PASS (9 tests)

- [ ] **Step 4: Implement TerminalRenderer.kt**

```kotlin
package com.remotecontrol.terminal

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import android.graphics.Paint
import android.graphics.Typeface

@Composable
fun TerminalRenderer(
    emulator: TerminalEmulator,
    version: Long,
    fontSize: Float,
    modifier: Modifier = Modifier,
    onSizeChanged: ((cols: Int, rows: Int) -> Unit)? = null,
) {
    val paint = remember {
        Paint().apply {
            typeface = Typeface.MONOSPACE
            isAntiAlias = true
        }
    }

    var currentFontSize by remember { mutableFloatStateOf(fontSize) }

    val gestureModifier = modifier.pointerInput(Unit) {
        detectTransformGestures { _, _, zoom, _ ->
            currentFontSize = (currentFontSize * zoom).coerceIn(8f, 32f)
        }
    }

    Canvas(modifier = gestureModifier) {
        paint.textSize = currentFontSize * density
        val charWidth = paint.measureText("M")
        val charHeight = paint.fontSpacing

        val cols = (size.width / charWidth).toInt().coerceAtLeast(1)
        val rows = (size.height / charHeight).toInt().coerceAtLeast(1)

        if (cols != emulator.cols || rows != emulator.rows) {
            onSizeChanged?.invoke(cols, rows)
        }

        drawTerminal(emulator, paint, charWidth, charHeight)
    }
}

private fun DrawScope.drawTerminal(
    emulator: TerminalEmulator,
    paint: Paint,
    charWidth: Float,
    charHeight: Float,
) {
    val canvas = drawContext.canvas.nativeCanvas
    for (row in 0 until emulator.rows) {
        for (col in 0 until emulator.cols) {
            val cell = emulator.getCell(row, col)
            val x = col * charWidth
            val y = row * charHeight

            if (cell.bg != TerminalCell.DEFAULT_BG) {
                paint.color = cell.bg
                paint.style = Paint.Style.FILL
                canvas.drawRect(x, y, x + charWidth, y + charHeight, paint)
            }

            if (cell.char != ' ') {
                paint.color = cell.fg
                paint.style = Paint.Style.FILL
                paint.isFakeBoldText = cell.bold
                paint.isUnderlineText = cell.underline
                canvas.drawText(cell.char.toString(), x, y + charHeight - paint.descent(), paint)
                paint.isFakeBoldText = false
                paint.isUnderlineText = false
            }
        }
    }

    val cursorX = emulator.cursorCol * charWidth
    val cursorY = emulator.cursorRow * charHeight
    drawRect(
        color = Color(0xAAFFFFFF),
        topLeft = Offset(cursorX, cursorY),
        size = androidx.compose.ui.geometry.Size(charWidth, charHeight),
    )
}
```

- [ ] **Step 5: Verify build**

Run: `cd android && ./gradlew compileDebugKotlin`

- [ ] **Step 6: Commit**

```bash
git add android/ && git commit -m "feat(android): terminal emulator with ANSI parser and Compose renderer"
```

---

### Task 11: Terminal Screen

**Files:**
- Create: `android/app/src/main/kotlin/com/remotecontrol/ui/terminal/TerminalViewModel.kt`
- Create: `android/app/src/main/kotlin/com/remotecontrol/ui/terminal/ExtraKeysBar.kt`
- Modify: `android/app/src/main/kotlin/com/remotecontrol/ui/terminal/TerminalScreen.kt`

- [ ] **Step 1: Implement TerminalViewModel.kt**

```kotlin
package com.remotecontrol.ui.terminal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.remotecontrol.data.websocket.ConnectionState
import com.remotecontrol.data.websocket.Frame
import com.remotecontrol.data.websocket.TerminalSocket
import com.remotecontrol.terminal.TerminalEmulator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

class TerminalViewModel(
    private val serverUrl: String,
    private val token: String,
    private val sessionId: String,
    okHttpClient: OkHttpClient,
) : ViewModel() {
    val emulator = TerminalEmulator()

    private val socket = TerminalSocket(client = okHttpClient, scope = viewModelScope)
    val connectionState: StateFlow<ConnectionState> = socket.state

    private val _version = MutableStateFlow(0L)
    val version: StateFlow<Long> = _version

    init {
        viewModelScope.launch {
            socket.incoming.collect { frame ->
                when (frame) {
                    is Frame.Data -> {
                        emulator.process(frame.payload)
                        _version.value = emulator.version
                    }
                    is Frame.SessionEvent -> {
                        if (frame.eventType == "ended") {
                            socket.disconnect()
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    fun connect() {
        socket.connect(serverUrl, sessionId, token)
    }

    fun sendInput(text: String) {
        socket.send(Frame.Data(text.toByteArray()))
    }

    fun sendKey(key: String) {
        val bytes = when (key) {
            "Tab" -> byteArrayOf(0x09)
            "Esc" -> byteArrayOf(0x1b)
            "Enter" -> byteArrayOf(0x0d)
            "Up" -> "\u001b[A".toByteArray()
            "Down" -> "\u001b[B".toByteArray()
            "Right" -> "\u001b[C".toByteArray()
            "Left" -> "\u001b[D".toByteArray()
            else -> key.toByteArray()
        }
        socket.send(Frame.Data(bytes))
    }

    fun sendCtrl(c: Char) {
        val code = c.uppercaseChar().code - 64
        if (code in 0..31) {
            socket.send(Frame.Data(byteArrayOf(code.toByte())))
        }
    }

    fun resize(cols: Int, rows: Int) {
        emulator.resize(cols, rows)
        socket.send(Frame.Resize(cols = cols, rows = rows))
        _version.value = emulator.version
    }

    override fun onCleared() {
        socket.disconnect()
    }
}
```

- [ ] **Step 2: Implement ExtraKeysBar.kt**

```kotlin
package com.remotecontrol.ui.terminal

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ExtraKeysBar(
    onKey: (String) -> Unit,
    onCtrl: (Char) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            KeyButton("Esc") { onKey("Esc") }
            KeyButton("Tab") { onKey("Tab") }
            KeyButton("Ctrl") { onCtrl('C') }
            KeyButton("^D") { onCtrl('D') }
            KeyButton("^Z") { onCtrl('Z') }
            KeyButton("^L") { onCtrl('L') }
            KeyButton("|") { onKey("|") }
            KeyButton("~") { onKey("~") }
            KeyButton("/") { onKey("/") }
            KeyButton("-") { onKey("-") }
            KeyButton("Up") { onKey("Up") }
            KeyButton("Down") { onKey("Down") }
            KeyButton("Left") { onKey("Left") }
            KeyButton("Right") { onKey("Right") }
        }
    }
}

@Composable
private fun KeyButton(label: String, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        modifier = Modifier.height(36.dp),
    ) {
        Text(label, fontSize = 12.sp)
    }
}
```

- [ ] **Step 3: Implement full TerminalScreen.kt**

```kotlin
package com.remotecontrol.ui.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import com.remotecontrol.App
import com.remotecontrol.data.websocket.ConnectionState
import com.remotecontrol.terminal.TerminalRenderer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(app: App, sessionId: String, onBack: () -> Unit) {
    val apiClient = app.apiClient ?: return
    val serverUrl by app.settings.serverUrl.collectAsState(initial = "")
    val token by app.settings.token.collectAsState(initial = "")
    val fontSize by app.settings.fontSize.collectAsState(initial = 14)

    val vm = remember(sessionId) {
        TerminalViewModel(
            serverUrl = serverUrl,
            token = token,
            sessionId = sessionId,
            okHttpClient = apiClient.okHttpClient(),
        )
    }

    val connectionState by vm.connectionState.collectAsState()
    val version by vm.version.collectAsState()
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    var showCommands by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        vm.connect()
        focusRequester.requestFocus()
        keyboard?.show()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(sessionId)
                        Spacer(Modifier.width(8.dp))
                        val color = when (connectionState) {
                            ConnectionState.CONNECTED -> Color.Green
                            ConnectionState.CONNECTING -> Color.Yellow
                            ConnectionState.DISCONNECTED -> Color.Red
                        }
                        Box(Modifier.size(8.dp).background(color, shape = androidx.compose.foundation.shape.CircleShape))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCommands = true },
                modifier = Modifier.padding(bottom = 48.dp),
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Commands")
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .focusRequester(focusRequester)
                .focusable()
                .onKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown) {
                        val char = event.utf16CodePoint.toChar()
                        if (char.code in 32..126) {
                            vm.sendInput(char.toString())
                            true
                        } else when (event.key) {
                            Key.Enter -> { vm.sendKey("Enter"); true }
                            Key.Backspace -> { vm.sendInput("\u007f"); true }
                            Key.Tab -> { vm.sendKey("Tab"); true }
                            Key.DirectionUp -> { vm.sendKey("Up"); true }
                            Key.DirectionDown -> { vm.sendKey("Down"); true }
                            Key.DirectionLeft -> { vm.sendKey("Left"); true }
                            Key.DirectionRight -> { vm.sendKey("Right"); true }
                            Key.Escape -> { vm.sendKey("Esc"); true }
                            else -> false
                        }
                    } else false
                },
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color(0xFF1E1E1E)),
            ) {
                TerminalRenderer(
                    emulator = vm.emulator,
                    version = version,
                    fontSize = fontSize.toFloat(),
                    modifier = Modifier.fillMaxSize(),
                    onSizeChanged = { cols, rows -> vm.resize(cols, rows) },
                )

                if (connectionState == ConnectionState.DISCONNECTED) {
                    Surface(
                        modifier = Modifier.align(Alignment.TopCenter).padding(8.dp),
                        color = MaterialTheme.colorScheme.error,
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Text(
                            "Disconnected - reconnecting...",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            color = MaterialTheme.colorScheme.onError,
                        )
                    }
                }
            }

            ExtraKeysBar(
                onKey = { vm.sendKey(it) },
                onCtrl = { vm.sendCtrl(it) },
            )
        }
    }

    if (showCommands) {
        com.remotecontrol.ui.commands.CommandsSheet(
            app = app,
            onDismiss = { showCommands = false },
            onCommandSelected = { command ->
                vm.sendInput(command.command + "\n")
                showCommands = false
            },
        )
    }
}
```

- [ ] **Step 4: Verify build**

Run: `cd android && ./gradlew compileDebugKotlin`
Note: This will fail because CommandsSheet doesn't exist yet. Create a stub:

Create `android/app/src/main/kotlin/com/remotecontrol/ui/commands/CommandsSheet.kt`:

```kotlin
package com.remotecontrol.ui.commands

import androidx.compose.runtime.Composable
import com.remotecontrol.App
import com.remotecontrol.data.model.Command

@Composable
fun CommandsSheet(app: App, onDismiss: () -> Unit, onCommandSelected: (Command) -> Unit) {}
```

Run: `cd android && ./gradlew compileDebugKotlin`

- [ ] **Step 5: Commit**

```bash
git add android/ && git commit -m "feat(android): terminal screen with WebSocket, ANSI rendering, and extra keys"
```

---

### Task 12: Commands Bottom Sheet

**Files:**
- Create: `android/app/src/main/kotlin/com/remotecontrol/ui/commands/CommandsViewModel.kt`
- Modify: `android/app/src/main/kotlin/com/remotecontrol/ui/commands/CommandsSheet.kt`
- Create: `android/app/src/test/kotlin/com/remotecontrol/ui/commands/CommandsViewModelTest.kt`

- [ ] **Step 1: Write ViewModel tests**

```kotlin
package com.remotecontrol.ui.commands

import com.remotecontrol.data.api.CommandsApi
import com.remotecontrol.data.model.Command
import com.remotecontrol.data.model.CreateCommand
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CommandsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val api = mockk<CommandsApi>()

    @Before
    fun setup() { Dispatchers.setMain(dispatcher) }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun loadCommandsGroupsByCategory() = runTest {
        coEvery { api.list() } returns listOf(
            Command("1", "push", "git push", null, "git", null, "", ""),
            Command("2", "status", "git status", null, "git", null, "", ""),
            Command("3", "up", "docker compose up", null, "docker", null, "", ""),
        )
        val vm = CommandsViewModel(api)
        vm.loadCommands()
        advanceUntilIdle()

        val grouped = vm.groupedCommands.value
        assertEquals(2, grouped.size)
        assertEquals(listOf("docker", "git"), grouped.keys.sorted())
    }

    @Test
    fun searchFiltersCommands() = runTest {
        coEvery { api.list() } returns listOf(
            Command("1", "push", "git push", null, "git", null, "", ""),
            Command("2", "deploy", "kubectl deploy", null, "k8s", null, "", ""),
        )
        val vm = CommandsViewModel(api)
        vm.loadCommands()
        advanceUntilIdle()
        vm.setSearch("push")

        val grouped = vm.groupedCommands.value
        assertEquals(1, grouped.size)
        assertEquals("push", grouped["git"]?.first()?.name)
    }

    @Test
    fun createCommandCallsApiAndReloads() = runTest {
        coEvery { api.list() } returns emptyList()
        coEvery { api.create(any()) } returns Command("1", "test", "echo", null, "misc", null, "", "")

        val vm = CommandsViewModel(api)
        vm.createCommand(CreateCommand("test", "echo", null, "misc"))
        advanceUntilIdle()

        coVerify { api.create(any()) }
    }
}
```

- [ ] **Step 2: Implement CommandsViewModel.kt**

```kotlin
package com.remotecontrol.ui.commands

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.remotecontrol.data.api.CommandsApi
import com.remotecontrol.data.model.Command
import com.remotecontrol.data.model.CreateCommand
import com.remotecontrol.data.model.UpdateCommand
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class CommandsViewModel(private val api: CommandsApi) : ViewModel() {
    private val _commands = MutableStateFlow<List<Command>>(emptyList())
    private val _search = MutableStateFlow("")

    val groupedCommands: StateFlow<Map<String, List<Command>>> =
        combine(_commands, _search) { commands, search ->
            val filtered = if (search.isBlank()) commands
            else commands.filter {
                it.name.contains(search, ignoreCase = true) ||
                it.command.contains(search, ignoreCase = true) ||
                it.category.contains(search, ignoreCase = true)
            }
            filtered.groupBy { it.category }.toSortedMap()
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyMap())

    fun loadCommands() {
        viewModelScope.launch {
            try { _commands.value = api.list() } catch (_: Exception) {}
        }
    }

    fun setSearch(query: String) { _search.value = query }

    fun createCommand(input: CreateCommand) {
        viewModelScope.launch {
            try { api.create(input); loadCommands() } catch (_: Exception) {}
        }
    }

    fun updateCommand(id: String, input: UpdateCommand) {
        viewModelScope.launch {
            try { api.update(id, input); loadCommands() } catch (_: Exception) {}
        }
    }

    fun deleteCommand(id: String) {
        viewModelScope.launch {
            try { api.delete(id); loadCommands() } catch (_: Exception) {}
        }
    }
}
```

- [ ] **Step 3: Run ViewModel tests**

Run: `cd android && ./gradlew test --tests "com.remotecontrol.ui.commands.CommandsViewModelTest"`
Expected: PASS (3 tests)

- [ ] **Step 4: Implement full CommandsSheet.kt**

```kotlin
package com.remotecontrol.ui.commands

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.remotecontrol.App
import com.remotecontrol.data.model.Command
import com.remotecontrol.data.model.CreateCommand

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommandsSheet(
    app: App,
    onDismiss: () -> Unit,
    onCommandSelected: (Command) -> Unit,
) {
    val api = app.apiClient?.commands ?: return
    val vm = remember { CommandsViewModel(api) }
    val grouped by vm.groupedCommands.collectAsState()
    var showAdd by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.loadCommands() }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Commands", style = MaterialTheme.typography.titleLarge)
                IconButton(onClick = { showAdd = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Add command")
                }
            }

            var search by remember { mutableStateOf("") }
            OutlinedTextField(
                value = search,
                onValueChange = { search = it; vm.setSearch(it) },
                placeholder = { Text("Search commands...") },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                singleLine = true,
            )

            LazyColumn(
                modifier = Modifier.heightIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                grouped.forEach { (category, commands) ->
                    item {
                        Text(
                            category.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                        )
                    }
                    items(commands) { cmd ->
                        ListItem(
                            modifier = Modifier.clickable { onCommandSelected(cmd) },
                            headlineContent = { Text(cmd.name) },
                            supportingContent = {
                                Text(cmd.command, maxLines = 1, color = MaterialTheme.colorScheme.outline)
                            },
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    if (showAdd) {
        AddCommandDialog(
            onDismiss = { showAdd = false },
            onConfirm = { name, command, category ->
                vm.createCommand(CreateCommand(name, command, null, category))
                showAdd = false
            },
        )
    }
}

@Composable
private fun AddCommandDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, command: String, category: String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var command by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Command") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true)
                OutlinedTextField(value = command, onValueChange = { command = it }, label = { Text("Command") }, singleLine = true)
                OutlinedTextField(value = category, onValueChange = { category = it }, label = { Text("Category") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name, command, category) },
                enabled = name.isNotBlank() && command.isNotBlank() && category.isNotBlank(),
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
```

- [ ] **Step 5: Verify build**

Run: `cd android && ./gradlew compileDebugKotlin`

- [ ] **Step 6: Commit**

```bash
git add android/ && git commit -m "feat(android): commands bottom sheet with search, categories, and add dialog"
```

---

### Task 13: Settings Screen

**Files:**
- Create: `android/app/src/main/kotlin/com/remotecontrol/ui/settings/SettingsViewModel.kt`
- Modify: `android/app/src/main/kotlin/com/remotecontrol/ui/settings/SettingsScreen.kt`

- [ ] **Step 1: Implement SettingsViewModel.kt**

```kotlin
package com.remotecontrol.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.remotecontrol.App
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class SettingsViewModel(private val app: App) : ViewModel() {
    val serverUrl = app.settings.serverUrl.stateIn(viewModelScope, SharingStarted.Lazily, "")
    val token = app.settings.token.stateIn(viewModelScope, SharingStarted.Lazily, "")
    val fontSize = app.settings.fontSize.stateIn(viewModelScope, SharingStarted.Lazily, 14)
    val darkMode = app.settings.darkMode.stateIn(viewModelScope, SharingStarted.Lazily, true)
    val scrollbackLines = app.settings.scrollbackLines.stateIn(viewModelScope, SharingStarted.Lazily, 10000)

    fun setServerUrl(url: String) {
        viewModelScope.launch {
            app.settings.setServerUrl(url)
            app.updateApiClient(url, token.value)
        }
    }

    fun setToken(token: String) {
        viewModelScope.launch {
            app.settings.setToken(token)
            app.updateApiClient(serverUrl.value, token)
        }
    }

    fun setFontSize(size: Int) {
        viewModelScope.launch { app.settings.setFontSize(size) }
    }

    fun setDarkMode(dark: Boolean) {
        viewModelScope.launch { app.settings.setDarkMode(dark) }
    }

    fun setScrollbackLines(lines: Int) {
        viewModelScope.launch { app.settings.setScrollbackLines(lines) }
    }
}
```

- [ ] **Step 2: Implement full SettingsScreen.kt**

```kotlin
package com.remotecontrol.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.remotecontrol.App

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(app: App, onBack: () -> Unit) {
    val vm = remember { SettingsViewModel(app) }
    val serverUrl by vm.serverUrl.collectAsState()
    val token by vm.token.collectAsState()
    val fontSize by vm.fontSize.collectAsState()
    val darkMode by vm.darkMode.collectAsState()
    val scrollback by vm.scrollbackLines.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Connection", style = MaterialTheme.typography.titleMedium)

            OutlinedTextField(
                value = serverUrl,
                onValueChange = { vm.setServerUrl(it) },
                label = { Text("Server URL") },
                placeholder = { Text("http://192.168.1.100:48322/") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            OutlinedTextField(
                value = token,
                onValueChange = { vm.setToken(it) },
                label = { Text("Auth Token") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            HorizontalDivider()
            Text("Terminal", style = MaterialTheme.typography.titleMedium)

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Font Size: $fontSize", modifier = Modifier.weight(1f))
                Slider(
                    value = fontSize.toFloat(),
                    onValueChange = { vm.setFontSize(it.toInt()) },
                    valueRange = 8f..32f,
                    steps = 23,
                    modifier = Modifier.weight(2f),
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Dark Mode", modifier = Modifier.weight(1f))
                Switch(checked = darkMode, onCheckedChange = { vm.setDarkMode(it) })
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Scrollback: $scrollback lines", modifier = Modifier.weight(1f))
            }
            Slider(
                value = scrollback.toFloat(),
                onValueChange = { vm.setScrollbackLines(it.toInt()) },
                valueRange = 1000f..50000f,
                steps = 48,
            )
        }
    }
}
```

- [ ] **Step 3: Verify build**

Run: `cd android && ./gradlew compileDebugKotlin`

- [ ] **Step 4: Commit**

```bash
git add android/ && git commit -m "feat(android): settings screen with server config and terminal preferences"
```

---

## Summary

| Task | Component | Tests |
|------|-----------|-------|
| 1 | Project scaffold | build check |
| 2 | Data models | build check |
| 3 | Binary protocol | 7 unit |
| 4 | Reconnector | 5 unit |
| 5 | API client | build check |
| 6 | WebSocket manager | build check |
| 7 | Settings store | build check |
| 8 | Theme + navigation | build check |
| 9 | Sessions screen | 4 unit (ViewModel) |
| 10 | Terminal emulator | 9 unit |
| 11 | Terminal screen | build check |
| 12 | Commands bottom sheet | 3 unit (ViewModel) |
| 13 | Settings screen | build check |

**Total: 13 tasks, ~60 steps, 28 tests**
