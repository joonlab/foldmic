package kr.joonlab.foldmic

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** 카드 하나(맥 하나)의 큰 상태. 전환 중인지는 [MacState.switching] 이 따로 들고 있다. */
enum class Phase {
    Loading,      // 아직 한 번도 못 물어봄
    Off,          // 맥 자기 마이크
    On,           // 폴드8 마이크 사용 중 (fold=true)
    Mismatch,     // 브리지만 살아 있고 기본 입력은 원래 마이크 (bridge=on, fold=false)
    AgentError,   // /status 가 {"error": …} — 관측 불가(꺼짐으로 보이면 안 된다)
    Unreachable,  // 맥에 닿지 않음
}

/** 켜기·끄기 실패 — 다음 동작이나 닫기 전까지 남는다(폴링이 지우지 않는다). */
data class ActionFail(val on: Boolean, val title: String, val hint: String, val detail: String, val fix: Fix? = null)

enum class Fix { DevSettings, Tailscale }

data class MacState(
    val phase: Phase = Phase.Loading,
    val input: String = "",
    val host: String = "",
    val switching: Boolean? = null,   // null = 아님, true = 켜는 중, false = 끄는 중
    val since: Long = 0L,             // 전환 시작 시각(경과 초 표시)
    val checking: Boolean = false,    // 사람이 누른 «다시 시도» 확인 중
    val reach: Reach? = null,
    val agentErr: String? = null,
    val fail: ActionFail? = null,
    val lastOk: Long = 0L,            // 마지막으로 상태를 제대로 받은 시각
    val restoreTo: String? = null,    // 끄면 돌아갈 입력(켜기 응답 message 에서만 알 수 있다)
) {
    val busy get() = switching != null
    /** 스위치를 만질 수 있는가 — 상태를 모르면(로딩·오류·연결 안 됨) 잠근다 */
    val canToggle get() = !busy && phase in setOf(Phase.Off, Phase.On, Phase.Mismatch)
}

/** 화면이 한 번 느끼게 할 것(햅틱·공지) */
data class Feedback(val seq: Int, val ok: Boolean)

/**
 * 맥별 상태를 들고 폴링·전환을 한다. 예전 MainActivity.Row 의 refresh()/toggle()/show() 를 옮긴 것.
 * 바뀐 점(동작은 같음, 버그만 막음):
 *  - 맥당 진행 중인 /status 는 하나만(스레드 누적 방지)
 *  - 전환을 시작하면 epoch 가 올라가 그 전에 떠난 옛 /status 응답은 버린다(순서 역전 방지)
 *  - ViewModel 이라 접고 펴도 전환 중 상태가 남는다
 */
class MicModel : ViewModel() {
    val states = mutableStateMapOf<String, MacState>().apply { MACS.forEach { put(it.label, MacState()) } }
    var feedback by mutableStateOf<Feedback?>(null)
        private set

    private val epoch = HashMap<String, Int>()
    private val statusJob = HashMap<String, Job>()
    private var pollJob: Job? = null
    private var fbSeq = 0

    fun state(mac: Mac) = states[mac.label] ?: MacState()
    private fun set(mac: Mac, f: (MacState) -> MacState) { states[mac.label] = f(state(mac)) }

    /** 화면이 보일 때만 5초마다 묻는다(onResume/onPause). */
    fun setActive(on: Boolean) {
        pollJob?.cancel()
        pollJob = if (!on) null else viewModelScope.launch {
            while (isActive) {
                MACS.forEach { refresh(it) }
                delay(5000)
            }
        }
    }

    fun refresh(mac: Mac, manual: Boolean = false) {
        if (state(mac).busy) return
        if (manual) set(mac) { it.copy(checking = true) }
        if (statusJob[mac.label]?.isActive == true) return
        val e = epoch[mac.label] ?: 0
        statusJob[mac.label] = viewModelScope.launch {
            val r = withContext(Dispatchers.IO) { call(mac.base + "/status", "GET") }
            if ((epoch[mac.label] ?: 0) != e || state(mac).busy) return@launch
            val now = System.currentTimeMillis()
            set(mac) { s ->
                when (r) {
                    is Reply.Json -> applyStatus(s, r.body, now)
                    is Reply.Fail -> s.copy(phase = Phase.Unreachable, reach = r.reach)
                }.copy(checking = false)
            }
        }
    }

