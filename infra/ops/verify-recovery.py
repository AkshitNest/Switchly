"""Run on the documented, disposable RC-1 drill project after browser acceptance."""
import argparse
import hashlib
import http.client
import json
import os
from pathlib import Path
import secrets
import ssl
import tempfile
import subprocess
import time
import uuid

parser = argparse.ArgumentParser()
parser.add_argument("--ca", required=True)
parser.add_argument("--backup-dir", required=True)
args = parser.parse_args()
if os.environ.get("COMPOSE_PROJECT_NAME") != "switchly-rc1-drill" or "rc1-drill.compose.yml" not in os.environ.get("COMPOSE_FILE", ""):
    raise SystemExit("This restart drill requires the isolated switchly-rc1-drill project")
if os.environ.get("SWITCHLY_DATABASE", "switchly") != "switchly":
    raise SystemExit("Start the drill on its original switchly database")
tls = ssl.create_default_context(cafile=args.ca)
root = Path(__file__).resolve().parent
canaries = [p.read_text().strip() for p in Path(os.environ["SWITCHLY_SECRETS_DIR"]).iterdir() if p.is_file()]
report = {}


def command(*argv, data=None, env=None):
    result = subprocess.run(argv, input=data, text=True, capture_output=True, env=env)
    if result.returncode:
        # Keep stderr (which can include sensitive details) out of release evidence.
        raise RuntimeError(f"{argv[0]} {argv[1]} exited with {result.returncode}")
    return result.stdout.strip()


def compose(*argv, **kw):
    return command("docker", "compose", *argv, **kw)


def sql(db, query):
    return compose("exec", "-T", "postgres", "psql", "-XqAt", "-v", "ON_ERROR_STOP=1", "-U", "postgres", "-d", db, data=query)


def api(path, body=None, *, method=None, auth=None, cookie=None, status=200, direct=False):
    c = http.client.HTTPConnection("127.0.0.1", 18088, timeout=30) if direct else http.client.HTTPSConnection("localhost", 18443, context=tls, timeout=35)
    headers = {"Content-Type": "application/json", "X-Switchly-Request": "console"}
    if auth:
        headers["Authorization"] = auth
    if cookie:
        headers["Cookie"] = cookie
    try:
        c.request(method or ("POST" if body is not None else "GET"), path,
                  None if body is None else json.dumps(body), headers)
        r = c.getresponse()
        raw = r.read()
        if r.status != status:
            raise RuntimeError(f"{path.split('?')[0]} expected {status}, received {r.status}")
        result = json.loads(raw) if raw else None
        if status >= 400:
            assert all(value.encode() not in raw for value in canaries)
            if path != "/healthz":
                assert set(result) == {"error"} and set(result["error"]) == {"code", "message"}
        return result, dict((k.lower(), v) for k, v in r.getheaders())
    finally:
        c.close()


def fingerprint(database):
    # The isolated instance is quiescent while comparing. Only hashes leave psql.
    return sql(database, """
SELECT format('SELECT %L,count(*),md5(coalesce(string_agg(md5(to_jsonb(t)::text),%L ORDER BY md5(to_jsonb(t)::text)),%L)) FROM %I AS t',
table_name,'','',table_name)
FROM information_schema.tables WHERE table_schema='public' AND table_type='BASE TABLE' ORDER BY table_name
\\gexec
""")


def scan_logs():
    logs = compose("logs", "--no-color")
    assert all(c not in logs for c in canaries), "A secret appeared in a service log"
    return logs


def wait_up():
    for _ in range(50):
        try:
            if api("/healthz")[0] == {"status": "UP"}:
                return
        except (OSError, RuntimeError, http.client.HTTPException):
            pass
        time.sleep(1)
    raise RuntimeError("Readiness did not recover")


prefix = "/api/v1"
email = uuid.uuid4().hex + "@rc1.invalid"
password = secrets.token_urlsafe(32)
canaries.append(password)
account = {"email": email, "password": password}
tokens, _ = api(prefix + "/auth/register", {**account, "organizationName": "Recovery drill", "fullName": "RC Operator"}, direct=True, status=201)
canaries.extend([tokens["accessToken"], tokens["refreshToken"]])
_, headers = api(prefix + "/auth/login", account)
cookie = headers["set-cookie"].split(";")[0]
canaries.append(cookie.split("=", 1)[1])
assert all(v in headers["set-cookie"] for v in ["HttpOnly", "SameSite=Strict", "Secure"])
assert "max-age=" in headers["strict-transport-security"] and headers["cache-control"] == "no-store"
org = api(prefix+"/orgs", cookie=cookie)[0][0]["id"]
project = api(f"{prefix}/orgs/{org}/projects", {"name":"Recovery", "key":"restore-proof"}, cookie=cookie, status=201)[0]["id"]
pp = f"{prefix}/projects/{project}"
env = next(e["id"] for e in api(pp+"/environments", cookie=cookie)[0] if e["key"] == "production")
flag = api(pp+"/flags", {"name":"Release","key":"release","valueType":"BOOLEAN","defaultValue":False}, cookie=cookie,status=201)[0]["id"]
config = f"{prefix}/environments/{env}/flags/{flag}"
for tail, body in [("state", {"enabled":True}),("value",{"value":True}),("rollout",{"percentage":100})]:
    api(config+"/"+tail, body, method="PUT", cookie=cookie)
sdk = api(pp+"/sdk-keys", {"name":"Drill","environmentKeys":["production"]}, cookie=cookie,status=201)[0]["secret"]
invitation = api(f"{prefix}/orgs/{org}/members/invitations", {"email":"drill@example.invalid","role":"EMPLOYEE","projectId":project},cookie=cookie,status=201)[0]["token"]
canaries.extend([sdk, invitation])


