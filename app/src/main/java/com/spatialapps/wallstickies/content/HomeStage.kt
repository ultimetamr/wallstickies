package com.spatialapps.wallstickies.content

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.ui.design.Button
import com.pico.spatial.ui.design.Checkbox
import com.pico.spatial.ui.design.IconButton
import com.pico.spatial.ui.design.IconButtonDefaults
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.design.TextArea
import com.pico.spatial.ui.design.TextField
import com.pico.spatial.ui.foundation.content.SpatialView
import com.pico.spatial.ui.foundation.material.backgroundMaterial
import com.pico.spatial.ui.foundation.hover.spatialHoverEffect
import com.pico.spatial.ui.foundation.hover.tween as hoverTween
import com.pico.spatial.ui.platform.Material
import com.pico.spatial.sense.plane.PlaneTrackingManager
import com.pico.spatial.sense.base.SemanticLabelType
import com.pico.spatial.tracking.controller.ControllerTrackingData
import com.pico.spatial.tracking.controller.ControllerTrackingProvider
import com.pico.spatial.tracking.hmd.HMDPose
import com.pico.spatial.tracking.hmd.HMDTrackingProvider
import com.spatialapps.wallstickies.data.local.StickyStore
import com.spatialapps.wallstickies.data.repository.RoomStickyNoteRepository
import com.spatialapps.wallstickies.data.repository.WorldAnchorRepository
import com.spatialapps.wallstickies.domain.usecase.ManageStickyNotes
import com.spatialapps.wallstickies.domain.model.TodoItem
import com.spatialapps.wallstickies.domain.model.StickyNote
import com.spatialapps.wallstickies.domain.model.StickyStyle
import com.spatialapps.wallstickies.domain.model.StickyColor
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import java.util.UUID

