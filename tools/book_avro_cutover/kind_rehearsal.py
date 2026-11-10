#!/usr/bin/env python3
"""Destructive lab rehearsal, restricted to the disposable M18 Kind context.
Uses the ephemeral CI signing key only; never logs credentials or token bytes.
"""
import base64
from contextlib import contextmanager
import json
import os
from pathlib import Path
import re
import secrets
import socket
import subprocess
import tempfile
import time
import urllib.error
import urllib.request
import uuid

OUT = Path("target/metrics/book-avro-kind.json")
BOOK_FIELDS = (
    "title", "description", "releaseYear", "coverUrl", "author",
    "isbn", "pageCount", "publisher", "genres",
)


def cmd(*args, stdin=None, timeout=90, safe_diagnostic=False):
    p = subprocess.run(args, input=stdin, text=True, capture_output=True, timeout=timeout)
    if p.returncode:
        # Never include command args or arbitrary stderr: a fixture may hold ephemeral credentials.
        detail = ("; " + (p.stdout+"\n"+p.stderr)[-3000:]) if safe_diagnostic else ""
        raise RuntimeError(f"Command failed: {Path(args[0]).name}, exit={p.returncode}"+detail)
    return p.stdout.strip()


def k(*args, **kwargs):
    return cmd("kubectl", "-n", "vellumhub", *args, **kwargs)


def sql(service, statement):
    return k("exec", "-i", f"statefulset/{service}-postgres", "--", "sh", "-ec",
             'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -qAt -v ON_ERROR_STOP=1', stdin=statement)


