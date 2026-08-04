#!/bin/bash
# WebUI API 综合测试 — auth-off 与 auth-on 两阶段
# 用法: bash api-test.sh <base_url> <phase_name> <data_dir>
set -u
BASE="$1"; PHASE="$2"; DATA="$3"
PASS=0; FAIL=0; FAILED_NAMES=()

ck() { # ck <name> <expected_http> <curl_args...>
  local name="$1" expect="$2"; shift 2
  local code body
  body=$(curl -s -m 15 -w '\n%{http_code}' "$@" 2>/dev/null)
  code=$(echo "$body" | tail -1)
  body=$(echo "$body" | sed '$d')
  if [ "$code" = "$expect" ]; then PASS=$((PASS+1)); echo "  ✓ $name ($code)";
  else FAIL=$((FAIL+1)); FAILED_NAMES+=("$name"); echo "  ✗ $name: expect $expect got $code"; echo "    body: $(echo "$body" | head -c 300)"; fi
}
jq_get() { echo "$1" | python3 -c "import json,sys
try:
  d=json.load(sys.stdin)
except Exception:
  print('__BADJSON__'); sys.exit()
import functools
for k in '$2'.split('.'):
  if isinstance(d, dict) and k in d: d=d[k]
  elif isinstance(d, list) and k.isdigit(): d=d[int(k)]
  else: print('__MISSING__'); sys.exit()
print('' if d is None else (d if not isinstance(d,(dict,list)) else json.dumps(d)))"; }
jcheck() { # jcheck <name> <actual_json> <jqpath> <expect>（布尔值大小写不敏感）
  local name="$1" actual="$2" path="$3" expect="$4" got
  got=$(jq_get "$actual" "$path")
  if [ "$(echo "$got" | tr 'A-Z' 'a-z')" = "$(echo "$expect" | tr 'A-Z' 'a-z')" ]; then PASS=$((PASS+1)); echo "  ✓ $name ($path=$got)";
  else FAIL=$((FAIL+1)); FAILED_NAMES+=("$name"); echo "  ✗ $name: $path expect $expect got $got"; fi
}
contains() { # contains <name> <haystack> <needle>
  if echo "$2" | grep -qF "$3"; then PASS=$((PASS+1)); echo "  ✓ $1";
  else FAIL=$((FAIL+1)); FAILED_NAMES+=("$1"); echo "  ✗ $1: missing '$3' in $(echo "$2" | head -c 200)"; fi
}
section() { echo ""; echo "== [$PHASE] $1 =="; }

section "基础"
ck "health 200" 200 "$BASE/api/v1/health"
H=$(curl -s -m 15 "$BASE/api/v1/health")
jcheck "health version=1.1.0" "$H" "version" "1.1.0"
jcheck "health degraded (galleryApi down expected)" "$H" "status" "DEGRADED"
ck "metrics 200" 200 "$BASE/api/v1/metrics"
ck "auth/status 200" 200 "$BASE/api/v1/auth/status"
S=$(curl -s -m 15 "$BASE/api/v1/auth/status")
jcheck "authRequired=$AUTHREQ" "$S" "authRequired" "$AUTHREQ"

section "M-3 CORS / M-4 安全头"
ck "OPTIONS preflight 200" 200 -X OPTIONS -H "Origin: http://localhost:3000" -H "Access-Control-Request-Method: GET" "$BASE/api/v1/auth/status"
ACAO=$(curl -s -m 15 -D - -o /dev/null -H "Origin: http://localhost:3000" "$BASE/api/v1/auth/status" | grep -i "^access-control-allow-origin:" | tr -d '\r')
contains "CORS localhost origin reflected" "$ACAO" "localhost"
ACAO2=$(curl -s -m 15 -D - -o /dev/null -H "Origin: http://evil.example" "$BASE/api/v1/auth/status" | grep -i "^access-control-allow-origin:" | tr -d '\r')
if [ -z "$ACAO2" ]; then PASS=$((PASS+1)); echo "  ✓ CORS evil origin not reflected"; else FAIL=$((FAIL+1)); FAILED_NAMES+=("CORS-evil"); echo "  ✗ CORS evil origin reflected: $ACAO2"; fi
HDRS=$(curl -s -m 15 -D - -o /dev/null "$BASE/api/v1/health")
contains "X-Frame-Options DENY" "$HDRS" "X-Frame-Options: DENY"
contains "CSP header" "$HDRS" "Content-Security-Policy"
contains "X-Content-Type-Options" "$HDRS" "X-Content-Type-Options: nosniff"

section "M-6 统一错误 envelope"
E404=$(curl -s -m 15 "$BASE/api/v1/no-such-route")
jcheck "404 envelope code" "$E404" "error.code" "NOT_FOUND"
TID=$(jq_get "$E404" "error.traceId")
[ "$TID" != "__MISSING__" ] && [ "$TID" != "" ] && { PASS=$((PASS+1)); echo "  ✓ 404 traceId present ($TID)"; } || { FAIL=$((FAIL+1)); echo "  ✗ 404 traceId missing"; }
E405=$(curl -s -m 15 -X POST "$BASE/api/v1/auth/status")
jcheck "405 envelope code" "$E405" "error.code" "METHOD_NOT_ALLOWED"
E400=$(curl -s -m 15 -X POST "$BASE/api/v1/favorite/add" -H 'content-type: application/json' -d '{"gid":0,"token":"x"}')
jcheck "400 validation envelope" "$E400" "error.code" "VALIDATION_ERROR"

section "E2E-1/N-3/E2E-8 preferences"
PUSH0=$(curl -s -m 15 -X POST "$BASE/api/v1/sync/push" -H 'content-type: application/json' -d '{"deviceId":"api-test-1","timestamp":1755000000000,"entities":{"preferences":{"preferences":"{\"general\":{\"theme\":\"dark\",\"foreignKey\":true},\"mystery\":42}","lastModified":1755000000000,"deviceId":"api-test-1"}}}')
jcheck "push prefs success" "$PUSH0" "success" "True"
ck "GET /preferences 200 (异构串容错)" 200 "$BASE/api/v1/preferences"
PREF=$(curl -s -m 15 "$BASE/api/v1/preferences")
jcheck "pref theme=dark 生效" "$PREF" "general.theme" "dark"
# N-3 LWW: 旧戳不覆盖
curl -s -m 15 -X POST "$BASE/api/v1/sync/push" -H 'content-type: application/json' -d '{"deviceId":"api-test-1","timestamp":1755000000001,"entities":{"preferences":{"preferences":"{\"general\":{\"theme\":\"light\"}}","lastModified":1754000000000,"deviceId":"api-test-1"}}}' > /dev/null
PREF2=$(curl -s -m 15 "$BASE/api/v1/preferences")
jcheck "LWW: 旧戳不覆盖 (theme 仍 dark)" "$PREF2" "general.theme" "dark"
# E2E-8: lastModified 保留客户端值
PL=$(curl -s -m 15 "$BASE/api/v1/sync/pull?since=0")
jcheck "preferences lastModified=客户端值" "$PL" "entities.preferences.lastModified" "1755000000000"

section "同步域: H-3/N-1/M-13/M-14/E2E-4"
# 全实体 push（含 lastModified=0 记录验证 since=0 边界）
PUSH1=$(curl -s -m 15 -X POST "$BASE/api/v1/sync/push" -H 'content-type: application/json' -d '{"deviceId":"api-test-1","timestamp":1755000000010,"entities":{
 "favorites":[{"gid":1001,"token":"t1","title":"Fav One","category":512,"rating":4,"rated":true,"simpleTags":"a;b","pages":5,"favoriteSlot":2,"lastModified":1755000000010,"deviceId":"api-test-1"}],
 "history":[{"gid":2001,"token":"t2","title":"Hist One","category":2,"rating":0,"rated":false,"mode":9,"time":1755000000011,"lastModified":1755000000011,"deviceId":"api-test-1"}],
 "downloads":[{"gid":3001,"token":"t3","title":"Dl One","category":0,"state":2,"legacy":0,"total":10,"finished":5,"label":"SmokeLabel","time":1755000000012,"lastModified":1755000000012,"deviceId":"api-test-1"}],
 "bookmarks":[{"gid":4001,"token":"t4","title":"Bm One","page":7,"lastModified":1755000000013,"deviceId":"api-test-1"}],
 "filters":[{"mode":0,"text":"ft","enabled":true,"lastModified":1755000000014,"deviceId":"api-test-1"}],
 "quickSearches":[{"name":"qs","mode":0,"category":0,"keyword":"sakura","time":1755000000015,"lastModified":1755000000015,"deviceId":"api-test-1"}],
 "downloadLabels":[{"label":"SmokeLabel","time":1755000000016,"lastModified":1755000000016,"deviceId":"api-test-1"}]
}}')
jcheck "push 7实体 success" "$PUSH1" "success" "True"
PULL=$(curl -s -m 15 "$BASE/api/v1/sync/pull?since=0")
jcheck "pull history mode=9 (M-13)" "$PULL" "entities.history.0.mode" "9"
jcheck "pull download label=SmokeLabel (M-14)" "$PULL" "entities.downloads.0.label" "SmokeLabel"
jcheck "pull favorite category=512 (Int)" "$PULL" "entities.favorites.0.category" "512"
jcheck "pull download state=2 透传 (B3 侧)" "$PULL" "entities.downloads.0.state" "2"
jcheck "pull favorite rated=true 持久化 (B4)" "$PULL" "entities.favorites.0.rated" "True"
jcheck "pull favorite simpleTags 持久化 (B4)" "$PULL" "entities.favorites.0.simpleTags" "a;b"
# N-1: 硬删 tombstone 增量传播
# （注意顺序：history/list 的 mode 断言必须在 tombstone 删除 history 之前做）
HL0=$(curl -s -m 15 "$BASE/api/v1/history/list")
jcheck "history/list mode=9 回显 (M-13 REST)" "$HL0" "history.0.mode" "9"
curl -s -m 15 -X POST "$BASE/api/v1/sync/push" -H 'content-type: application/json' -d '{"deviceId":"api-test-1","timestamp":1755000000020,"entities":{"history":[{"gid":2001,"token":"t2","title":"","category":0,"mode":0,"time":0,"lastModified":1755000000021,"deviceId":"api-test-1","deleted":true}]}}' > /dev/null
PULL2=$(curl -s -m 15 "$BASE/api/v1/sync/pull?since=1755000000019")
jcheck "增量 pull 传播墓碑 deleted=true (N-1)" "$PULL2" "entities.history.0.deleted" "True"
# H-3 since=0 边界: lastModified=0 记录
# （leader 裁决：契约不承诺 pull 顺序——@Index 后 SQLite 行序可变，下标断言作废；
#  改集合断言：history 集合包含 gid=5001 且其 lastModified=0/deleted=false，
#  且包含 gid=2001。H-3 原意保留：since=0 全量拉取必须含 lm=0 记录。）
curl -s -m 15 -X POST "$BASE/api/v1/sync/push" -H 'content-type: application/json' -d '{"deviceId":"api-test-1","timestamp":1755000000030,"entities":{"history":[{"gid":5001,"token":"z","title":"Zero","category":0,"mode":0,"time":0,"lastModified":0,"deviceId":"api-test-1"}]}}' > /dev/null
PULL3=$(curl -s -m 15 "$BASE/api/v1/sync/pull?since=0")
echo "$PULL3" | python3 -c "
import json, sys
try:
    h = json.load(sys.stdin)['entities']['history']
