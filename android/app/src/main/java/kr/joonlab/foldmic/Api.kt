package kr.joonlab.foldmic

import org.json.JSONException
import org.json.JSONObject
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * foldmicd 호출 — 동작은 예전 MainActivity.call() 그대로(connect 6s / read 45s, 4xx·5xx 도 본문 JSON 을 읽는다).
 * 달라진 건 실패를 문자열 대신 [Reach] 로 돌려줘서 화면이 사람 말로 바꿀 수 있게 한 것뿐이다.
 */
sealed interface Reply {
    data class Json(val code: Int, val body: JSONObject) : Reply
    data class Fail(val reach: Reach) : Reply
}

/** 맥에 닿지 못한 이유 — 원인 추정(사람 말) + 원문(접어서 보여 줌) */
data class Reach(val kind: Kind, val raw: String) {
    enum class Kind { NoHost, Refused, Timeout, Tls, BadReply, Other }
}

fun call(url: String, method: String): Reply {
    var code = 0
    return try {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = method
        // 맥의 foldmicd 에 FOLDMICD_TOKEN 을 걸었다면 같은 값을 foldmic.token 으로 넣어 빌드한다
        if (BuildConfig.AGENT_TOKEN.isNotEmpty()) c.setRequestProperty("Authorization", "Bearer " + BuildConfig.AGENT_TOKEN)
        c.connectTimeout = 6000
        c.readTimeout = 45000
        code = c.responseCode
        val body = (if (code < 400) c.inputStream else c.errorStream).bufferedReader().readText()
        Reply.Json(code, JSONObject(body))
    } catch (e: Exception) {
        val raw = e.javaClass.simpleName + ": " + (e.message ?: "") + if (code > 0) " (HTTP $code)" else ""
        val kind = when (e) {
            is UnknownHostException -> Reach.Kind.NoHost
            is ConnectException, is NoRouteToHostException -> Reach.Kind.Refused
            is SocketTimeoutException -> Reach.Kind.Timeout
            is SSLException -> Reach.Kind.Tls
            is JSONException -> Reach.Kind.BadReply
            else -> Reach.Kind.Other
        }
        Reply.Fail(Reach(kind, raw))
    }
}
