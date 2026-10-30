# DevLedger release notes

## Version 1.0.0 (version code 100) — Final PoE

**Repository:** [Institutional GitHub repository](https://github.com/EMGPPT/prog7314-2026-poe-st10443021)
**Targets:** Android 8.0 (API 26) and up; compiled and targeted for Android 16 (API 36), as Google Play requires for new apps from 31 August 2026 (Google Play Console Help, n.d.).

These notes list every functional update made since the Part 2 prototype (`1.0.0-prototype`) and say which innovative features were added in this final version. Entries are grouped by the rubric features they support.

### What is new in the final version at a glance

| Rubric feature | Added in the final version |
|---|---|
| Biometric authentication | Prompt bound to a hardware-backed Android Keystore key that is invalidated when biometrics change; PIN moved to encrypted storage and kept on the device; escalating PIN lock-out |
| Offline mode with sync | Periodic sync now scheduled at app start and sign-in; automatic-sync switch respected everywhere; interval, Wi-Fi-only and charging-only choices no longer overwritten; conflict recovery and honest sync status |
| Real-time notifications | Data-only FCM messages so every alert reaches the in-app list (also when the app is in the background); tap opens the Notifications list after the security gate; "Send test notification" button and API endpoint; budget alerts at 80% and 100% |
| Multi-language | English, Afrikaans and isiXhosa for every screen, prompt and notification; remaining placeholders moved to resources |
| User Defined 4: Snippet and Config Vault | Folder and tag rename/delete from the three-dot menu; working search; correct All / Archived counts; overflow menu on snippet cards |
| User Defined 5: Developer Financial Tracker | Monthly budget field in Settings, budget progress bar, working month picker |
| User interface | Every inert control fixed or removed; consistent labels for sync switches |
| Play Store readiness | Target API 36, version 1.0.0, release signing, ProGuard rules, App Bundle with all languages, store listing, privacy policy and feature graphic |
| DevOps | CI builds the release variant, signed-release workflow, final-tag verification script |

### Biometric authentication and security

- Native BiometricPrompt (strong biometrics) is now bound to an Android Keystore key that needs a fresh biometric for each use. A successful scan must also unlock a cryptographic operation, and the key is invalidated if a fingerprint or face is added, so a stranger who enrols their own biometric on an unlocked phone cannot open DevLedger. In that case the app asks for the PIN and for biometrics to be switched on again (Google Developers, n.d.d; Google Developers, n.d.m).
- The PIN is stored as a salted hash in EncryptedSharedPreferences instead of plain SharedPreferences; existing values are migrated on first launch.
- The PIN is no longer uploaded. The API does not store or return PIN hash or salt, so a database leak cannot be used to brute-force 4-digit PINs. A PIN is therefore set once per device.
- The PIN pad locks after five wrong entries (30 seconds, then 1, 5, 15 and 30 minutes) (OWASP Foundation, n.d.a).
- Tapping a notification can never skip the security gate.
- Debug builds no longer log HTTP bodies (which contained passwords and tokens); only the request line and status are logged and `Authorization`/cookie headers are redacted (OWASP Foundation, n.d.b). An email address was also removed from a log line.
- Earlier Part 3 work retained: biometric preference and local registration reconciled; enabling biometrics needs a successful native verification; the PIN fallback is preserved.

### Offline synchronisation and conflict recovery

- **Fixed:** periodic sync was only scheduled as a side effect of saving a record, so a fresh install showed automatic sync as on but had no job. The application now observes the signed-in user and the saved preferences and schedules or cancels the job at start-up, after sign-in and whenever a preference changes.
- **Fixed:** saving a record re-enqueued periodic work with default values and ignored the automatic-sync switch, and saving Settings reset the interval and charging choices. All paths now read the saved preferences. With automatic sync off, changes stay queued (PENDING in Room) until the user taps Sync now.
- **Clarified:** "Automatic background sync" (whether DevLedger syncs by itself) and "Sync on Wi-Fi only" (over which network) are separate switches, now both visible in Settings and on the Sync status screen with explanatory text. The Wi-Fi-only switch on the Sync screen is also saved to the account so the two screens cannot disagree.
- Retained from Part 3: batch API alignment for folders, tags and deletions, upload acknowledgement that keeps a newer local edit pending, explicit conflicts with a keep-both option, and status that separates queued, running, retrying, failed and incomplete work.
- Sessions renew silently: the previously stubbed `/api/auth/refresh` endpoint now issues rotated, signed 30-day refresh tokens, which the app's existing authenticator uses after a 401.

### Real-time notifications (Firebase Cloud Messaging)

- **Fixed:** the API sent a notification payload, so Android displayed the alert itself while the app was in the background and `onMessageReceived` never ran; the alert was missing from the in-app list. The API now sends data-only, high-priority messages, so every alert is shown and stored (Firebase, n.d.a).
- Tapping a notification opens the Notifications list (after PIN/biometric unlock).
- New `POST /api/notifications/test` and a "Send test notification" button in the app send a real push to the signed-in user's devices, in their language, so the whole chain can be demonstrated on demand.
- Budget alerts at 80% and 100% (direct entries and offline sync) are now reachable because the budget can be set in the app.

### Multi-language (English, Afrikaans, isiXhosa)

- 640 strings and 8 plural resources in each language with matching keys; new strings were added for every feature in this release.
- Remaining hard-coded sample text (name, email, PIN and amount placeholders) moved to resources.
- Server-side test push and budget alerts are localised.

### User Defined 4: Snippet and Config Vault

- **Fixed:** the three-dot icon on folder and tag rows had no click handler, so rename and delete were unreachable. Both rows now open a menu with Rename and Delete; tags are renamed or removed on every snippet that uses them and the change syncs.
- The search icon filters folders and tags; the dead overflow button was removed.
- The "All snippets" and "Archived" counts show real totals.
- The overflow menu on snippet cards (Open, Archive) works and the vault's menu button opens Settings.

### User Defined 5: Developer Financial Tracker

- **Fixed:** the monthly budget was hard-coded to 0, so the budget bar was always empty and no budget alert could ever fire. A "Monthly budget" field in Settings is validated, saved to the API and cached for offline use; the Financial Tracker shows spent, remaining and percentage with a colour-coded bar.
- The month selector now opens the last twelve months (it previously did nothing); the Add Transaction history button opens the matching list.

### API hardening

- **Removed the hard-coded JWT signing-key fallback.** The API refuses to start without `Jwt__SigningKey` (32+ characters). **Set this variable on Render before deploying this version.**
- Swagger is available only in Development, or when `Swagger__Enabled=true` is set deliberately (for example while recording a demonstration).
- Sign-in, registration, SSO and refresh are rate limited to 10 requests per minute per IP; the open "allow any origin" CORS policy was removed; auth error messages no longer echo provider internals.
- Registration validates email, name and an 8-character minimum password; emails are matched case-insensitively.
- The Docker image uses the released .NET 10 images and runs as a non-root user.

### Play Store readiness

- `compileSdk` and `targetSdk` 36, version `1.0.0` / code 100, Android Gradle plugin 8.9.1, Gradle 8.11.1.
- Release signing from `keystore.properties` or CI secrets (never committed), `proguard-rules.pro` for R8, cleartext traffic disabled in release builds, and all three languages kept in the App Bundle.
- `store/listing.md`, `docs/privacy-policy.md` and `store/feature-graphic-1024x500.png` prepared; promotional screenshots are captured on the phone (see `docs/screenshots/README.md`).

### DevOps and documentation

- Android CI uses SDK 36, supplies `google-services.json` from a repository secret (or a placeholder for forks) and now also builds the minified release variant, so shrinking problems surface on every push.
- New `Signed release` workflow builds and verifies the signed APK and AAB and publishes a GitHub Release when the `Final-POE` tag is pushed.
- `scripts/verify-submission.sh` checks the PoE checklist (no archives, no leaked secrets, 75+ commits, 2+ contributors, final tag, video link, screenshots, AI write-up length).
- `REFERENCES-CODE.md` now exists and contains every IIE Harvard entry cited in the code; every source file carries a reference note.
- Added unit tests for tag rewriting, budget parsing, PIN lock-out, sync planning, refresh tokens and the localised test push (static count: 169 Android test methods and 83 API test cases).

### Carried forward from Part 3

- Budget alert logic on the offline sync path, retryable device registration, language-aware notifications, production launcher artwork and the move of CI to repository-root workflows (see the repository history for details).

### Known limitations

- Attachments are stored as Base64 in PostgreSQL, which is a relational store. The brief's separate blob-storage and NoSQL outcomes are not implemented as separate services; this is stated openly in the README and should be confirmed with the lecturer.
- The Android currency converter uses approximate, manually maintained rates.
- FCM delivery needs valid server credentials, a registered device, connectivity and notification permission; no delivery guarantee is claimed.
- User content and previously delivered notifications are not retroactively translated.
- The PIN is set once per device because it is no longer stored on the server.

### Final release metadata (complete when the artefacts exist)

| Field | Value |
|---|---|
| Release date | REPLACE_WITH_DATE |
| Tested commit SHA | REPLACE_WITH_SHA |
| Tag | `Final-POE` |
| GitHub release URL | REPLACE_WITH_RELEASE_URL |
| Signed APK SHA-256 | see `SHA256SUMS.txt` in the release |
| Phone and Android version used for testing | REPLACE_WITH_DEVICE |
| Demonstration video | REPLACE_WITH_VIDEO_URL |

### Earlier verified history (Part 3 working branch)

Android CI #3 and API CI #3 passed for `055bf26` on `blake` on 24 October 2026 (151 Android and 68 API tests, 0 lint errors); see the [Android run](https://github.com/EMGPPT/prog7314-2026-poe-st10443021/actions/runs/40089993728) and [API run](https://github.com/EMGPPT/prog7314-2026-poe-st10443021/actions/runs/40089993753). Results for the final revision are in the Actions tab after the tag is pushed.

See the [README](README.md) for setup, architecture and the evidence checklist, and [REFERENCES-CODE.md](REFERENCES-CODE.md) for the references cited above.
