package kr.joonlab.foldmic

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import kr.joonlab.foldmic.ui.FoldMicScreen
import kr.joonlab.foldmic.ui.JlTheme
import kr.joonlab.foldmic.ui.ThemeMode

/** 맥 하나 = foldmicd 에이전트 하나 (tailscale serve https :8798). */
data class Mac(val label: String, val base: String, val kind: Kind = Kind.Laptop) {
    enum class Kind { Laptop, Home }
}

/**
 * 맥 목록은 빌드 설정에서 온다(android/local.properties 의 foldmic.macs — 소스에 주소를 두지 않는다).
 * 형식: 「이름|https://주소:8798|laptop」 을 ; 로 잇는다. 종류(laptop/home)는 생략하면 laptop.
 */
val MACS: List<Mac> = parseMacs(BuildConfig.MACS)

fun parseMacs(spec: String): List<Mac> = spec.split(';').mapNotNull { item ->
    val f = item.split('|').map { it.trim() }
    if (f.size < 2 || f[0].isEmpty() || f[1].isEmpty()) return@mapNotNull null
    val kind = if (f.getOrNull(2).equals("home", ignoreCase = true)) Mac.Kind.Home else Mac.Kind.Laptop
    Mac(f[0], f[1].trimEnd('/'), kind)
}

/** 화면에 나오는 폰 이름 — foldmic.phoneName (기본 「폴드8」). */
val PHONE_NAME: String = BuildConfig.PHONE_NAME

class MainActivity : ComponentActivity() {
    private lateinit var model: MicModel

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        model = ViewModelProvider(this)[MicModel::class.java]
        ThemeMode.load(this)   // 첫 프레임부터 저장된 테마로
        setContent { JlTheme { FoldMicScreen(model) } }
    }

    override fun onResume() { super.onResume(); model.setActive(true) }
    override fun onPause() { super.onPause(); model.setActive(false) }
}
