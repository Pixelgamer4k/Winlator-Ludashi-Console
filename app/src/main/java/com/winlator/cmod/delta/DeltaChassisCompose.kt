package com.winlator.cmod.delta

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.winlator.cmod.inputcontrols.ExternalController
import kotlinx.coroutines.delay
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

// ── Design tokens ──────────────────────────────────────────────────────────
private val BgDeep = Color(0xFF050608)
private val PanelTop = Color(0xFF1C1F26)
private val PanelMid = Color(0xFF14171C)
private val PanelBot = Color(0xFF0C0E12)
private val Well = Color(0xFF0A0B0E)
private val Edge = Color(0xFF3A3F4A)
private val EdgeSoft = Color(0xFF2A2E36)
private val TextMain = Color(0xFFF2F4F8)
private val TextMuted = Color(0xFF9AA3B2)
private val Purple = Color(0xFFB24BFF)
private val PurpleDim = Color(0xFF6B2A99)
private val AGreen = Color(0xFF3DD068)
private val BRed = Color(0xFFE84B4B)
private val XBlue = Color(0xFF3D8BFF)
private val YYellow = Color(0xFFF0C83C)

/**
 * Premium portrait chassis — pure Compose (no bitmap skin).
 * Multi-touch: stick + face + shoulders + trackpad work together.
 */
@Composable
fun DeltaChassisScreen() {
    // Right-stick pad inertia decay
    var padVx by remember { mutableStateOf(0f) }
    var padVy by remember { mutableStateOf(0f) }
    var stickX by remember { mutableStateOf(0f) }
    var stickY by remember { mutableStateOf(0f) }
    val padHeld = remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(16)
            if (!padHeld.value) {
                padVx *= 0.90f
                padVy *= 0.90f
                if (hypot(padVx.toDouble(), padVy.toDouble()) < 0.02) {
                    padVx = 0f
                    padVy = 0f
                }
                ChassisInputBridge.get().setStick(stickX, stickY, padVx, padVy)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDeep)
    ) {
        // Soft vignette / depth
        Canvas(Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.verticalGradient(
                    listOf(PanelTop.copy(alpha = 0.55f), PanelMid, PanelBot)
                )
            )
            // top highlight strip
            drawRect(
                brush = Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.06f), Color.Transparent)
                ),
                size = Size(size.width, size.height * 0.18f)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // ── Shoulders ──────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ShoulderButton("L2") {
                        ChassisInputBridge.get().setTrigger(it, null)
                    }
                    ShoulderButton("L1") {
                        ChassisInputBridge.get()
                            .setButton(ExternalController.IDX_BUTTON_L1, it)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ShoulderButton("R1") {
                        ChassisInputBridge.get()
                            .setButton(ExternalController.IDX_BUTTON_R1, it)
                    }
                    ShoulderButton("R2") {
                        ChassisInputBridge.get().setTrigger(null, it)
                    }
                }
            }

            // ── Mid: D-pad / system / face ──────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = true),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left: D-pad + system
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    GuideButton {
                        ChassisInputBridge.get()
                            .setButton(ExternalController.IDX_BUTTON_START, it)
                    }
                    DPadControl()
                }

                // Center system pills
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(horizontal = 4.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SysPill("VIEW") {
                            ChassisInputBridge.get()
                                .setButton(ExternalController.IDX_BUTTON_SELECT, it)
                        }
                        SysPill("MENU") {
                            ChassisInputBridge.get()
                                .setButton(ExternalController.IDX_BUTTON_START, it)
                        }
                    }
                }

                // Face diamond
                FaceCluster()
            }

            // ── Stick + Trackpad ────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(148.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AnalogStick(
                    modifier = Modifier.size(132.dp),
                    onMove = { x, y ->
                        stickX = x
                        stickY = y
                        ChassisInputBridge.get().setStick(x, y, padVx, padVy)
                    }
                )
                Trackpad(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(start = 14.dp),
                    onVelocity = { vx, vy ->
                        padVx = vx
                        padVy = vy
                        ChassisInputBridge.get().setStick(stickX, stickY, vx, vy)
                    },
                    onHeld = { held -> padHeld.value = held }
                )
            }

            // ── Brand ──────────────────────────────────────────────────
            BrandBar(Modifier.fillMaxWidth())
        }
    }
}