except Exception:
    sys.exit(1)
zero = [r for r in h if r.get('gid') == 5001]
ok = bool(zero) and zero[0].get('lastModified') == 0 and not zero[0].get('deleted')
ok = ok and any(r.get('gid') == 2001 for r in h)
sys.exit(0 if ok else 1)
" && { PASS=$((PASS+1)); echo "  ✓ since=0 返回 lastModified=0 记录 (H-3)"; } || { FAIL=$((FAIL+1)); FAILED_NAMES+=("since=0 返回 lastModified=0 记录 (H-3)"); echo "  ✗ since=0 H-3 集合断言失败: $(echo "$PULL3" | head -c 300)"; }

section "收藏/历史 REST (E2E-4/N-5)"
ck "POST /favorite/add 200" 200 -X POST "$BASE/api/v1/favorite/add" -H 'content-type: application/json' -d '{"gid":6001,"token":"t6","title":"Slot Test","category":512,"slot":99}'
FAV=$(curl -s -m 15 "$BASE/api/v1/favorite/list")
jcheck "favorite/list category Int (E2E-4)" "$FAV" "favorites.0.category" "512"
SLOT=$(curl -s -m 15 "$BASE/api/v1/favorite/list?slot=9")
jcheck "slot=99 clamp 到 9 (N-5)" "$SLOT" "favorites.0.gid" "6001"
# history/list 的 mode 断言已在同步域（tombstone 删除前）执行