    fun toggle(mac: Mac, on: Boolean) {
        val s = state(mac)
        if (s.busy) return
        if (!s.canToggle) return
        epoch[mac.label] = (epoch[mac.label] ?: 0) + 1
        statusJob.remove(mac.label)?.cancel()
        set(mac) { it.copy(switching = on, since = System.currentTimeMillis(), fail = null, checking = false) }
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) { call(mac.base + if (on) "/on" else "/off", "POST") }
            val now = System.currentTimeMillis()
            when (r) {
                is Reply.Json -> {
                    val j = r.body
                    val ok = j.optBoolean("ok", false) && r.code < 400
                    val msg = j.optString("message").ifBlank { j.optString("error") }
                    set(mac) { cur ->
                        var n = if (j.has("fold") || (j.has("error") && j.has("ok"))) applyStatus(cur, j, now) else cur
                        if (ok && on) n = n.copy(restoreTo = parseRestore(msg))
                        n.copy(switching = null, fail = if (ok) null else parseFail(msg, on))
                    }
                    feedback = Feedback(++fbSeq, ok)
                }
                is Reply.Fail -> {
                    // 응답을 못 받았다고 실패는 아니다(맥은 바뀌었을 수 있다) → 바로 다시 물어 확정한다
                    set(mac) {
                        it.copy(switching = null, fail = ActionFail(on, "결과를 받지 못했어요",
                            reachText(r.reach).second + " 맥 상태를 다시 확인할게요.", r.reach.raw,
                            if (r.reach.kind == Reach.Kind.NoHost) Fix.Tailscale else null))
                    }
                    feedback = Feedback(++fbSeq, false)
                    refresh(mac, manual = true)
                }
            }
        }
    }

    fun dismissFail(mac: Mac) = set(mac) { it.copy(fail = null) }

    private fun applyStatus(s: MacState, j: JSONObject, now: Long): MacState {
        if (!j.has("fold")) {
            return s.copy(phase = Phase.AgentError, agentErr = j.optString("error").ifBlank { j.toString() }, reach = null)
        }
        val ph = when {
            j.optBoolean("fold") -> Phase.On
            j.optString("bridge") == "on" -> Phase.Mismatch
            else -> Phase.Off
        }
        // 실패했던 동작이 결국 이뤄졌으면(예: 사람이 폰 화면을 켜 adb 가 붙음) 실패 상자를 거둔다
        val fail = s.fail?.takeUnless { (it.on && ph == Phase.On) || (!it.on && ph == Phase.Off) }
        return s.copy(phase = ph, input = j.optString("input"), host = j.optString("host"), reach = null, agentErr = null,
            lastOk = now, fail = fail, restoreTo = if (ph == Phase.On) s.restoreTo else null)
    }
}

/** `✓ 폴드8 마이크 사용 중 (기본 입력: …, 복귀 대상: MacBook Pro Microphone)` → 복귀 대상 */
private fun parseRestore(msg: String): String? =
    Regex("복귀 대상: ([^)\\n]+)\\)").find(msg)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }

/** foldmic CLI 출력(✗ 한 줄 + 로그 여러 줄) → 사람 말 한 줄 + 다음 행동. 원문은 detail 로 접어 둔다. */
fun parseFail(msg: String, on: Boolean): ActionFail {
    val lines = msg.lines().map { it.trim() }.filter { it.isNotEmpty() }
    val head = (lines.firstOrNull { it.startsWith("✗") } ?: lines.firstOrNull() ?: "").removePrefix("✗").trim()
    return when {
        "adb" in head -> ActionFail(on, "폰에 연결하지 못했어요",
            "폰 화면을 한 번 켠 뒤 다시 시도하세요. 폰을 다시 켰다면 맥에서 adb tcpip 5555 가 한 번 필요해요.", msg, Fix.DevSettings)
        "BlackHole" in head -> ActionFail(on, "맥에 BlackHole 2ch 가 없어요",
            "맥에서 BlackHole 2ch 를 설치하고 coreaudiod 를 다시 시작하세요.", msg)
        "브리지 시작 실패" in head -> ActionFail(on, "폰 소리가 맥에 닿지 않았어요",
            "원래 마이크로 되돌려 두었어요. 폰 화면을 켜고 다시 시도해 보세요.", msg)
        head == "not found" -> ActionFail(on, "맥의 에이전트가 이 요청을 몰라요", "맥의 foldmicd 를 최신으로 바꿔 주세요.", msg)
        else -> ActionFail(on, if (on) "켜지 못했어요" else "끄지 못했어요", head.ifEmpty { "맥이 이유를 알려 주지 않았어요." }, msg)
    }
}

/** 닿지 못한 이유 → (제목, 원인 추정) */
fun reachText(r: Reach): Pair<String, String> = when (r.kind) {
    Reach.Kind.NoHost -> "맥 주소를 찾지 못했어요" to "폰의 Tailscale 이 꺼져 있을 수 있어요."
    Reach.Kind.Refused -> "맥이 연결을 받지 않아요" to "맥이 꺼졌거나 Tailscale 연결이 끊겼을 수 있어요."
    Reach.Kind.Timeout -> "맥이 응답하지 않아요" to "맥이 잠들었거나 네트워크가 느려요."
    Reach.Kind.Tls -> "보안 연결에 실패했어요" to "맥의 tailscale serve 설정을 확인해 보세요."
    Reach.Kind.BadReply -> "맥의 에이전트가 답하지 않아요" to "맥에서 foldmicd(pm2)가 꺼져 있을 수 있어요."
    Reach.Kind.Other -> "맥에 닿지 않아요" to "잠시 뒤 다시 시도해 보세요."
}
