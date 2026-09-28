// pm2 start agent/ecosystem.config.js — 경로는 이 파일 기준. 토큰을 쓰려면 FOLDMICD_TOKEN 을 export 한 셸에서 시작한다.
module.exports = { apps: [{
  name: "foldmicd",
  script: "/usr/bin/python3",
  args: [__dirname + "/foldmicd.py"],
  interpreter: "none",
  autorestart: true,
  env: {
    FOLDMICD_PORT: process.env.FOLDMICD_PORT || "7798",
    FOLDMICD_TOKEN: process.env.FOLDMICD_TOKEN || "",
  },
}] };
