package kr.joonlab.foldmic.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kr.joonlab.foldmic.R

// ───────── 폴드 앱 묶음의 공통 컴포넌트 문법 — 토큰 P 로만 칠한다 ─────────

/** 카드 = panel 면 + 1dp 테두리 + 모서리 14. 강조는 면 물들이기(tint)로. 그림자 없음.
 *  모서리는 두 가지만: 카드 14 · 카드 안의 타일·상자 10 (알약·스위치는 완전 둥글게). */
fun Modifier.card(tint: Color = Color.Transparent, outline: Color? = null, r: Dp = 14.dp): Modifier {
    val s = RoundedCornerShape(r)
    return clip(s).background(P.panel).background(tint).border(1.dp, outline ?: P.border, s)
}

/**
 * 앱 표식 = 실제 런처 아이콘(적응형 아이콘의 배경+전경 두 겹). 108dp 캔버스 중 가운데 72dp 만 보이는 규칙대로
 * 1.5배로 그린 뒤 둥근 사각으로 자른다 — 홈 화면 아이콘과 같은 그림.
 */
@Composable
fun Mark(size: Dp = 30.dp) {
    val shape = RoundedCornerShape(size * .28f)
    // 다크 바탕과 타일(#0B0E12)이 거의 같아 모서리가 사라진다 → 다크에서만 1dp 테두리.
    Box(Modifier.size(size).clip(shape).then(if (ThemeMode.dark) Modifier.border(1.dp, P.border, shape) else Modifier),
        contentAlignment = Alignment.Center) {
        Image(painterResource(R.drawable.ic_launcher_background), null, Modifier.requiredSize(size * 1.5f))
        Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.requiredSize(size * 1.5f))
    }
}

