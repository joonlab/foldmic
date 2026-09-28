#!/usr/bin/env python3
"""foldmicd — 폰 앱이 이 맥의 마이크를 폴드8 로 전환/복귀하게 해 주는 작은 에이전트.

127.0.0.1:7798 에만 바인딩하고 `tailscale serve --https=8798` 로만 내보낸다.
  GET  /status  → {"host","input","bridge","fold"}
  POST /on      → foldmic on  (폴드8 마이크로 전환)
  POST /off     → foldmic off (원래 마이크로 복귀)

위협 모델: 기본은 «내 Tailscale 망 안의 기기는 믿는다» 이다. tailnet 을 다른 사람과 같이 쓰거나
노드를 공유한다면 FOLDMICD_TOKEN 을 설정하라 — 그러면 모든 요청에 `Authorization: Bearer <토큰>` 이 필요하다.
환경변수:
  FOLDMICD_PORT   바인딩 포트(기본 7798)
  FOLDMICD_TOKEN  비우면 인증 없음
  FOLDMIC_BIN     foldmic CLI 경로(기본: 이 파일 기준 ../bin/foldmic)
"""
import hmac, json, os, subprocess, threading, time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

HERE = os.path.dirname(os.path.abspath(__file__))
FOLDMIC = os.environ.get("FOLDMIC_BIN") or os.path.normpath(os.path.join(HERE, "..", "bin", "foldmic"))
PORT = int(os.environ.get("FOLDMICD_PORT", "7798"))
TOKEN = os.environ.get("FOLDMICD_TOKEN", "")
AUDIT = os.path.expanduser("~/.local/state/foldmic/audit.log")
LOCK = threading.Lock()  # on/off 가 겹치면 기본 입력 복귀 대상이 꼬인다


def run(*args, timeout=40):
    p = subprocess.run([FOLDMIC, *args], capture_output=True, text=True, timeout=timeout)
    return p.returncode, (p.stdout + p.stderr).strip()


def status():
    _, out = run("status", "--json", timeout=10)
    try:
        return json.loads(out.splitlines()[-1])
    except Exception:
        return {"error": out}


class H(BaseHTTPRequestHandler):
    def reply(self, code, body):
        b = json.dumps(body, ensure_ascii=False).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(b)))
        self.end_headers()
        self.wfile.write(b)

    def authorized(self):
        if not TOKEN:
            return True
        got = self.headers.get("Authorization", "")
        return hmac.compare_digest(got.encode(), f"Bearer {TOKEN}".encode())

    def do_GET(self):
        if not self.authorized():
            return self.reply(401, {"error": "unauthorized"})
        if self.path == "/status":
            return self.reply(200, status())
        self.reply(404, {"error": "not found"})

    def do_POST(self):
        if not self.authorized():
            return self.reply(401, {"error": "unauthorized"})
        if self.path not in ("/on", "/off"):
            return self.reply(404, {"error": "not found"})
        act = self.path[1:]
        with LOCK:
            t0 = time.time()
            rc, msg = run(act)
            st = status()
        os.makedirs(os.path.dirname(AUDIT), exist_ok=True)
        with open(AUDIT, "a") as f:
            f.write(f"{time.strftime('%F %T')}\t{act}\trc={rc}\t{time.time() - t0:.1f}s\t{msg.splitlines()[-1] if msg else ''}\n")
        self.reply(200 if rc == 0 else 500, {"ok": rc == 0, "message": msg, **st})

    def log_message(self, *a):
        pass


if __name__ == "__main__":
    ThreadingHTTPServer(("127.0.0.1", PORT), H).serve_forever()
