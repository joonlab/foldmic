package kr.joonlab.foldmic.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// lucide 선 아이콘(ISC) 중 쓰는 것만 — 24×24, stroke 2, round.
// material-icons 의존을 넣지 않으려고 path 를 직접 둔다.
private val PATHS: Map<String, List<String>> = mapOf(
    "mic" to listOf("M12 19v3", "M19 10v2a7 7 0 0 1-14 0v-2", "M12 2a3 3 0 0 0-3 3v7a3 3 0 0 0 6 0V5a3 3 0 0 0-3-3z"),
    "mic-off" to listOf("M12 19v3", "M15 9.34V5a3 3 0 0 0-5.68-1.33", "M16.95 16.95A7 7 0 0 1 5 12v-2",
        "M18.89 13.23A7 7 0 0 0 19 12v-2", "m2 2 20 20", "M9 9v3a3 3 0 0 0 5.12 2.12"),
    "laptop" to listOf("M18 5a2 2 0 0 1 2 2v8.526a2 2 0 0 0 .212.897l1.068 2.127a1 1 0 0 1-.9 1.45H3.62a1 1 0 0 1-.9-1.45l1.068-2.127A2 2 0 0 0 4 15.526V7a2 2 0 0 1 2-2z",
        "M20.054 15.987H3.946"),
    "house" to listOf("M15 21v-8a1 1 0 0 0-1-1h-4a1 1 0 0 0-1 1v8",
        "M3 10a2 2 0 0 1 .709-1.528l7-6a2 2 0 0 1 2.582 0l7 6A2 2 0 0 1 21 10v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"),
    "sun" to listOf("M8 12a4 4 0 1 0 8 0a4 4 0 1 0 -8 0", "M12 2v2", "M12 20v2", "m4.93 4.93 1.41 1.41", "m17.66 17.66 1.41 1.41",
        "M2 12h2", "M20 12h2", "m6.34 17.66-1.41 1.41", "m19.07 4.93-1.41 1.41"),
    "moon" to listOf("M20.985 12.486a9 9 0 1 1-9.473-9.472c.405-.022.617.46.402.803a6 6 0 0 0 8.268 8.268c.344-.215.825-.004.803.401"),
    "loader" to listOf("M21 12a9 9 0 1 1-6.219-8.56"),
    "refresh" to listOf("M21 12a9 9 0 1 1-9-9c2.52 0 4.93 1 6.74 2.74L21 8", "M21 3v5h-5"),
    "alert" to listOf("m21.73 18-8-14a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3", "M12 9v4", "M12 17h.01"),
    "wifi-off" to listOf("M12 20h.01", "M8.5 16.429a5 5 0 0 1 7 0", "M5 12.859a10 10 0 0 1 5.17-2.69", "M19 12.859a10 10 0 0 0-2.007-1.523",
        "M2 8.82a15 15 0 0 1 4.177-2.643", "M22 8.82a15 15 0 0 0-11.288-3.764", "m2 2 20 20"),
    "circle-x" to listOf("M2 12a10 10 0 1 0 20 0a10 10 0 1 0 -20 0", "m15 9-6 6", "m9 9 6 6"),
    "check" to listOf("M20 6 9 17l-5-5"),
    "x" to listOf("M18 6 6 18", "m6 6 12 12"),
    "down" to listOf("m6 9 6 6 6-6"),
    "external" to listOf("M15 3h6v6", "M10 14 21 3", "M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6"),
    "audio" to listOf("M2 10v3", "M6 6v11", "M10 3v18", "M14 8v7", "M18 5v13", "M22 10v3"),
    "arrow-right" to listOf("M5 12h14", "m12 5 7 7-7 7"),
    "phone" to listOf("M7 2h10a2 2 0 0 1 2 2v16a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2z", "M12 18h.01"),
)

private val VEC = HashMap<String, ImageVector>()

private fun vec(name: String): ImageVector? {
    VEC[name]?.let { return it }
    val paths = PATHS[name] ?: return null
    val b = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
    paths.forEach { d ->
        b.addPath(PathParser().parsePathString(d).toNodes(), stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
    }
    return b.build().also { VEC[name] = it }
}

/** 장식 아이콘(이름은 옆 글자·부모의 onClickLabel 이 준다) */
@Composable
fun Ic(name: String, color: Color = P.dim, size: Dp = 18.dp, spin: Boolean = false, modifier: Modifier = Modifier) {
    val v = vec(name) ?: return
    var m = modifier.size(size)
    if (spin) {
        val a by rememberInfiniteTransition(label = "spin").animateFloat(0f, 360f,
            infiniteRepeatable(tween(1000, easing = LinearEasing), RepeatMode.Restart), label = "spin")
        m = m.rotate(a)
    }
    Icon(v, null, tint = color, modifier = m)
}
