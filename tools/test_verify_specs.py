"""Regression tests for the specification validator (standard library only)."""

from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest


ROOT = Path(__file__).resolve().parent.parent


class ValidatorTests(unittest.TestCase):
    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp_dir.cleanup)
        self.root = Path(self.temp_dir.name)
        for directory in ("spec", "ai", "architecture", "tools"):
            shutil.copytree(ROOT / directory, self.root / directory,
                            ignore=shutil.ignore_patterns("__pycache__"))
        for filename in ("AGENTS.md", "GEMINI.md"):
            shutil.copy2(ROOT / filename, self.root / filename)

    def write(self, path, content):
        (self.root / path).write_text(content, encoding="utf-8")

    def run_validator(self, expected_code, message):
        result = subprocess.run(
            [sys.executable, str(self.root / "tools/verify-specs.py")],
            cwd=self.root.parent,
            capture_output=True,
            text=True,
        )
        self.assertEqual(result.returncode, expected_code, result.stdout + result.stderr)
        self.assertIn(message, result.stdout)
        self.assertNotIn("Traceback", result.stderr)
        return result.stdout

    def test_repository_passes_from_another_working_directory(self):
        output = self.run_validator(0, "RESULT: PASSED")
        self.assertIn("Warnings: 0", output)

    def test_missing_required_file_fails(self):
        (self.root / "GEMINI.md").unlink()
        self.run_validator(1, "Missing required file: GEMINI.md")

    def test_missing_acceptance_heading_fails(self):
        self.write("spec/01-modulith-foundation.md", "11. Exit Criteria\nReady.\n")
        self.run_validator(1, "missing an Acceptance Criteria heading")

    def test_empty_acceptance_section_fails(self):
        self.write("spec/01-modulith-foundation.md",
                   "10. Acceptance Criteria\n\n11. Exit Criteria\nReady.\n")
        self.run_validator(1, "acceptance criteria section has no detectable items")

    def test_missing_exit_heading_fails(self):
        self.write("spec/01-modulith-foundation.md",
                   "10. Acceptance Criteria\nArchitecture verification passes.\n")
        self.run_validator(1, "missing an Exit Criteria heading")

    def test_markdown_sections_and_dependency_metadata_pass(self):
        self.write("spec/01-modulith-foundation.md",
                   "- **Depends on:** `spec/spring-modulith-migration.md`\n\n"
                   "## Acceptance Criteria\n- [ ] Architecture verification passes.\n\n"
                   "## Exit Criteria\nReady.\n")
        self.run_validator(0, "RESULT: PASSED")

    def test_numbered_markdown_requirements_pass(self):
        self.write("spec/01-modulith-foundation.md",
                   "Depends on: spec/spring-modulith-migration.md\n"
                   "## Acceptance Criteria\n1. Architecture verification passes.\n"
                   "## Exit Criteria\nReady.\n")
        self.run_validator(0, "RESULT: PASSED")

    def test_invalid_frontmatter_fails(self):
        self.write("ai/skills/spring-modulith/SKILL.md", "# Missing metadata\n")
        self.run_validator(1, "missing valid YAML frontmatter delimiters")

    def test_empty_skill_description_fails(self):
        self.write("ai/skills/spring-modulith/SKILL.md",
                   "---\nname: spring-modulith\ndescription: \n---\n")
        self.run_validator(1, "missing or empty skill description")

    def test_mismatched_skill_name_fails(self):
        self.write("ai/skills/spring-modulith/SKILL.md",
                   "---\nname: incorrect\ndescription: Engineering guidance\n---\n")
        self.run_validator(1, "skill name must match directory name 'spring-modulith'")

    def test_broken_markdown_reference_fails(self):
        with (self.root / "AGENTS.md").open("a", encoding="utf-8") as document:
            document.write("\nSee spec/missing.md\n")
        self.run_validator(1, "broken file reference 'spec/missing.md'")

    def test_unknown_dependency_fails(self):
        self.write("spec/02-catalog-module.md",
                   "Depends on: spec/missing.md\n10. Acceptance Criteria\nVerified.\n"
                   "11. Exit Criteria\nReady.\n")
        self.run_validator(1, "unknown dependency 'spec/missing.md'")

    def test_unknown_backticked_dependency_fails(self):
        self.write("spec/02-catalog-module.md", "- **Depends on:** `unknown.md`\n")
        self.run_validator(1, "unknown dependency 'unknown.md'")

    def test_self_dependency_fails(self):
        self.write("spec/02-catalog-module.md",
                   "Depends on: spec/02-catalog-module.md\n")
        self.run_validator(1, "a phase cannot depend on itself")

    def test_later_phase_dependency_fails(self):
        self.write("spec/01-modulith-foundation.md",
                   "Depends on: spec/07-migration-verification.md\n")
        self.run_validator(1, "depends on later phase spec/07-migration-verification.md")

    def test_dependency_cycle_fails(self):
        self.write("spec/01-modulith-foundation.md",
                   "Depends on: spec/02-catalog-module.md\n")
        self.run_validator(1, "Phase dependency graph contains a cycle")

    def test_missing_phase_in_master_fails(self):
        master = self.root / "spec/spring-modulith-migration.md"
        master.write_text(master.read_text(encoding="utf-8").replace(
            "07-migration-verification.md", "missing-phase.md"), encoding="utf-8")
        self.run_validator(1, "does not reference 07-migration-verification.md")


if __name__ == "__main__":
    unittest.main()