// ── Controls ───────────────────────────────────────────────────────────────

@Composable
private fun ShoulderButton(label: String, onPress: (Boolean) -> Unit) {
    PressableSurface(
        onPress = onPress,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .width(76.dp)
            .height(40.dp)
    ) { pressed ->
        Canvas(Modifier.fillMaxSize()) {
            val r = CornerRadius(16.dp.toPx(), 16.dp.toPx())
            drawRoundRect(
                brush = Brush.verticalGradient(
                    if (pressed) listOf(Color(0xFF2A2E38), Color(0xFF181B22))
                    else listOf(Color(0xFF2C303A), Color(0xFF1A1D24))
                ),
                cornerRadius = r
            )
            drawRoundRect(
                color = Edge,
                cornerRadius = r,
                style = Stroke(width = 1.5.dp.toPx())
            )
            // purple top accent
            drawRoundRect(
                color = Purple,
                topLeft = Offset(size.width * 0.18f, 5.dp.toPx()),
                size = Size(size.width * 0.64f, 3.5.dp.toPx()),
                cornerRadius = CornerRadius(2.dp.toPx())
            )
        }
        Text(
            text = label,
            color = TextMain,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            modifier = Modifier.align(Alignment.Center)
        )
    }
}

@Composable
private fun GuideButton(onPress: (Boolean) -> Unit) {
    PressableSurface(
        onPress = onPress,
        shape = CircleShape,
        modifier = Modifier.size(44.dp)
    ) { pressed ->
        Canvas(Modifier.fillMaxSize()) {
            val c = center
            val r = size.minDimension / 2f
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Color.White.copy(alpha = if (pressed) 0.35f else 0.18f), Color.Transparent),
                    center = c,
                    radius = r * 1.15f
                )
            )
            drawCircle(color = Color(0xFF2A2D34), radius = r * 0.92f)
            drawCircle(color = Color(0xFFE8E8EE), radius = r * 0.92f, style = Stroke(3.dp.toPx()))
            // X glyph
            val s = r * 0.32f
            drawLine(Color.White, Offset(c.x - s, c.y - s), Offset(c.x + s, c.y + s), 3.5.dp.toPx(), StrokeCap.Round)
            drawLine(Color.White, Offset(c.x + s, c.y - s), Offset(c.x - s, c.y + s), 3.5.dp.toPx(), StrokeCap.Round)
        }
    }
}