@Composable
fun HomeStage() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val notesRepository = remember { RoomStickyNoteRepository(StickyStore.database(context).stickyDao()) }
    val manager = remember { ManageStickyNotes(notesRepository, WorldAnchorRepository()) }
    val stageEntities = remember { mutableMapOf<String, Entity>() }
    // Keep the renderer log useful: SpatialView updates frequently, so only emit a
    // line when a note changes from missing to attached (or vice versa).
    val renderedNoteStates = remember { mutableMapOf<String, String>() }
    val scope = rememberCoroutineScope()
    val notes by notesRepository.observe().collectAsState(emptyList())
    val leftControllerProvider = remember { ControllerTrackingProvider() }
    val hmdTrackingProvider = remember { HMDTrackingProvider() }
    val leftControllerData by leftControllerProvider.dataFlow.collectAsState(ControllerTrackingData(null, null, 0L))
    var leftTriggerSequence by remember { mutableIntStateOf(0) }
    var interactionMessage by remember { mutableStateOf("正在等待左手柄空间姿态…") }
    var hintVisible by remember { mutableStateOf(true) }
    var restoredPositions by remember { mutableStateOf<Map<String, Vector3>>(emptyMap()) }
    var restoredRotations by remember { mutableStateOf<Map<String, EulerAngles>>(emptyMap()) }
    var draftPosition by remember { mutableStateOf(Vector3(0f, 1.5f, -1.2f)) }
    var draftRotation by remember { mutableStateOf(EulerAngles(0f, 0f, 0f)) }
    // The Stage origin is at the user's feet. Keep transient UI near eye height;
    // only saved notes use the ray-hit world position.
    val defaultEditorPosition = Vector3(0f, 1.45f, -1.2f)
    var editorPosition by remember { mutableStateOf(defaultEditorPosition) }
    var statusPosition by remember { mutableStateOf(Vector3(0f, 1.85f, -1.2f)) }
    var hmdPose by remember { mutableStateOf<HMDPose?>(null) }
    var editingNote by remember { mutableStateOf<StickyNote?>(null) }
    var editorVisible by remember { mutableStateOf(false) }
    var ignoreAnchorUntilTriggerRelease by remember { mutableStateOf(true) }
    var appActive by remember { mutableStateOf(true) }
    var selectionGeneration by remember { mutableIntStateOf(0) }
    val editorOpen by rememberUpdatedState(editorVisible)
    val anchorSelectionBlocked by rememberUpdatedState(ignoreAnchorUntilTriggerRelease)
    val latestNotes by rememberUpdatedState(notes)
    // Keep surface discovery active while the mixed-reality Stage is visible. The
    // anchor remains the source of truth after a note has been saved.
    DisposableEffect(editorVisible, appActive) {
        if (appActive && !editorVisible) PlaneTrackingManager.start()
        onDispose { PlaneTrackingManager.stop() }
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                    appActive = false
                    selectionGeneration++
                    editorVisible = false
                    hintVisible = false
                    ignoreAnchorUntilTriggerRelease = true
                    stageEntities["sticky"]?.enabled = false
                    stageEntities["placement_hint"]?.enabled = false
                }
                Lifecycle.Event.ON_RESUME -> {
                    appActive = true
                    ignoreAnchorUntilTriggerRelease = true
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    DisposableEffect(Unit) {
        onDispose {
            stageEntities.values.forEach { entity -> runCatching { entity.destroy() } }
            stageEntities.clear()
        }
    }
    // A loaded anchor can arrive after the initial all-anchor call. Apply its
    // current pose only when it belongs to a note that is still in Room.
    DisposableEffect(manager) {
        val subscription = manager.subscribeAnchorUpdates { anchor ->
            val note = latestNotes.firstOrNull { it.anchorUuid == anchor.anchorUUID.toString() }
                ?: return@subscribeAnchorUpdates
            scope.launch {
                restoredPositions = restoredPositions + (note.id to anchor.transform.position)
                restoredRotations = restoredRotations + (note.id to anchor.transform.rotation)
                Log.i(
                    RESTORE_TAG,
                    "note id=${note.id} anchor=${note.anchorUuid} restored=true source=anchor_event position=${anchor.transform.position}",
                )
            }
        }
        onDispose { subscription.cancel() }
    }
    val leftTriggerListener = remember(scope) {
        var wasPressed = false
        ControllerTrackingProvider.ControllerActionListener { action ->
            val pressed = action.left.triggerPressed
            if (!pressed) scope.launch { ignoreAnchorUntilTriggerRelease = false }
            if (pressed && !wasPressed && appActive && !editorOpen && !anchorSelectionBlocked) scope.launch {
                interactionMessage = "已收到左扳机，正在检测墙面或桌面…"
                leftTriggerSequence++
            }
            wasPressed = pressed
        }
    }
    DisposableEffect(leftControllerProvider, appActive) {
        if (appActive) {
            leftControllerProvider.addControllerActionListener(leftTriggerListener)
            leftControllerProvider.start()
            Log.i(TAG, "left controller input started")
        }
        onDispose {
            leftControllerProvider.removeControllerActionListener(leftTriggerListener)
            leftControllerProvider.stop()
        }
    }
    DisposableEffect(hmdTrackingProvider, appActive) {
        if (appActive) hmdTrackingProvider.start()
        onDispose {
            hmdTrackingProvider.stop()
        }
    }
    LaunchedEffect(hmdTrackingProvider) {
        hmdTrackingProvider.dataFlow.collect { hmdPose = it.hmdPose }
    }
    LaunchedEffect(hmdPose, editorVisible) {
        hmdPose?.let { pose ->
            val forward = pose.rotation.rotateVector(Vector3.BACK)
            // Stage coordinates are meters. Temporary panels stay in front of the HMD;
            // anchored notes keep their own world-space transforms.
            editorPosition = Vector3(
                pose.position.x + forward.x * 0.75f,
                pose.position.y + forward.y * 0.75f,
                pose.position.z + forward.z * 0.75f,
            )
            statusPosition = Vector3(
                pose.position.x + forward.x * 0.75f,
                pose.position.y + forward.y * 0.75f + 0.35f,
                pose.position.z + forward.z * 0.75f,
            )
        }
    }
    LaunchedEffect(notes) {
        if (notes.isEmpty()) {
            Log.i(RESTORE_TAG, "restore skipped: Room contains 0 notes")
            restoredPositions = emptyMap()
            restoredRotations = emptyMap()
            return@LaunchedEffect
        }
        Log.i(
            RESTORE_TAG,
            "restore started: roomNotes=${notes.size}; loading all anchors owned by this app",
        )
        // WorldTrackingManager is Full-Space-only; wait for Stage activation before restore.
        delay(1_000)
        val recoveredPositions = mutableMapOf<String, Vector3>()
        val recoveredRotations = mutableMapOf<String, EulerAngles>()
        repeat(10) { attempt ->
            val anchors = manager.restore()
            Log.i(
                RESTORE_TAG,
                "restore attempt=${attempt + 1}/10 roomNotes=${notes.size} returnedAll=${anchors.size} anchorUuids=${anchors.keys.joinToString()}",
            )
            val positions = notes.mapNotNull { note ->
                anchors[note.anchorUuid]?.transform?.position?.let { note.id to it }
            }.toMap()
            val rotations = notes.mapNotNull { note ->
                anchors[note.anchorUuid]?.transform?.rotation?.let { note.id to it }
            }.toMap()
            recoveredPositions.putAll(positions)
            recoveredRotations.putAll(rotations)
            restoredPositions = recoveredPositions.toMap()
            restoredRotations = recoveredRotations.toMap()
            notes.forEach { note ->
                val position = recoveredPositions[note.id]
                if (position == null) {
                    Log.w(
                        RESTORE_TAG,
                        "note id=${note.id} anchor=${note.anchorUuid} restored=false reason=anchor_not_returned",
                    )
                } else {
                    Log.i(
                        RESTORE_TAG,
                        "note id=${note.id} anchor=${note.anchorUuid} restored=true position=$position",
                    )
                }
            }
            if (recoveredPositions.size == notes.size) return@LaunchedEffect
            delay(500)
        }
        Log.w(
            RESTORE_TAG,
            "restore finished incomplete: restored=${recoveredPositions.size}/${notes.size}",
        )
    }
    LaunchedEffect(leftControllerData.left) {
        if (leftControllerData.left != null && interactionMessage == "正在等待左手柄空间姿态…") {
            interactionMessage = "左手柄已就绪：指向已识别墙面或桌面，再按扳机。"
        }
    }
    LaunchedEffect(Unit) {
        delay(2_000)
        if (leftControllerData.left == null) {
            interactionMessage = "未获取左手柄空间姿态：请唤醒左手柄，并开启头显的位置追踪。"
        }
    }
    LaunchedEffect(Unit) {
        delay(3_000)
        hintVisible = false
    }
    LaunchedEffect(leftTriggerSequence) {
        if (leftTriggerSequence == 0) return@LaunchedEffect
        if (!appActive || editorVisible || ignoreAnchorUntilTriggerRelease) return@LaunchedEffect
        val generation = selectionGeneration
        val pose = leftControllerData.left ?: run {
            interactionMessage = "未获取左手柄空间姿态：本次点击未创建便签。"
            Log.w(TAG, "left trigger ignored: controller pose unavailable")
            return@LaunchedEffect
        }
        val origin = pose.position
        val direction = pose.rotation.rotateVector(Vector3.BACK)
        val hit = findSurfaceRayHit(origin, direction)
        if (!appActive || generation != selectionGeneration || editorVisible || ignoreAnchorUntilTriggerRelease) return@LaunchedEffect
        hit?.let { hit ->
            Log.i(TAG, "left trigger surface hit=${hit.position} type=${hit.semantic}")
            interactionMessage = "已命中${hit.semantic}，已打开便签编辑器。"
            hintVisible = false
            draftPosition = hit.position
            draftRotation = cameraFacingRotation(hmdPose?.rotation?.rotateVector(Vector3.BACK) ?: direction)
            ignoreAnchorUntilTriggerRelease = true
            editingNote = null
            editorVisible = true
        } ?: run {
            interactionMessage = "未命中已识别墙面或桌面：请先环顾房间并对准真实表面。"
            hintVisible = true
            Log.i(TAG, "left trigger ignored: no wall/table hit")
            delay(1_000)
            hintVisible = false
        }
    }
    SpatialView(
        update = { content, attachments ->
            attachments.entity("sticky")?.apply {
                stageEntities["sticky"] = this
                enabled = editorVisible
                if (editorVisible) {
                    components[TransformComponent::class.java]?.apply {
                        setPosition(editorPosition)
                        hmdPose?.let { setQuaternion(it.rotation) }
                    }
                    content.addEntity(this)
                }
            }
            attachments.entity("placement_hint")?.apply {
                stageEntities["placement_hint"] = this
                enabled = hintVisible
                if (hintVisible) {
                    components[TransformComponent::class.java]?.apply {
                        setPosition(statusPosition)
                        hmdPose?.let { setQuaternion(it.rotation) }
                    }
                    content.addEntity(this)
                }
            }
            notes.forEach { note ->
                val anchorPosition = restoredPositions[note.id]
                if (anchorPosition == null) {
                    logRenderState(renderedNoteStates, note.id, "SKIPPED_NO_RECOVERED_ANCHOR")
                    return@forEach
                }
                val entity = attachments.entity(note.id)
                if (entity == null) {
                    logRenderState(renderedNoteStates, note.id, "ATTACHMENT_ENTITY_UNAVAILABLE")
                    return@forEach
                }
                entity.apply {
                    stageEntities[note.id] = this
                    components[TransformComponent::class.java]?.apply {
                        setPosition(anchorPosition)
                        setEulerAngles(restoredRotations[note.id] ?: EulerAngles())
                    }
                    content.addEntity(this)
                    logRenderState(renderedNoteStates, note.id, "ATTACHED position=$anchorPosition")
                }
            }
        },
        initial = { content, attachments ->
            attachments.entity(id = "sticky")?.apply {
                components[TransformComponent::class.java]?.apply {
                    setPosition(editorPosition)
                    hmdPose?.let { setQuaternion(it.rotation) }
                }
                content.addEntity(this)
            }
            attachments.entity(id = "placement_hint")?.apply {
                components[TransformComponent::class.java]?.apply {
                    setPosition(statusPosition)
                    hmdPose?.let { setQuaternion(it.rotation) }
                }
                content.addEntity(this)
            }
        },
        attachments = {
            if (hintVisible) {
            AttachmentPanel(id = "placement_hint") {
                if (hintVisible) PlacementHint(interactionMessage)
            }
            }
            notes.forEach { note ->
                AttachmentPanel(id = note.id) {
                    StickyCard(
                        note = note,
                        onEdit = {
                            ignoreAnchorUntilTriggerRelease = true
                            editingNote = note
                            editorVisible = true
                        },
                        onDelete = {
                            ignoreAnchorUntilTriggerRelease = true
                            editorVisible = false
                            scope.launch { manager.delete(note) }
                        },
                    )
                }
            }
            AttachmentPanel(id = "sticky") {
                if (editorVisible) {
                    StickyEditor(
                        editing = editingNote,
                        initialPlacement = draftPosition,
                        initialRotation = draftRotation,
                        manager = manager,
                        onNoteSaved = { note, position, rotation ->
                            restoredPositions = restoredPositions + (note.id to position)
                            restoredRotations = restoredRotations + (note.id to rotation)
                        },
                        onFinishedEditing = {
                            ignoreAnchorUntilTriggerRelease = true
                            editingNote = null
                            editorVisible = false
                        },
                    )
                }
            }
        },
    )
}

