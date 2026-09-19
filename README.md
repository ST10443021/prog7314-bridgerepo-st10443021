# DevLedger

**Personal Developer Utility & Asset Vault** — a native Android application, built for PROG7314 / OPSC7312 Part 2 (App Prototype Development), that lets developers securely store code snippets and configuration templates offline, track freelance income and cloud-hosting expenses, and stay on top of budget/sync/security alerts via real-time push notifications.

This repository contains two projects:

| Folder | What it is |
|---|---|
| `android/` | The native Kotlin/Jetpack Compose Android client |
| `api/` | The custom ASP.NET Core Web API + PostgreSQL database hosted on Render the app talks to |

**YouTube Video**: https://youtu.be/Qk9ElXJkmPA

## Team Members

| Name | Student Number |
|---|---|
| Blake Derek Godfrey | ST10435415 |
| Syed Muhammad Hamza Kazmi | ST10443021 |
| Connor Albertyn | ST10437293 |
| Muzammil Cassim | ST10259792 |

---

## 1. Purpose and Scope

DevLedger is a native Android app for developers who juggle reusable code snippets and freelance/contract income tracking in one place. It was designed and researched in Part 1 (competitive analysis against SnippetsLab and similar tools) and built out fully in Part 2 against a self-hosted REST API.

### Core requirements implemented in this prototype:

| Requirement | Status | Where |
|---|---|---|
| Register/log in via SSO | ✅ Implemented — real Google & GitHub OAuth 2.0, verified server-side | `AuthScreen.kt`, `AuthViewModel.kt`, `AuthController.cs` |
| Change settings in-app | ✅ Implemented | `SettingsScreen.kt` ↔ `/api/settings` |
| Connect to a custom REST API + database | ✅ Implemented | `DevLedgerApiService.kt` ↔ `api/DevLedger.Api` (PostgreSQL via EF Core, hosted on Render) |
| Offline mode with sync | ✅ Implemented | RoomDB (offline-first) + `SyncWorker`/`SyncScheduler` (WorkManager) + `/api/sync/batch` |
| Real-time push notifications | ✅ Implemented | Firebase Cloud Messaging + Firebase Admin SDK, triggered by a budget-threshold alert |
| Biometric authentication | ✅ Implemented | `BiometricScreen.kt` (`BiometricPrompt`) + PIN fallback (`PinVerificationScreen.kt`) |
| Multi-language support | ✅ Implemented (English, Afrikaans, isiXhosa) | `res/values-af`, `res/values-xh` |

### Custom, user-defined features:

*   **Snippet Vault** — folders, tags, syntax-aware code editor with line numbers, favourites, search, share/duplicate/archive.
*   **Financial Tracker** — income/expense logging, category breakdowns, monthly summary chart, recurring transactions (auto-generated via a background worker), receipt/invoice attachments.
*   **Sync Status dashboard** — a dedicated screen showing pending/completed/failed sync items, manual "Sync Now", and Wi-Fi-only / charging-only / auto-sync preferences.

---

## 2. Architecture

```
┌─────────────────────────────┐        HTTPS / JSON / JWT        ┌───────────────────────────────┐
│   Android Client (Kotlin)   │ ────────────────────────────────▶│  DevLedger.Api (ASP.NET Core)  │
│  Jetpack Compose · MVVM     │◀──────────────────────────────── │  EF Core · PostgreSQL (Render) │
│  Hilt DI · Retrofit         │                                   └──────────────┬────────────────┘
│  RoomDB (offline cache)     │        Firebase Cloud Messaging                  │
│  WorkManager (sync queue)   │◀──────────────────────────────── (Admin SDK)     │ Server-side verification
│  Firebase Messaging SDK     │                                                  ▼
│  Google Sign-In SDK         │────────────────────────▶  ┌──────────────────────────────────┐
│  Custom Tabs (GitHub OAuth) │────────────────────────▶  │  Google OAuth 2.0 · GitHub OAuth 2.0 │
│  BiometricPrompt + PIN      │                            └──────────────────────────────────┘
└─────────────────────────────┘
```

