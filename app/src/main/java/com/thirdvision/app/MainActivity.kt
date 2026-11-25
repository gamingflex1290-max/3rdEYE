package com.thirdvision.app

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val BG = Color(0xFF0B1220)
private val CARD = Color(0xFF121B2E)
private val CYAN = Color(0xFF22D3EE)
private val INDIGO = Color(0xFF6366F1)
private val MUTED = Color(0xFF94A3B8)
private val GREEN = Color(0xFF22C55E)
private val AMBER = Color(0xFFF59E0B)
private val RED = Color(0xFFEF4444)
private fun sevColor(s: Sev) = when (s) { Sev.HIGH -> RED; Sev.WARN -> AMBER; Sev.INFO -> Color(0xFF38BDF8) }
private fun verdictColor(v: String) = when (v) { "HIGH RISK" -> RED; "SUSPICIOUS" -> AMBER; else -> GREEN }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = CYAN, background = BG, surface = CARD)) { Root() }
        }
    }
}

@Composable
fun EyeView(modifier: Modifier, scanning: Boolean) {
    val inf = rememberInfiniteTransition(label = "eye")
    val wave by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(2600, easing = LinearEasing)), label = "wave")
    val look by inf.animateFloat(-1f, 1f, infiniteRepeatable(tween(1300, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "look")
    val sweep by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(1500, easing = LinearEasing)), label = "sweep")
    val blink by inf.animateFloat(
        1f, 1f,
        infiniteRepeatable(keyframes { durationMillis = 4200; 1f at 0; 1f at 3800; 0.06f at 3950; 1f at 4100 }),
        label = "blink"
    )
    Canvas(modifier) {
        val w = size.minDimension
        val c = center
        listOf(0f, 0.5f).forEach { off ->
            val p = (wave + off) % 1f
            drawCircle(CYAN.copy(alpha = (1f - p) * 0.30f), radius = w * (0.28f + p * 0.22f), center = c, style = Stroke(w * 0.012f))
        }
        if (scanning) {
            drawArc(
                CYAN.copy(alpha = 0.32f), startAngle = sweep, sweepAngle = 70f, useCenter = true,
                topLeft = Offset(c.x - w * 0.48f, c.y - w * 0.48f), size = Size(w * 0.96f, w * 0.96f)
            )
        }
        val ew = w * 0.68f
        val eh = ew * 0.22f * blink
        val path = Path().apply {
            moveTo(c.x - ew / 2, c.y)
            cubicTo(c.x - ew / 4, c.y - eh * 2, c.x + ew / 4, c.y - eh * 2, c.x + ew / 2, c.y)
            cubicTo(c.x + ew / 4, c.y + eh * 2, c.x - ew / 4, c.y + eh * 2, c.x - ew / 2, c.y)
            close()
        }
        drawPath(path, Brush.radialGradient(listOf(Color(0xFF123053), Color(0xFF0B1220)), c, ew / 2))
        clipPath(path) {
            val ic = Offset(c.x + look * ew * 0.10f, c.y)
            drawCircle(Brush.radialGradient(listOf(Color(0xFF67E8F9), CYAN, Color(0xFF0369A1)), ic, ew * 0.2f), ew * 0.2f, ic)
            drawCircle(Color(0xFF06101F), ew * 0.085f, ic)
            drawCircle(Color.White.copy(alpha = 0.85f), ew * 0.03f, Offset(ic.x - ew * 0.05f, ic.y - ew * 0.06f))
        }
        drawPath(
            path,
            Brush.linearGradient(listOf(CYAN, INDIGO), Offset(c.x - ew / 2, c.y), Offset(c.x + ew / 2, c.y)),
            style = Stroke(w * 0.022f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

@Composable
fun Gauge(score: Int, color: Color, label: String) {
    val prog = remember { Animatable(0f) }
    LaunchedEffect(score) { prog.animateTo(score / 100f, tween(1400, easing = FastOutSlowInEasing)) }
    Box(Modifier.size(160.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val m = size.minDimension
            val st = Stroke(m * 0.09f, cap = StrokeCap.Round)
            val pad = m * 0.06f
            val tl = Offset(pad, pad)
            val sz = Size(m - pad * 2, m - pad * 2)
            drawArc(Color(0xFF1E293B), 135f, 270f, false, tl, sz, style = st)
            drawArc(color, 135f, 270f * prog.value, false, tl, sz, style = st)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${(prog.value * 100).toInt()}", fontSize = 40.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text(label, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun ScanButton(busy: Boolean, label: String, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val inf = rememberInfiniteTransition(label = "btn")
    val pulse by inf.animateFloat(1f, 1.025f, infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse")
    val press by animateFloatAsState(if (pressed) 0.94f else 1f, spring(dampingRatio = 0.5f), label = "press")
    val s = press * (if (busy) 1f else pulse)
    Box(
        Modifier.fillMaxWidth().height(58.dp)
            .graphicsLayer { scaleX = s; scaleY = s }
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.horizontalGradient(listOf(CYAN, INDIGO)))
            .clickable(interactionSource = src, indication = null, enabled = !busy, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF04121F))
    }
}

@Composable
fun ResultRow(f: Finding, index: Int) {
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(minOf(index, 8) * 70L); show = true }
    AnimatedVisibility(show, enter = fadeIn(tween(400)) + slideInVertically(tween(400)) { it / 2 }) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(CARD)
        ) {
            Box(Modifier.width(4.dp).height(IntrinsicSize.Min).fillMaxHeight().background(sevColor(f.sev)))
            Column(Modifier.padding(12.dp).weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(f.category.uppercase(), color = MUTED, fontSize = 10.sp, modifier = Modifier.weight(1f))
                    Text(
                        f.sev.name, color = sevColor(f.sev), fontSize = 10.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(sevColor(f.sev).copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
                Text(f.title, color = Color.White, fontWeight = FontWeight.SemiBold)
                if (f.detail.isNotBlank()) Text(f.detail, color = MUTED, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun Root() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var agreed by rememberSaveable { mutableStateOf(false) }
    var usage by remember { mutableStateOf(Scanner.hasUsageAccess(ctx)) }
    var days by rememberSaveable { mutableIntStateOf(4) }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<ScanResult?>(null) }
    val phases = listOf("Reading installed apps…", "Tracing removed apps…", "Hunting hack tools…", "Inspecting device integrity…")
    var phase by remember { mutableIntStateOf(0) }

    LaunchedEffect(busy) {
        if (busy) { phase = 0; while (true) { delay(650); phase = (phase + 1) % phases.size } }
    }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val ob = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) usage = Scanner.hasUsageAccess(ctx) }
        owner.lifecycle.addObserver(ob)
        onDispose { owner.lifecycle.removeObserver(ob) }
    }
    val eyeSize by animateDpAsState(if (result == null) 200.dp else 110.dp, spring(stiffness = Spring.StiffnessLow), label = "eyeSize")
    val usageColor by animateColorAsState(if (usage) GREEN else AMBER, tween(400), label = "usageColor")

    Box(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(BG, Color(0xFF0F1D3A), BG)))
    ) {
        LazyColumn(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
            contentPadding = PaddingValues(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { EyeView(Modifier.size(eyeSize), busy) }
            item {
                Text(
                    "3rdVision",
                    style = TextStyle(brush = Brush.horizontalGradient(listOf(CYAN, INDIGO)), fontSize = 30.sp, fontWeight = FontWeight.Bold)
                )
                Text("Device activity & integrity scanner", color = MUTED, fontSize = 13.sp)
            }
            item {
                AnimatedContent(
                    agreed,
                    transitionSpec = { (fadeIn(tween(450)) + scaleIn(initialScale = 0.92f)) togetherWith fadeOut(tween(250)) },
                    label = "main"
                ) { ag ->
                    if (!ag) {
                        Card(colors = CardDefaults.cardColors(containerColor = CARD), shape = RoundedCornerShape(20.dp)) {
                            Column(Modifier.padding(18.dp)) {
                                Text("Before you scan", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 17.sp)
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "On this phone only, this app checks:\n" +
                                        "• Apps installed, updated or removed recently\n" +
                                        "• Known hack, cheat and clone tools\n" +
                                        "• Root, emulator, hooking and debugging signs\n\n" +
                                        "It needs 'App list' and 'Usage access' permission. Nothing is hidden or uploaded; results show only on this screen.",
                                    color = MUTED, fontSize = 14.sp
                                )
                                Spacer(Modifier.height(14.dp))
                                ScanButton(false, "I understand & agree") { agreed = true }
                            }
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Card(colors = CardDefaults.cardColors(containerColor = CARD), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                                Row(Modifier.padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(10.dp).clip(CircleShape).background(usageColor))
                                    Spacer(Modifier.width(10.dp))
                                    Text(if (usage) "Usage access granted" else "Usage access needed", color = usageColor, modifier = Modifier.weight(1f))
                                    if (!usage) TextButton({ ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }) { Text("Grant") }
                                }
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("Look back", color = MUTED, modifier = Modifier.weight(1f))
                                listOf(2, 3, 4, 7).forEach { d -> FilterChip(days == d, { days = d }, { Text("${d}d") }) }
                            }
                            ScanButton(busy, if (busy) phases[phase] else "SCAN") {
                                result = null
                                busy = true
                                scope.launch {
                                    val t0 = System.currentTimeMillis()
                                    val r = withContext(Dispatchers.Default) { Scanner.scan(ctx, days) }
                                    val el = System.currentTimeMillis() - t0
                                    if (el < 2600) delay(2600 - el)
                                    result = r
                                    busy = false
                                }
                            }
                        }
                    }
                }
            }
            result?.let { r ->
                item {
                    val vc = verdictColor(r.verdict)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Gauge(r.score, vc, r.verdict)
                        val h = r.findings.count { it.sev == Sev.HIGH }
                        val w = r.findings.count { it.sev == Sev.WARN }
                        Text("$h high • $w warning • last ${r.days} days", color = MUTED, fontSize = 12.sp, textAlign = TextAlign.Center)
                        TextButton({
                            val txt = "3rdVision report (${r.days}d) - ${r.verdict} ${r.score}/100\n\n" +
                                r.findings.joinToString("\n\n") { "[${it.sev}] ${it.category}: ${it.title}\n${it.detail}" }
                            ctx.startActivity(
                                Intent.createChooser(
                                    Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, txt) },
                                    "Share report"
                                )
                            )
                        }) { Text("Share report") }
                    }
                }
                itemsIndexed(r.findings) { i, f -> ResultRow(f, i) }
                if (r.findings.isEmpty()) item { Text("Nothing found in this window.", color = MUTED) }
            }
        }
    }
}