@Composable
private fun PlacementHint(message: String) {
    Column(
        modifier = Modifier.size(440.dp, 150.dp).clip(RoundedCornerShape(24.dp))
            .backgroundMaterial(true, Material.Regular).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("墙面永久空间便签", style = PicoTheme.typography.titleLarge)
        Text("用左手柄射线指向已识别的墙面或桌面，按下扳机创建便签。")
        Text(message)
    }
}

@Composable
private fun StickyEditor(
    editing: StickyNote?,
    initialPlacement: Vector3,
    initialRotation: EulerAngles,
    manager: ManageStickyNotes,
    onNoteSaved: (StickyNote, Vector3, EulerAngles) -> Unit,
    onFinishedEditing: () -> Unit,
) {
    var title by remember { mutableStateOf("今日提醒") }
    var body by remember { mutableStateOf("看完这张便签后开始下一项任务。") }
    val todos = remember { mutableStateListOf<TodoItem>() }
    var todoText by remember { mutableStateOf("") }
    var style by remember { mutableStateOf(StickyStyle.SOLID) }
    var color by remember { mutableStateOf(StickyColor.YELLOW) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    LaunchedEffect(editing?.id) {
        editing?.let {
            title = it.title
            body = it.body
            todos.clear()
            todos.addAll(it.todos)
            style = it.style
            color = it.color
        }
    }
    Column(
        modifier = Modifier.size(520.dp, 460.dp).clip(RoundedCornerShape(24.dp))
            .then(noteBackground(style, color))
            .spatialHoverEffect().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text("墙面便签", style = PicoTheme.typography.titleLarge)
                Text("编辑器会跟随当前视线方向")
            }
            IconButton(
                onClick = onFinishedEditing,
                modifier = Modifier.size(56.dp),
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = Color.Transparent,
                    contentColor = PicoTheme.colorScheme.labelPrimary,
                ),
            ) { Text("×", style = PicoTheme.typography.titleLarge) }
        }
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(if (editing == null) "此便签会固定在刚才命中的墙面或桌面。" else "编辑内容不会改变原来的空间位置。")
            TextField(value = title, onValueChange = { title = it }, placeholder = { Text("标题") }, modifier = Modifier.fillMaxWidth())
            TextArea(value = body, onValueChange = { body = it }, placeholder = { Text("内容") }, modifier = Modifier.fillMaxWidth().height(100.dp))
            todos.forEachIndexed { index, todo ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Checkbox(checked = todo.done, onCheckedChange = { checked ->
                        todos[index] = todo.copy(done = checked)
                        editing?.let { note -> scope.launch { manager.update(note.copy(todos = todos.toList())) } }
                    })
                    Text(todo.text, modifier = Modifier.weight(1f))
                    Button(onClick = {
                        todos.remove(todo)
                        editing?.let { note -> scope.launch { manager.update(note.copy(todos = todos.toList())) } }
                    }) { Text("移除") }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextField(value = todoText, onValueChange = { todoText = it }, placeholder = { Text("添加待办") }, modifier = Modifier.weight(1f))
                Button(onClick = {
                    if (todoText.isNotBlank()) {
                        todos.add(TodoItem(UUID.randomUUID().toString(), todoText))
                        todoText = ""
                        editing?.let { note -> scope.launch { manager.update(note.copy(todos = todos.toList())) } }
                    }
                }) { Text("添加") }
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(modifier = Modifier.weight(1f), onClick = { style = if (style == StickyStyle.FROSTED) StickyStyle.SOLID else StickyStyle.FROSTED }) {
                Text(if (style == StickyStyle.FROSTED) "磨砂样式" else "纯色样式")
            }
            Button(modifier = Modifier.weight(1f), onClick = { color = StickyColor.entries[(color.ordinal + 1) % StickyColor.entries.size] }) { Text("配色：${color.name}") }
            Button(modifier = Modifier.weight(1f), onClick = {
                scope.launch {
                    if (editing == null) {
                        manager.create(UUID.randomUUID().toString(), title, body, initialPlacement, initialRotation, todos.toList(), style, color)
                            ?.let { onNoteSaved(it, initialPlacement, initialRotation) }
                        onFinishedEditing()
                    } else {
                        manager.update(editing.copy(title = title, body = body, todos = todos.toList(), style = style, color = color))
                        onFinishedEditing()
                    }
                }
            }) { Text(if (editing == null) "创建" else "保存") }
        }
    }
}

