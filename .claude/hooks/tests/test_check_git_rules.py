import json
import os
import pathlib
import shutil
import subprocess
import sys
import tempfile
import unittest

HOOKS = pathlib.Path(__file__).resolve().parents[1]
REPO = HOOKS.parents[1]


def run(*args, cwd, check=True):
    return subprocess.run(list(args), cwd=cwd, capture_output=True, text=True, check=check, stdin=subprocess.DEVNULL)


class GitRulesHookTests(unittest.TestCase):
    """임시 git 저장소에 훅을 복사해 PreToolUse 입력 그대로 실행한다."""

    def setUp(self):
        self.tmp = tempfile.mkdtemp(prefix="harness-test-")
        self.repo = os.path.join(self.tmp, "repo")
        os.makedirs(os.path.join(self.repo, "app"))
        shutil.copytree(HOOKS, os.path.join(self.repo, ".claude", "hooks"),
                        ignore=shutil.ignore_patterns("tests", "__pycache__"))
        shutil.copytree(REPO / ".githooks", os.path.join(self.repo, ".githooks"))
        run("git", "init", "-q", "-b", "develop", cwd=self.repo)
        run("git", "config", "user.name", "tester", cwd=self.repo)
        run("git", "config", "user.email", "tester@example.com", cwd=self.repo)
        self.write("app/Main.kt", "fun main() = Unit\n")
        shutil.copyfile(REPO / ".gitignore", os.path.join(self.repo, ".gitignore"))
        run("git", "-c", "core.excludesFile=/dev/null", "add", "-A", cwd=self.repo)
        run("git", "commit", "-q", "-m", "init", cwd=self.repo)
        run("git", "checkout", "-q", "-b", "feature/agent-harness", cwd=self.repo)

    def tearDown(self):
        shutil.rmtree(self.tmp, ignore_errors=True)

    def write(self, path, text, mode="w"):
        with open(os.path.join(self.repo, path), mode) as f:
            f.write(text)

    def hook(self, command, cwd=None):
        payload = json.dumps({"tool_input": {"command": command}, "cwd": cwd or self.repo})
        return subprocess.run(["bash", os.path.join(self.repo, ".claude", "hooks", "check-git-rules.sh")],
                              input=payload, capture_output=True, text=True)

    def direct(self, *args, stdin=None):
        return subprocess.run(["bash", os.path.join(self.repo, ".claude", "hooks", "check-git-rules.sh"), *args],
                              input=stdin, capture_output=True, text=True)

    def assertBlocked(self, result, text):
        self.assertEqual(2, result.returncode, result.stderr)
        self.assertIn(text, result.stderr)

    def test_non_git_command_passes(self):
        self.assertEqual(0, self.hook("ls -la").returncode)
        self.assertEqual(0, self.hook('echo "git commit -m x"').returncode)

    def test_valid_commit_passes(self):
        self.assertEqual(0, self.hook('git commit -m "하네스를 추가한다 #1"').returncode)

    def test_message_format(self):
        self.assertEqual(0, self.direct("--check-message", "하네스를 추가한다 #1").returncode)
        self.assertEqual(0, self.direct("--check-message", "하네스를 추가한다 #1 (#2)").returncode)
        self.assertBlocked(self.direct("--check-message", "하네스 추가"), "이슈 번호")
        self.assertBlocked(self.direct("--check-message", "#1 하네스를 추가한다"), "이슈 번호")
        self.assertBlocked(self.direct("--check-message", "Add harness #1"), "한국어")

    def test_protected_branch_commit_and_push_blocked(self):
        run("git", "checkout", "-q", "develop", cwd=self.repo)
        self.assertBlocked(self.hook('git commit -m "하네스를 추가한다 #1"'), "보호 브랜치")
        self.assertBlocked(self.hook("git push origin develop"), "보호 브랜치")
        self.assertBlocked(self.direct("--check-branch", "develop"), "보호 브랜치")
        self.assertBlocked(self.direct("--check-branch", "main"), "보호 브랜치")

    def test_push_to_protected_from_feature_blocked(self):
        self.assertBlocked(self.hook("git push origin HEAD:develop"), "직접 푸시")
        self.assertEqual(0, self.hook("git push -u origin feature/agent-harness").returncode)

    def test_branch_name(self):
        self.assertBlocked(self.direct("--check-branch", "feature/Agent_Harness"), "브랜치 이름")
        self.assertBlocked(self.direct("--check-branch", "fix/harness"), "브랜치 이름")
        self.assertEqual(0, self.direct("--check-branch", "feature/gradle-skeleton").returncode)

    def test_co_authored_by_blocked_for_any_author(self):
        for author in ("Claude <noreply@anthropic.com>", "Codex <codex@openai.com>", "Gemini <g@example.com>",
                       "홍길동 <hong@example.com>"):
            command = f'git commit -m "하네스를 추가한다 #1" -m "Co-Authored-By: {author}"'
            self.assertBlocked(self.hook(command), "Co-Authored-By")
        self.assertBlocked(self.hook('git commit -m "하네스를 추가한다 #1" --trailer "Co-authored-by: x <x@y>"'),
                           "Co-Authored-By")
        command = "git commit -F - <<'EOF'\n하네스를 추가한다 #1\n\nco-authored-by: Gemini <g@example.com>\nEOF"
        self.assertBlocked(self.hook(command), "Co-Authored-By")

    def test_session_link_blocked(self):
        command = 'git commit -m "하네스를 추가한다 #1" -m "Claude-Session: https://claude.ai/code/session_x"'
        self.assertBlocked(self.hook(command), "AI 표시")
        self.assertBlocked(self.hook('gh pr create --title "하네스를 추가한다 #1" --body "https://claude.ai/code/session_x"'),
                           "AI 표시")

    def test_check_body_for_ci(self):
        self.assertEqual(0, self.direct("--check-body", stdin="하네스를 추가한다 #1\n\n본문\n").returncode)
        self.assertBlocked(self.direct("--check-body", stdin="제목 #1\n\nCo-Authored-By: Gemini <g@example.com>\n"),
                           "Co-Authored-By")

    def test_hook_bypass_blocked(self):
        self.assertBlocked(self.hook('git commit --no-verify -m "하네스를 추가한다 #1"'), "건너뛰는")
        self.assertBlocked(self.hook('git commit -nm "하네스를 추가한다 #1"'), "건너뛰는")
        self.assertBlocked(self.hook("git config core.hooksPath /dev/null"), "core.hooksPath")
        self.assertEqual(0, self.hook("git config core.hooksPath .githooks").returncode)

    def test_ds_store_blocked(self):
        self.write(".DS_Store", "")
        run("git", "add", "-f", ".DS_Store", cwd=self.repo)
        self.assertBlocked(self.hook('git commit -m "하네스를 추가한다 #1"'), ".DS_Store")

    def test_unverified_code_blocked_until_verify_record_matches(self):
        sys.path.insert(0, os.path.join(self.repo, ".claude", "hooks"))
        import harness_state as hs
        self.write("app/Main.kt", "val a = 1\n", "a")
        run("git", "add", "-A", "app", cwd=self.repo)
        self.assertBlocked(self.hook('git commit -m "앱을 바꾼다 #1"'), "검증하지 않은")
        hs.save(self.repo, {"passed": True, "fingerprint": hs.fingerprint(self.repo), "code": hs.code_entries(self.repo)})
        self.assertEqual(0, self.hook('git commit -m "앱을 바꾼다 #1"').returncode)
        self.write("app/Main.kt", "val b = 2\n", "a")
        self.assertBlocked(self.hook('git commit -m "앱을 바꾼다 #1"'), "검증하지 않은")

    def test_harness_files_count_as_code(self):
        sys.path.insert(0, os.path.join(self.repo, ".claude", "hooks"))
        import harness_state as hs
        self.assertTrue(hs.is_code(".claude/hooks/check_git_rules.py"))
        self.assertTrue(hs.is_code(".claude/settings.json"))
        self.assertTrue(hs.is_code("feature/auth/sources/build.gradle.kts"))
        self.assertFalse(hs.is_code(".claude/docs/harness.md"))
        self.assertFalse(hs.is_code("README.md"))

    def test_pre_commit_gate_checks_real_index(self):
        sys.path.insert(0, os.path.join(self.repo, ".claude", "hooks"))
        import harness_state as hs
        hs.install_git_hooks(self.repo)
        self.write("app/Main.kt", "val a = 1\n", "a")
        hs.save(self.repo, {"passed": True, "fingerprint": hs.fingerprint(self.repo), "code": hs.code_entries(self.repo)})
        self.write("app/Main.kt", "val b = 2\n", "a")
        result = run("git", "commit", "-q", "-am", "앱을 바꾼다 #1", cwd=self.repo, check=False)
        self.assertNotEqual(0, result.returncode)
        self.assertIn("검증 게이트", result.stderr)

    def test_docs_only_change_needs_no_verify(self):
        self.write("README.md", "x")
        run("git", "add", "-A", cwd=self.repo)
        self.assertEqual(0, self.hook('git commit -m "문서를 고친다 #1"').returncode)

    def test_stop_guard_blocks_once(self):
        self.write("app/Main.kt", "val a = 1\n", "a")
        guard = os.path.join(self.repo, ".claude", "hooks", "stop_guard.py")
        result = subprocess.run(["python3", "-I", "-S", guard], input=json.dumps({"cwd": self.repo}),
                                capture_output=True, text=True)
        self.assertEqual("block", json.loads(result.stdout)["decision"])
        result = subprocess.run(["python3", "-I", "-S", guard],
                                input=json.dumps({"cwd": self.repo, "stop_hook_active": True}),
                                capture_output=True, text=True)
        self.assertEqual("", result.stdout)


