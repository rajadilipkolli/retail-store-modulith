#!/usr/bin/env python3
"""
Validate specification-driven development files in this repository.

Usage:
    python3 tools/verify-specs.py

No third-party Python packages are required.

Exit codes:
    0 - Validation passed.
    1 - Validation errors found.
"""

from __future__ import annotations

import re
import sys
from collections import defaultdict, deque
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

MASTER_SPEC = Path("spec/spring-modulith-migration.md")

PHASE_SPECS = [
    Path("spec/01-modulith-foundation.md"),
    Path("spec/02-catalog-module.md"),
    Path("spec/03-inventory-module.md"),
    Path("spec/04-payments-module.md"),
    Path("spec/05-orders-orchestration.md"),
    Path("spec/06-api-and-deployment.md"),
    Path("spec/07-migration-verification.md"),
]

REQUIRED_SKILLS = [
    Path("ai/skills/spec-driven-development/SKILL.md"),
    Path("ai/skills/spring-modulith/SKILL.md"),
    Path("ai/skills/test-driven-development/SKILL.md"),
    Path("ai/skills/migration-verification/SKILL.md"),
]
REQUIRED_AGENT_FILES = [
    Path("AGENTS.md"),
    Path("GEMINI.md"),
]

# Specifications use both Markdown and plain numbered section headings.
HEADING_PREFIX = r"^(?:#{1,6}[ \t]+(?:\d+\.[ \t]+)?|\d+\.[ \t]+)"
SECTION_HEADING = re.compile(HEADING_PREFIX, re.MULTILINE)
ACCEPTANCE_HEADING = re.compile(
    HEADING_PREFIX + r"acceptance[ \t]+criteria[ \t]*$",
    re.IGNORECASE | re.MULTILINE,
)

# The final verification phase names its exit requirements "Definition of Done".
EXIT_HEADING = re.compile(
    HEADING_PREFIX + r"(?:exit[ \t]+criteria|definition[ \t]+of[ \t]+done)[ \t]*$",
    re.IGNORECASE | re.MULTILINE,
)

SPEC_PATH_PATTERN = re.compile(
    r"(?<![A-Za-z0-9_./-])"
    r"(?:spec|ai/skills|architecture/decisions)/"
    r"[A-Za-z0-9_.-]+(?:/[A-Za-z0-9_.-]+)*"
    r"\.md\b"
)

SKILL_REFERENCE_PATTERN = re.compile(r"ai/skills/[A-Za-z0-9_.-]+/SKILL\.md")

DEPENDENCY_HEADING_PATTERN = re.compile(
    r"^[ \t]*(?:-[ \t]+)?(?:\*\*Depends on:\*\*|Depends on:)[ \t]*(.*?)[ \t]*$",
    re.IGNORECASE | re.MULTILINE,
)

FRONTMATTER_PATTERN = re.compile(
    r"\A---[ \t]*\r?\n(.*?)\r?\n---[ \t]*(?:\r?\n|\Z)",
    re.DOTALL,
)