@Composable
private fun StickyCard(note: StickyNote, onEdit: () -> Unit, onDelete: () -> Unit) {
    Column(
        modifier = Modifier.size(360.dp, 240.dp).clip(RoundedCornerShape(20.dp))
            .then(noteBackground(note.style, note.color))
            .spatialHoverEffect {
                animation(hoverTween(durationMillis = 200, easing = FastOutSlowInEasing)) {
                    scale(if (it.isActive) 1.03f else 1f)
                    alpha(if (it.isActive) 1f else 0.92f)
                }
            }.padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(note.title, style = PicoTheme.typography.titleLarge)
        Text(note.body)
        note.todos.forEach { todo -> Text((if (todo.done) "✓ " else "○ ") + todo.text) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onEdit) { Text("编辑") }
            Button(modifier = Modifier.height(56.dp), onClick = onDelete) { Text("删除") }
        }
    }
}

@Composable
private fun noteBackground(style: StickyStyle, color: StickyColor): Modifier =
    if (style == StickyStyle.FROSTED) {
        Modifier.backgroundMaterial(true, Material.Regular)
    } else {
        // Six explicit user-selectable sticky colours; solid mode intentionally does not layer glass.
        Modifier.background(stickyColor(color))
    }

private fun stickyColor(color: StickyColor): Color = when (color) {
    StickyColor.YELLOW -> Color(0xFFFFE082) // design-style: fixed-figma-color sticky palette
    StickyColor.PINK -> Color(0xFFF8BBD0) // design-style: fixed-figma-color sticky palette
    StickyColor.BLUE -> Color(0xFFB3E5FC) // design-style: fixed-figma-color sticky palette
    StickyColor.GREEN -> Color(0xFFC8E6C9) // design-style: fixed-figma-color sticky palette
    StickyColor.PURPLE -> Color(0xFFE1BEE7) // design-style: fixed-figma-color sticky palette
    StickyColor.ORANGE -> Color(0xFFFFCC80) // design-style: fixed-figma-color sticky palette
}