/** 우상단 테마 버튼 — 아이콘은 지금 보이는 테마, 시스템 추종이면 «자동» */
@Composable
fun ThemeButton() {
    val ctx = LocalContext.current
    val label = when (ThemeMode.mode) { "system" -> "자동(폰 설정)"; "light" -> "밝게"; else -> "어둡게" }
    Row(Modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(10.dp))
            .border(1.dp, P.border, RoundedCornerShape(10.dp))
            .clickable(onClickLabel = "테마 바꾸기") { ThemeMode.cycle(ctx) }
            .semantics { stateDescription = "테마 $label" }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Ic(if (ThemeMode.dark) "moon" else "sun", P.text, 18.dp)
        if (ThemeMode.mode == "system") Text("자동", color = P.dim, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

/** 상태 알약 — 11sp SemiBold, 모서리 5 */
@Composable
fun Pill(text: String, fg: Color, bg: Color, dot: Color? = null) {
    Row(Modifier.background(bg, RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        if (dot != null) Box(Modifier.size(6.dp).background(dot, CircleShape))
        Text(text, color = fg, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** 글자 버튼 — 높이 ≥ 48, 알약. fill 을 주면 주 버튼(한 화면에 accent 채움은 하나) */
@Composable
fun TextBtn(label: String, color: Color = P.text, border: Color = P.border, fill: Color = Color.Transparent,
            icon: String? = null, enabled: Boolean = true, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(24.dp)).background(fill)
            .border(1.dp, border, RoundedCornerShape(24.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick).alpha(if (enabled) 1f else .4f)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterHorizontally)) {
        if (icon != null) Ic(icon, color, 17.dp)
        Text(label, color = color, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** 아이콘 버튼 — 48dp 정사각 터치, 이름 필수 */
@Composable
fun IconBtn(icon: String, desc: String, tint: Color = P.dim, onClick: () -> Unit) {
    Box(Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)).clickable(onClickLabel = desc, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center) { Ic(icon, tint, 18.dp) }
}

/**
 * 스위치 트랙 — M3 Switch 대신 패밀리 문법(52×30, 원 24). 네트워크 전환 중(pending)이면
 * 원이 목표 쪽으로 가 있고 원 안에 작은 스피너가 돈다. 확정은 서버 응답이 한다.
 */
@Composable
fun SwitchTrack(on: Boolean, pending: Boolean, enabled: Boolean) {
    val x by animateDpAsState(if (on) 22.dp else 0.dp, tween(160), label = "thumb")
    val track by animateColorAsState(when {
        pending -> P.accentTint
        on -> P.accent
        else -> P.panel2
    }, tween(160), label = "track")
    val edge = if (on || pending) P.accent else P.border
    Box(Modifier.size(52.dp, 30.dp).alpha(if (enabled || pending) 1f else .45f)
            .background(track, RoundedCornerShape(15.dp)).border(1.dp, edge, RoundedCornerShape(15.dp)).padding(3.dp)) {
        // 꺼짐 손잡이: 라이트는 흰 원 + 테두리(진한 원이면 켜진 것처럼 무겁다), 다크는 dim 원(흰 원은 너무 튄다).
        val offKnob = !on && !pending && !ThemeMode.dark
        Box(Modifier.offset { androidx.compose.ui.unit.IntOffset(x.roundToPx(), 0) }.size(24.dp).background(
                when { pending -> P.raised; on -> P.onAccent; offKnob -> Color.White; else -> P.dim }, CircleShape)
                .then(if (offKnob) Modifier.border(1.dp, P.border, CircleShape) else Modifier),
            contentAlignment = Alignment.Center) {
            if (pending) Ic("loader", P.accent, 14.dp, spin = true)
        }
    }
}

/** 오류·경고 상자 — 물든 면 + 같은 색 테두리 + 모서리 10. 원문은 «자세히» 로 접는다. */
@Composable
fun NoticeBox(
    tone: Color, tint: Color, icon: String, title: String, body: String,
    raw: String? = null, trailing: (@Composable () -> Unit)? = null, actions: (@Composable () -> Unit)? = null,
) {
    var open by rememberSaveable(raw) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(tint)
            .border(1.dp, tone.copy(alpha = .38f), RoundedCornerShape(10.dp)).padding(start = 14.dp, end = 4.dp, top = 12.dp, bottom = 12.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Ic(icon, tone, 18.dp, modifier = Modifier.padding(top = 1.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f).padding(end = 10.dp)) {
                Text(title, color = P.text, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, lineHeight = 20.sp)
                if (body.isNotEmpty()) Text(body, color = P.dim, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 2.dp))
            }
            trailing?.invoke()
        }
        if (!raw.isNullOrBlank()) {
            Row(Modifier.padding(start = 18.dp).heightIn(min = 44.dp).clip(RoundedCornerShape(8.dp))
                    .clickable(onClickLabel = if (open) "원문 접기" else "원문 펼치기") { open = !open }.padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(if (open) "원문 접기" else "자세히", color = P.dim, fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
                Ic("down", P.dim, 14.dp, modifier = Modifier.rotate(if (open) 180f else 0f))
            }
            if (open) SelectionContainer {
                Text(raw.trim(), color = P.dim, fontSize = 11.5.sp, lineHeight = 16.sp, fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(start = 28.dp, end = 10.dp, bottom = 4.dp).fillMaxWidth()
                        .background(P.bg.copy(alpha = .6f), RoundedCornerShape(6.dp)).padding(10.dp))
            }
        }
        if (actions != null) Row(Modifier.padding(start = 28.dp, top = 8.dp, end = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) { actions() }
    }
}

/** 라벨 : 값 한 줄(작은 아이콘 + 12.5 dim 라벨 + 값). 값이 길면 줄을 바꾼다. */
@Composable
fun InfoLine(icon: String, label: String, value: String, mono: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
        Ic(icon, P.dim, 15.dp, modifier = Modifier.padding(top = 2.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, color = P.dim, fontSize = 12.5.sp, lineHeight = 18.sp, maxLines = 1)
        Spacer(Modifier.width(12.dp))
        // 값은 오른쪽 끝에 붙인다 — 라벨 길이가 달라도 두 줄의 값 끝이 맞는다
        Text(value, color = P.text, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium,
            fontFamily = if (mono) FontFamily.Monospace else null, textAlign = TextAlign.End,
            modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
