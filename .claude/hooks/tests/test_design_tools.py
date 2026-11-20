import os
import pathlib
import shutil
import subprocess
import sys
import tempfile
import unittest

REPO = pathlib.Path(__file__).resolve().parents[3]
CONVERTER = REPO / "gradle" / "svg-to-vector.py"
SYNC = REPO / "gradle" / "sync-design-tokens"
TOKENS = ("FestiCoreLayout.kt", "FestiCoreSemanticColor.kt", "FestiCoreTypography.kt")


def run(*args, cwd=None):
    return subprocess.run(list(args), cwd=cwd, capture_output=True, text=True, stdin=subprocess.DEVNULL)


class SvgToVectorTests(unittest.TestCase):
    """gradle/svg-to-vector.py: 의미를 옮길 수 있는 SVG만 바꾸고 나머지는 실패한다."""

    def setUp(self):
        self.tmp = pathlib.Path(tempfile.mkdtemp(prefix="svg-test-"))
        (self.tmp / "src" / "Icon").mkdir(parents=True)
        (self.tmp / "out").mkdir()

    def tearDown(self):
        shutil.rmtree(self.tmp, ignore_errors=True)

    def convert(self, body):
        svg = ('<svg width="16" height="16" viewBox="0 0 16 16" fill="none" xmlns="http://www.w3.org/2000/svg">'
               f"{body}</svg>")
        (self.tmp / "src" / "Icon" / "x.svg").write_text(svg)
        return run(sys.executable, "-I", str(CONVERTER), str(self.tmp / "src"), str(self.tmp / "out"))

    def test_path_with_fill_converts(self):
        result = self.convert('<path d="M0 0H16V16Z" fill="#111113"/>')
        self.assertEqual(0, result.returncode, result.stderr)
        xml = (self.tmp / "out" / "ic_x.xml").read_text()
        self.assertIn('android:fillColor="#111113"', xml)
        self.assertIn('android:pathData="M0 0H16V16Z"', xml)

    def test_rgba_hex_becomes_android_argb(self):
        result = self.convert('<path d="M0 0H16V16Z" fill="#11223380"/>')
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn('android:fillColor="#80112233"', (self.tmp / "out" / "ic_x.xml").read_text())

    def test_full_canvas_clip_is_dropped(self):
        result = self.convert('<g clip-path="url(#c)"><path d="M0 0H16V16Z" fill="black"/></g>'
                              '<defs><clipPath id="c"><rect width="16" height="16" fill="white"/></clipPath></defs>')
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(1, (self.tmp / "out" / "ic_x.xml").read_text().count("<path"))

    def test_unsupported_semantics_fail(self):
        cases = {
            "transform": '<path d="M0 0H16Z" fill="black" transform="rotate(45)"/>',
            "opacity": '<g opacity="0.5"><path d="M0 0H16Z" fill="black"/></g>',
            "stroke": '<path d="M0 0H16Z" fill="black" stroke="black"/>',
            "partial clip": ('<g clip-path="url(#c)"><path d="M0 0H16Z" fill="black"/></g>'
                             '<defs><clipPath id="c"><rect width="8" height="16" fill="white"/></clipPath></defs>'),
            "circle": '<circle cx="8" cy="8" r="4" fill="black"/>',
        }
        for name, body in cases.items():
            with self.subTest(name):
                self.assertNotEqual(0, self.convert(body).returncode)


class SyncDesignTokensTests(unittest.TestCase):
    """gradle/sync-design-tokens: 기대 파일이 모두 있을 때만 교체하고 실패하면 기존 토큰을 남긴다."""

    def setUp(self):
        self.tmp = pathlib.Path(tempfile.mkdtemp(prefix="sync-test-"))
        self.android = self.tmp / "android"
        (self.android / "gradle").mkdir(parents=True)
        shutil.copy(SYNC, self.android / "gradle" / "sync-design-tokens")
        self.dest = self.android / "ui/designkit/src/main/kotlin/io/festicore/ui/designkit/tokens"
        self.dest.mkdir(parents=True)
        for name in TOKENS:
            (self.dest / name).write_text("old\n")
        (self.android / "ui/designkit/tokens.source-sha").write_text("old-sha\n")
        self.source = self.tmp / "design-system"
        (self.source / "generated/android/tokens").mkdir(parents=True)
        run("git", "init", "-q", cwd=self.source)
        run("git", "config", "user.email", "t@example.com", cwd=self.source)
        run("git", "config", "user.name", "t", cwd=self.source)

    def tearDown(self):
        shutil.rmtree(self.tmp, ignore_errors=True)

    def commit_source(self, names):
        for name in names:
            (self.source / "generated/android/tokens" / name).write_text(f"new {name}\n")
        run("git", "add", "-A", cwd=self.source)
        run("git", "commit", "-q", "-m", "tokens", cwd=self.source)
        return run("git", "rev-parse", "HEAD", cwd=self.source).stdout.strip()

    def sync(self):
        return run("sh", str(self.android / "gradle" / "sync-design-tokens"), str(self.source))

    def test_copies_tokens_and_records_sha(self):
        sha = self.commit_source(TOKENS)
        result = self.sync()
        self.assertEqual(0, result.returncode, result.stderr)
        for name in TOKENS:
            self.assertEqual(f"new {name}\n", (self.dest / name).read_text())
        self.assertEqual(f"{sha}\n", (self.android / "ui/designkit/tokens.source-sha").read_text())

    def test_missing_source_file_keeps_existing_tokens(self):
        self.commit_source(TOKENS[:2])
        self.assertNotEqual(0, self.sync().returncode)
        for name in TOKENS:
            self.assertEqual("old\n", (self.dest / name).read_text())
        self.assertEqual("old-sha\n", (self.android / "ui/designkit/tokens.source-sha").read_text())

    def test_uncommitted_source_is_rejected(self):
        self.commit_source(TOKENS)
        (self.source / "generated/android/tokens" / TOKENS[0]).write_text("dirty\n")
        self.assertNotEqual(0, self.sync().returncode)
        self.assertEqual("old\n", (self.dest / TOKENS[0]).read_text())


if __name__ == "__main__":
    unittest.main()