private data class SurfaceHit(
    val position: Vector3,
    val semantic: SemanticLabelType,
)

private suspend fun findSurfaceRayHit(origin: Vector3, direction: Vector3): SurfaceHit? =
    PlaneTrackingManager.loadAllAnchors()
        .asSequence()
        .filter { it.semantics == SemanticLabelType.WALL || it.semantics == SemanticLabelType.TABLE }
        .flatMap { plane ->
            val rotation = plane.transform.rotation.toQuat()
            val center = plane.transform.position
            val vertices = plane.vertices.map { vertex ->
                rotation.rotateVector(vertex).let { Vector3(it.x + center.x, it.y + center.y, it.z + center.z) }
            }
            plane.indices.chunked(3).asSequence().mapNotNull { triangle ->
                if (triangle.size != 3 || triangle.any { it !in vertices.indices }) null
                else rayTriangleDistance(origin, direction, vertices[triangle[0]], vertices[triangle[1]], vertices[triangle[2]])
                    ?.let { distance -> distance to plane.semantics }
            }
        }
        .filter { it.first > 0f }
        .minByOrNull { it.first }
        ?.let { (distance, semantic) ->
            SurfaceHit(
                Vector3(origin.x + direction.x * distance, origin.y + direction.y * distance, origin.z + direction.z * distance),
                semantic,
            )
        }

