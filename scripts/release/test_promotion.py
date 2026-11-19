"""Validation tests for selecting a debug release for production promotion."""

import importlib.util
import json
import os
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch


spec = importlib.util.spec_from_file_location("resolve_promotion", Path(__file__).with_name("resolve-promotion.py"))
resolver = importlib.util.module_from_spec(spec)
spec.loader.exec_module(resolver)


class PromotionValidationTest(unittest.TestCase):
    def setUp(self):
        self.work = tempfile.TemporaryDirectory()
        self.addCleanup(self.work.cleanup)
        self.output = Path(self.work.name) / "outputs"
        self.env = {
            "GITHUB_REPOSITORY": "owner/repo", "SOURCE_DEBUG_TAG": "debug-5-791409c",
            "RELEASE_VERSION": "1.2.3", "RELEASE_VERSION_CODE": "2", "GITHUB_OUTPUT": str(self.output),
        }
        self.release = {
            "draft": False, "prerelease": True,
            "assets": [{"name": f"dhmo-{module}-debug-5-791409c.apk"} for module in ["app", "wear"]],
        }
        self.sha = "791409c" + "0" * 33
        self.ancestry = "behind"
        self.tags = "debug-5-791409c\n"
        self.releases = []

    def api(self, endpoint):
        if "/releases/tags/" in endpoint:
            return self.release
        if "/commits/" in endpoint:
            return {"sha": self.sha}
        if "/compare/" in endpoint:
            return {"status": self.ancestry}
        raise AssertionError(endpoint)

    def command(self, args, **kwargs):
        if "/tags?" in args[3]:
            return self.tags
        if "/releases?" in args[3]:
            return "\n".join(json.dumps(item) for item in self.releases)
        raise AssertionError(args)

    def resolve(self):
        with patch.dict(os.environ, self.env), patch.object(resolver, "api", side_effect=self.api), \
                patch.object(resolver.subprocess, "check_output", side_effect=self.command):
            resolver.main()

    def test_valid_release_resolves_exact_commit_and_version(self):
        self.resolve()
        self.assertIn(f"sha={self.sha}\n", self.output.read_text())
        self.assertIn("tag=v1.2.3\nversion=1.2.3\nversion_code=2\n", self.output.read_text())

    def test_invalid_or_shell_injected_inputs_are_rejected(self):
        for key, value in [("SOURCE_DEBUG_TAG", "main; touch /tmp/bad"), ("RELEASE_VERSION", "../1.2.3"),
                           ("RELEASE_VERSION_CODE", "0"), ("RELEASE_VERSION_CODE", "2100000001")]:
            with self.subTest(key=key, value=value):
                before = self.env[key]
                self.env[key] = value
                with self.assertRaises(ValueError):
                    self.resolve()
                self.env[key] = before

    def test_missing_watch_build_is_rejected(self):
        self.release["assets"].pop()
        with self.assertRaisesRegex(ValueError, "wear"):
            self.resolve()

    def test_unpublished_and_non_debug_releases_are_rejected(self):
        for field, value in [("draft", True), ("prerelease", False)]:
            with self.subTest(field=field):
                before = self.release[field]
                self.release[field] = value
                with self.assertRaises(ValueError):
                    self.resolve()
                self.release[field] = before

    def test_retargeted_tag_and_non_main_source_are_rejected(self):
        self.sha = "0" * 40
        with self.assertRaisesRegex(ValueError, "source commit"):
            self.resolve()
        self.sha = "791409c" + "0" * 33
        self.ancestry = "diverged"
        with self.assertRaisesRegex(ValueError, "main"):
            self.resolve()

    def test_existing_version_is_never_overwritten(self):
        self.tags += "v1.2.3\n"
        with self.assertRaisesRegex(ValueError, "already exists"):
            self.resolve()

    def test_android_version_code_must_increase(self):
        self.releases = [{"tag_name": "v1.2.2", "draft": False, "prerelease": False,
                          "body": "Production build\nAndroid version code: 2\n"}]
        with self.assertRaisesRegex(ValueError, "version code must exceed"):
            self.resolve()


if __name__ == "__main__":
    unittest.main()