@Composable
private fun SysPill(label: String, onPress: (Boolean) -> Unit) {
    PressableSurface(
        onPress = onPress,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .width(56.dp)
            .height(28.dp)
    ) { pressed ->
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    if (pressed) Color(0xFF2A2E36) else Color(0xFF1A1D22),
                    RoundedCornerShape(10.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(label, color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun FaceCluster() {
    val sp = 34.dp
    Box(Modifier.size(sp * 3)) {
        FaceButton("Y", YYellow, Modifier.align(Alignment.TopCenter)) {
            ChassisInputBridge.get().setButton(ExternalController.IDX_BUTTON_Y, it)
        }
        FaceButton("X", XBlue, Modifier.align(Alignment.CenterStart)) {
            ChassisInputBridge.get().setButton(ExternalController.IDX_BUTTON_X, it)
        }
        FaceButton("B", BRed, Modifier.align(Alignment.CenterEnd)) {
            ChassisInputBridge.get().setButton(ExternalController.IDX_BUTTON_B, it)
        }
        FaceButton("A", AGreen, Modifier.align(Alignment.BottomCenter)) {
            ChassisInputBridge.get().setButton(ExternalController.IDX_BUTTON_A, it)
        }
    }
}

@Composable
private fun FaceButton(label: String, accent: Color, modifier: Modifier, onPress: (Boolean) -> Unit) {
    PressableSurface(
        onPress = onPress,
        shape = CircleShape,
        modifier = modifier.size(48.dp)
    ) { pressed ->
        val scale by animateFloatAsState(
            if (pressed) 0.90f else 1f,
            animationSpec = spring(dampingRatio = 0.65f, stiffness = 500f),
            label = "face"
        )
        Box(
            Modifier
                .fillMaxSize()
                .scale(scale)
                .shadow(if (pressed) 2.dp else 6.dp, CircleShape)
                .background(
                    brush = Brush.verticalGradient(
                        listOf(Color(0xFF2A2E36), Color(0xFF16181E))
                    ),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(
                    color = accent,
                    radius = size.minDimension / 2f - 3.dp.toPx(),
                    style = Stroke(width = 3.dp.toPx())
                )
            }
            Text(
                text = label,
                color = accent,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }
    }
}

@Composable
private fun DPadControl() {
    val dpadDp = 96.dp
    val bridge = ChassisInputBridge.get()
    Box(
        Modifier
            .size(dpadDp)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    fun map(p: Offset) {
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        val dx = p.x - cx
                        val dy = p.y - cy
                        val dead = min(size.width, size.height) * 0.14f
                        val up = dy < -dead
                        val downB = dy > dead
                        val left = dx < -dead
                        val right = dx > dead
                        bridge.setDpad(up, right, downB, left)
                    }
                    map(down.position)
                    drag(down.id) { change ->
                        map(change.position)
                        change.consume()
                    }
                    bridge.setDpad(false, false, false, false)
                }
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val armW = size.minDimension * 0.30f
            val armL = size.minDimension * 0.92f
            val cx = size.width / 2f
            val cy = size.height / 2f
            val cr = CornerRadius(10.dp.toPx())
            drawRoundRect(
                brush = Brush.verticalGradient(listOf(Color(0xFF2A2E36), Color(0xFF16191F))),
                topLeft = Offset(cx - armW / 2, cy - armL / 2),
                size = Size(armW, armL),
                cornerRadius = cr
            )
            drawRoundRect(
                brush = Brush.horizontalGradient(listOf(Color(0xFF2A2E36), Color(0xFF16191F))),
                topLeft = Offset(cx - armL / 2, cy - armW / 2),
                size = Size(armL, armW),
                cornerRadius = cr
            )
            drawCircle(Well, radius = armW * 0.42f, center = Offset(cx, cy))
            drawCircle(EdgeSoft, radius = armW * 0.42f, center = Offset(cx, cy), style = Stroke(1.5.dp.toPx()))
        }
    }
}

@Composable
private fun AnalogStick(modifier: Modifier, onMove: (Float, Float) -> Unit) {
    var knob by remember { mutableStateOf(Offset.Zero) }
    val onMoveState by rememberUpdatedState(onMove)

    Box(
        modifier = modifier
            .pointerInput(Unit) {
                val maxR = min(size.width, size.height) * 0.32f
                awaitEachGesture {
                    val down = awaitFirstDown()
                    fun apply(p: Offset) {
                        val c = Offset(size.width / 2f, size.height / 2f)
                        var d = p - c
                        val mag = hypot(d.x.toDouble(), d.y.toDouble()).toFloat()
                        if (mag > maxR && mag > 0f) {
                            val s = maxR / mag
                            d = Offset(d.x * s, d.y * s)
                        }
                        knob = d
                        val nx = (d.x / maxR).coerceIn(-1f, 1f)
                        val ny = (d.y / maxR).coerceIn(-1f, 1f)
                        onMoveState(nx, ny)
                    }
                    apply(down.position)
                    drag(down.id) { change ->
                        apply(change.position)
                        change.consume()
                    }
                    knob = Offset.Zero
                    onMoveState(0f, 0f)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val c = center
            val r = size.minDimension / 2f
            // outer well
            drawCircle(Well, radius = r * 0.96f)
            drawCircle(Edge, radius = r * 0.96f, style = Stroke(2.dp.toPx()))
            // purple ring
            drawCircle(
                brush = Brush.sweepGradient(
                    listOf(Purple, PurpleDim, Purple, Color(0xFFD0A0FF), Purple)
                ),
                radius = r * 0.72f,
                center = c,
                style = Stroke(width = 8.dp.toPx())
            )
            // inner dish
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Color(0xFF1A1024), Well),
                    center = c,
                    radius = r * 0.58f
                ),
                radius = r * 0.58f
            )
            // soft purple glow under knob
            val kc = c + knob
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Purple.copy(alpha = 0.35f), Color.Transparent),
                    center = kc,
                    radius = r * 0.42f
                ),
                radius = r * 0.42f,
                center = kc
            )
            // knob body
            val kr = r * 0.34f
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Color(0xFF3A2058), Color(0xFF1A0E28)),
                    center = kc + Offset(-kr * 0.2f, -kr * 0.25f),
                    radius = kr * 1.2f
                ),
                radius = kr,
                center = kc
            )
            drawCircle(Purple.copy(alpha = 0.9f), radius = kr, center = kc, style = Stroke(3.dp.toPx()))
            // gloss
            drawCircle(
                Color.White.copy(alpha = 0.18f),
                radius = kr * 0.35f,
                center = kc + Offset(-kr * 0.22f, -kr * 0.28f)
            )
            drawCircle(Color(0xFF0C0812), radius = kr * 0.22f, center = kc)
        }
    }
}