private fun cameraFacingRotation(viewDirection: Vector3): EulerAngles =
    EulerAngles(0f, Math.toDegrees(kotlin.math.atan2(-viewDirection.x, -viewDirection.z).toDouble()).toFloat(), 0f)

private fun subtract(left: Vector3, right: Vector3) = Vector3(left.x - right.x, left.y - right.y, left.z - right.z)

private fun cross(left: Vector3, right: Vector3) = Vector3(
    left.y * right.z - left.z * right.y,
    left.z * right.x - left.x * right.z,
    left.x * right.y - left.y * right.x,
)

private fun rayTriangleDistance(origin: Vector3, direction: Vector3, a: Vector3, b: Vector3, c: Vector3): Float? {
    fun dot(left: Vector3, right: Vector3) = left.x * right.x + left.y * right.y + left.z * right.z
    val edge1 = subtract(b, a)
    val edge2 = subtract(c, a)
    val p = cross(direction, edge2)
    val determinant = dot(edge1, p)
    if (kotlin.math.abs(determinant) < 0.00001f) return null
    val inverse = 1f / determinant
    val t = subtract(origin, a)
    val u = dot(t, p) * inverse
    if (u !in 0f..1f) return null
    val q = cross(t, edge1)
    val v = dot(direction, q) * inverse
    if (v < 0f || u + v > 1f) return null
    return dot(edge2, q) * inverse
}

private const val TAG = "WallStickiesInput"
private const val RESTORE_TAG = "WallStickiesRestore"
private const val RENDER_TAG = "WallStickiesRender"

private fun logRenderState(states: MutableMap<String, String>, noteId: String, state: String) {
    if (states.put(noteId, state) != state) {
        Log.i(RENDER_TAG, "note id=$noteId display=$state")
    }
}