class Validator:
    def __init__(self) -> None:
        self.errors: list[str] = []
        self.warnings: list[str] = []
        self.checks = 0

    def error(self, message: str) -> None:
        self.errors.append(message)

    def warning(self, message: str) -> None:
        self.warnings.append(message)

    def check(self, condition: bool, message: str) -> None:
        self.checks += 1
        if not condition:
            self.error(message)

    def read(self, relative_path: Path) -> str | None:
        path = ROOT / relative_path
        self.checks += 1

        if not path.is_file():
            self.error(f"Missing required file: {relative_path}")
            return None

        try:
            return path.read_text(encoding="utf-8")
        except (OSError, UnicodeError) as exc:
            self.error(f"Cannot read {relative_path}: {exc}")
            return None

    def validate_required_files(self) -> dict[Path, str]:
        files: dict[Path, str] = {}

        required = [
            MASTER_SPEC,
            *PHASE_SPECS,
            *REQUIRED_SKILLS,
            *REQUIRED_AGENT_FILES,
            Path("tools/verify-specs.py"),
            Path("architecture/decisions/README.md"),
            Path("architecture/decisions/0000-template.md"),
            Path("architecture/decisions/0001-module-boundaries.md"),
            Path("architecture/decisions/0002-module-communication.md"),
            Path("architecture/decisions/0003-order-processing-workflow.md"),
            Path("architecture/decisions/0004-event-reliability.md"),
            Path("architecture/decisions/0005-database-migration-strategy.md"),
            Path("spec/execution-status.md"),
        ]

        for relative_path in required:
            content = self.read(relative_path)
            if content is not None:
                files[relative_path] = content

        return files

    def validate_phase_sections(
        self,
        files: dict[Path, str],
    ) -> None:
        for phase in PHASE_SPECS:
            content = files.get(phase)
            if content is None:
                continue

            self.check(
                bool(ACCEPTANCE_HEADING.search(content)),
                f"{phase}: missing an Acceptance Criteria heading",
            )

            self.check(
                bool(EXIT_HEADING.search(content)),
                f"{phase}: missing an Exit Criteria heading",
            )

            # Requirements may be checklist items, bullets, numbered items,
            # or plain lines, as used in the migration specifications.
            match = ACCEPTANCE_HEADING.search(content)

            if match:
                section = content[match.end():]

                # In Markdown sections, numbered requirements are list items.
                next_heading = (
                    re.search(r"^#{1,6}[ \t]+", section, re.MULTILINE)
                    if match.group(0).startswith("#")
                    else SECTION_HEADING.search(section)
                )

                if next_heading:
                    section = section[:next_heading.start()]

                self.check(
                    bool(section.strip()),
                    f"{phase}: acceptance criteria section has no detectable items",
                )

    def validate_skill_frontmatter(
        self,
        files: dict[Path, str],
    ) -> None:
        for skill_path in REQUIRED_SKILLS:
            content = files.get(skill_path)
            if content is None:
                continue

            match = FRONTMATTER_PATTERN.match(content)

            self.check(
                match is not None,
                f"{skill_path}: missing valid YAML frontmatter delimiters",
            )

            if match is None:
                continue

            frontmatter = match.group(1)

            name_match = re.search(
                r"^name:\s*([A-Za-z0-9][A-Za-z0-9_-]*)\s*$",
                frontmatter,
                re.MULTILINE,
            )

            description_match = re.search(
                r"^description:\s*(.*?)\s*$",
                frontmatter,
                re.MULTILINE,
            )

            self.check(
                name_match is not None,
                f"{skill_path}: missing or invalid skill name",
            )

            self.check(
                description_match is not None
                and bool(description_match.group(1).strip())
                and not description_match.group(1).strip().startswith("#"),
                f"{skill_path}: missing or empty skill description",
            )

            if name_match:
                expected_name = skill_path.parent.name

                self.check(
                    name_match.group(1) == expected_name,
                    (
                        f"{skill_path}: skill name must match directory name "
                        f"'{expected_name}'"
                    ),
                )

    def validate_skill_references(
        self,
        files: dict[Path, str],
    ) -> None:
        agents_content = files.get(Path("AGENTS.md"))

        if agents_content is None:
            return

        references = set(SKILL_REFERENCE_PATTERN.findall(agents_content))

        for reference in sorted(references):
            relative_path = Path(reference)

            self.check(
                (ROOT / relative_path).is_file(),
                f"AGENTS.md references a missing skill: {reference}",
            )

        for skill_path in REQUIRED_SKILLS:
            self.check(
                skill_path.as_posix() in agents_content,
                f"AGENTS.md should reference {skill_path.as_posix()}",
            )

    def validate_markdown_references(
        self,
        files: dict[Path, str],
    ) -> None:
        # Check explicit repository-relative Markdown references in
        # relevant documentation. Ignore URLs, anchors, and examples
        # that do not match the recognized repository path prefixes.
        for relative_path, content in files.items():
            if relative_path.suffix.lower() != ".md":
                continue

            for match in SPEC_PATH_PATTERN.finditer(content):
                reference = match.group(0)
                target = ROOT / reference

                self.check(
                    target.is_file(),
                    f"{relative_path}: broken file reference '{reference}'",
                )

    def validate_dependencies(
        self,
        files: dict[Path, str],
    ) -> None:
        known_specs = {MASTER_SPEC, *PHASE_SPECS}
        graph: dict[Path, set[Path]] = defaultdict(set)

        for phase in PHASE_SPECS:
            content = files.get(phase)
            if content is None:
                continue

            match = DEPENDENCY_HEADING_PATTERN.search(content)

            if not match:
                self.warning(
                    f"{phase}: no '**Depends on:**' metadata found; "
                    "verify prerequisites manually"
                )
                continue

            dependency_text = match.group(1).strip()

            if not dependency_text or dependency_text.lower() in {
                "none",
                "n/a",
                "not applicable",
            }:
                continue

            references = sorted(
                set(re.findall(r"`([^`]+)`", dependency_text))
                | set(SPEC_PATH_PATTERN.findall(dependency_text))
            )

            if not references:
                self.warning(
                    f"{phase}: dependencies should use repository-relative file paths"
                )
                continue

            for reference in references:
                normalized = reference.strip()
                dependency = Path(normalized)

                if dependency not in known_specs:
                    self.error(
                        f"{phase}: unknown dependency '{normalized}'"
                    )
                    continue

                if dependency == phase:
                    self.error(f"{phase}: a phase cannot depend on itself")
                    continue

                self.check(
                    (ROOT / dependency).is_file(),
                    f"{phase}: missing dependency file '{dependency}'",
                )

                if dependency in PHASE_SPECS:
                    graph[phase].add(dependency)

        # Detect cycles in the phase dependency graph.
        indegree = {phase: 0 for phase in PHASE_SPECS}
        dependents: dict[Path, set[Path]] = defaultdict(set)

        for phase, dependencies in graph.items():
            for dependency in dependencies:
                dependents[dependency].add(phase)
                indegree[phase] += 1

        queue = deque(
            phase for phase, degree in indegree.items() if degree == 0
        )

        visited = 0

        while queue:
            phase = queue.popleft()
            visited += 1

            for dependent in dependents[phase]:
                indegree[dependent] -= 1

                if indegree[dependent] == 0:
                    queue.append(dependent)

        self.check(
            visited == len(PHASE_SPECS),
            "Phase dependency graph contains a cycle",
        )

    def validate_phase_order(
        self,
        files: dict[Path, str],
    ) -> None:
        # Ensure the master specification refers to every phase.
        master = files.get(MASTER_SPEC)

        if master is not None:
            for phase in PHASE_SPECS:
                self.check(
                    phase.name in master,
                    f"{MASTER_SPEC}: does not reference {phase.name}",
                )

        # Check dependencies do not point to later numbered phases.
        phase_index = {phase: index for index, phase in enumerate(PHASE_SPECS)}

        for phase in PHASE_SPECS:
            content = files.get(phase)
            if content is None:
                continue

            match = DEPENDENCY_HEADING_PATTERN.search(content)

            if not match:
                continue

            references = SPEC_PATH_PATTERN.findall(match.group(1))

            for reference in references:
                dependency = Path(reference)

                if (
                    dependency in phase_index
                    and phase_index[dependency] > phase_index[phase]
                ):
                    self.error(
                        f"{phase}: depends on later phase {dependency}"
                    )

    def run(self) -> int:
        print("Specification validation")
        print(f"Repository: {ROOT}")
        print()

        files = self.validate_required_files()

        self.validate_phase_sections(files)
        self.validate_skill_frontmatter(files)
        self.validate_skill_references(files)
        self.validate_markdown_references(files)
        self.validate_dependencies(files)
        self.validate_phase_order(files)

        print(f"Checks executed: {self.checks}")
        print(f"Errors: {len(self.errors)}")
        print(f"Warnings: {len(self.warnings)}")
        print()

        for error in self.errors:
            print(f"ERROR: {error}")

        for warning in self.warnings:
            print(f"WARNING: {warning}")

        if self.errors:
            print()
            print("RESULT: FAILED")
            return 1

        print("RESULT: PASSED")
        return 0


def main() -> int:
    validator = Validator()
    return validator.run()

if __name__ == "__main__":
    sys.exit(main())
