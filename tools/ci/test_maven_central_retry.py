"""Regression checks for bounded Maven Central retries in CI."""
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "tools/ci/retry_dependency_resolution.sh"
WORKFLOW = ROOT / ".github/workflows/verify-images.yml"
MIRROR = ROOT / "tools/ci/maven-central-fallback-settings.xml"

FAKE_MAVEN = """#!/usr/bin/env bash
printf '%s\\n' "$*" >> "$TRACE"
count=$(wc -l < "$TRACE")
if [[ "$MODE" == cached-miss ]]; then
  if (( count > 1 )) && [[ " $* " == *" -U "* ]]; then
    echo "BUILD SUCCESS"
    exit 0
  fi
  echo "[ERROR] Plugin org.apache.maven.plugins:maven-resources-plugin:3.4.0 or one of its dependencies could not be resolved:"
  exit 41
fi
if [[ "$MODE" == mirror-recovery ]]; then
  if [[ " $* " == *" -U "* ]] && [[ " $* " == *" -s "* ]]; then
    echo "BUILD SUCCESS"
    exit 0
  fi
  echo "[ERROR] Plugin org.apache.maven.plugins:maven-resources-plugin:3.4.0 or one of its dependencies could not be resolved:"
  exit 43
fi
if [[ "$MODE" == test-failure ]]; then
  echo "[ERROR] Tests run: 1, Failures: 1"
  exit 17
fi
echo "[ERROR] status code: 429, reason phrase: Too Many Requests"
exit 42
"""


class MavenCentralRetryTest(unittest.TestCase):
    def setUp(self):
        temp = tempfile.TemporaryDirectory()
        self.addCleanup(temp.cleanup)
        self.workdir = Path(temp.name)
        fake_mvn = self.workdir / "mvn"
        fake_mvn.write_text(FAKE_MAVEN, encoding="utf-8")
        fake_mvn.chmod(0o755)
        self.trace = self.workdir / "invocations.txt"

    def execute(self, mode):
        env = dict(os.environ)
        env.update(
            PATH=str(self.workdir) + os.pathsep + env.get("PATH", ""),
            MODE=mode,
            TRACE=str(self.trace),
            RETRY_DEPENDENCY_DELAY_SECONDS="0",
        )
        result = subprocess.run(
            ["bash", str(SCRIPT), "mvn", "-B", "verify"],
            env=env, capture_output=True, text=True, check=False,
            timeout=10,
        )
        calls = self.trace.read_text(encoding="utf-8").splitlines()
        return result, calls

    def test_cached_resolution_failure_is_retried_with_refresh(self):
        result, calls = self.execute("cached-miss")
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertEqual(len(calls), 2)
        self.assertNotIn("-U", calls[0])
        self.assertIn("-U", calls[1])

    def test_independent_mirror_recovers_persistent_central_failure(self):
        result, calls = self.execute("mirror-recovery")
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertEqual(len(calls), 2)
        self.assertNotIn("-s", calls[0])
        self.assertIn("-U", calls[1])
        self.assertIn("-s", calls[1])
        self.assertIn(str(MIRROR), calls[1])

    def test_fallback_settings_mirrors_only_central(self):
        root = ET.parse(MIRROR).getroot()
        ns = {"m": "http://maven.apache.org/SETTINGS/1.2.0"}
        mirrors = root.findall("m:mirrors/m:mirror", ns)
        self.assertEqual(len(mirrors), 1)
        self.assertEqual(mirrors[0].findtext("m:mirrorOf", namespaces=ns), "central")
        self.assertEqual(
            mirrors[0].findtext("m:url", namespaces=ns),
            "https://maven-central.storage-download.googleapis.com/maven2/",
        )

    def test_real_test_failure_is_not_retried(self):
        result, calls = self.execute("test-failure")
        self.assertEqual(result.returncode, 17)
        self.assertEqual(len(calls), 1)

    def test_exhausted_transient_failures_keep_nonzero_exit(self):
        result, calls = self.execute("always-429")
        self.assertEqual(result.returncode, 42)
        self.assertEqual(len(calls), 3)
        self.assertNotIn("-U", calls[0])
        self.assertTrue(all("-U" in cmd for cmd in calls[1:]))

    def test_distributed_warmup_and_timed_test_are_protected(self):
        workflow = WORKFLOW.read_text(encoding="utf-8")
        self.assertIn(
            "run: bash tools/ci/retry_dependency_resolution.sh mvn -B -ntp -Pdistributed-e2e",
            workflow,
        )
        self.assertIn(
            'bash tools/ci/retry_dependency_resolution.sh mvn -B -ntp -T 2 -Pdistributed-e2e',
            workflow,
        )


if __name__ == "__main__":
    unittest.main()
