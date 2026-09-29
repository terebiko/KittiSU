# Manager i18n and Weblate design

## Goal and scope

Create `i18n` from current `dev` and make manager Android UI text editable through Android string resources and, once provisioned, Weblate. Volunteers should be able to translate and correct wording without editing Kotlin UI logic. Preserve existing language coverage and behavior. Only user-visible text in `manager/app` is in scope; shell commands, log output, URLs, protocol values, CLI/daemon messages, script contents, identifiers, and developer-only test data remain unchanged.

## Current state

`manager/app/src/main/res/values/strings.xml` is the English source; 43 localized `values-*` directories contain existing translations. `crowdin.yml`, `CONTRIBUTING.md`, and `docs/README.md` point to Crowdin. No Weblate project or server has been provisioned. Work begins from `dev` at the Strix fixes; untracked `strix_runs/` and `scripts/__pycache__/` are not part of this work.

## Resource migration

Keep Android XML resources as the single source format. Inventory user-visible literals in Kotlin UI code and UI-facing XML, then migrate by independent screen/feature groups. Reuse an existing resource when meaning and grammatical context match; otherwise add a descriptive stable key in `values/strings.xml`. Use `stringResource` in Compose and `context.getString` or `resources.getString` outside Compose. Preserve format arguments, HTML/escaping, accessibility labels, and plural semantics (`plurals` for quantities requiring language-dependent forms). Do not replace technical constants or shell/diagnostic text based on superficial string matching. New keys without translations fall back to English; do not generate fake translations.

Existing `values-*` files remain intact and are imported into the future Weblate component. English stays the source. Simplified Chinese remains under maintainer review. Validate resource keys, XML syntax, formatting placeholders, and Android resource compilation where build tools are available.

## Weblate handoff

Prepare repository-side configuration for one Android XML Weblate component: source `manager/app/src/main/res/values/strings.xml`, translated files `manager/app/src/main/res/values-*/strings.xml`, branch `i18n`. Document the exact source/translation mask, language-code caveats, contribution/review flow, and how to configure Weblate Git integration after server/project details are provided. Do not claim a live integration or create placeholder URLs. Retire Crowdin-specific config and guidance only when repository-side replacement guidance is ready; do not run Crowdin and Weblate as concurrent writers to the same files. Weblate changes should arrive as reviewable PRs against `i18n`, not automatic direct pushes, until maintainers approve the integration policy.

## Execution boundaries

Partition work by disjoint Kotlin file groups, with one owner for shared `values/strings.xml` and documentation to avoid concurrent XML edits. Subagents return proposed keys and changes; integration verifies collisions and placeholders. Preserve unrelated work and avoid formatting-only churn. No translations are removed or overwritten. No GitHub-side Weblate integration, secrets, server provisioning, or push to `i18n` until those actions are separately reviewed.

## Acceptance checks

1. `i18n` branches from current `dev`; only scoped changes are committed.
2. Newly migrated manager UI text resolves via Android resources, with no altered commands, log formats, or protocol strings.
3. All existing translated XML files remain, parse, and retain existing entries; untranslated new keys use English fallback.
4. Repository documentation gives volunteers a Weblate migration path without asserting a live project exists.
5. Resource compilation and relevant tests run where available; unavailable toolchain or remaining hardcoded UI strings are reported explicitly, not called complete.
