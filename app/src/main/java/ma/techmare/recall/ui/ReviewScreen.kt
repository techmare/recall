package ma.techmare.recall.ui

import android.annotation.SuppressLint
import android.graphics.Color as AColor
import android.media.MediaPlayer
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import ma.techmare.recall.data.Card
import ma.techmare.recall.fsrs.CardState
import ma.techmare.recall.fsrs.Grade
import java.io.File

private val soundRe = Regex("""\[sound:([^\]]+)\]""")

@Composable
fun ReviewScreen(vm: AppViewModel, onClose: () -> Unit) {
    val s by vm.session.collectAsState()
    val session = s ?: return
    val haptics = LocalHapticFeedback.current
    val player = remember { SoundPlayer(vm.repo.mediaDir) }
    DisposableEffect(Unit) { onDispose { player.release() } }
    BackHandler { vm.endSession(); onClose() }

    Column(
        Modifier.fillMaxSize().background(C.Ground).statusBarsPadding().navigationBarsPadding()
            .padding(horizontal = 20.dp).padding(top = 16.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        // Top bar
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(C.Surface)
                .clickable(role = Role.Button) { vm.endSession(); onClose() }, contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Close, "Close review", tint = C.Text, modifier = Modifier.size(20.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row {
                    Text(session.title, style = body(13, 700), modifier = Modifier.weight(1f), maxLines = 1)
                    Text("${session.done} / ${session.total}", style = body(13, 500, C.Muted))
                }
                val frac by animateFloatAsState(if (session.total == 0) 0f else session.done.toFloat() / session.total, tween(400), label = "p")
                Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(C.Surface2)) {
                    Box(Modifier.fillMaxWidth(frac).height(6.dp).clip(RoundedCornerShape(3.dp)).background(C.Accent))
                }
            }
            IconButton(onClick = { vm.undoLast() }, enabled = session.canUndo) {
                Icon(Icons.AutoMirrored.Filled.Undo, "Undo last answer", tint = if (session.canUndo) C.Text else C.Surface2)
            }
        }

        val card = session.card
        if (card == null) {
            if (!session.loading) FinishedPanel(session.done) { vm.endSession(); onClose() }
            return@Column
        }

        val frontSounds = remember(card.id) { soundRe.findAll(card.front).map { it.groupValues[1] }.toList() }
        val backSounds = remember(card.id) { soundRe.findAll(card.back).map { it.groupValues[1] }.toList() }
        LaunchedEffect(card.id, session.flipped) {
            if (!vm.repo.settings.autoplayAudio) return@LaunchedEffect
            if (!session.flipped) player.play(frontSounds) else player.play(backSounds)
        }

        // Card with a quick flip animation.
        val rotation by animateFloatAsState(if (session.flipped) 180f else 0f, tween(260), label = "flip")
        Box(
            Modifier.weight(1f).fillMaxWidth()
                .graphicsLayer { rotationY = if (rotation > 90f) rotation - 180f else rotation; cameraDistance = 14f * density }
                .shadow(24.dp, RoundedCornerShape(28.dp), ambientColor = Color.Black, spotColor = Color.Black)
                .clip(RoundedCornerShape(28.dp)).background(C.Surface),
        ) {
            Column(Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 22.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StateTag(card.state)
                    Spacer(Modifier.weight(1f))
                    val sounds = if (session.flipped) frontSounds + backSounds else frontSounds
                    if (sounds.isNotEmpty()) IconButton(onClick = { player.play(sounds) }) {
                        Icon(Icons.AutoMirrored.Filled.VolumeUp, "Play audio", tint = C.Muted)
                    }
                }
                CardWebView(card, session.flipped, vm.repo.mediaDir, Modifier.weight(1f).fillMaxWidth())
                if (!session.flipped) Text("Tap the card to reveal", style = body(13, 500, C.Muted),
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp))
            }
            if (!session.flipped) Box(Modifier.matchParentSize().clickable(
                interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Button,
            ) { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); vm.flip() })
        }

        if (!session.flipped) {
            Box(Modifier.fillMaxWidth().height(60.dp).clip(RoundedCornerShape(18.dp)).background(C.Accent)
                .clickable(role = Role.Button) { vm.flip() }, contentAlignment = Alignment.Center) {
                Text("Show answer", style = body(16, 700, C.Ground))
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val specs = listOf(
                    Triple("Again", C.Rose, C.RoseBg), Triple("Hard", C.Amber, C.AmberBg),
                    Triple("Good", C.Green, C.GreenBg), Triple("Easy", C.Blue, C.BlueBg),
                )
                specs.forEachIndexed { i, (label, fg, bg) ->
                    Column(
                        Modifier.weight(1f).height(60.dp).clip(RoundedCornerShape(16.dp)).background(bg)
                            .clickable(role = Role.Button) {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                vm.grade(Grade.entries[i])
                            },
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                    ) {
                        Text(label, style = body(15, 700, fg))
                        Text(session.labels.getOrElse(i) { "" }, style = body(12, 500, fg.copy(alpha = 0.75f)))
                    }
                }
            }
        }
    }
}

