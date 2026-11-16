# Code audit — 2026-11-16

Base: `main` at `512c497` (verified against `origin/main` before editing).

## Review scope

Manual review covered backup container detection, decoding, crypto envelope validation, merge and retention; CSV encoding; intake persistence and Health Connect queue transactions; container ordering; profile and goal mutations; hydration-day transitions; reminder decisions; settings serialization and validation; volume input; navigation and manifest exposure; backup inclusion rules; network client configuration; and CI permissions. Automated checks complement this review; this is not a claim that every possible defect has been eliminated.

## Confirmed findings and fixes

| Priority | Finding and trigger | Effect | Fix and regression evidence |
| --- | --- | --- | --- |
| P2 | `VolumeInputSheet.parseVolumeField` accepted finite numbers whose converted millilitres exceed `Int`, including a long string of digits typed into the litres field. | `intValueExact()` throws during composition/input handling, crashing the logging sheet. Multiplication of a finite value can also produce infinity. | Catch conversion overflow and invalid numeric representation and return an invalid field value. UI regression types a huge amount and checks that confirmation is disabled; parser checks cover integer overflow and multiplication overflow. |
| P2 | `BackupCodec.gunzip` used unbounded `readBytes()` on supplied gzip data, including decrypted containers. | A small compressed document could exhaust the app heap before schema validation. Plain JSON also had no decoder size limit. The restore UI is currently a planned feature, but the codec already accepts these inputs. | Reject containers over 32 MiB before decryption/parsing, and stop expansion before writing more than 32 MiB. Tests cover oversized plain JSON, gzip expansion, and encrypted gzip expansion. Invalid input remains a typed `BackupException(CORRUPT)`. |
| P2 | CSV notes and external application labels could start with spreadsheet formula characters; RFC 4180 quoting did not neutralize them. | Opening the exported CSV in a spreadsheet could interpret attacker-controlled text as a formula. Exposure depends on the consumer and on export integration, which is currently a planned feature. | Prefix dangerous text with an apostrophe and quote it, including formula prefixes following whitespace and leading tab/CR/LF. Only untrusted text columns are transformed; numeric columns and signed UTC offsets retain their existing representation. Tests cover notes and labels, commas, control characters, and ordinary text. |
| P2 | `OfflineContainerRepository.reorder` processed repeated IDs as separate positions. | Repeating an ID, for example `[bottle, bottle, glass]`, wrote the same row twice and left gaps in the stored order. Unknown IDs also caused repeated linear searches. | Deduplicate requested IDs in insertion order, resolve against a map, ignore unknown IDs, and append unlisted containers once. Regression asserts exact IDs and contiguous positions. Lookup work falls from quadratic to linear in live/requested IDs. |
| P3 | `rollingMean` rebuilt and scanned each window, and did not validate nonpositive window sizes. | Work grew with both history length and window length, with temporary filtered lists when excluding no-log days. Zero/negative windows returned misleading results or failed inconsistently. | Maintain a running sum and included-day count in O(n) time with constant auxiliary state beyond the result. Reject nonpositive windows consistently. Existing golden datasets verify both no-log modes; new tests cover invalid windows, window one, empty input, and a window longer than the series. |

All new failure regressions were run against the original implementations and failed before the corresponding fixes. Existing CSV and backup round trips, statistics datasets, and repository tests remain part of validation.

## Compatibility and limits

- The decoder now accepts at most 32 MiB for both the supplied container and expanded JSON. This is a byte limit, not a promise that parsing uses only 32 MiB of memory. Very large legitimate backups need a future streaming format/parser or a deliberate revision of the bound; encoders currently have no matching export-size policy.
- Spreadsheet-safe CSV intentionally adds a leading apostrophe to dangerous notes and application labels. A generic CSV parser sees that apostrophe as data. Original text remains unchanged in stored entries and JSON backups.
- No schema, dependency, Android permission, or screenshot baseline changes are needed.
- Drive, Health Connect, weather, widget, reminder scheduling, backup repository, and several settings/insights screens still contain planned stubs or no-op adapters. This audit does not implement those roadmap features or certify their eventual integrations.
- No device/emulator instrumentation, OEM background-execution testing, live Drive/Health Connect exercises, or external dependency vulnerability database scan was performed. Dependency versions were not changed.

## Validation

The full repository gate passed on the fix branch (`BUILD SUCCESSFUL in 3m 36s`, 1,554 actionable tasks):

```bash
./gradlew ktlintFormat ktlintCheck detekt checkModuleGraph lint \
  testDebugUnitTest :core:common:test :core:model:test :core:domain:test :core:backup:test \
  verifyRoborazziDebug assembleDebug --continue
```

The generated JUnit reports contain 478 tests across 123 report files, with zero failures and zero errors. The module graph check validated 193 declared dependencies. Screenshot verification passed without changing reference images. `git diff --check` passed. `origin/main` was fetched again before publication and remained at `512c497`.

The SDK path was set in ignored `local.properties`; it is not part of the change.
