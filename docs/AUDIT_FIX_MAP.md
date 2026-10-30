# Audit finding to fix map

| Audit finding | Fix | Main files |
|---|---|---|
| 1. Folder and tag three-dot icons inert; folder/tag rename and delete unreachable | Overflow menu with Rename / Delete on folder and tag rows; tag rename/delete rewrites every snippet and syncs; search works; real All/Archived counts | `ui/screens/folders/FoldersTagsScreen.kt`, `FoldersTagViewModel.kt`, `SnippetRepository.kt`, `UserTagRepository.kt`, `SnippetDao.kt` |
| 2. Auto sync vs Wi-Fi only confusion (label and design defect) | Separate, clearly worded switches in Settings and on the Sync screen; Wi-Fi-only mirrored to the account | `SettingsScreen.kt`, `SyncPreferencesViewModel.kt`, `SyncViewModel.kt`, `SettingsRepository.kt`, `strings*.xml` |
| 3. Periodic sync only scheduled as a side effect | Scheduled at app start, after sign-in and on every preference change | `DevLedgerApplication.kt`, `SyncScheduler.kt` |
| Auto-sync off undone by every save; Settings save reset interval/charging | One `queueAfterLocalChange` path that reads saved preferences; Settings override removed | `SyncScheduler.kt`, four repositories, `SettingsScreen.kt` |
| No way to set a monthly budget (PUSH-01 blocked) | Budget field, validation, API save, offline cache, budget bar | `SettingsViewModel.kt`, `SettingsScreen.kt`, `SettingsRepository.kt`, `FinanceViewModel.kt`, `FinancialTrackerScreen.kt`, `BudgetInput.kt` |
| Backgrounded pushes skip the in-app list; tap has no deep link | Data-only FCM messages; tap opens Notifications after the security gate; test-push endpoint and button | `NotificationService.cs`, `NotificationsController.cs`, `DevLedgerMessagingService.kt`, `NotificationDeepLink.kt`, `DevLedgerNavHost.kt` |
| Debug logging leaks credentials | BASIC logging with header redaction; email removed from a log line | `NetworkModule.kt`, `AuthScreen.kt` |
| JWT signing key fallback | Required configuration, startup failure otherwise | `JwtSettings.cs`, `TokenService.cs`, `Program.cs` |
| Swagger enabled in production | Development or explicit `Swagger__Enabled` only | `Program.cs` |
| PIN hash returned in auth responses; 4-digit PIN brute force | PIN device-only (never uploaded or returned); lock-out policy; encrypted storage | `AuthController.cs`, `ProfileController.cs`, `PinStorage.kt`, `PinLockoutPolicy.kt`, `PinVerificationViewModel.kt` |
| Biometrics not bound to a hardware-backed key; PIN in plain prefs | Keystore key with `setInvalidatedByBiometricEnrollment`, CryptoObject prompt; EncryptedSharedPreferences with migration | `BiometricKeyManager.kt`, `BiometricAuthHelper.kt`, `PinStorage.kt` |
| No signing config; proguard-rules.pro missing; targetSdk 34; version 1.0.0-prototype | Signing from properties/CI secrets; rules file; API 36, AGP 8.9.1, Gradle 8.11.1; version 1.0.0 | `app/build.gradle.kts`, `proguard-rules.pro`, root `build.gradle.kts`, wrapper |
| No signed APK / screenshots | `release.yml` builds and verifies signed APK + AAB; store listing, graphic, privacy policy, screenshot checklist | `.github/workflows/release.yml`, `store/`, `docs/` |
| Hard-coded placeholders | Moved to localised resources | `RegisterScreen.kt`, `SecuritySetupScreen.kt`, `AddTransactionScreen.kt` |
| Inert menu icons (UI) | Vault menu, snippet card menu, history button, month picker wired; dead back arrow removed | `DevLedgerNavHost.kt`, `SnippetVaultScreen.kt`, `AddTransactionScreen.kt`, `FinancialTrackerScreen.kt`, `BiometricScreen.kt` |
| 57 files cite a missing REFERENCES-CODE.md | File created; every source file now has a reference note; unverifiable citation removed | `REFERENCES-CODE.md`, all `.kt`/`.cs` |
| README full of "Pending"; UD4/UD5 unmapped; no tag | README rewritten as a technical report with rubric mapping; release notes; verification script | `README.md`, `RELEASE_NOTES.md`, `scripts/verify-submission.sh` |
| AI write-up not written | Draft prepared (needs your confirmation lines) | `AI_USE_WRITEUP.md` |
| Refresh endpoint was a stub (session expired hourly) | Signed, rotated refresh tokens | `TokenService.cs`, `AuthController.cs` |

## Items only you can complete
Demonstration video, real screenshots, the signing keystore and GitHub secrets, the `Final-POE` tag, the commit-count and contributor evidence, access dates in the references, the confirmation lines in the AI write-up, a native-speaker check of the Afrikaans and isiXhosa strings, and setting `Jwt__SigningKey` on Render.
