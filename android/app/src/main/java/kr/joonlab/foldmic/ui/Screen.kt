package kr.joonlab.foldmic.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kr.joonlab.foldmic.ActionFail
import kr.joonlab.foldmic.BuildConfig
import kr.joonlab.foldmic.Fix
import kr.joonlab.foldmic.MACS
import kr.joonlab.foldmic.Mac
import kr.joonlab.foldmic.MacState
import kr.joonlab.foldmic.MicModel
import kr.joonlab.foldmic.PHONE_NAME
import kr.joonlab.foldmic.Phase
import kr.joonlab.foldmic.Reach
import kr.joonlab.foldmic.reachText

private const val TAILSCALE = "com.tailscale.ipn"

/** 숫자(초·분)가 흔들리지 않게 고정폭 숫자 */
private val TNUM @Composable get() = androidx.compose.material3.LocalTextStyle.current.copy(fontFeatureSettings = "tnum")

/**
 * 폴드 마이크 한 화면.
 *   머리줄(표식·이름·테마) → 「지금」 요약(어느 맥이 폰 마이크를 쓰는지) → 맥 카드들 → 어떻게 동작하나요 → 빌드
 * 폭 600dp 이상(펼침)에서는 맥 카드를 나란히, 높이 480dp 미만(커버 가로)에서는 요약을 한 줄로 줄인다.
 * 화면 전체가 세로 스크롤 하나 — 커버 화면·큰 글꼴에서도 잘리지 않는다.
 */