section "设置 (M-5)"
S0=$(curl -s -m 15 "$BASE/api/v1/settings")
jcheck "GET /settings 200" "$S0" "download.workerCount" "3"
ck "PUT /settings 非法 workerCount -> 400" 400 -X PUT "$BASE/api/v1/settings" -H 'content-type: application/json' -d '{"download":{"workerCount":99999}}'
ck "PUT /settings 合法 -> 200" 200 -X PUT "$BASE/api/v1/settings" -H 'content-type: application/json' -d '{"download":{"workerCount":7}}'
S1=$(curl -s -m 15 "$BASE/api/v1/settings")
jcheck "workerCount=7 持久化" "$S1" "download.workerCount" "7"

section "备份导出 (M-12 无关 / T-3 日志)"
ck "GET /backup/export 200" 200 "$BASE/api/v1/backup/export" -o /tmp/av-api-test/backup.zip
BZ=$(file /tmp/av-api-test/backup.zip 2>/dev/null | grep -o "Zip archive")
contains "backup zip 合法" "$BZ" "Zip archive"
python3 -c "import zipfile; z=zipfile.ZipFile('/tmp/av-api-test/backup.zip'); n=z.namelist(); assert 'manifest.json' in n and any(s.startswith('slice-') for s in n), n; import json; m=json.loads(z.read('manifest.json')); s=m['slices'][0]; assert m['appVersion']=='1.1.0' and s['sha256'] and s['sizeBytes']>0, m" && { PASS=$((PASS+1)); echo "  ✓ backup manifest 结构/appVersion=1.1.0/slices[0].sha256"; } || { FAIL=$((FAIL+1)); echo "  ✗ backup manifest 校验失败"; }

