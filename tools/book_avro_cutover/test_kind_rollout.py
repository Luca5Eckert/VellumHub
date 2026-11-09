#!/usr/bin/env python3
"""Regression: scarce-CPU Kind rollouts must not require a surge replica."""
import importlib.util
import json
import ast
import io
import re
import urllib.error
from pathlib import Path
import unittest
from unittest.mock import call, patch


SOURCE = Path(__file__).with_name("kind_rehearsal.py")
spec = importlib.util.spec_from_file_location("book_avro_kind_rehearsal", SOURCE)
rehearsal = importlib.util.module_from_spec(spec)
spec.loader.exec_module(rehearsal)


class KindRolloutTest(unittest.TestCase):
    def test_all_book_services_use_no_surge_only_during_rehearsal(self):
        for service in ("recommendation-service", "engagement-service", "catalog-service"):
            with self.subTest(service=service), patch.object(rehearsal, "k") as kubectl:
                rehearsal.stage(service, BOOK_AVRO_CONSUMER_ENABLED="true")
                self.assertEqual(kubectl.call_count, 3)
                patch_args = kubectl.call_args_list[0].args
                self.assertEqual(patch_args[:5], ("patch", "deployment", service, "--type=merge", "-p"))
                self.assertEqual(
                    json.loads(patch_args[5]),
                    {"spec": {"strategy": {"type": "RollingUpdate",
                                           "rollingUpdate": {"maxSurge": 0, "maxUnavailable": 1}}}},
                )
                self.assertEqual(
                    kubectl.call_args_list[1],
                    call("set", "env", f"deployment/{service}", "BOOK_AVRO_CONSUMER_ENABLED=true"),
                )
                self.assertEqual(
                    kubectl.call_args_list[2],
                    call("rollout", "status", f"deployment/{service}", "--timeout=240s", timeout=255),
                )

    def test_other_deployments_are_not_reconfigured(self):
        with patch.object(rehearsal, "k") as kubectl:
            with self.assertRaises(ValueError):
                rehearsal.stage("auth-service", BOOK_AVRO_CONSUMER_ENABLED="true")
            kubectl.assert_not_called()



class BookFixtureContractTest(unittest.TestCase):
    @staticmethod
    def sample():
        return dict(
            title="Kind Book Avro unique",
            description="Disposable cutover fixture",
            releaseYear=2026,
            coverUrl="https://example.invalid/cover.png",
            author="Kind fixture",
            isbn="test-uuid",
            pageCount=100,
            publisher="CI",
            genres=["kind-avro"],
        )

    def test_complete_put_matches_catalog_dto_and_does_not_modify_post_fixture(self):
        dto = Path("services/catalog-service/src/main/java/com/vellumhub/"
                   "catalog_service/module/book/presentation/dto/UpdateBookRequest.java").read_text()
        body = dto.split("public record UpdateBookRequest(", 1)[1].split(") {", 1)[0]
        declared = set(re.findall(r"\b(?:String|int|Set<String>)\s+(\w+)\b", body))
        self.assertEqual(declared, set(rehearsal.BOOK_FIELDS))
        created = self.sample()
        for title in ("Kind Book Avro frozen", "Kind Book Avro Avro"):
            with self.subTest(title=title):
                updated = rehearsal.book_update_payload(created, title)
                self.assertEqual(set(updated), declared)
                self.assertEqual(updated["title"], title)
                self.assertEqual(updated["releaseYear"], 2026)
                self.assertEqual(updated["pageCount"], 100)
                self.assertEqual(updated["genres"], ["kind-avro"])
                self.assertEqual(created["title"], "Kind Book Avro unique")

    def test_partial_put_is_rejected_and_both_live_puts_use_complete_fixture(self):
        with self.assertRaisesRegex(RuntimeError, "incomplete"):
            rehearsal.book_update_payload({"title": "partial"}, "updated")
        code = ast.parse(SOURCE.read_text())
        calls = [node for node in ast.walk(code)
                 if isinstance(node, ast.Call) and isinstance(node.func, ast.Name)
                 and node.func.id == "book_update_payload"]
        self.assertEqual(len(calls), 2)
        self.assertIn('request(root+"/books/"+book,"DELETE",token=token,expected=503)', SOURCE.read_text())

    def test_frozen_503_requires_retry_after(self):
        body = json.dumps({"status": 503, "error": "Service Unavailable"}).encode()
        error = urllib.error.HTTPError(
            "http://127.0.0.1/books/x", 503, "unavailable",
            {"Retry-After": "5"}, io.BytesIO(body),
        )
        with patch.object(rehearsal.urllib.request, "urlopen", side_effect=error):
            response = rehearsal.request(
                "http://127.0.0.1/books/x", "PUT",
                rehearsal.book_update_payload(self.sample(), "new"),
                token="ephemeral-test-token", expected=503,
            )
        self.assertEqual(response["status"], 503)
        missing_header = urllib.error.HTTPError(
            "http://127.0.0.1/books/x", 503, "unavailable", {}, io.BytesIO(body),
        )
        with patch.object(rehearsal.urllib.request, "urlopen", side_effect=missing_header):
            with self.assertRaisesRegex(RuntimeError, "Retry-After"):
                rehearsal.request("http://127.0.0.1/books/x", "PUT", self.sample(), expected=503)

    def test_unexpected_400_diagnostics_only_expose_error_category(self):
        body = b'{"error":"Malformed Request","details":["sensitive diagnostic"]}'
        error = urllib.error.HTTPError(
            "http://127.0.0.1/books/x", 400, "invalid", {}, io.BytesIO(body),
        )
        with patch.object(rehearsal.urllib.request, "urlopen", side_effect=error):
            with self.assertRaisesRegex(RuntimeError, "API error category=Malformed Request") as ctx:
                rehearsal.request("http://127.0.0.1/books/x", "PUT", self.sample(), expected=503)
        self.assertNotIn("sensitive diagnostic", str(ctx.exception))


if __name__ == "__main__":
    unittest.main()