def evaluate():
    result, _ = api(prefix+"/evaluate", {"projectKey":"restore-proof","environmentKey":"production","flagKey":"release","context":{"userId":"repeatable"}},auth="SDK "+sdk)
    assert result["value"] is True and result["reason"] == "ROLLOUT_INCLUDED"


evaluate()
api(prefix+"/auth/login", {**account, "password":sdk},status=401)
api(prefix+"/evaluate", {},auth="SDK "+password,status=401)
api(config+"/preview", {"context":sdk}, cookie=cookie,status=400)
report["tls_and_errors"] = "Verified certificate chain, HSTS, secure session cookie and redacted 400/401 responses"
report["app_role_superuser_createdb_createrole"] = sql("switchly","select rolsuper,rolcreatedb,rolcreaterole from pg_roles where rolname='switchly';")
assert report["app_role_superuser_createdb_createrole"] == "f|f|f"

# Leave a login request in flight, then finish it during SIGTERM drainage.
stream = http.client.HTTPSConnection("localhost",18443,context=tls,timeout=15)
payload=json.dumps(account).encode()
stream.putrequest("POST",prefix+"/auth/login")
for k,v in {"Content-Type":"application/json","X-Switchly-Request":"console","Content-Length":str(len(payload))}.items():
    stream.putheader(k,v)
stream.endheaders(payload[:5])
time.sleep(.5)
stop = subprocess.Popen(["docker","compose","stop","web"], stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
for _ in range(30):
    if "draining connections" in compose("logs","--since","5s","web"):
        break
    time.sleep(.1)
stream.send(payload[5:])
finished = stream.getresponse()
assert finished.status==200
finished.read()
stream.close()
assert stop.wait(timeout=25)==0
report["in_flight_login_on_shutdown"] = 200
compose("up","-d","--wait","web")
api(prefix+"/auth/me",cookie=cookie,status=401)
evaluate()
compose("stop","postgres")
assert api("/healthz",status=503)[0]=={"status":"DOWN"}
compose("up","-d","--wait","postgres")
wait_up()
report["readiness_on_database_outage_and_recovery"] = [503,200]

snapshot = fingerprint("switchly")
bundle = command(str(root/"backup.sh"),args.backup_dir)
assert (Path(bundle)/"database.dump").stat().st_mode & 0o077 == 0
restored = "rc1_restore_" + uuid.uuid4().hex[:12]
command(str(root/"restore.sh"),bundle,restored)
assert snapshot == fingerprint(restored)
duplicate = subprocess.run([str(root/"restore.sh"),bundle,restored],capture_output=True)
assert duplicate.returncode != 0
report["backup_bytes"] = (Path(bundle)/"database.dump").stat().st_size
report["restored_tables"] = len(snapshot.splitlines())
report["matching_database_fingerprint_sha256"] = hashlib.sha256(snapshot.encode()).hexdigest()
report["existing_restore_target_refused"] = True
with tempfile.TemporaryDirectory(dir=args.backup_dir) as temp:
    invalid = Path(temp)
    (invalid/"SHA256SUMS").write_bytes((Path(bundle)/"SHA256SUMS").read_bytes())
    (invalid/"database.dump").write_bytes(b"damaged archive")
    rejected = subprocess.run([str(root/"restore.sh"),temp,restored+"_bad"],capture_output=True)
    assert rejected.returncode != 0
    assert sql("postgres",f"select count(*) from pg_database where datname='{restored}_bad'")=="0"
report["damaged_archive_refused_before_database_creation"] = True
report["restored_flyway_version"] = sql(restored,"SELECT version FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1;")
assert report["restored_flyway_version"]=="10"
scan_logs()
compose("stop")
states = {}
for svc in ["postgres","api","web"]:
    container = compose("ps","--all","--quiet",svc)
    state = json.loads(command("docker","inspect","--format","{{json .State}}",container))
    assert not state["OOMKilled"] and state["ExitCode"] in [0,143]
    states[svc] = state["ExitCode"]
logs = scan_logs()
assert "Graceful shutdown complete" in logs and "database system is shut down" in logs
report["stop_exit_codes"] = states
compose("down")
compose("up","-d","--wait","--wait-timeout","145")
wait_up()
assert fingerprint("switchly") == snapshot
evaluate()
report["volume_after_container_recreation"] = "All table fingerprints matched, original SDK key evaluated"
try:
    compose("up","-d","--wait","--wait-timeout","145","api",env={**os.environ,"SWITCHLY_DATABASE":restored})
    wait_up()
    evaluate()
    _, signed = api(prefix+"/auth/login",account)
    auth_cookie = signed["set-cookie"].split(";")[0]
    assert api(pp+"/flags",cookie=auth_cookie)[0][0]["id"] == flag
    assert api(config+"/rollout",cookie=auth_cookie)[0]["percentage"] == 100
    api(pp+"/flags",{"name":"Cutover witness","key":"cutover-witness"},cookie=auth_cookie,status=201)
    lookup = f"SELECT count(*) FROM flags WHERE project_id='{project}' AND key='cutover-witness';"
    assert sql(restored,lookup) == "1" and sql("switchly",lookup) == "0"
    report["cutover_witness_counts_restored_original"] = [1,0]
    report["restored_application"] = "Login, project flags, rollout and original SDK credential passed"
    scan_logs()
finally:
    compose("up","-d","--wait","--wait-timeout","145","api")
wait_up()
evaluate()
scan_logs()
report["switchback"] = "Original database served the same SDK evaluation"
report["plaintext_canaries_absent_from_logs"] = len(canaries)
print(json.dumps(report,indent=2))
