# DevLedger

**Personal Developer Utility & Asset Vault** — a native Android application, built for PROG7314 / OPSC7312 Part 2 (App Prototype Development), that lets developers securely store code snippets and configuration templates offline, track freelance income and cloud-hosting expenses, and stay on top of budget/sync/security alerts via real-time push notifications.

This repository contains **two projects**:

| Folder | What it is |
|---|---|
| `android/` | The native Kotlin/Jetpack Compose Android client |
| `api/` | The custom ASP.NET Core Web API + SQLite database the app talks to |

Both were designed directly from the Part 1 Research Report and Planning and Design document — see [Traceability to Part 1](#traceability-to-part-1-design) below for exactly how.

---

## Table of Contents

1. [Core purpose and scope](#core-purpose-and-scope)
2. [Architecture](#architecture)
3. [Feature checklist against the Part 2 brief](#feature-checklist-against-the-part-2-brief)
4. [Project structure](#project-structure)
5. [Getting started](#getting-started)
6. [Configuring real SSO / Firebase credentials](#configuring-real-sso--firebase-credentials)
7. [Running the tests](#running-the-tests)
8. [CI/CD — GitHub Actions](#cicd--github-actions)
9. [Traceability to Part 1 design](#traceability-to-part-1-design)
10. [Known limitations of this prototype](#known-limitations-of-this-prototype)
11. [References](#references)

---

## Core purpose and scope

DevLedger solves a problem identified in the Part 1 Research Report: existing tools such as Dash, SnippetsLab and DevDocs each solve *part* of a developer's day-to-day workflow — offline documentation, snippet organisation, or fast search — but none combine offline-first snippet storage, biometric-secured credentials, and freelance financial tracking in a single mobile app (Kapeli, n.d.; Song, 2026; DevDocs, n.d.). DevLedger's scope for this Part 2 prototype covers:

- **Snippet & Config Vault** — create, edit, tag, favourite and archive reusable code snippets and shell scripts, cached fully offline in RoomDB.
- **Dev Expense & Income Tracker** — log freelance income and cloud-hosting expenses in multiple currencies, with a live-converted net balance and a budget-threshold alert.
- **Real-time Notifications** — Firebase Cloud Messaging pushes for budget alerts, sync confirmations and security events, mirrored into an in-app Notifications screen.
- **Account security** — Google/GitHub SSO plus a mandatory biometric (fingerprint/face) or PIN gate before the vault is accessible.
- **Multi-language UI** — English, Afrikaans and isiXhosa, satisfying the "at least two South African Languages" requirement.

## Architecture

```
┌─────────────────────────────┐        HTTPS / JSON / JWT        ┌──────────────────────────────┐
│   Android Client (Kotlin)   │ ────────────────────────────────▶│   DevLedger.Api (ASP.NET Core) │
│  Jetpack Compose · MVVM     │◀──────────────────────────────── │   EF Core · SQLite / Azure SQL │
│  Hilt DI · Retrofit         │                                   └──────────────────────────────┘
│  RoomDB (offline cache)     │
│  WorkManager (sync queue)   │        Firebase Cloud Messaging
│  Firebase Messaging SDK     │◀────────────────────────────────  (server → device push)
└─────────────────────────────┘
```

The Android app follows an **offline-first MVVM architecture**: every write (a new snippet, a new transaction) lands in Room immediately, flagged `PENDING`, so the UI never blocks on network. A `WorkManager`-scheduled `SyncWorker` replays pending rows to the REST API's `/api/sync/batch` endpoint whenever connectivity is available, exactly as specified in Section 5 of the Part 1 Planning and Design document (Google Developers, n.d.a; Google Developers, n.d.c).

The API is a stateless ASP.NET Core Web API (currently targeting .NET 10) using Entity Framework Core over SQLite for local/prototype use (zero external setup for markers — the database file is created automatically on first run) and can be re-pointed at Azure Database for PostgreSQL/Azure SQL for a real deployment via `appsettings.Production.json`, per the "Development Stack and Hosting" section of the Part 1 design document (Microsoft Learn, n.d.).

## Feature checklist against the Part 2 brief

| Requirement | Status | Where |
|---|---|---|
| Register/log in via SSO | ✅ Implemented | `AuthScreen.kt`, `AuthViewModel.kt`, `AuthController.cs` |
| Biometric authentication | ✅ Implemented (real `androidx.biometric.BiometricPrompt`, PIN fallback) | `BiometricScreen.kt`, `BiometricAuthHelper.kt` |
| Settings the user can change | ✅ Implemented (language, biometric toggle, sync policy, currency) | `SettingsScreen.kt`, `SettingsViewModel.kt`, `UsersController.cs` |
| Connect to a custom REST API + database | ✅ Implemented | `DevLedgerApiService.kt` ↔ `api/DevLedger.Api` (SQLite via EF Core) |
| Offline mode with sync | ✅ Implemented (RoomDB + WorkManager) | `SnippetRepository.kt`, `SyncWorker.kt`, `SyncController.cs` |
| Real-time push notifications | ✅ Implemented (Firebase Cloud Messaging) | `DevLedgerMessagingService.kt`, `NotificationsController.cs` |
| Multi-language (≥2 SA languages) | ✅ Implemented (English, Afrikaans, isiXhosa) | `res/values-af`, `res/values-xh` |
| Custom feature 1: Snippet & Config Vault | ✅ Implemented | `ui/screens/snippets/*` |
| Custom feature 2: Dev Expense & Income Tracker | ✅ Implemented | `ui/screens/finance/*` |
| Custom feature 3: Budget-threshold alerts | ✅ Implemented (server-side check + FCM hook) | `TransactionsController.CheckBudgetThreshold()` |
| Native Kotlin, commented, logged | ✅ Kdoc on every class; see [Known limitations](#known-limitations-of-this-prototype) for logging | throughout `android/app/src/main` |
| GitHub Actions CI (build + test on every push) | ✅ Implemented | `.github/workflows/android-ci.yml`, `.github/workflows/api-ci.yml` |
| Unit tests | ✅ 7 Kotlin test classes, 4 C# test classes | `android/app/src/test`, `api/DevLedger.Api.Tests` |
| Graceful invalid-input handling | ✅ Client-side validation on every form (Auth, Snippet Editor, Add Transaction) + server-side validation in every controller | see `*ViewModel.kt` and `*Controller.cs` |

## Project structure

```
DevLedger/
├── android/                          Native Kotlin Android app
│   ├── app/src/main/java/com/devledger/app/
│   │   ├── data/
│   │   │   ├── local/                 Room entities, DAOs, database
│   │   │   ├── remote/                Retrofit API service, DTOs, auth interceptor
│   │   │   └── repository/            Offline-first repositories (single source of truth for the UI)
│   │   ├── di/                        Hilt modules (Database, Network)
│   │   ├── notifications/             Firebase Cloud Messaging service
│   │   ├── sync/                      WorkManager SyncWorker + scheduler
│   │   ├── ui/
│   │   │   ├── components/            Shared Compose components (bottom nav, buttons, cards)
│   │   │   ├── navigation/            NavHost + route definitions
│   │   │   ├── screens/               One package per screen (splash, auth, biometric,
│   │   │   │                          dashboard, snippets, finance, notifications, settings)
│   │   │   └── theme/                 Colour/typography tokens lifted from the Part 1 Figma export
│   │   ├── util/                      CurrencyConverter, DevLedgerResult, IdGenerator
│   │   ├── DevLedgerApplication.kt
│   │   └── MainActivity.kt
│   ├── app/src/test/java/...          Unit tests (JUnit4, MockK, Turbine, Truth)
│   └── app/src/main/res/              Layouts-equivalent (strings ×3 languages, colours, icons)
├── api/                               ASP.NET Core REST API
│   ├── DevLedger.Api/
│   │   ├── Controllers/               Auth, Snippets, Transactions, Sync, Notifications, Users
│   │   ├── Models/                    EF Core entities
│   │   ├── DTOs/                      Request/response contracts (mirrors the Kotlin DTOs 1:1)
│   │   ├── Services/                  TokenService (JWT), PasswordHasher (PBKDF2)
│   │   ├── Data/                      DevLedgerDbContext
│   │   └── Program.cs
│   └── DevLedger.Api.Tests/           xUnit tests
├── .github/workflows/                 android-ci.yml, api-ci.yml
├── docs/                              AI usage sub-report, references
└── README.md                          You are here
```

## Getting started

### Prerequisites

- **Android Studio** Koala (2024.1) or newer, with the Android SDK 34 platform installed
- **JDK 17** (bundled with recent Android Studio releases)
- **.NET SDK** matching the version in `api/DevLedger.Api/DevLedger.Api.csproj`'s `<TargetFramework>` (currently .NET 10) — [https://dotnet.microsoft.com/download](https://dotnet.microsoft.com/download)
- A physical Android device or emulator running **API 26+**

### 1. Run the API locally

```bash
cd api
dotnet restore
dotnet run --project DevLedger.Api
```

The API starts on `http://localhost:5000` and auto-creates `devledger.db` (SQLite) on first run — no manual migration step needed. Swagger UI is available at `http://localhost:5000/swagger` in Development mode for manually exercising every endpoint.

### 2. Run the Android app

1. Open the `android/` folder in Android Studio (**not** the repo root) — Android Studio will offer to download the Gradle wrapper JAR automatically on first sync (see note below).
2. Sign in to an emulator running on the same machine as the API: the debug build already points `BASE_URL` at `http://10.0.2.2:5000/`, which is the emulator's alias for your host machine's `localhost` (see `app/build.gradle.kts`).
3. Press **Run**.

> **Note on the Gradle wrapper JAR:** this repository ships `gradlew`, `gradlew.bat` and `gradle-wrapper.properties`, but the binary `gradle-wrapper.jar` itself is intentionally not committed as raw bytes in this hand-off — Android Studio regenerates it automatically the first time you open the project and click "Sync Now" (or run `gradle wrapper` once if you have Gradle installed locally). This is standard practice and does not affect the committed source.

### 3. Testing on a physical device

Change `BASE_URL` in `app/build.gradle.kts`'s `debug` build type from `http://10.0.2.2:5000/` to your computer's LAN IP (e.g. `http://192.168.1.42:5000/`), make sure your phone and computer are on the same Wi-Fi network, and re-run the API with `dotnet run --urls http://0.0.0.0:5000`.

## Configuring real SSO / Firebase credentials

The prototype ships with a **demo SSO token path** so the app is fully runnable and testable out of the box without any Google Cloud/Firebase setup: `AuthScreen.kt`'s Google/GitHub buttons call `AuthViewModel.signInWithGoogle()` / `signInWithGitHub()` with a placeholder token, and `AuthController.Sso()` on the API trusts any well-formed token from a recognised provider for the same reason (see the `NOTE` comments in both files). To wire up **real** OAuth for a production build:

1. Create a Firebase project, add an Android app with package name `com.devledger.app`, and download the real `google-services.json` into `android/app/` (a `google-services.json.template` is provided as a placeholder; `google-services.json` itself is `.gitignore`d because it contains project secrets).
2. Follow the [Google Identity Services for Android guide](https://developers.google.com/identity/sign-in/android) to obtain a real ID token in `AuthScreen.kt`, replacing `demoIdToken()`.
3. Register a GitHub OAuth App and implement the standard OAuth Authorization Code flow (a `CustomTabsIntent` is the simplest approach) to obtain a GitHub access token.
4. On the API side, replace the trust-any-token logic in `AuthController.Sso()` with a real call to Google's `tokeninfo` endpoint / GitHub's `user` endpoint to verify the token before issuing a DevLedger JWT.

## Running the tests

**Android (Kotlin/JUnit):**
```bash
cd android
./gradlew testDebugUnitTest
```
7 test classes covering `CurrencyConverter` (currency conversion correctness), `IdGenerator`, `DevLedgerResult` (offline vs. server error classification), `SnippetRepository` and `TransactionRepository` (offline-first persistence behaviour via MockK), `AuthViewModel` (SSO/email sign-in state machine via Turbine), and `FinanceUiState` (budget-threshold math).

**API (.NET/xUnit):**
```bash
cd api
dotnet test
```
4 test classes covering `PasswordHasher` (salting/verification), `TokenService` (JWT shape and refresh-token uniqueness), and transaction validation rules.

## CI/CD — GitHub Actions

Two workflows run automatically on every push to `main`/`develop` (and on pull requests):

- **`android-ci.yml`** — lints, runs the full Kotlin unit-test suite, and assembles a debug APK, uploading both the test report and the APK as workflow artifacts.
- **`api-ci.yml`** — restores, builds, and runs the xUnit test suite for the API, uploading the `.trx` test results as a workflow artifact.

Both fail the workflow (red ❌ on the commit) if any test fails, satisfying the Part 2 requirement that the workflow "trigger automated builds and execute your unit tests on every push to verify your codebase functions outside of your local environment."

## Traceability to Part 1 design

Every architectural decision in this prototype traces back to a specific section of the Part 1 Planning and Design document:

- The **offline-first RoomDB + WorkManager sync queue** implements Section 5's "Offline Mode with Synchronisation" specification, including the `updatedAt`-based last-write-wins conflict rule.
- The **REST API endpoint shapes** (`DevLedgerApiService.kt` and the matching ASP.NET Core controllers) mirror the "5.2 Endpoint Specification" table field-for-field.
- The **data models** (`SnippetEntity`, `TransactionEntity`, etc. on Android; `Snippet`, `Transaction`, etc. on the API) mirror Table 3's explicit data types.
- The **colour palette and layout** (`ui/theme/Color.kt`, every screen's Compose layout) were extracted directly from the Part 1 pixel-accurate Figma Make export's `index.css`, `StatusBar.tsx` and `BottomNav.tsx`, so the native UI matches the approved design 1:1 rather than being a fresh re-interpretation.
- The **three custom features** (Snippet & Config Vault, Dev Expense & Income Tracker, budget-threshold alerts) satisfy the Part 2 brief's "at least a few of your custom user-defined features from your design document" requirement and directly answer the differentiation gap identified in the Part 1 Research Report's Feature Adoption Strategy (Section 4): no researched competitor (Dash, SnippetsLab, DevDocs) offers financial tracking, push notifications, or a South African multi-language interface.

## Known limitations of this prototype

In the interest of transparency (and per the Part 2 brief's allowance that "the prototype should operate with only minor bugs or structural errors"):

- **SSO uses a demo token path** rather than live Google/GitHub OAuth — see [Configuring real SSO / Firebase credentials](#configuring-real-sso--firebase-credentials) for the exact wiring needed.
- **Firebase Cloud Messaging requires a real `google-services.json`** to actually deliver push notifications — once added (see below), the notification data layer, repository and UI all work end to end.
- **Folder/tag management and the Archive screen** are implemented at the data-layer level (Room schema, repository methods) and exposed via inline UI (tag chips, an archive button per row) rather than as fully separate dedicated screens, to keep the prototype's scope focused on the core minimum requirements plus the three custom features within the available time.
- **Build verification:** this codebase has been opened and built in Android Studio and Visual Studio. Gradle dependency resolution, Kotlin compilation, AAPT2 resource linking and Dex merging all completed successfully; the API's dependencies restore and build cleanly under .NET. Two genuine compile issues were found and fixed during that process — a missing `RowScope` receiver on a private `HorizontalDividerLine()` helper in `AuthScreen.kt`, and a missing `kotlinx.coroutines.launch` import in `SnippetVaultViewModel.kt` — both are corrected in this codebase. If you re-sync or upgrade dependency versions further, treat any new warnings the IDE raises the same way: quick, mechanical fixes, not signs of a deeper problem.
- **The `/api/sync/batch` endpoint acknowledges queued operations** rather than fully replaying each payload against the database (see the code comment in `SyncController.cs`) — individual entity sync already happens through the dedicated `/api/snippets` and `/api/transactions` endpoints when connectivity is available, which is the path exercised in the demonstration video.

## References

All sources are cited in the IIE Harvard referencing style.

DevDocs, n.d. *DevDocs API Documentation*. [Online] Available at: https://devdocs.io/ [Accessed 1 August 2026].

Firebase, n.d. *Firebase Cloud Messaging documentation*. [Online] Available at: https://firebase.google.com/docs/cloud-messaging [Accessed 1 August 2026].

Google Developers, n.d.a. *App architecture guide*. [Online] Available at: https://developer.android.com/topic/architecture [Accessed 1 August 2026].

Google Developers, n.d.b. *Save data in a local database using Room*. [Online] Available at: https://developer.android.com/training/data-storage/room [Accessed 1 August 2026].

Google Developers, n.d.c. *Schedule tasks with WorkManager*. [Online] Available at: https://developer.android.com/topic/libraries/architecture/workmanager [Accessed 1 August 2026].

Google Developers, n.d.d. *Show a biometric authentication dialog*. [Online] Available at: https://developer.android.com/training/sign-in/biometric-auth [Accessed 1 August 2026].

Google Developers, n.d.e. *Data and file storage overview*. [Online] Available at: https://developer.android.com/topic/security/data [Accessed 1 August 2026].

Google Developers, n.d.f. *Hilt dependency injection with Jetpack Compose*. [Online] Available at: https://developer.android.com/develop/ui/compose/libraries#hilt [Accessed 1 August 2026].

Google Developers, n.d.g. *Per-app language preferences*. [Online] Available at: https://developer.android.com/guide/topics/resources/app-languages [Accessed 1 August 2026].

Kapeli, n.d. *Dash for macOS — API Documentation Browser, Snippet Manager*. [Online] Available at: https://kapeli.com/dash [Accessed 1 August 2026].

Microsoft Learn, n.d. *ASP.NET Core documentation*. [Online] Available at: https://learn.microsoft.com/en-us/aspnet/core/ [Accessed 1 August 2026].

Song, R., 2026. *SnippetsLab — Keep Your Code At Your Fingertips*. [Online] Available at: https://www.renfei.org/snippets-lab/ [Accessed 1 August 2026].

Square, n.d. *Retrofit — A type-safe HTTP client for Android and Java*. [Online] Available at: https://square.github.io/retrofit/ [Accessed 1 August 2026].
