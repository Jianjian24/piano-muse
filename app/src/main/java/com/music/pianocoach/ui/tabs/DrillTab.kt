package com.music.pianocoach.ui.tabs

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.pianocoach.drill.PracticeDrill
import com.music.pianocoach.drill.PracticeHandFilter
import com.music.pianocoach.drill.PracticePlan
import com.music.pianocoach.drill.SongPiece
import com.music.pianocoach.drill.totalTicks
import com.music.pianocoach.ui.MainViewModel
import com.music.pianocoach.ui.PianoRollView
import com.music.pianocoach.ui.PianoUiState
import com.music.pianocoach.ui.theme.MuseColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DrillTab(
    state: PianoUiState,
    viewModel: MainViewModel,
    fullscreen: Boolean = false,
    onExitFullscreen: () -> Unit = {},
) {
    val drill = state.songDrill
    val showLibrary = state.showSongLibrary
    var scrubbing by remember { mutableStateOf(false) }
    var scrubValue by remember { mutableFloatStateOf(0f) }
    val pickMidi = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            viewModel.importMidi(uri)
            viewModel.setShowSongLibrary(true)
        }
    }

    val bg = if (fullscreen) MuseColors.WaterfallBg else Color.Transparent
    val onBg = if (fullscreen) MuseColors.Ivory else MaterialTheme.colorScheme.onSurface
    val playPulse = rememberInfiniteTransition(label = "playPulse")
    val playGlow by playPulse.animateFloat(
        initialValue = 0.72f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "playGlow",
    )
    val totalTicks = (drill.piece?.totalTicks() ?: 1).coerceAtLeast(1)
    val progress = (drill.playheadTick.toFloat() / totalTicks).coerceIn(0f, 1f)
    val sliderValue = if (scrubbing) scrubValue else progress
    val titleText = when {
        state.activeDrillRef?.isFullPiece == false ->
            state.activeDrillRef.label.ifBlank { drill.piece?.title ?: "跟练" }
        else -> drill.piece?.title ?: "Play"
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(bg)
            .then(if (fullscreen) Modifier.statusBarsPadding() else Modifier)
            .padding(if (fullscreen) 6.dp else 12.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (fullscreen) {
                IconButton(onClick = onExitFullscreen) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "退出", tint = onBg)
                }
            }
            Text(
                text = titleText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = onBg,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
            )
            OutlinedButton(onClick = { viewModel.setShowSongLibrary(!showLibrary) }) {
                Text(if (showLibrary) "收起" else "曲库", fontSize = 13.sp)
            }
            if (drill.piece != null && !showLibrary) {
                OutlinedButton(
                    onClick = { viewModel.resetSongDrill() },
                    modifier = Modifier.padding(start = 4.dp),
                ) { Text("重开", fontSize = 13.sp) }
            }
        }

        if (showLibrary) {
            SongLibraryPanel(
                state = state,
                viewModel = viewModel,
                onBg = onBg,
                onPickMidi = { pickMidi.launch(arrayOf("audio/midi", "audio/mid", "*/*")) },
                onCloseLibrary = { viewModel.setShowSongLibrary(false) },
                modifier = Modifier.weight(1f, fill = true),
            )
            return
        }

        val piece = drill.piece
        if (piece == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("请打开曲库选择或导入 MIDI", color = onBg)
            }
            return
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = { viewModel.toggleScorePlayback() },
                modifier = Modifier.padding(end = 2.dp),
            ) {
                Icon(
                    imageVector = if (state.scorePlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (state.scorePlaying) "暂停" else "播放",
                    tint = MuseColors.PlayAccent.copy(
                        alpha = if (state.scorePlaying) playGlow else 1f,
                    ),
                )
            }
            Text(
                "${drill.stepIndex.coerceAtMost(drill.totalSteps)}/${drill.totalSteps}",
                color = onBg.copy(alpha = 0.7f),
                fontSize = 11.sp,
                modifier = Modifier.padding(end = 6.dp),
            )
            Slider(
                value = sliderValue,
                onValueChange = {
                    scrubbing = true
                    scrubValue = it
                    viewModel.seekSongDrill(it)
                },
                onValueChangeFinished = {
                    viewModel.seekSongDrill(scrubValue)
                    scrubbing = false
                },
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = MuseColors.PlayAccent,
                    activeTrackColor = MuseColors.PlayAccent,
                    inactiveTrackColor = MuseColors.InkElevated,
                ),
            )
            if (drill.completed && !state.scorePlaying) {
                Button(
                    onClick = { viewModel.requestDrillReview() },
                    modifier = Modifier.padding(start = 4.dp),
                ) { Text("点评", fontSize = 12.sp) }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val showHold = !state.scorePlaying && !drill.completed && drill.expectedChord.isNotEmpty()
            if (showHold) {
                Text(
                    if (drill.remaining.isEmpty()) "按住" else "齐键",
                    color = onBg.copy(alpha = 0.65f),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(end = 4.dp),
                )
                LinearProgressIndicator(
                    progress = { drill.holdProgress },
                    modifier = Modifier
                        .weight(0.55f)
                        .padding(end = 6.dp),
                    color = MuseColors.SageLight,
                    trackColor = MuseColors.InkElevated,
                )
            }
            Text("BPM", color = onBg.copy(alpha = 0.7f), fontSize = 11.sp)
            TextButton(
                onClick = { viewModel.setScoreBpm(state.scoreBpm - 5f) },
                enabled = state.scoreBpm > 40f,
            ) { Text("−", fontSize = 16.sp, color = onBg) }
            Text(
                "${state.scoreBpm.toInt()}",
                color = onBg,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 2.dp),
            )
            TextButton(
                onClick = { viewModel.setScoreBpm(state.scoreBpm + 5f) },
                enabled = state.scoreBpm < 200f,
            ) { Text("+", fontSize = 16.sp, color = onBg) }
            Slider(
                value = state.scoreBpm,
                onValueChange = { viewModel.setScoreBpm(it) },
                valueRange = 40f..200f,
                modifier = Modifier.weight(1f).padding(start = 2.dp),
                colors = SliderDefaults.colors(
                    thumbColor = MuseColors.Amber,
                    activeTrackColor = MuseColors.Amber,
                    inactiveTrackColor = MuseColors.InkElevated,
                ),
            )
            OutlinedButton(
                onClick = { viewModel.toggleShowFingering() },
                modifier = Modifier.padding(start = 4.dp),
            ) {
                Text(if (state.showFingering) "指法开" else "指法关", fontSize = 12.sp)
            }
        }

        PianoRollView(
            piece = piece,
            playheadTick = drill.playheadTick,
            currentChord = drill.expectedChord,
            remaining = drill.remaining,
            pressed = state.activeNotes,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            compactHeader = true,
            showFingering = state.showFingering,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SongLibraryPanel(
    state: PianoUiState,
    viewModel: MainViewModel,
    onBg: Color,
    onPickMidi: () -> Unit,
    onCloseLibrary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val expandedSongId = state.libraryExpandedSongId
    val aiExpandedSongId = state.libraryAiExpandedSongId
    val expandedPlanId = state.libraryExpandedPlanId

    Column(
        modifier
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(onClick = onPickMidi, modifier = Modifier.fillMaxWidth()) {
            Text("导入 MIDI (.mid)")
        }
        state.importMessage?.let {
            Text(it, color = onBg, style = MaterialTheme.typography.bodySmall)
        }
        Text(
            "点曲名展开；默认只露整曲。导入会自动分析一次；长按导入曲可删。",
            color = onBg.copy(alpha = 0.7f),
            style = MaterialTheme.typography.bodySmall,
        )

        state.songCatalog.forEach { piece ->
            val songExpanded = expandedSongId == piece.id
            val plans = state.practicePlans[piece.id].orEmpty()
            val analyzing = piece.id in state.analyzingSongIds
            val error = state.analyzeErrors[piece.id]
            val imported = piece.id.startsWith("import_")
            val active = state.activeDrillRef?.sourceSongId == piece.id

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (active) MuseColors.Amber.copy(alpha = 0.12f) else MuseColors.InkElevated,
                        RoundedCornerShape(10.dp),
                    )
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = { viewModel.toggleLibrarySongExpanded(piece.id) },
                                onLongClick = {
                                    if (imported) viewModel.removeImportedSong(piece)
                                },
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                buildString {
                                    append(piece.title)
                                    if (imported) append(" · 已保存")
                                    if (plans.isNotEmpty()) append(" · ${plans.size}版计划")
                                },
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(piece.description, style = MaterialTheme.typography.bodySmall)
                        }
                        Icon(
                            if (songExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = null,
                        )
                    }

                    if (songExpanded) {
                        OutlinedButton(
                            onClick = {
                                viewModel.selectFullPiece(piece)
                                onCloseLibrary()
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("整曲跟练")
                        }

                        AiPlansSection(
                            piece = piece,
                            plans = plans,
                            analyzing = analyzing,
                            error = error,
                            aiExpanded = aiExpandedSongId == piece.id,
                            expandedPlanId = expandedPlanId,
                            onToggleAi = { viewModel.toggleLibraryAiExpanded(piece.id) },
                            onTogglePlan = { id -> viewModel.toggleLibraryPlanExpanded(id) },
                            onReanalyze = { viewModel.reanalyzePracticePlan(piece.id) },
                            onDeletePlan = { planId -> viewModel.deletePracticePlan(piece.id, planId) },
                            onSelectDrill = { plan, drill ->
                                viewModel.selectPracticeDrill(piece, plan, drill)
                                onCloseLibrary()
                            },
                        )
                    }
            }
        }
    }
}