@Composable
fun FoldMicScreen(model: MicModel) {
    val cfg = LocalConfiguration.current
    val wide = cfg.screenWidthDp >= 600
    val short = cfg.screenHeightDp < 480
    val view = LocalView.current

    // 성공·실패를 손으로 느끼게(폰을 들고 말하려는 순간이라 화면을 안 볼 수 있다)
    val fb = model.feedback
    LaunchedEffect(fb?.seq) {
        if (fb != null) view.performHapticFeedback(if (fb.ok) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.REJECT)
    }

    // 「n초 전」·경과 초를 위한 시계 — 1초마다
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1000) } }

    Box(Modifier.fillMaxSize().background(P.bg)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))) {
        Column(Modifier.fillMaxSize()) {
            val maxW = if (wide) 820.dp else 600.dp
            Header(maxW)
            val nav = WindowInsets.navigationBars.asPaddingValues()
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(bottom = nav.calculateBottomPadding() + 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Column(Modifier.widthIn(max = maxW).fillMaxWidth().padding(horizontal = 16.dp)) {
                    Spacer(Modifier.height(if (short) 10.dp else 16.dp))
                    NowPanel(model, compact = short)
                    Spacer(Modifier.height(if (short) 14.dp else 22.dp))
                    SectionLabel("연결할 맥")
                    if (MACS.isEmpty()) {
                        NoticeBox(P.warn, P.warnTint, "alert", "설정된 맥이 없어요",
                            "android/local.properties 에 foldmic.macs 를 넣고 다시 빌드하세요. 형식은 README 의 「설정」을 보세요.")
                    } else if (wide) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                            MACS.forEach { mac -> Box(Modifier.weight(1f)) { MacCard(mac, model.state(mac), now, model) } }
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            MACS.forEach { mac -> MacCard(mac, model.state(mac), now, model) }
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    HowItWorks()
                    Text("빌드 ${BuildConfig.BUILD_TIME}", color = P.dim, fontSize = 12.sp,
                        modifier = Modifier.fillMaxWidth().padding(top = 18.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
        }
    }
}

// ───────────────────────── 머리줄 ─────────────────────────

@Composable
private fun Header(maxW: Dp) {
    // 머리줄 안쪽을 본문 열과 같은 폭·거터로 맞춘다 — 표식 왼끝과 테마 버튼 오른끝이 카드 가장자리와 한 줄에 선다(펼침에서도)
    Column(Modifier.fillMaxWidth().background(P.bg), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.widthIn(max = maxW).fillMaxWidth().height(56.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Mark(28.dp)
            Spacer(Modifier.width(10.dp))
            Text("폴드 마이크", color = P.text, fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold,
                letterSpacing = (-0.2).sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).semantics { heading() })
            ThemeButton()
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(P.border))
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, color = P.dim, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 2.dp, bottom = 8.dp).semantics { heading() })
}

// ───────────────────────── 「지금」 요약 ─────────────────────────

/** 한눈에: 지금 어느 맥이 폰 마이크를 쓰는가. 켜져 있으면 폰 마이크가 열려 있다는 경고도 여기서. */
@Composable
private fun NowPanel(model: MicModel, compact: Boolean) {
    val all = MACS.map { it to model.state(it) }
    val listening = all.filter { (_, s) -> s.phase == Phase.On && s.switching != false }.map { it.first.label }
    val switching = all.firstOrNull { it.second.busy }
    val loading = all.all { it.second.phase == Phase.Loading }
    val unreachable = all.all { it.second.phase == Phase.Unreachable || it.second.phase == Phase.AgentError }
    val live = listening.isNotEmpty()

    val (title, sub) = when {
        live -> "${listening.joinToString("·")}에서 듣는 중" to "폰 마이크가 열려 있어요. 다 쓰면 꺼 주세요."
        switching != null -> (if (switching.second.switching == true) "${switching.first.label}로 연결하는 중" else "${switching.first.label} 연결을 끊는 중") to
            "보통 3~5초 걸려요."
        all.isEmpty() -> "설정된 맥이 없어요" to "빌드 설정 foldmic.macs 에 맥 주소를 넣어 주세요."
        loading -> "맥 상태를 확인하는 중" to "잠시만요."
        unreachable -> "맥에 닿지 않아요" to "폰과 맥의 Tailscale 연결을 확인해 보세요."
        else -> "쉬는 중" to "켜면 그 맥의 모든 앱이 이 폰 마이크로 들어요."
    }
    val face by animateColorAsState(if (live) P.accentTint else Color.Transparent, tween(220), label = "nowFace")
    val edge = if (live) P.accent.copy(alpha = .45f) else P.border

    Row(Modifier.fillMaxWidth().card(face, edge)
            .padding(horizontal = if (compact) 14.dp else 18.dp, vertical = if (compact) 12.dp else 20.dp)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically) {
        BigMic(live = live, busy = switching != null, off = unreachable, size = if (compact) 48.dp else 68.dp)
        Spacer(Modifier.width(if (compact) 14.dp else 18.dp))
        Column(Modifier.weight(1f)) {
            Text(if (live) "지금" else "지금 폰 마이크", color = if (unreachable) P.dim else P.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(title, color = P.text, fontSize = if (compact) 18.sp else 22.sp, lineHeight = if (compact) 24.sp else 28.sp,
                fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp, modifier = Modifier.padding(top = 1.dp))
            if (!compact || live) Text(sub, color = if (live) P.text else P.dim, fontSize = 13.sp, lineHeight = 19.sp,
                modifier = Modifier.padding(top = 3.dp))
        }
    }
}

/** 큰 원: 켜짐 = 그라데이션 원 + 퍼지는 고리, 전환 중 = 도는 고리, 꺼짐 = panel2 원 */
@Composable
private fun BigMic(live: Boolean, busy: Boolean, off: Boolean, size: Dp) {
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        if (live) {
            val t = rememberInfiniteTransition(label = "ring")
            val p by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart), label = "p")
            Box(Modifier.size(size).scale(.78f + .32f * p).alpha((1f - p) * .55f).border(2.dp, P.accent, CircleShape))
            Box(Modifier.size(size * .78f).background(Brush.linearGradient(listOf(P.gradA, P.gradB)), CircleShape),
                contentAlignment = Alignment.Center) { Ic("mic", P.onGrad, size * .36f) }
        } else {
            // 쉬는 중에도 대표색을 절제해서 — accentTint 원 + accent 선 + accent 기호. 닿지 않을 때만 무채(dim).
            Box(Modifier.size(size * .78f).background(if (off) P.panel2 else P.accentTint, CircleShape)
                    .border(1.dp, if (off) P.border else P.accent.copy(alpha = .35f), CircleShape),
                contentAlignment = Alignment.Center) {
                Ic(if (off) "wifi-off" else "mic-off", if (off) P.dim else P.accent, size * .34f)
            }
            if (busy) Ic("loader", P.accent, size * .9f, spin = true)
        }
    }
}

