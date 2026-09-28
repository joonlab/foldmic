import java.text.SimpleDateFormat
import java.util.Date
import java.util.Properties
import java.util.TimeZone

// 개인 설정은 소스에 두지 않는다 — android/local.properties(git 제외) · -P 속성 · 환경변수 순으로 읽는다.
// 키: foldmic.macs / foldmic.token / foldmic.phoneName (android/local.properties.example 참고)
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.reader(Charsets.UTF_8).use { load(it) }   // 한글 이름을 그대로 쓸 수 있게 UTF-8
}
fun setting(key: String, env: String, default: String = ""): String =
    (findProperty(key) as String?) ?: localProps.getProperty(key) ?: System.getenv(env) ?: default
fun kstr(v: String) = "\"" + v.replace("\\", "\\\\").replace("\"", "\\\"") + "\""   // BuildConfig 는 Java 문자열

plugins {
    id("com.android.application")
    // AGP 9 부터 Kotlin 은 AGP 내장 — kotlin.android 플러그인은 넣지 않는다.
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "kr.joonlab.foldmic"
    compileSdk = 36

    defaultConfig {
        applicationId = "kr.joonlab.foldmic"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        val stamp = SimpleDateFormat("MM-dd HH:mm").apply { timeZone = TimeZone.getTimeZone("Asia/Seoul") }.format(Date())
        buildConfigField("String", "BUILD_TIME", "\"$stamp\"")
        // 「이름|https://주소:8798|laptop 또는 home」 을 ; 로 잇는다
        buildConfigField("String", "MACS", kstr(setting("foldmic.macs", "FOLDMIC_MACS")))
        buildConfigField("String", "AGENT_TOKEN", kstr(setting("foldmic.token", "FOLDMIC_TOKEN")))
        buildConfigField("String", "PHONE_NAME", kstr(setting("foldmic.phoneName", "FOLDMIC_PHONE_NAME", "폴드8")))
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    buildTypes { release { isMinifyEnabled = false } }
}

// 폴드 앱 묶음과 같은 Compose 버전. 네트워크는 여전히 표준 HttpURLConnection 하나.
// BOM 2026.08+ 는 compileSdk 37 을 요구한다 — 2026.06.01 고정.
dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.06.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.12.4")
}