@Composable
private fun AiPlansSection(
    piece: SongPiece,
    plans: List<PracticePlan>,
    analyzing: Boolean,
    error: String?,
    aiExpanded: Boolean,
    expandedPlanId: String?,
    onToggleAi: () -> Unit,
    onTogglePlan: (String) -> Unit,
    onReanalyze: () -> Unit,
    onDeletePlan: (String) -> Unit,
    onSelectDrill: (PracticePlan, PracticeDrill) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggleAi)
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                when {
                    analyzing -> "AI 练习计划（分析中…）"
                    plans.isEmpty() -> "AI 练习计划（尚未生成）"
                    else -> "AI 练习计划（${plans.size} 版）"
                },
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
                fontSize = 14.sp,
            )
            Icon(
                if (aiExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = null,
            )
        }

        if (!aiExpanded) return

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                onClick = onReanalyze,
                enabled = !analyzing && piece.timedNotes.isNotEmpty(),
            ) {
                Text(if (plans.isEmpty()) "生成练习" else "重新分析", fontSize = 12.sp)
            }
            if (analyzing) {
                Text("调用模型中，请稍候…", fontSize = 12.sp)
            }
        }
        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
        }

        if (plans.isEmpty() && !analyzing) {
            Text(
                "导入后会自动分析；也可手动生成。不会在后台反复调用。",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        plans.forEachIndexed { index, plan ->
            val open = expandedPlanId == plan.id
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MuseColors.InkSurface, RoundedCornerShape(8.dp))
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onTogglePlan(plan.id) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            buildString {
                                append(if (index == 0) "最新 · " else "历史 · ")
                                append(formatPlanTime(plan.createdAtEpochMs))
                            },
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { onDeletePlan(plan.id) }) {
                            Text("删除", fontSize = 12.sp)
                        }
                        Icon(
                            if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = null,
                        )
                    }
                    if (open) {
                        if (plan.summary.isNotBlank()) {
                            Text(plan.summary, style = MaterialTheme.typography.bodySmall)
                        }
                        plan.layers.forEach { layer ->
                            Text(
                                layer.title,
                                fontWeight = FontWeight.Medium,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                            if (layer.drills.isEmpty()) {
                                Text("（本层暂无条目）", fontSize = 12.sp)
                            }
                            layer.drills.forEach { d ->
                                OutlinedButton(
                                    onClick = { onSelectDrill(plan, d) },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Column(Modifier.fillMaxWidth()) {
                                        Text(d.title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                        Text(
                                            buildString {
                                                if (d.goal.isNotBlank()) append(d.goal).append(" · ")
                                                append(handLabel(d.hand))
                                                append(" · tick ${d.startTick}-${d.endTick}")
                                            },
                                            fontSize = 11.sp,
                                        )
                                    }
                                }
                            }
                        }
                    }
            }
        }
    }
}

private fun handLabel(hand: PracticeHandFilter): String = when (hand) {
    PracticeHandFilter.LEFT -> "左手"
    PracticeHandFilter.RIGHT -> "右手"
    PracticeHandFilter.BOTH -> "双手"
}

private fun formatPlanTime(epochMs: Long): String {
    return SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(epochMs))
}