// ───────────────────────── 맥 카드 ─────────────────────────

@Composable
private fun MacCard(mac: Mac, s: MacState, now: Long, model: MicModel) {
    val ctx = LocalContext.current
    val on = s.phase == Phase.On
    val tint = when {
        s.busy || on -> P.accentTint
        s.phase == Phase.Mismatch -> P.warnTint
        else -> Color.Transparent
    }
    val outline = when {
        on && !s.busy -> P.accent.copy(alpha = .5f)
        s.busy -> P.accent.copy(alpha = .3f)
        s.phase == Phase.Mismatch -> P.warn.copy(alpha = .45f)
        else -> null
    }

    Column(Modifier.fillMaxWidth().card(tint, outline).padding(16.dp)) {
        // ① 머리: 기기 아이콘 · 이름 · 호스트 · 상태 알약
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 꺼짐에도 대표색을 옅게(accentTint 면 + accent 기호) — 다크에서 앱 정체성이 사라지지 않게. 켜짐은 accent 채움.
            Box(Modifier.size(42.dp).background(if (on) P.accent else P.accentTint, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center) {
                Ic(if (mac.kind == Mac.Kind.Home) "house" else "laptop", if (on) P.onAccent else P.accent, 21.dp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(mac.label, color = P.text, fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { heading() })
                // 호스트명 · 신선도 한 줄(따로 떨어진 «방금 확인» 줄을 없앴다)
                val host = s.host.ifEmpty { mac.base.removePrefix("https://").substringBefore('.') }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(host, color = P.dim, fontSize = 12.5.sp, lineHeight = 18.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (s.lastOk > 0 && s.phase != Phase.Unreachable) {
                        Text(" · ${ago(now - s.lastOk)} 확인", color = P.dim, fontSize = 12.5.sp, lineHeight = 18.sp,
                            style = TNUM, maxLines = 1)
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            StatusPill(s)
        }

        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(P.border))

        // ② 큰 스위치 줄 — 줄 전체가 눌린다(최소 64dp). TalkBack: 「노트북 폰 마이크로 듣기, 스위치, 켜짐」
        val shownOn = s.switching ?: (s.phase == Phase.On)
        val (lineText, lineColor) = statusLine(s, now)
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).clip(RoundedCornerShape(10.dp))
                .toggleable(shownOn, enabled = s.canToggle, role = Role.Switch) { model.toggle(mac, it) }
                .semantics { if (s.busy) stateDescription = if (s.switching == true) "켜는 중" else "끄는 중" }
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text("$PHONE_NAME 마이크로 듣기", color = P.text, fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.Medium)
                Text(lineText, color = lineColor, fontSize = 12.5.sp, lineHeight = 18.sp,
                    style = TNUM, modifier = Modifier.padding(top = 2.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite })
            }
            SwitchTrack(on = shownOn, pending = s.busy, enabled = s.canToggle)
        }

        // ③ 상태별 본문
        when (s.phase) {
            Phase.Loading -> Unit
            Phase.On -> {
                InfoLine("audio", "맥 기본 입력", "$PHONE_NAME 마이크 (${s.input.ifEmpty { "BlackHole 2ch" }} 경유)")
                if (s.restoreTo != null) InfoLine("mic", "끄면 돌아갈 입력", s.restoreTo)
            }
            Phase.Off -> if (s.input.isNotEmpty()) InfoLine("mic", "맥 기본 입력", s.input)
            Phase.Mismatch -> {
                if (s.input.isNotEmpty()) InfoLine("mic", "맥 기본 입력", s.input)
                Spacer(Modifier.height(8.dp))
                NoticeBox(P.warn, P.warnTint, "alert", "폰 소리 연결만 남아 있어요",
                    "맥은 원래 마이크를 쓰고 있어요. 정리하면 남은 연결을 닫아요. 다시 켜려면 위 스위치를 누르세요.",
                    actions = { TextBtn("정리하기", enabled = !s.busy) { model.toggle(mac, false) } })
            }
            Phase.AgentError -> {
                Spacer(Modifier.height(4.dp))
                NoticeBox(P.danger, P.dangerTint, "circle-x", "맥의 마이크 상태를 읽지 못했어요",
                    "맥에서 foldmic status 를 직접 실행해 보세요. 상태를 모르는 동안은 스위치를 잠가 둘게요.",
                    raw = s.agentErr, actions = { RetryBtn(s) { model.refresh(mac, manual = true) } })
            }
            Phase.Unreachable -> {
                val r = s.reach ?: Reach(Reach.Kind.Other, "")
                val (t, hint) = reachText(r)
                val last = if (s.lastOk > 0) " 마지막 확인 ${ago(now - s.lastOk)}." else ""
                Spacer(Modifier.height(4.dp))
                NoticeBox(P.danger, P.dangerTint, "wifi-off", t, hint + last, raw = r.raw,
                    actions = {
                        RetryBtn(s) { model.refresh(mac, manual = true) }
                        if (r.kind != Reach.Kind.BadReply && hasTailscale(ctx)) TextBtn("Tailscale", icon = "external") { openTailscale(ctx) }
                    })
            }
        }

        // ④ 동작 실패 — 다음 동작이나 닫기 전까지 남는다
        AnimatedVisibility(s.fail != null, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            val f = s.fail
            if (f != null) Column {
                Spacer(Modifier.height(10.dp))
                FailBox(f, s, ctx, onRetry = { model.toggle(mac, f.on) }, onDismiss = { model.dismissFail(mac) })
            }
        }

    }
}