*   **Client**: Offline-first MVVM. Every write (a new snippet, a new transaction) lands in Room immediately, flagged `PENDING`, so the UI never blocks on network. A WorkManager-scheduled `SyncWorker` replays pending rows to the API's `/api/sync/batch` endpoint whenever connectivity is available, respecting the user's Wi-Fi-only and charging-only sync preferences (Google Developers, n.d.a; Google Developers, n.d.c).
*   **Backend**: A stateless ASP.NET Core Web API (.NET 10) using Entity Framework Core over PostgreSQL, hosted on Render (Render, n.d.). Authentication is fully server-verified: the API validates every Google ID token against Google's public keys and audience (Google Developers, n.d.h) and exchanges every GitHub authorization code server-side for a verified profile (GitHub, n.d.) — the client secret never leaves the API. Push notifications are sent through the Firebase Admin SDK for .NET (Google Developers, n.d.i) to devices registered via Firebase Cloud Messaging (Google Developers, n.d.j).
*   **Security layer**: After JWT authentication succeeds, a local device security step runs — biometric authentication via Android's `BiometricPrompt` API backed by the hardware Keystore, with a PIN fallback for devices without biometric hardware.

---

## 3. Configuration

Fully wired to live services — no demo/placeholder token path in this build. To run your own copy:

