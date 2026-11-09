package com.music.pianocoach.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.music.pianocoach.coach.CoachAgent
import com.music.pianocoach.coach.DeepSeekClient
import com.music.pianocoach.coach.PiecePracticeAnalyzer
import com.music.pianocoach.coach.PracticeContextBuilder
import com.music.pianocoach.coach.UiChatMessage
import com.music.pianocoach.drill.ActiveDrillRef
import com.music.pianocoach.drill.BuiltinSongs
import com.music.pianocoach.drill.DrillResult
import com.music.pianocoach.drill.ScoreFileImporter
import com.music.pianocoach.drill.PracticeDrill
import com.music.pianocoach.drill.PracticePlan
import com.music.pianocoach.drill.PracticePlanStore
import com.music.pianocoach.drill.ScorePlaybackEngine
import com.music.pianocoach.drill.SongDrillEngine
import com.music.pianocoach.drill.SongDrillState
import com.music.pianocoach.drill.SongLibraryStore
import com.music.pianocoach.drill.SongPiece
import com.music.pianocoach.drill.sliceForDrill
import com.music.pianocoach.drill.toChordSteps
import com.music.pianocoach.midi.BleMidiScanner
import com.music.pianocoach.midi.ConnectionState
import com.music.pianocoach.midi.MidiBleDevice
import com.music.pianocoach.midi.MidiSession
import com.music.pianocoach.midi.NoteEvent
import com.music.pianocoach.session.PracticeSession
import com.music.pianocoach.settings.CoachSettings
import com.music.pianocoach.settings.CoachSettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PianoUiState(
    val bluetoothEnabled: Boolean = false,
    val scanPermissionGranted: Boolean = false,
    val connectPermissionGranted: Boolean = false,
    val isScanning: Boolean = false,
    val devices: List<MidiBleDevice> = emptyList(),
    val connectionState: ConnectionState = ConnectionState.Disconnected,
    val connectedDeviceName: String? = null,
    val statusMessage: String? = null,
    val activeNotes: Set<Int> = emptySet(),
    val lastEvent: NoteEvent? = null,
    val recentEvents: List<NoteEvent> = emptyList(),
    val refreshHint: String? = null,
    val coachMessages: List<UiChatMessage> = emptyList(),
    val coachInput: String = "",
    val coachStreaming: Boolean = false,
    val coachStreamText: String = "",
    val coachError: String? = null,
    val coachInfo: String? = null,
    val coachSettings: CoachSettings = CoachSettings(),
    val showSettings: Boolean = false,
    val suggestAnalyze: Boolean = false,
    val contextPreview: String = "",
    val songCatalog: List<SongPiece> = BuiltinSongs.all,
    val songDrill: SongDrillState = SongDrillState(),
    val importMessage: String? = null,
    /** songId → 练习计划历史（新→旧） */
    val practicePlans: Map<String, List<PracticePlan>> = emptyMap(),
    val analyzingSongIds: Set<String> = emptySet(),
    val analyzeErrors: Map<String, String> = emptyMap(),
    val activeDrillRef: ActiveDrillRef? = null,
    /** 曲库面板与折叠态（存在 ViewModel，进出界面不丢） */
    val showSongLibrary: Boolean = true,
    val libraryExpandedSongId: String? = null,
    val libraryAiExpandedSongId: String? = null,
    val libraryExpandedPlanId: String? = null,
    val scorePlaying: Boolean = false,
    val scoreBpm: Float = 100f,
    /** 瀑布上显示指法 1–5（仅提示） */
    val showFingering: Boolean = false,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(PianoUiState())
    val uiState: StateFlow<PianoUiState> = _uiState.asStateFlow()

    private val scanner = BleMidiScanner(
        context = application,
        onDevicesUpdated = { devices -> _uiState.update { it.copy(devices = devices) } },
        onScanError = { msg -> _uiState.update { it.copy(statusMessage = msg, isScanning = false) } },
    )

    private val practiceSession = PracticeSession()
    private val songDrillEngine = SongDrillEngine()
    private val settingsStore = CoachSettingsStore(application)
    private val songLibrary = SongLibraryStore(application)
    private val practicePlanStore = PracticePlanStore(application)
    private val deepSeek = DeepSeekClient()
    private val coachAgent = CoachAgent(deepSeek)
    private val pieceAnalyzer = PiecePracticeAnalyzer(deepSeek)
    private val scorePlayback = ScorePlaybackEngine(
        scope = viewModelScope,
        context = application,
        onTick = { tick -> songDrillEngine.setPlayheadTick(tick) },
    )

    private val settingsFlow = settingsStore.settings.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        CoachSettings(),
    )

    private var lastDrillResult: DrillResult? = null
    private var streamJob: Job? = null
    private var autoRefreshJob: Job? = null
    private var idleTickJob: Job? = null
    private var holdTickJob: Job? = null
    private val analyzeJobs = mutableMapOf<String, Job>()

    private val session = MidiSession(
        context = application,
        onConnection = { state, detail ->
            _uiState.update {
                it.copy(
                    connectionState = state,
                    connectedDeviceName = if (state == ConnectionState.Connected) detail else it.connectedDeviceName,
                    statusMessage = when (state) {
                        ConnectionState.Error -> detail
                        ConnectionState.Disconnected -> null
                        else -> it.statusMessage
                    },
                )
            }
            if (state == ConnectionState.Disconnected || state == ConnectionState.Error) {
                _uiState.update { s -> s.copy(activeNotes = emptySet(), connectedDeviceName = null) }
            }
            if (state == ConnectionState.Disconnected) refreshAllDevices()
        },
        onNote = { event -> onMidiEvent(event) },
    )

    init {
        val first = BuiltinSongs.all.first()
        songDrillEngine.load(first)
        bindScorePlayback(first, 0)
        scanner.registerMidiTopologyCallbacks { refreshAllDevices() }
        session.registerDeviceCallback { refreshAllDevices() }
        refreshBluetoothState()
        startAutoRefreshWhileIdle()
        startIdleTicker()
        startHoldTicker()
        songDrillEngine.setPracticeBpm(100f)
        scorePlayback.warmUp()
        restoreSongLibrary()
        viewModelScope.launch {
            settingsFlow.collect { s -> _uiState.update { it.copy(coachSettings = s) } }
        }
        viewModelScope.launch {
            practiceSession.suggestAnalyze.collect { suggest ->
                _uiState.update { it.copy(suggestAnalyze = suggest) }
            }
        }
        viewModelScope.launch {
            songDrillEngine.state.collect { drill ->
                _uiState.update { it.copy(songDrill = drill) }
                if (drill.completed && !_uiState.value.scorePlaying) {
                    lastDrillResult = songDrillEngine.takeResult()
                }
            }
        }
        viewModelScope.launch {
            scorePlayback.state.collect { pb ->
                _uiState.update {
                    it.copy(scorePlaying = pb.playing, scoreBpm = pb.bpm)
                }
            }
        }
    }

    private fun restoreSongLibrary() {
        viewModelScope.launch {
            val imported = songLibrary.loadImported()
            val plans = practicePlanStore.loadAll()
            val lastId = songLibrary.lastSelectedId()
            val catalog = (BuiltinSongs.all + imported).distinctBy { it.id }
            _uiState.update { it.copy(songCatalog = catalog, practicePlans = plans) }
            val toSelect = catalog.firstOrNull { it.id == lastId } ?: catalog.firstOrNull()
            if (toSelect != null) {
                selectFullPiece(toSelect, persistSelection = false)
            }
        }
    }

    private fun findCatalogPiece(songId: String): SongPiece? =
        _uiState.value.songCatalog.firstOrNull { it.id == songId }

    private fun onMidiEvent(event: NoteEvent) {
        practiceSession.onEvent(event)
        // 自动播放时不吃跟练判定，避免和试听抢进度
        if (!_uiState.value.scorePlaying) {
            songDrillEngine.onEvent(event)
        }
        updateContextPreview()
        _uiState.update { state ->
            val active = state.activeNotes.toMutableSet()
            if (event.on) active.add(event.note) else active.remove(event.note)
            state.copy(
                activeNotes = active,
                lastEvent = event,
                recentEvents = (listOf(event) + state.recentEvents).take(100),
            )
        }
    }

    private fun bindScorePlayback(piece: SongPiece, tick: Int = 0) {
        scorePlayback.attach(piece, tick)
    }

    private fun updateContextPreview() {
        val summary = PracticeContextBuilder.build(practiceSession.snapshot(), lastDrillResult)
        _uiState.update { it.copy(contextPreview = PracticeContextBuilder.toJson(summary)) }
    }

    private fun startIdleTicker() {
        idleTickJob?.cancel()
        idleTickJob = viewModelScope.launch {
            while (isActive) {
                practiceSession.tickIdle(System.nanoTime())
                delay(400)
            }
        }
    }

    private fun startHoldTicker() {
        holdTickJob?.cancel()
        holdTickJob = viewModelScope.launch {
            while (isActive) {
                if (!_uiState.value.scorePlaying) {
                    songDrillEngine.tickHold()
                }
                delay(40)
            }
        }
    }

    fun refreshBluetoothState() {
        _uiState.update {
            it.copy(
                bluetoothEnabled = scanner.isBluetoothEnabled(),
                scanPermissionGranted = scanner.hasScanPermission(),
                connectPermissionGranted = scanner.hasConnectPermission(),
            )
        }
        if (scanner.hasConnectPermission()) refreshAllDevices()
    }

    fun onPermissionsResult() = refreshBluetoothState()

    fun refreshAllDevices() {
        val stats = scanner.refreshAllSources()
        _uiState.update { it.copy(refreshHint = buildRefreshHint(stats)) }
    }

    fun refreshPairedDevices() = refreshAllDevices()

    private fun buildRefreshHint(stats: BleMidiScanner.RefreshStats): String = when {
        stats.systemOnlineCount > 0 -> "系统 MIDI 在线 ${stats.systemOnlineCount} 个（可连接）"
        stats.bondedCount > 0 -> "已配对 ${stats.bondedCount} 个；请开琴并点刷新"
        !scanner.hasConnectPermission() -> "未授予蓝牙连接权限"
        else -> "未发现设备：请确认琴已开机并在本页刷新"
    }

    fun startAutoRefreshWhileIdle() {
        autoRefreshJob?.cancel()
        autoRefreshJob = viewModelScope.launch {
            while (isActive) {
                if (_uiState.value.connectionState == ConnectionState.Disconnected) refreshAllDevices()
                delay(2_500)
            }
        }
    }

    fun onAppResumed() {
        refreshBluetoothState()
        refreshAllDevices()
    }

    fun startScan() {
        if (!_uiState.value.bluetoothEnabled) {
            _uiState.update { it.copy(statusMessage = "请先打开手机蓝牙") }
            return
        }
        _uiState.update { it.copy(isScanning = true, statusMessage = null) }
        scanner.startScan()
    }

    fun stopScan() {
        scanner.stopScan()
        _uiState.update { it.copy(isScanning = false) }
    }

    fun connect(device: MidiBleDevice) {
        stopScan()
        device.systemMidiDeviceId?.let { systemId ->
            scanner.getSystemMidiDeviceInfo(systemId)?.let {
                session.connect(it)
                return
            }
        }
        val bt = scanner.getBluetoothDevice(device.address)
        if (bt == null) {
            _uiState.update {
                it.copy(connectionState = ConnectionState.Error, statusMessage = "无法访问蓝牙设备")
            }
            return
        }
        session.connect(bt)
    }

    fun disconnect() = session.disconnect()

    fun setCoachInput(text: String) {
        _uiState.update { it.copy(coachInput = text) }
    }

    fun showSettings(show: Boolean) {
        _uiState.update { it.copy(showSettings = show) }
    }

    fun setShowSongLibrary(show: Boolean) {
        _uiState.update { it.copy(showSongLibrary = show) }
    }

    fun toggleLibrarySongExpanded(songId: String) {
        _uiState.update { st ->
            val collapse = st.libraryExpandedSongId == songId
            val switching = !collapse && st.libraryExpandedSongId != null && st.libraryExpandedSongId != songId
            st.copy(
                libraryExpandedSongId = if (collapse) null else songId,
                libraryAiExpandedSongId = if (collapse || switching) null else st.libraryAiExpandedSongId,
                libraryExpandedPlanId = if (collapse || switching) null else st.libraryExpandedPlanId,
            )
        }
    }

    fun toggleLibraryAiExpanded(songId: String) {
        _uiState.update { st ->
            st.copy(
                libraryAiExpandedSongId = if (st.libraryAiExpandedSongId == songId) null else songId,
            )
        }
    }

    fun toggleLibraryPlanExpanded(planId: String) {
        _uiState.update { st ->
            st.copy(
                libraryExpandedPlanId = if (st.libraryExpandedPlanId == planId) null else planId,
            )
        }
    }

    fun saveSettings(apiKey: String, baseUrl: String, model: String, skillLevel: String) {
        viewModelScope.launch {
            settingsStore.update(apiKey = apiKey, baseUrl = baseUrl, model = model, skillLevel = skillLevel)
        }
    }

    fun testApiConnection() {
        val s = _uiState.value.coachSettings
        if (s.apiKey.isBlank()) {
            _uiState.update { it.copy(coachError = "请先填写 API Key", coachInfo = null) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(coachError = null, coachInfo = "正在测试…") }
            val r = deepSeek.ping(s.baseUrl, s.apiKey, s.model)
            _uiState.update {
                if (r.isSuccess) it.copy(coachInfo = "连接测试成功", coachError = null)
                else it.copy(coachError = r.exceptionOrNull()?.message, coachInfo = null)
            }
        }
    }

    fun dismissAnalyzeSuggestion() = practiceSession.dismissAnalyzeSuggestion()

    fun analyzeLastPhrase() {
        sendCoachMessage(coachAgent.defaultAnalyzePrompt(), isAnalyze = true)
    }

    fun sendCoachMessage(message: String = _uiState.value.coachInput, isAnalyze: Boolean = false) {
        val text = message.trim()
        if (text.isEmpty() || _uiState.value.coachStreaming) return
        val userMsg = UiChatMessage("user", text)
        _uiState.update {
            it.copy(
                coachMessages = it.coachMessages + userMsg,
                coachInput = if (isAnalyze) it.coachInput else "",
                coachStreaming = true,
                coachStreamText = "",
                coachError = null,
                coachInfo = null,
            )
        }
        practiceSession.dismissAnalyzeSuggestion()
        val settings = _uiState.value.coachSettings
        val summary = PracticeContextBuilder.build(practiceSession.snapshot(), lastDrillResult)
        val summaryJson = PracticeContextBuilder.toJson(summary)
        val history = _uiState.value.coachMessages.dropLast(1)

        streamJob?.cancel()
        streamJob = viewModelScope.launch {
            try {
                var full = ""
                coachAgent.streamCoachReply(
                    settings.baseUrl,
                    settings.apiKey,
                    settings.model,
                    settings.skillLevel,
                    summaryJson,
                    text,
                    history,
                ).collect { chunk ->
                    full += chunk
                    _uiState.update { it.copy(coachStreamText = full) }
                }
                _uiState.update {
                    it.copy(
                        coachMessages = it.coachMessages + UiChatMessage("assistant", full),
                        coachStreaming = false,
                        coachStreamText = "",
                    )
                }
            } catch (e: Exception) {
                val msg = e.message.orEmpty()
                // 绝不把整段 JSON 当错误展示
                val friendly = if (msg.trimStart().startsWith("{")) {
                    "教练回复失败，请检查网络或 API Key"
                } else {
                    msg.ifBlank { "教练回复失败" }
                }
                _uiState.update {
                    it.copy(coachStreaming = false, coachStreamText = "", coachError = friendly)
                }
            }
        }
    }

    fun requestDrillReview() {
        val result = songDrillEngine.takeResult() ?: lastDrillResult
        if (result == null) {
            _uiState.update { it.copy(coachError = "请先完成一轮跟练") }
            return
        }
        lastDrillResult = result
        updateContextPreview()
        sendCoachMessage(coachAgent.drillReviewPrompt(), isAnalyze = true)
    }

    fun selectSong(piece: SongPiece) = selectFullPiece(piece)

    fun selectFullPiece(piece: SongPiece, persistSelection: Boolean = true) {
        lastDrillResult = null
        songDrillEngine.load(piece)
        bindScorePlayback(piece, 0)
        _uiState.update {
            it.copy(
                importMessage = null,
                activeDrillRef = ActiveDrillRef(
                    sourceSongId = piece.id,
                    isFullPiece = true,
                    label = "整曲跟练",
                ),
            )
        }
        if (persistSelection) {
            viewModelScope.launch { songLibrary.setLastSelected(piece.id) }
        }
    }

    fun selectPracticeDrill(source: SongPiece, plan: PracticePlan, drill: PracticeDrill) {
        val sliced = source.sliceForDrill(drill, plan.id)
        if (sliced.timedNotes.isEmpty()) {
            _uiState.update {
                it.copy(importMessage = "该练习片段无有效音符，请重新分析或换一条")
            }
            return
        }
        lastDrillResult = null
        songDrillEngine.load(sliced)
        bindScorePlayback(sliced, 0)
        _uiState.update {
            it.copy(
                importMessage = null,
                activeDrillRef = ActiveDrillRef(
                    sourceSongId = source.id,
                    isFullPiece = false,
                    planId = plan.id,
                    drillId = drill.id,
                    label = drill.title,
                ),
            )
        }
        viewModelScope.launch { songLibrary.setLastSelected(source.id) }
    }

    fun removeImportedSong(piece: SongPiece) {
        if (!piece.id.startsWith("import_")) return
        analyzeJobs.remove(piece.id)?.cancel()
        viewModelScope.launch {
            songLibrary.delete(piece.id)
            practicePlanStore.deleteAllForSong(piece.id)
            val catalog = _uiState.value.songCatalog.filterNot { it.id == piece.id }
                .ifEmpty { BuiltinSongs.all }
            _uiState.update {
                it.copy(
                    songCatalog = catalog,
                    practicePlans = it.practicePlans - piece.id,
                    analyzingSongIds = it.analyzingSongIds - piece.id,
                    analyzeErrors = it.analyzeErrors - piece.id,
                    importMessage = "已删除「${piece.title}」",
                )
            }
            val ref = _uiState.value.activeDrillRef
            if (ref?.sourceSongId == piece.id) {
                selectFullPiece(catalog.first())
            }
        }
    }

    fun reanalyzePracticePlan(songId: String) {
        val piece = findCatalogPiece(songId) ?: return
        runPieceAnalysis(piece, fromImport = false)
    }

    fun deletePracticePlan(songId: String, planId: String) {
        viewModelScope.launch {
            val next = practicePlanStore.deletePlan(songId, planId)
            _uiState.update {
                it.copy(
                    practicePlans = if (next.isEmpty()) {
                        it.practicePlans - songId
                    } else {
                        it.practicePlans + (songId to next)
                    },
                    importMessage = "已删除一版练习计划",
                )
            }
        }
    }

    private fun runPieceAnalysis(piece: SongPiece, fromImport: Boolean) {
        analyzeJobs[piece.id]?.cancel()
        val settings = _uiState.value.coachSettings
        _uiState.update {
            it.copy(
                analyzingSongIds = it.analyzingSongIds + piece.id,
                analyzeErrors = it.analyzeErrors - piece.id,
                importMessage = if (fromImport) {
                    "已保存「${piece.title}」，正在生成 AI 练习计划…"
                } else {
                    "正在重新分析「${piece.title}」…"
                },
            )
        }
        analyzeJobs[piece.id] = viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                pieceAnalyzer.analyze(
                    piece = piece,
                    baseUrl = settings.baseUrl,
                    apiKey = settings.apiKey,
                    model = settings.model,
                    skillLevel = settings.skillLevel,
                )
            }
            result.onSuccess { plan ->
                val plans = practicePlanStore.append(plan)
                _uiState.update {
                    it.copy(
                        practicePlans = it.practicePlans + (piece.id to plans),
                        analyzingSongIds = it.analyzingSongIds - piece.id,
                        analyzeErrors = it.analyzeErrors - piece.id,
                        importMessage = "「${piece.title}」练习计划已生成（共 ${plans.size} 版，可在曲库展开）",
                    )
                }
            }.onFailure { e ->
                val msg = e.message?.takeIf { m -> !m.trimStart().startsWith("{") }
                    ?: "分析失败，请检查网络或 API Key"
                _uiState.update {
                    it.copy(
                        analyzingSongIds = it.analyzingSongIds - piece.id,
                        analyzeErrors = it.analyzeErrors + (piece.id to msg),
                        importMessage = "「${piece.title}」分析失败：$msg",
                    )
                }
            }
        }
    }

    fun resetSongDrill() {
        lastDrillResult = null
        scorePlayback.pause()
        songDrillEngine.reset()
        _uiState.value.songDrill.piece?.let { bindScorePlayback(it, 0) }
    }

    fun seekSongDrill(progress: Float) {
        scorePlayback.seekToProgress(progress)
        songDrillEngine.seekToProgress(progress)
    }

    fun seekSongDrillStep(stepIndex: Int) {
        songDrillEngine.seekToStep(stepIndex)
        val tick = _uiState.value.songDrill.playheadTick
        scorePlayback.seekToTick(tick)
    }

    fun toggleScorePlayback() {
        val piece = _uiState.value.songDrill.piece ?: return
        if (_uiState.value.scorePlaying) {
            scorePlayback.pause()
        } else {
            scorePlayback.attach(piece, _uiState.value.songDrill.playheadTick)
            scorePlayback.play()
        }
    }

    fun setScoreBpm(bpm: Float) {
        scorePlayback.setBpm(bpm)
        songDrillEngine.setPracticeBpm(bpm)
    }

    fun setShowFingering(show: Boolean) {
        _uiState.update { it.copy(showFingering = show) }
    }

    fun toggleShowFingering() {
        _uiState.update { it.copy(showFingering = !it.showFingering) }
    }

    fun importMidi(uri: Uri) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                val cr = getApplication<Application>().contentResolver
                val name = cr.resolveDisplayName(uri) ?: uri.lastPathSegment ?: "导入曲谱"
                val bytes = cr.openInputStream(uri)?.use { it.readBytes() }
                    ?: return@withContext Result.failure(
                        IllegalStateException("无法打开文件"),
                    )
                ScoreFileImporter.import(bytes, name)
            }
            result.onSuccess { imported ->
                val piece = imported.piece
                songLibrary.saveImported(piece)
                songLibrary.saveImportBytes(piece.id, imported.rawBytes, imported.sourceExt)
                val steps = piece.toChordSteps().size
                val catalog = (_uiState.value.songCatalog + piece).distinctBy { it.id }
                _uiState.update {
                    it.copy(
                        songCatalog = catalog,
                        importMessage = "已保存「${piece.title}」· ${piece.timedNotes.size} 音 · $steps 步",
                    )
                }
                selectFullPiece(piece)
                runPieceAnalysis(piece, fromImport = true)
            }.onFailure { e ->
                _uiState.update { it.copy(importMessage = "导入失败：${e.message}") }
            }
        }
    }

    private fun android.content.ContentResolver.resolveDisplayName(uri: Uri): String? = runCatching {
        query(uri, null, null, null, null)?.use { c ->
            if (!c.moveToFirst()) return null
            val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx < 0) return null
            c.getString(idx)
        }
    }.getOrNull()

    override fun onCleared() {
        streamJob?.cancel()
        autoRefreshJob?.cancel()
        idleTickJob?.cancel()
        holdTickJob?.cancel()
        analyzeJobs.values.forEach { it.cancel() }
        analyzeJobs.clear()
        scorePlayback.release()
        stopScan()
        session.disconnect()
        super.onCleared()
    }
}