@Composable
private fun Trackpad(
    modifier: Modifier,
    onVelocity: (Float, Float) -> Unit,
    onHeld: (Boolean) -> Unit
) {
    val onVel by rememberUpdatedState(onVelocity)
    val onHeldState by rememberUpdatedState(onHeld)
    var last by remember { mutableStateOf(Offset.Zero) }
    var lastT by remember { mutableStateOf(0L) }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    onHeldState(true)
                    last = down.position
                    lastT = System.nanoTime()
                    drag(down.id) { change ->
                        val now = System.nanoTime()
                        val dt = max(1e-3f, (now - lastT) / 1e9f)
                        val dx = change.position.x - last.x
                        val dy = change.position.y - last.y
                        last = change.position
                        lastT = now
                        val inv = 1f / dt
                        val vx = ((dx * inv) / 900f * 1.35f).coerceIn(-1f, 1f)
                        val vy = ((dy * inv) / 900f * 1.35f * 0.85f).coerceIn(-1f, 1f)
                        onVel(vx, vy)
                        change.consume()
                    }
                    onHeldState(false)
                    onVel(0f, 0f)
                }
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val r = CornerRadius(18.dp.toPx())
            drawRoundRect(
                brush = Brush.verticalGradient(
                    listOf(Color(0xFF1A1C22), Color(0xFF101216))
                ),
                cornerRadius = r
            )
            drawRoundRect(EdgeSoft, cornerRadius = r, style = Stroke(1.8.dp.toPx()))
            // brushed lines
            val step = 3.5.dp.toPx()
            var y = 14.dp.toPx()
            while (y < size.height - 14.dp.toPx()) {
                drawLine(
                    Color(0xFF5A5F69).copy(alpha = 0.18f + ((y.toInt() % 9) / 90f)),
                    Offset(12.dp.toPx(), y),
                    Offset(size.width - 12.dp.toPx(), y),
                    strokeWidth = 1f
                )
                y += step
            }
            // sheen
            drawRoundRect(
                brush = Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.07f), Color.Transparent)
                ),
                topLeft = Offset(10.dp.toPx(), 10.dp.toPx()),
                size = Size(size.width - 20.dp.toPx(), size.height * 0.45f),
                cornerRadius = CornerRadius(12.dp.toPx())
            )
        }
        Text(
            text = "TRACKPAD",
            color = TextMuted.copy(alpha = 0.75f),
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.5.sp,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 10.dp)
        )
    }
}

@Composable
private fun BrandBar(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(28.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF101114)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawRoundRect(
                color = Color(0xFF3C285A).copy(alpha = 0.7f),
                cornerRadius = CornerRadius(12.dp.toPx()),
                style = Stroke(1.2.dp.toPx())
            )
            drawCircle(Purple, radius = 4.dp.toPx(), center = Offset(16.dp.toPx(), size.height / 2))
            drawCircle(Purple, radius = 4.dp.toPx(), center = Offset(size.width - 16.dp.toPx(), size.height / 2))
        }
        Text(
            text = "PC EMU  ·  DELTA PC",
            color = Color(0xFFC878FF),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Generic press surface that reports down/up (multi-touch friendly across siblings).
 */
@Composable
private fun PressableSurface(
    onPress: (Boolean) -> Unit,
    shape: androidx.compose.ui.graphics.Shape,
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.BoxScope.(pressed: Boolean) -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    val onPressState by rememberUpdatedState(onPress)
    Box(
        modifier = modifier
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    pressed = true
                    onPressState(true)
                    do {
                        val event = awaitPointerEvent()
                        val stillDown = event.changes.any { it.pressed }
                        if (!stillDown) break
                    } while (true)
                    pressed = false
                    onPressState(false)
                }
            },
        content = { content(pressed) }
    )
}