@Composable
private fun StateTag(state: Int) {
    val (label, fg, bg) = when (state) {
        CardState.NEW -> Triple("New", C.Blue, C.BlueBg)
        CardState.REVIEW -> Triple("Review", C.Green, C.GreenBg)
        else -> Triple("Learning", C.Amber, C.AmberBg)
    }
    Box(Modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 10.dp, vertical = 5.dp)) {
        Text(label, style = body(12, 700, fg))
    }
}

@Composable
private fun FinishedPanel(done: Int, onHome: () -> Unit) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(84.dp).clip(RoundedCornerShape(28.dp)).background(C.GreenBg), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Check, null, tint = C.Green, modifier = Modifier.size(42.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text("Done for now", style = display(28))
        Spacer(Modifier.height(6.dp))
        Text(if (done > 0) "You reviewed $done ${if (done == 1) "card" else "cards"}." else "No cards are due right now.",
            style = body(15, 500, C.Muted))
        Spacer(Modifier.height(28.dp))
        PrimaryButton("Back home", Modifier.padding(horizontal = 40.dp), onClick = onHome)
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun CardWebView(card: Card, flipped: Boolean, mediaDir: File, modifier: Modifier) {
    val html = remember(card.id, card.front, card.back, flipped) { cardHtml(card, flipped) }
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                setBackgroundColor(AColor.TRANSPARENT)
                settings.allowFileAccess = true
                settings.javaScriptEnabled = false
                settings.textZoom = 100
                isVerticalScrollBarEnabled = false
            }
        },
        update = { wv ->
            if (wv.tag != html) {
                wv.tag = html
                wv.loadDataWithBaseURL("file://${mediaDir.absolutePath}/", html, "text/html", "utf-8", null)
            }
        },
    )
}

fun cardHtml(card: Card, flipped: Boolean): String {
    val front = soundRe.replace(card.front, "")
    val back = soundRe.replace(card.back, "")
    val plainLen = htmlToText(front).length
    val frontClass = when { plainLen > 120 -> "front small"; plainLen > 40 -> "front mid"; else -> "front" }
    val answer = if (flipped) "<hr><div class=\"answer\">$back</div>" else ""
    return """
<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<style>
@font-face{font-family:Manrope;src:url('file:///android_asset/manrope.ttf');font-weight:200 800}
html,body{height:100%;margin:0}
body{background:transparent;color:#E9EBF0;font-family:Manrope,sans-serif;font-size:19px;line-height:1.5;text-align:center;overflow-wrap:anywhere}
.wrap{min-height:100%;display:flex;flex-direction:column;justify-content:center;padding:8px 0;box-sizing:border-box}
.front{font-size:32px;font-weight:650;letter-spacing:-0.3px;line-height:1.25}
.front.mid{font-size:24px}.front.small{font-size:19px;font-weight:500;line-height:1.5}
.answer{color:#C9C1FF;font-size:22px;font-weight:600}
.answer .front, .answer div{font-size:inherit}
hr{border:none;height:1px;background:#2A2E38;margin:22px 0}
img{max-width:100%;height:auto;border-radius:12px}
.cloze{color:#B3A8FF;font-weight:700}
a{color:#B3A8FF}
table{margin:0 auto;border-collapse:collapse}td,th{border:1px solid #2A2E38;padding:4px 8px}
font[color="#000000"],font[color="black"],[style*="color: rgb(0, 0, 0)"],[style*="color:#000"],[style*="color: #000"],[style*="color: black"]{color:inherit !important}
[style*="background-color: rgb(255, 255, 255)"],[style*="background-color: white"],[style*="background-color:#fff"]{background-color:transparent !important}
</style></head><body><div class="wrap"><div class="$frontClass">$front</div>$answer</div></body></html>
"""
}

class SoundPlayer(private val dir: File) {
    private var mp: MediaPlayer? = null
    private var queue: List<File> = emptyList()

    fun play(names: List<String>) {
        stop()
        queue = names.map { File(dir, it) }.filter { it.exists() }
        next()
    }

    private fun next() {
        val f = queue.firstOrNull() ?: return
        queue = queue.drop(1)
        mp = runCatching {
            MediaPlayer().apply {
                setDataSource(f.absolutePath)
                setOnCompletionListener { it.release(); if (mp === it) mp = null; next() }
                prepare(); start()
            }
        }.getOrNull()
        if (mp == null) next()
    }

    private fun stop() { runCatching { mp?.stop(); mp?.release() }; mp = null; queue = emptyList() }
    fun release() = stop()
}