section "代理测试"
PT=$(curl -s -m 20 -X POST "$BASE/api/v1/proxy/test" -H 'content-type: application/json' -d '{"url":"https://gallery.test/"}')
jcheck "proxy/test success=false" "$PT" "success" "False"
contains "proxy/test 错误串含 gallery.test" "$PT" "gallery.test"

section "M-2 登录限速 (auth-on 阶段验证)"
if [ "$PHASE" = "auth-on" ]; then
  for i in 1 2 3 4 5; do curl -s -o /dev/null -m 15 -X POST "$BASE/api/v1/auth/login" -H 'content-type: application/json' -d '{"username":"nobody","password":"wrong"}'; done
  R6=$(curl -s -m 15 -w '%{http_code}' -X POST "$BASE/api/v1/auth/login" -H 'content-type: application/json' -d '{"username":"nobody","password":"wrong"}')
  contains "第6次登录 429" "$R6" "429"
  ck "匿名访问受保护 API -> 401" 401 "$BASE/api/v1/favorite/list"
  REG=$(curl -s -m 15 -X POST "$BASE/api/v1/auth/register" -H 'content-type: application/json' -d '{"username":"u1","password":"pass1234"}')
  contains "注册成功(首用户) auth-on" "$REG" "Registration successful"
  LOGIN=$(curl -s -m 15 -X POST "$BASE/api/v1/auth/login" -H 'content-type: application/json' -d '{"username":"u1","password":"pass1234"}')
  TOKEN=$(jq_get "$LOGIN" "token")
  ck "带 token 访问 /favorite/list 200" 200 "$BASE/api/v1/favorite/list" -H "Authorization: Bearer $TOKEN"
  PAIR=$(curl -s -m 15 -X POST "$BASE/api/v1/auth/pair" -H "Authorization: Bearer $TOKEN")
  CODE=$(jq_get "$PAIR" "code")
  [ "$CODE" != "__MISSING__" ] && [ ${#CODE} -eq 6 ] && { PASS=$((PASS+1)); echo "  ✓ pair 6位码"; } || { FAIL=$((FAIL+1)); echo "  ✗ pair code: $PAIR"; }
  PC=$(curl -s -m 15 -X POST "$BASE/api/v1/auth/pair/complete" -H 'content-type: application/json' -d "{\"code\":\"$CODE\",\"deviceId\":\"api-device-1\",\"deviceName\":\"APITest\"}")
  NEWTOKEN=$(jq_get "$PC" "token")
  [ "$NEWTOKEN" != "__MISSING__" ] && [ "$NEWTOKEN" != "" ] && { PASS=$((PASS+1)); echo "  ✓ pair/complete 换 token"; } || { FAIL=$((FAIL+1)); echo "  ✗ pair/complete: $PC"; }
  ck "配对 token 可用" 200 "$BASE/api/v1/sync/status" -H "Authorization: Bearer $NEWTOKEN"
  ck "backup/export 带 token 200 (T-3)" 200 "$BASE/api/v1/backup/export" -o /tmp/av-api-test/backup2.zip -H "Authorization: Bearer $NEWTOKEN"
fi

echo ""
echo "===== [$PHASE] 结果: $PASS 通过 / $FAIL 失败 ====="
[ "$FAIL" -gt 0 ] && printf '失败项: %s\n' "${FAILED_NAMES[*]}"
exit $FAIL
