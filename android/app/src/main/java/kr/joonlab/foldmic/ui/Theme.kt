package kr.joonlab.foldmic.ui

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.core.view.WindowCompat
import kr.joonlab.foldmic.R

/**
 * 폴드 앱 묶음의 공통 팔레트 인터페이스 — 역할 10개.
 * 앱마다 바뀌는 건 accent 하나(폴드 마이크 = 라일락→바이올렛).
 */
interface Pal {
    val bg: Color
    val panel: Color
    val panel2: Color
    val raised: Color
    val border: Color
    val text: Color
    val dim: Color
    val accent: Color
    val accentTint: Color
    val onAccent: Color
}

/** getter 로 [ThemeMode.dark] 를 읽는다 — 테마를 바꾸면 읽은 자리만 다시 그려진다. 값은 앱 묶음 디자인 가이드 기준. */
object P : Pal {
    private fun t(l: Long, d: Long) = Color(if (ThemeMode.dark) d else l)
    override val bg get() = t(0xFFFAF9FC, 0xFF0D0C12)
    override val panel get() = t(0xFFF3F1F8, 0xFF14121B)
    override val panel2 get() = t(0xFFE9E6F2, 0xFF1C1926)
    override val raised get() = t(0xFFFFFFFF, 0xFF211E2C)
    override val border get() = t(0xFFD8D4E6, 0xFF2E2A3B)
    override val text get() = t(0xFF1A1726, 0xFFECEAF3)
    override val dim get() = t(0xFF5C5870, 0xFFA29EB4)
    override val accent get() = t(0xFF6D28D9, 0xFFA78BFA)
    override val accentTint get() = accent.copy(alpha = if (ThemeMode.dark) .16f else .10f)
    override val onAccent get() = t(0xFFFFFFFF, 0xFF1A0B3D)

    /** 아이콘 그라데이션 양 끝(표식·큰 원에만) — 라일락 #C4B5FD → 바이올렛 #7C3AED */
    val gradA = Color(0xFFC4B5FD)
    val gradB = Color(0xFF7C3AED)
    /** 그라데이션 면 위의 기호색 — 그라데이션이 테마와 무관하므로 고정 */
    val onGrad = Color(0xFFFFFFFF)

    // 의미색 — 면 물들이기·작은 아이콘·점에만
    val ok get() = t(0xFF1A7F3C, 0xFF5ED18A)
    val warn get() = t(0xFF8A6100, 0xFFD9AE45)
    val warnText get() = t(0xFF6B4C00, 0xFFE6C97E)
    val danger get() = t(0xFFC4322C, 0xFFEF6157)
    val dangerTint get() = danger.copy(alpha = if (ThemeMode.dark) .12f else .10f)
    val warnTint get() = warn.copy(alpha = .16f)
}

/** 테마는 셋을 돈다: system(폰 설정 추종) → light → dark. */
object ThemeMode {
    const val PREFS = "ui"
    var mode by mutableStateOf("system")
    var sysDark by mutableStateOf(false)
    val dark get() = mode == "dark" || (mode == "system" && sysDark)

    /** composition 전에 불러 첫 프레임부터 저장된 테마로 그린다(깜빡임 방지). */
    fun load(ctx: Context) {
        mode = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("theme", "system") ?: "system"
    }

    fun cycle(ctx: Context) {
        mode = when (mode) { "system" -> "light"; "light" -> "dark"; else -> "system" }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("theme", mode).apply()
    }
}

val Pretendard = FontFamily(
    Font(R.font.pretendard_regular, FontWeight.Normal),
    Font(R.font.pretendard_medium, FontWeight.Medium),
    Font(R.font.pretendard_semibold, FontWeight.SemiBold),
    Font(R.font.pretendard_bold, FontWeight.Bold),
)

/** 뿌리에서 한 번 — 시스템 다크 추종 + 상태·내비 바 글자색 + 창 배경 + 기본 글꼴·글자색. */
@Composable
fun JlTheme(content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    ThemeMode.sysDark = isSystemInDarkTheme()
    val view = LocalView.current
    val dark = ThemeMode.dark
    LaunchedEffect(dark) {
        (ctx as? Activity)?.window?.let { w ->
            w.decorView.setBackgroundColor(P.bg.toArgb())   // 접을 때 잠깐 비치는 창 바탕도 테마색
            WindowCompat.getInsetsController(w, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    CompositionLocalProvider(LocalTextStyle provides TextStyle(fontFamily = Pretendard, color = P.text)) {
        content()
    }
}
