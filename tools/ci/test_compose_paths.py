"""Enforce the default and optional Compose entry points without Docker dependencies."""

from pathlib import Path
import subprocess
import unittest


ROOT = Path(__file__).resolve().parents[2]
OVERLAYS = (
    "infra/compose/kafka-secure.yaml",
    "infra/compose/oidc-local.yaml",
    "infra/compose/google-federation.yaml",
)
STANDALONE = (
    "infra/compose/schema-registry.yaml",
    "infra/compose/tests/auth-poc.yaml",
)


class ComposePathContractTest(unittest.TestCase):
    def test_default_and_optional_paths_are_explicit(self):
        self.assertTrue((ROOT / "compose.yaml").is_file())
        self.assertFalse((ROOT / ("docker" + "-compose.yml")).exists())
        for p in OVERLAYS + STANDALONE:
            self.assertTrue((ROOT / p).is_file(), p)
        self.assertIn("name: vellum-hub", (ROOT / "compose.yaml").read_text())
        registry = (ROOT / STANDALONE[0]).read_text()
        self.assertIn("name: vellumhub-schema-registry", registry)
        self.assertIn("name: vellumhub-schema-registry-net", registry)

    def test_ide_state_is_not_tracked(self):
        self.assertIn("/.idea/", (ROOT / ".gitignore").read_text())
        tracked = subprocess.run(
            ["git", "-C", str(ROOT), "ls-files", "--", ".idea"],
            capture_output=True, text=True, check=True
        )
        self.assertEqual("", tracked.stdout.strip(), "IntelliJ metadata must be untracked")

    def test_reference_paths_have_no_legacy_compose_filenames(self):
        legacy = ("docker" + "-compose.yml",) + tuple(
            "docker" + "-compose." + suffix + ".yml"
            for suffix in ("auth-poc", "kafka-secure", "oidc-lote2", "oidc-lote3", "schema-registry")
        )
        roots = (ROOT / ".github", ROOT / "docs", ROOT / "infra",
                 ROOT / "tools", ROOT / "services")
        for folder in roots:
            for f in folder.rglob("*"):
                if not f.is_file() or "target" in f.parts or f.name == Path(__file__).name:
                    continue
                if f.suffix not in (".yml", ".yaml", ".md", ".py", ".sh") and f.name != "Dockerfile":
                    continue
                data = f.read_text(encoding="utf-8")
                for name in legacy:
                    self.assertNotIn(name, data, str(f.relative_to(ROOT)))
        for path in (ROOT / "README.md", ROOT / ".dockerignore"):
            data = path.read_text(encoding="utf-8")
            for name in legacy:
                self.assertNotIn(name, data, str(path))

    def test_standalone_auth_uses_root_paths(self):
        poc = (ROOT / STANDALONE[1]).read_text()
        self.assertIn("context: .", poc)
        self.assertIn("dockerfile: infra/docker/java/Dockerfile", poc)
        self.assertIn("./.local/auth-poc:/run/keys:ro", poc)
        workflow = (ROOT / ".github/workflows/auth-poc.yml").read_text()
        cmd = "docker compose --project-directory . -f infra/compose/tests/auth-poc.yaml"
        self.assertIn(cmd, workflow)
        self.assertIn("--profile auth-poc up -d --build", workflow)
        registry_workflow = (ROOT / ".github/workflows/schema-registry-governance.yml").read_text()
        self.assertIn("docker compose --project-directory . -f infra/compose/schema-registry.yaml",
                      registry_workflow)
        for name in OVERLAYS:
            self.assertIn(name, (ROOT / "docs/COMPOSE_LAYOUT.md").read_text())


if __name__ == "__main__":
    unittest.main()