*   **Google OAuth** — create a Web application OAuth Client ID in Google Cloud Console, plus an Android-type client entry (package name + SHA-1 from `./gradlew signingReport`) in the same project. Put the Web Client ID in `GOOGLE_WEB_CLIENT_ID` (`build.gradle.kts`) and in the API's `Google:WebClientId` via `dotnet user-secrets` locally / `Google__WebClientId` on Render.
*   **GitHub OAuth** — register an OAuth App at `github.com/settings/developers` with redirect URI `devledger://oauth/github/callback`. The Client ID goes in `GITHUB_CLIENT_ID` (`build.gradle.kts`, safe to embed); the Client Secret goes only into `dotnet user-secrets` and Render's `GitHub__ClientSecret` — never into the Android project or version control.
*   **Firebase** — download a service-account key from Firebase Console → Project Settings → Service Accounts, and point `Firebase:CredentialsPath` at it via user-secrets locally / a Render Secret File in production.
*   **Database** — provision a PostgreSQL instance (Render's managed Postgres) and set `ConnectionStrings__DefaultConnection` accordingly; `dotnet ef database update` applies the schema on first run (the API also runs pending migrations automatically on startup).

All four values above are secrets or environment-specific and are intentionally excluded from this repository via `.gitignore` and Render's Environment tab.

---

## 4. Technology Stack

| Layer | Technology |
|---|---|
| **Mobile client** | Kotlin, Jetpack Compose, MVVM, Hilt, Retrofit, Room, WorkManager |
| **Authentication** | JWT (HMAC-SHA256), Google Identity Services, GitHub OAuth 2.0, BiometricPrompt |
| **Backend API** | ASP.NET Core 10 Web API, Entity Framework Core |
| **Database** | PostgreSQL (Npgsql provider) |
| **Hosting** | Render (Docker web service + managed Postgres) |
| **Push notifications** | Firebase Cloud Messaging, Firebase Admin SDK |
| **CI/CD** | GitHub Actions (Android build + unit tests, API build + unit tests) |

---

## 5. Version Control and CI/CD

Two GitHub repositories track this project's history: the Android client and the ASP.NET Core API, each committed to incrementally as features were built, debugged, and iterated — not delivered as a single dump.
*   `.github/workflows/android-ci.yml` builds the Android project, generates the missing Gradle wrapper binary, and runs its JUnit/MockK unit test suite on every push.
*   `.github/workflows/api-ci.yml` builds the API against the .NET 10 SDK and runs its xUnit test suite on every push.
*   Both workflows must be green before a change is considered complete; a .NET 8 vs .NET 10 SDK mismatch that previously caused this workflow to fail silently on every push has been corrected.

---

## 6. Testing

*   **Android**: JUnit + MockK + Turbine cover the ViewModel and Repository layers — authentication state transitions, snippet/transaction validation rules, and the offline sync queue's success/conflict/retry paths.
*   **API**: xUnit covers controller-level authorization scoping (a user can never read or write another user's data), input validation on transaction/snippet creation, and the sync batch endpoint's conflict-resolution logic.
*   Tests are run automatically by GitHub Actions on every push — not only locally. The current codebase passes all 38 unit tests locally and in CI.

---

## 7. Known Limitations

*   **Receipt/invoice attachments** are stored as base64 in PostgreSQL rather than a dedicated blob storage service, since Render's local disk is not persistent across deploys — acceptable at prototype scale (2MB cap enforced), but a production build would move this to Azure Blob Storage or S3.
*   The `/api/auth/refresh` endpoint is a stub that forces re-authentication rather than issuing a rotated refresh token — acceptable for prototype session lengths, flagged for the final POE.
*   **Build verification**: The Android workflow is specifically configured to regenerate the `gradle-wrapper.jar` and create a placeholder `google-services.json` so that it can run in a clean environment without manual setup.

---

## 8. Demonstration Video

📺 **[https://youtu.be/Qk9ElXJkmPA]**

---

## References

All sources are cited in the IIE Harvard referencing style.

- DevDocs, n.d. *DevDocs API Documentation.* [Online] Available at: https://devdocs.io/ [Accessed 1 August 2026].
- Firebase, n.d. *Firebase Cloud Messaging documentation.* [Online] Available at: https://firebase.google.com/docs/cloud-messaging [Accessed 1 August 2026].
- GitHub, n.d. *Authorizing OAuth Apps.* [Online] Available at: https://docs.github.com/en/apps/oauth-apps/building-oauth-apps/authorizing-oauth-apps [Accessed 14 September 2026].
- Google Developers, n.d.a. *App architecture guide.* [Online] Available at: https://developer.android.com/topic/architecture [Accessed 1 August 2026].
- Google Developers, n.d.b. *Save data in a local database using Room.* [Online] Available at: https://developer.android.com/training/data-storage/room [Accessed 1 August 2026].
- Google Developers, n.d.c. *Schedule tasks with WorkManager.* [Online] Available at: https://developer.android.com/topic/libraries/architecture/workmanager [Accessed 1 August 2026].
- Google Developers, n.d.d. *Show a biometric authentication dialog.* [Online] Available at: https://developer.android.com/training/sign-in/biometric-auth [Accessed 1 August 2026].
- Google Developers, n.d.e. *Data and file storage overview.* [Online] Available at: https://developer.android.com/topic/security/data [Accessed 1 August 2026].
- Google Developers, n.d.f. *Hilt dependency injection with Jetpack Compose.* [Online] Available at: https://developer.android.com/develop/ui/compose/libraries#hilt [Accessed 1 August 2026].
- Google Developers, n.d.g. *Per-app language preferences.* [Online] Available at: https://developer.android.com/guide/topics/resources/app-languages [Accessed 1 August 2026].
- Google Developers, n.d.h. *Firebase Admin SDK for .NET.* [Online] Available at: https://firebase.google.com/docs/reference/admin/dotnet [Accessed 14 September 2026].
- Google Developers, n.d.i. *Set up a Firebase Cloud Messaging client app on Android.* [Online] Available at: https://firebase.google.com/docs/cloud-messaging/android/client [Accessed 14 September 2026].
- Google Developers, n.d.j. *Cloud Messaging architectural overview.* [Online] Available at: https://firebase.google.com/docs/cloud-messaging/fcm-architecture [Accessed 14 September 2026].
- Google Developers, n.d.k. *Authenticate with Google on Android.* [Online] Available at: https://developers.google.com/identity/sign-in/android [Accessed 14 September 2026].
- Microsoft Learn, n.d. *ASP.NET Core documentation.* [Online] Available at: https://learn.microsoft.com/en-us/aspnet/core/ [Accessed 1 August 2026].
- Render, n.d. *Render Docs — Web Services.* [Online] Available at: https://render.com/docs/web-services [Accessed 14 September 2026].
- Square, n.d. *Retrofit — A type-safe HTTP client for Android and Java.* [Online] Available at: https://square.github.io/retrofit/ [Accessed 1 August 2026].