@contextmanager
def forward(service, port):
    with socket.socket() as s:
        s.bind(("127.0.0.1", 0)); local = s.getsockname()[1]
    p = subprocess.Popen(["kubectl", "-n", "vellumhub", "port-forward", f"service/{service}", f"{local}:{port}"],
                         stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    try:
        deadline = time.monotonic()+20
        while True:
            try:
                with socket.create_connection(("127.0.0.1", local), timeout=1):
                    break
            except OSError:
                if p.poll() is not None or time.monotonic()>deadline:
                    raise RuntimeError("Port forwarding unavailable")
                time.sleep(.25)
        yield f"http://127.0.0.1:{local}"
    finally:
        p.terminate()
        try: p.wait(timeout=5)
        except subprocess.TimeoutExpired: p.kill(); p.wait()


def request(url, method="GET", body=None, token=None, expected=200):
    headers = {"Content-Type": "application/json"}
    if token: headers["Authorization"] = "Bearer "+token
    req = urllib.request.Request(url, method=method, headers=headers,
                                 data=json.dumps(body).encode() if body is not None else None)
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            status, raw, response_headers = r.status, r.read(), r.headers
    except urllib.error.HTTPError as e:
        status, raw, response_headers = e.code, e.read(), e.headers
    if status != expected:
        # Log only the bounded API error *category*, never arbitrary response
        # details, token claims, credentials or response bodies.
        try:
            parsed = json.loads(raw)
            category = parsed.get("error") if isinstance(parsed, dict) else None
        except (ValueError, TypeError):
            category = None
        raise RuntimeError(
            f"HTTP fixture expected {expected}, received {status}; "
            f"API error category={str(category or 'unavailable')[:80]}"
        )
    if expected == 503 and response_headers.get("Retry-After") != "5":
        raise RuntimeError("Frozen Book mutation must include Retry-After: 5")
    return json.loads(raw) if raw else None


def book_update_payload(original, title):
    """Send the complete API update shape, not a partial PUT that can fail
    decoding or validation before reaching the transactional Book write fence.
    """
    if set(original) != set(BOOK_FIELDS) or not original.get("genres"):
        raise RuntimeError("Book cutover fixture is incomplete for the PUT contract")
    return dict(original, title=title)


def token_for_lab():
    config = json.loads(k("get", "configmap", "vellumhub-config", "-o", "json"))["data"]
    with forward("auth-service", 9000) as root:
        kid = request(root+"/oauth2/jwks")["keys"][0]["kid"]
    encode = lambda b: base64.urlsafe_b64encode(b).rstrip(b"=").decode()
    now = int(time.time())
    claims = dict(iss=config["JWT_ISSUER"], aud=[config["JWT_AUDIENCE"]], sub=str(uuid.uuid4()),
                  iat=now, exp=now+1800, jti=str(uuid.uuid4()), client_id="kind-book-rehearsal",
                  roles=["ADMIN"], scope="catalog:read catalog:write", grant_type="authorization_code")
    unsigned = ".".join(encode(json.dumps(x).encode()) for x in ({"alg":"RS256","typ":"at+jwt","kid":kid}, claims))
    secret = json.loads(k("get", "secret", "vellumhub-jwt-private", "-o", "json"))["data"]["private.pem"]
    with tempfile.TemporaryDirectory() as d:
        key = Path(d)/"key.pem"; key.write_bytes(base64.b64decode(secret)); key.chmod(0o600)
        signed = subprocess.run(["openssl", "dgst", "-sha256", "-sign", str(key)], input=unsigned.encode(),
                                capture_output=True, check=True).stdout
    return unsigned+"."+encode(signed)


def kafka(script, *args):
    # These CLI calls describe/create topics and describe groups; they never carry payloads or credentials.
    return k("exec", "pod/book-avro-kafka-admin", "--", f"/opt/kafka/bin/{script}.sh", "--bootstrap-server", "kafka:9092", *args, safe_diagnostic=True)


def stage(service, **values):
    # Only this disposable Kind lab uses single-replica recreate-first rollouts.
    # With the Registry and Kafka admin pod running, the single Kind node cannot
    # schedule a second Java service replica (FailedScheduling: Insufficient cpu).
    # Keep the real deployment templates/production rollout strategy unchanged.
    if service not in ("recommendation-service", "engagement-service", "catalog-service"):
        raise ValueError("Unsupported Book Avro lab deployment")
    policy = {"spec": {"strategy": {"type": "RollingUpdate",
                                    "rollingUpdate": {"maxSurge": 0, "maxUnavailable": 1}}}}
    k("patch", "deployment", service, "--type=merge", "-p", json.dumps(policy))
    k("set", "env", f"deployment/{service}", *(f"{key}={value}" for key,value in values.items()))
    k("rollout", "status", f"deployment/{service}", "--timeout=240s", timeout=255)


def freeze(value):
    assert sql("catalog", f"UPDATE book_write_gate SET frozen={'TRUE' if value else 'FALSE'},updated_at=now() WHERE id=1 RETURNING frozen;") == ("t" if value else "f")


class ReconciliationTimeout(RuntimeError):
    """Fail closed, with bounded structured evidence (never dump payloads/tokens)."""
    def __init__(self, evidence):
        super().__init__("Book read models failed to reconcile; inspect failure_evidence in book-avro-kind.json")
        self.evidence = evidence


def reconciliation_evidence(book, revision, pending, revisions, quarantine, model, active, title, deleted):
    """Only read-only, per-fixture diagnostics. Avoid recording Kafka payloads or error messages."""
    evidence = {
        "expected_revision": revision,
        "pending_global": pending,
        "checkpoints": revisions,
        "quarantine_counts": quarantine,
        "recommendation_title_matches": model == ("" if deleted else title),
        "engagement_active_matches": active == ("f" if deleted else "t"),
    }

    def capture(name, fn):
        try:
            evidence[name] = fn()
        except Exception as exc:
            evidence[name] = {"collection_error_type": type(exc).__name__}

    capture("catalog_revision", lambda: sql("catalog",
        f"SELECT version FROM books WHERE id='{book}';"))
    capture("outbox_for_book", lambda: json.loads(sql("catalog", f"""
        SELECT coalesce(json_agg(e), '[]'::json) FROM (
            SELECT aggregate_version, topic, wire_format, status, attempts,
                   (next_attempt_at > now()) AS waiting_for_retry,
                   CASE WHEN split_part(last_error, ':', 1)
                             ~ '^[A-Za-z][A-Za-z0-9.$]{{0,80}}$'
                        THEN split_part(last_error, ':', 1)
                        WHEN last_error IS NULL THEN NULL ELSE 'UNCLASSIFIED'
                   END AS error_type
            FROM outbox_events WHERE aggregate_id='{book}'
            ORDER BY aggregate_version
        ) e;
    """)))
    for service in ("recommendation", "engagement"):
        capture(service + "_quarantine", lambda service=service: json.loads(sql(service, """
            SELECT coalesce(json_agg(e), '[]'::json) FROM (
                SELECT consumer_group, source_topic, source_partition,
                       source_offset, status, attempts, failure_category
                FROM kafka_consumer_quarantine WHERE status <> 'RESOLVED'
                ORDER BY source_topic, source_partition, source_offset LIMIT 10
            ) e;
        """)))
    for service in ("recommendation-service", "engagement-service"):
        group = service + "-book-avro"
        def consumer_position(group=group):
            lines = kafka("kafka-consumer-groups", "--describe", "--group", group).splitlines()
            rows = [parts for line in lines
                    if (parts := line.split()) and len(parts) >= 6
                    and parts[1] == "book-events.avro"]
            return [dict(partition=r[2], current_offset=r[3],
                         end_offset=r[4], lag=r[5]) for r in rows]
        capture(group + "_offsets", consumer_position)
    return evidence


def drained(book, revision, title, deleted=False):
    deadline = time.monotonic()+90
    while True:
        pending = sql("catalog", "SELECT count(*) FROM outbox_events WHERE aggregate_type='BOOK' AND status<>'PUBLISHED';")
        revisions = {s: sql(s, f"SELECT last_revision FROM book_event_offsets WHERE book_id='{book}';")
                     for s in ("recommendation", "engagement")}
        quarantine = {s: sql(s, "SELECT count(*) FROM kafka_consumer_quarantine WHERE status<>'RESOLVED';") for s in revisions}
        model = sql("recommendation", f"SELECT title FROM recommendations WHERE book_id='{book}';")
        active = sql("engagement", f"SELECT active FROM book_snapshot WHERE book_id='{book}';")
        if pending == "0" and all(r == str(revision) for r in revisions.values()) and all(q == "0" for q in quarantine.values()) and model == ("" if deleted else title) and active == ("f" if deleted else "t"):
            break
        if time.monotonic()>deadline:
            raise ReconciliationTimeout(reconciliation_evidence(
                book, revision, pending, revisions, quarantine, model, active, title, deleted))
        time.sleep(1)
    offsets = {}
    for topic, suffix in (("book-events", ""), ("book-events.avro", "-book-avro")):
        for service in ("recommendation-service", "engagement-service"):
            group = service+suffix
            lines = kafka("kafka-consumer-groups", "--describe", "--group", group).splitlines()
            rows = [x.split() for x in lines if len(x.split())>=6 and x.split()[1]==topic]
            if not rows and (suffix == "" or revision > 0):
                raise RuntimeError("Missing consumer group positions")
            # Before the first Avro fact, Spring may not have committed an empty partition's offset 0.
            startup_empty = lambda r: bool(suffix) and revision == 0 and r[4] == "0" and r[3] in ("-", "0")
            for row in rows:
                if not startup_empty(row) and (not all(v.isdigit() for v in row[2:6]) or int(row[5]) != 0):
                    raise RuntimeError("Consumer lag is not zero at the cutover boundary")
            offsets[group] = [dict(partition=int(r[2]), current=int(r[3]) if r[3].isdigit() else None,
                                   end=int(r[4]), lag=0 if startup_empty(r) else int(r[5])) for r in rows]
    rows = json.loads(sql("catalog", f"SELECT coalesce(json_agg(t),'[]') FROM (SELECT event_id,aggregate_version,topic,wire_format,status FROM outbox_events WHERE aggregate_id='{book}' ORDER BY aggregate_version) t;"))
    return dict(revision=revision, checkpoints=revisions, offsets=offsets, outbox=rows)


def main():
    if os.getenv("GITHUB_ACTIONS") != "true" or cmd("kubectl", "config", "current-context") != "kind-vellumhub-m18-full":
        raise RuntimeError("This rehearsal is restricted to the disposable M18 CI Kind cluster")
    report = dict(sha=os.environ["SOURCE_SHA"], run_id=os.getenv("GITHUB_RUN_ID"), environment="ephemeral Kind", production=False, status="IN_PROGRESS", stages=[])
    OUT.parent.mkdir(parents=True, exist_ok=True)
    try:
        password = secrets.token_hex(24)
        secret = dict(apiVersion="v1",kind="Secret",metadata=dict(name="schema-registry-db",namespace="vellumhub"),
                      stringData=dict(username="registry_user",password=password,url="jdbc:postgresql://registry-postgres:5432/registrydb"))
        k("apply", "-f", "-", stdin=json.dumps(secret))
        k("apply", "-k", "deploy/kubernetes/schema-registry/overlays/local")
        for resource in ("statefulset/registry-postgres", "deployment/schema-registry"):
            k("rollout", "status", resource, "--timeout=240s", timeout=255)
        with forward("schema-registry",8080) as root:
            cmd("python3","tools/schema_registry/registry_gate.py","publish","--ephemeral","--url",root,"--state","target/metrics/book-kind-schema-ids.json")
        report["schema_ids"] = json.loads(Path("target/metrics/book-kind-schema-ids.json").read_text())
        status = json.loads(k("get","pod","kafka-0","-o","json"))["status"]["containerStatuses"][0]
        report["broker_before_cutover"] = {"restarts":status.get("restartCount",0),
            "last_termination":status.get("lastState",{}).get("terminated",{}).get("reason")}
        print("Book Avro Kafka preflight: "+json.dumps(report["broker_before_cutover"]))
        # Administrative JVMs have their own cgroup, independent of the broker.
        admin = {
            "apiVersion":"v1", "kind":"Pod",
            "metadata":{"name":"book-avro-kafka-admin", "namespace":"vellumhub"},
            "spec":{"restartPolicy":"Never", "containers":[{
                "name":"admin", "image":"apache/kafka:3.9.1",
                "command":["sh","-c","sleep 1800"],
                "env":[{"name":"KAFKA_HEAP_OPTS","value":"-Xms32m -Xmx128m"}],
                "resources":{"requests":{"cpu":"100m","memory":"128Mi"},
                             "limits":{"cpu":"500m","memory":"384Mi"}}
            }]}
        }
        k("apply","-f","-",stdin=json.dumps(admin))
        k("wait","--for=condition=Ready","pod/book-avro-kafka-admin","--timeout=120s",timeout=135)
        # Controller election is asynchronous. Only retry the read-only Kafka
        # preflight; never retry business mutations or silently pass a failed cutover.
        for attempt in range(3):
            try:
                description = kafka("kafka-topics", "--describe", "--topic", "book-events")
                break
            except RuntimeError:
                if attempt == 2:
                    raise
                time.sleep(10)
        match = re.search(r"PartitionCount:\s*(\d+)",description)
        if not match: raise RuntimeError("Cannot determine legacy topic partition count")
        kafka("kafka-topics","--create","--if-not-exists","--topic","book-events.avro","--partitions",match[1],"--replication-factor","1","--config","retention.ms=86400000")
        for service in ("recommendation-service","engagement-service"):
            stage(service,BOOK_AVRO_REGISTRY_URL="http://schema-registry:8080/apis/ccompat/v7",BOOK_AVRO_CONSUMER_ENABLED="true")
        stage("catalog-service",BOOK_AVRO_REGISTRY_URL="http://schema-registry:8080/apis/ccompat/v7",BOOK_EVENTS_WIRE_FORMAT="json")
        token = token_for_lab()
        name = "Kind Book Avro "+str(uuid.uuid4())
        sql("catalog","INSERT INTO genres(name) VALUES('kind-avro') ON CONFLICT DO NOTHING;")
        payload = dict(title=name,description="Disposable cutover fixture",releaseYear=2026,coverUrl="https://example.invalid/cover.png",author="Kind fixture",isbn=str(uuid.uuid4()),pageCount=100,publisher="CI",genres=["kind-avro"])
        with forward("catalog-service",8080) as root:
            request(root+"/books","POST",payload,token,201)
        book = sql("catalog",f"SELECT id FROM books WHERE title='{name}';")
        uuid.UUID(book)
        report["book_id"] = book
        freeze(True)
        report["stages"].append(drained(book,0,name))
        with forward("catalog-service",8080) as root:
            # A frozen DELETE proves all mutations are gated independent of a
            # request body; the full PUT also proves updates return 503.
            request(root+"/books/"+book,"DELETE",token=token,expected=503)
            request(root+"/books/"+book,"PUT",book_update_payload(payload,name+" frozen"),token,503)
        if sql("catalog",f"SELECT title FROM books WHERE id='{book}';") != name:
            raise RuntimeError("Frozen Book mutation committed despite the write gate")
        stage("catalog-service",BOOK_EVENTS_WIRE_FORMAT="avro")
        freeze(False)
        with forward("catalog-service",8080) as root:
            request(root+"/books/"+book,"PUT",book_update_payload(payload,name+" Avro"),token)
        freeze(True)
        report["stages"].append(drained(book,1,name+" Avro"))
        assert report["stages"][-1]["outbox"][-1]["wire_format"] == "AVRO"
        stage("catalog-service",BOOK_EVENTS_WIRE_FORMAT="json")
        freeze(False)
        with forward("catalog-service",8080) as root:
            request(root+"/books/"+book,"DELETE",token=token,expected=204)
        freeze(True)
        report["stages"].append(drained(book,2,"",deleted=True))
        assert [r["wire_format"] for r in report["stages"][-1]["outbox"]] == ["JSON","AVRO","JSON"]
        assert len({r["event_id"] for r in report["stages"][-1]["outbox"]}) == 3
        freeze(False)
        report["status"] = "PASSED"
    except Exception as e:
        report["status"] = "FAILED"; report["failure_type"] = type(e).__name__
        if isinstance(e, ReconciliationTimeout):
            report["failure_evidence"] = e.evidence
        raise
    finally:
        OUT.write_text(json.dumps(report,indent=2)+"\n")
    print("Book Avro Kind HTTP create/update/delete, freeze/drain/cutover/rollback: PASSED")


if __name__ == "__main__": main()
