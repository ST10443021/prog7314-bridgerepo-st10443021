# DevLedger release notes

## Unreleased — Part 3 finalisation

**Documentation updated:** 22 October 2026  
**Repository:** [Institutional GitHub repository](https://github.com/EMGPPT/prog7314-2026-poe-st10443021)  
**Status:** Feature changes implemented and local checks reported successful. Physical-device acceptance, institutional CI verification and final release packaging are pending.

These notes describe the functional improvements made since the Part 2 prototype. They are not an announcement that a signed production release has been published. The reviewed Android configuration still uses `versionName = "1.0.0-prototype"` and `versionCode = 1`.

### Biometric authentication and settings

- Reconciled the saved account-level biometric preference with the local registration state used by the protected entry flow.
- Required a successful native biometric verification before enabling the feature, with device capability/enrolment checks.
- Preserved the app PIN fallback and prevented cancelled or failed verification from being treated as a successful unlock.
- Improved settings-save handling so a failed or timed-out save does not present an unconfirmed preference as saved.

**User impact:** the security preference and unlock behaviour now follow the same account-specific state.

### Offline synchronisation and conflict recovery

- Aligned queued folder/tag upserts and transaction deletions with the batch API, including the missing tags retrieval path.
- Protected account scoping and retry/restart behaviour across supported offline operations.
- Guarded upload acknowledgement so a newer local edit made during an in-flight sync remains pending.
- Added explicit conflict handling and a non-destructive keep-both recovery option.
- Improved sync status to distinguish queued, running, retrying, failed and incomplete work. A failed required download no longer appears as a wholly successful sync.

**User impact:** supported offline edits have a compatible server path, and users can see and resolve incomplete or conflicting work.

### Budget notifications and device registration

- Applied budget alert evaluation to batch-synchronised transactions as well as direct transaction creation.
- Restricted calculations to the relevant UTC month and the user's base currency, with supported conversion handling.
- Evaluated crossings of the 80% and 100% budget bands against the committed before/after state; replaying unchanged sync data does not create a new crossing.
- Prepared alerts within the transaction and attempted delivery after commit, so a push failure does not roll back a successful data sync.
- Replaced unconditional registration success with explicit registration outcomes. Temporary network/server failures can retry with WorkManager connectivity constraints and exponential backoff; permanent rejection and signed-out/session-changed states are handled separately.
- Added bounded token retrieval and account-change checks without putting JWTs or FCM tokens into worker input data.

**User impact:** expenses created offline can trigger the same budget rule after sync, and temporary device-registration failures can recover. Actual FCM delivery still requires valid deployment configuration and phone verification.

### English, Afrikaans and isiXhosa

- Replaced hard-coded visible English across screens, prompts, validation and relevant messages with translated resources.
- Added locale-independent `UiText`/resource handling so presentation resolves messages using the selected language.
- Completed Afrikaans and isiXhosa coverage for the added resources, including plurals, language names, accessibility labels, date controls and attachment fallback text.
- Localised relevant app notification and backend budget-alert text, and mapped biometric failure codes to translated app messages.
- Kept stored domain codes and user-entered content intact while translating recognised display labels.
- Updated repository unit-test fixtures to match the `UiText` signatures.

**Coverage snapshot:** the reviewed resource sets contain 610 named strings and 8 plural resources per language. Resource parity is verified structurally; fluent-speaker review, longer-text layouts and restart persistence remain part of phone acceptance.

### Production artwork

- Replaced the stock launcher background with the DevLedger navy background and regenerated density-specific legacy launcher/round assets from the existing store artwork.
- Corrected the manifest's round-icon reference and wired the adaptive and monochrome/themed resources consistently.
- Added a monochrome vector suitable for themed-icon rendering.
- Preserved full-colour branding in the biometric header by removing the unintended tint, and replaced the inaccurate Google sign-in artwork with the official multicolour asset.
- Inspected the notification icon and local/FCM references and retained the existing white notification artwork.

**User impact:** app entry points and sign-in branding are more consistent. Phone rendering and final store screenshots are still pending.

### Repository and documentation

- Transferred development history to the institution-provided repository, as confirmed by the team.
- Updated the README from the Part 2 prototype description to the Part 3 implementation and release-preparation status.
- Corrected build instructions for the nested `DevLedger/android` and `DevLedger/api` directories.
- Removed the old prototype video links and left an explicit placeholder for the unrecorded final demonstration.
- Replaced unsupported claims of fully passing CI/final verification with the supplied local results and named evidence still required.
- Added these release notes and placeholders for the final tag, signed APK, screenshots, acceptance results and AI-use write-up.

### Notable additions beyond the prototype

The main refinements are **conflict recovery that preserves both versions**, **sync status that exposes incomplete work**, **budget alerts on the offline sync path**, **registration retries tied to account state**, and **language-aware messages across the interface and notifications**. These extend the existing Snippet Vault and Financial Tracker rather than representing new, unconfirmed Part 1 feature numbers.

### Validation snapshot

| Check | Latest supplied result |
|---|---|
| Android | `:app:assembleDebug :app:testDebugUnitTest :app:lintDebug` completed with `BUILD SUCCESSFUL`. No unit-test count was included in the supplied summary. |
| API | 68 tests passed; 0 failed; 0 skipped on .NET 10. The build reported three warnings. |
| Whitespace | `git diff --check` reported no whitespace errors; Windows line-ending conversion warnings were observed. |
| Translation resources | Matching counts of 610 strings and 8 plurals in English, Afrikaans and isiXhosa. |
| Physical-device / institutional CI | Pending final evidence. |

The Android lint configuration does not fail the build on lint errors, so the successful task is not a claim of zero findings. The three reported API warnings concern deprecated Google credential loading, deprecated Firebase token-property use and a nullable folder-dictionary key. No fresh application build or device test was performed as part of this documentation-only update.

### Known limitations carried forward

- The reviewed refresh-token endpoint remains a stub; rejected renewal requires sign-in again.
- Attachments remain Base64-backed in PostgreSQL. Dedicated blob storage and NoSQL objectives need implementation/assessment clarification.
- The reviewed Android currency converter uses approximate, manually maintained rates.
- FCM delivery depends on server credentials, device registration, connectivity and permission; no durable delivery guarantee is asserted.
- User content and previously delivered notifications are not retroactively translated.

### Required before the final release

- [ ] Complete Hamza's physical-device acceptance checks, including biometric/PIN, offline recovery, push delivery, language persistence/layout and icon rendering.
- [ ] Verify institutional GitHub Actions placement, paths, toolchains and every-push triggers; retain passing Android/API runs and artifacts.
- [ ] Confirm the Part 1 mapping for User Defined 4 and User Defined 5, and resolve the blob/NoSQL assessment question.
- [ ] Review remaining lint/build findings and capture results for the exact release revision.
- [ ] Set final version values, produce a signed production APK and verify installation/regression on the phone.
- [ ] Check the migrated repository's deployment linkage and deployed API/database behaviour.
- [ ] Add actual promotional screenshots and publication-preparation evidence.
- [ ] Complete the cited AI-use write-up, maximum 500 words.
- [ ] Record the narrated physical-phone demonstration and add its accessible URL to the README.
- [ ] Tag the final tested deployment commit and complete the metadata below.

### Final release metadata

| Field | Value |
|---|---|
| Release version / Android version code | Pending. |
| Release date | Pending. |
| Tested commit SHA | Pending. |
| Final POE tag | Pending. |
| GitHub release URL | Pending. |
| Signed APK location / SHA-256 | Pending. |
| Passing Android / API CI runs | Pending. |
| Phone / Android version / test evidence | Pending. |
| Screenshots / store preparation evidence | Pending. |
| Final demonstration URL | Pending — video not yet recorded. |
| AI-use write-up location | Pending. |

Complete these fields only when the corresponding artifact or evidence exists. See the [README](README.md) for setup, architecture and the submission evidence checklist.
