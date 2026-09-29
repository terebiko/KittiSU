# Manager i18n and Weblate Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Put user-visible manager UI strings in Android resources on `i18n` and prepare a reviewable Weblate translation path without losing existing translations.

**Architecture:** English `values/strings.xml` remains source; localized `values-*` remain intact and fall back to English for new keys. UI file owners migrate independent feature groups and propose keys; one integrator owns shared resource XML. Weblate will read `i18n` and submit translation PRs, with no live connection until a project exists.

**Tech Stack:** Kotlin/Compose, Android XML resources, Gradle/JUnit, GitHub/Weblate Git integration.

**Spec:** `docs/superpowers/specs/2026-09-29-manager-i18n-weblate-design.md`

## Global Constraints

- Branch `i18n` already exists locally and remotely at `b14c0044`; work directly in current checkout as requested.
- Do not touch unrelated untracked `strix_runs/` or `scripts/__pycache__/`.
- UI Android only; never translate commands, log formats, URLs, protocol values, script bodies, identifiers, developer-only test strings.
- Preserve all 43 localized resource directories; no fabricated translations.
- Shared `manager/app/src/main/res/values/strings.xml` has one write owner; UI agents return key/value proposals, not XML edits.
- Do not connect Weblate, create credentials, or push code without separate verification and authorization. Crowdin on `main` remains unaffected until cutover.

## Review Focus

- Literal containing `%`, apostrophe, `&`, `<`, newline: parse new XML and compile resources; verify rendered formatting with focused tests (Tasks 2–8, 10).
- Numeric UI text whose grammar varies by language: assert quantity 1 and 2 use appropriate `plurals` items (Tasks 2–8).
- Same English word in different contexts: review reused keys against screen context and assert distinct meanings have distinct keys (Tasks 2–8).
- Strings that look UI-like but reach shell/log/protocol: diff check must show those literals unchanged (Tasks 2–8, 10).
- Weblate qualifiers `values-zh-rCN`, `values-pt-rBR`, and legacy `values-in`: list actual paths and document exact Weblate language mapping before enabling sync (Task 9).

---

### Task 1: Resource inventory and baseline

**Files:** `manager/app/src/main/res/values/strings.xml`; relevant UI files; `manager/app/src/test/...` only if a focused test fits existing patterns.

**Interfaces:** Produce an inventory of literal → file:line → proposed resource key/value → placeholders/plural classification. Integrator alone applies XML changes.

- [ ] Read UI literal inventory, existing resource names, and tests. Exclude technical strings explicitly.
- [ ] Run a read-only XML parse and key/placeholder comparison on all existing `values-*` resources; record baseline anomalies without rewriting translations.
- [ ] Verify baseline `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest` and Rust-independent checks where tools exist. Report missing Java/SDK rather than claiming green.
- [ ] Commit only scoped inventory/test artifacts if they provide runnable value; otherwise keep inventory in task output.

### Tasks 2–8: Feature-local UI migration

Each task owns Kotlin files only; no direct edit to shared XML. Before editing, agent returns exact key/English value/placeholder proposals; integrator reserves names and adds XML, then agent changes Kotlin and runs scoped checks. A reviewer checks each feature for unchanged behavior. Tests should exercise formatting or resource access where test runtime permits; do not add mock-heavy scaffolding.

- [ ] **Task 2 home/status:** `screen/main/HomePage.kt` and closely related home-only files. Test visible status strings and leave build/version identifiers unchanged.
- [ ] **Task 3 superuser:** `screen/main/SuperUserPage.kt` and its UI-only components. Test app-count plural and status labels.
- [ ] **Task 4 installed modules:** `screen/main/ModulePage.kt` and module-only UI components. Test snackbar formatting and action labels.
- [ ] **Task 5 online module repo:** `screen/moduleRepo/ModuleRepo.kt`, `OnlineModuleDetail.kt`, siblings under `screen/moduleRepo/`. Test accessibility labels and number formatting.
- [ ] **Task 6 presets:** `screen/modulePreset/` UI files. Test author/team wording and preset actions; leave preset JSON/API fields unchanged.
- [ ] **Task 7 flash/install/action:** `screen/Flash.kt`, `Install.kt`, `ExecuteModuleAction.kt`, `component/InstallConfirmationDialog.kt`. Test root-script review text and error displays; leave script bodies and root commands untouched.
- [ ] **Task 8 profiles/settings and remaining UI:** `screen/Template.kt`, `AppProfile.kt`, `TemplateEditor.kt`, `screen/main/SettingsPage.kt`, `component/profile/`, then a second inventory pass for other user-facing manager UI files. Test UID/GID labels and settings descriptions; document any unresolved literal by path.

For each task: identify literal and expected rendered output; reserve XML keys through integrator; add focused failing assertion when runnable; confirm RED; change Kotlin; confirm GREEN and compile resources before committing feature group. If Java/SDK remain unavailable, run read-only XML/reference checks and report unverified runtime behavior. Do not force parallel writes to `strings.xml`.

### Task 9: Weblate repository handoff

**Files:** `crowdin.yml`, `.github/workflows/crowdin.yml`, `CONTRIBUTING.md`, `docs/README.md`, optional repository Weblate metadata only if Weblate docs specify a supported format.

**Interfaces:** One Android component: repository `https://github.com/terebiko/KittiSU.git`, branch `i18n`, format Android String Resource, file mask `manager/app/src/main/res/values-*/strings.xml`, base `manager/app/src/main/res/values/strings.xml`, English source.

- [ ] Check official Weblate Android/GitHub PR docs and existing Crowdin trigger. Confirm Weblate PR integration target `i18n`; no direct push credentials are created here.
- [ ] Write contributor setup instructions with exact fields, language qualifier caveats, maintainer review for Simplified Chinese, and explicit 'not connected yet' status.
- [ ] Retire Crowdin-specific repository guidance/config on `i18n` only after replacement instructions exist. Confirm no scheduled Crowdin workflow remains on this branch; do not change `main` remotely.
- [ ] Validate docs paths and XML import set; commit scoped documentation/config change.

### Task 10: Integration and review

- [ ] Re-run inventory to identify remaining hardcoded user-visible UI text, classify misses by file, and fix scoped misses.
- [ ] Parse all resource XML, compare pre/post localized keys to prove no removal, and validate duplicate keys, format arguments, plurals, and escaping.
- [ ] Run `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest` from `manager/`, relevant lint/build tasks if toolchain available, and `git diff --check`; report exact failures and missing tools.
- [ ] Independent reviewer checks whole diff for accidental command/log changes and Weblate/Crowdin dual-writer risk.
- [ ] Confirm branch scope with `git status`, commit scoped changes, and request explicit authorization before pushing `i18n` updates.