@Composable
private fun StatusPill(s: MacState) = when {
    s.switching == true -> Pill("켜는 중", P.accent, P.accentTint)
    s.switching == false -> Pill("끄는 중", P.accent, P.accentTint)
    else -> when (s.phase) {
        Phase.Loading -> Pill("확인 중", P.dim, P.panel2)
        Phase.Off -> Pill("꺼짐", P.dim, P.panel2, dot = P.dim)
        Phase.On -> Pill("사용 중", P.onAccent, P.accent, dot = P.onAccent)
        Phase.Mismatch -> Pill("확인 필요", P.warnText, P.warnTint, dot = P.warn)
        Phase.AgentError -> Pill("오류", P.danger, P.dangerTint, dot = P.danger)
        Phase.Unreachable -> Pill("연결 안 됨", P.danger, P.dangerTint, dot = P.danger)
    }
}

/** 스위치 아래 한 줄 — 무엇이 일어나는 중이고 얼마나 됐는지 */
private fun statusLine(s: MacState, now: Long): Pair<String, Color> {
    if (s.busy) {
        val sec = ((now - s.since) / 1000).coerceAtLeast(0)
        val slow = if (sec >= 8) " · 조금 오래 걸리네요(최대 40초)" else ""
        return (if (s.switching == true) "$PHONE_NAME 마이크로 바꾸는 중 · ${sec}초$slow" else "원래 마이크로 돌리는 중 · ${sec}초$slow") to P.accent
    }
    return when (s.phase) {
        Phase.Loading -> "상태를 확인하고 있어요" to P.dim
        Phase.Off -> "켜면 3~5초 안에 이 맥의 입력이 바뀌어요" to P.dim
        Phase.On -> "이 맥의 모든 앱이 폰 마이크로 들어요" to P.text
        Phase.Mismatch -> "폰 연결만 남아 있어요" to P.warnText
        Phase.AgentError -> "상태를 몰라 잠가 두었어요" to P.dim
        Phase.Unreachable -> "맥에 닿으면 켤 수 있어요" to P.dim
    }
}