class BoundaryTests(unittest.TestCase):
    """check-boundaries 규칙을 임시 저장소에서 확인한다."""

    def setUp(self):
        self.tmp = tempfile.mkdtemp(prefix="boundary-test-")
        run("git", "init", "-q", cwd=self.tmp)

    def tearDown(self):
        shutil.rmtree(self.tmp, ignore_errors=True)

    def check(self, path, text):
        full = os.path.join(self.tmp, path)
        os.makedirs(os.path.dirname(full), exist_ok=True)
        with open(full, "w") as f:
            f.write(text)
        return run(str(REPO / ".claude" / "scripts" / "check-boundaries"), cwd=self.tmp, check=False)

    def test_room_outside_core_database_blocked(self):
        result = self.check("feature/auth/sources/src/main/kotlin/A.kt", "import androidx.room.Dao\n")
        self.assertEqual(1, result.returncode)
        self.assertIn("core:database", result.stdout)
        self.assertEqual(0, self.check("feature/auth/sources/src/main/kotlin/A.kt", "val a = 1\n").returncode)
        self.assertEqual(0, self.check("core/database/src/main/kotlin/A.kt", "import androidx.room.Dao\n").returncode)

    def test_feature_depends_on_sources_blocked(self):
        result = self.check("feature/home/sources/build.gradle.kts",
                            "dependencies { implementation(projects.domain.festival.sources) }\n")
        self.assertEqual(1, result.returncode)
        result = self.check("feature/home/sources/build.gradle.kts",
                            "dependencies { implementation(projects.domain.festival.`interface`) }\n")
        self.assertEqual(0, result.returncode)


if __name__ == "__main__":
    unittest.main()