@Composable
private fun RetryBtn(s: MacState, onClick: () -> Unit) {
    TextBtn(if (s.checking) "확인 중…" else "다시 시도", icon = "refresh", enabled = !s.checking, onClick = onClick)
}

@Composable
private fun FailBox(f: ActionFail, s: MacState, ctx: Context, onRetry: () -> Unit, onDismiss: () -> Unit) {
    NoticeBox(P.danger, P.dangerTint, "alert", f.title, f.hint, raw = f.detail.takeIf { it.isNotBlank() && it.trim() != f.hint.trim() },
        trailing = { IconBtn("x", "실패 알림 닫기", onClick = onDismiss) },
        actions = {
            if (s.canToggle) TextBtn(if (f.on) "다시 켜기" else "다시 끄기", icon = "refresh", onClick = onRetry)
            when (f.fix) {
                Fix.DevSettings -> TextBtn("무선 디버깅", icon = "external") { openDevSettings(ctx) }
                Fix.Tailscale -> if (hasTailscale(ctx)) TextBtn("Tailscale", icon = "external") { openTailscale(ctx) }
                null -> Unit
            }
        })
}

// ───────────────────────── 어떻게 동작하나요 ─────────────────────────

@Composable
private fun HowItWorks() {
    var open by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().card()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp)
                .clickable(onClickLabel = if (open) "설명 접기" else "설명 펼치기") { open = !open }
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text("어떻게 동작하나요?", color = P.text, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Ic("down", P.dim, 18.dp, modifier = Modifier.rotate(if (open) 180f else 0f))
        }
        AnimatedVisibility(open, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Step("1", "켜면 맥이 무선 디버깅(adb)으로 이 폰의 마이크 소리를 받아 가상 장치 BlackHole 2ch 로 흘리고, 맥의 기본 입력을 그 장치로 바꿔요.")
                Step("2", "그래서 Zoom·받아쓰기 같은 그 맥의 모든 앱이 폰 마이크로 들어요. 두 맥을 동시에 켜도 돼요.")
                Step("3", "끄면 바꾸기 직전에 쓰던 입력으로 돌아가요. 폰 연결이 끊겨도 맥이 알아서 원래 마이크로 돌아가요.")
                Step("4", "폰은 Tailscale 로 각 맥의 foldmicd 에 닿아요. 켜 두는 동안 폰 마이크는 계속 열려 있어요.")
            }
        }
    }
}

@Composable
private fun Step(n: String, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(Modifier.padding(top = 1.dp).size(20.dp).background(P.panel2, CircleShape), contentAlignment = Alignment.Center) {
            Text(n, color = P.dim, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.width(10.dp))
        Text(text, color = P.text, fontSize = 13.5.sp, lineHeight = 20.sp, modifier = Modifier.weight(1f))
    }
}

// ───────────────────────── 도우미 ─────────────────────────

private fun ago(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return when {
        s < 5 -> "방금"
        s < 60 -> "${s}초 전"
        s < 3600 -> "${s / 60}분 전"
        else -> "${s / 3600}시간 전"
    }
}

private fun hasTailscale(ctx: Context) = ctx.packageManager.getLaunchIntentForPackage(TAILSCALE) != null

private fun openTailscale(ctx: Context) {
    ctx.packageManager.getLaunchIntentForPackage(TAILSCALE)?.let { runCatching { ctx.startActivity(it) } }
}

private fun openDevSettings(ctx: Context) {
    runCatching { ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)) }
        .onFailure { runCatching { ctx.startActivity(Intent(Settings.ACTION_SETTINGS)) } }
}
