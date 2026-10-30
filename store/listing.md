# Google Play listing (copy and paste into Play Console)

| Field | Value | Limit |
|---|---|---|
| App name | DevLedger: Dev Vault & Ledger | 30 chars (29) |
| Short description | Offline code-snippet vault plus a freelance income and expense ledger. | 80 chars (70) |
| Category | Productivity | |
| Default language | English (United Kingdom) | |
| Additional languages | Afrikaans, isiXhosa | |
| Contact email | REPLACE_WITH_CONTACT_EMAIL | |
| Privacy policy URL | REPLACE_WITH_PRIVACY_POLICY_URL (host `docs/privacy-policy.md`, for example with GitHub Pages) | required |
| App icon | `DevLedger/android/app/src/main/ic_launcher-playstore.png` | 512x512 |
| Feature graphic | `store/feature-graphic-1024x500.png` | 1024x500 |
| Phone screenshots | `docs/screenshots/play-01.png` to `play-08.png` (see `docs/screenshots/README.md`) | 2 to 8 |
| Release asset | Signed `.aab` from the `Signed release` workflow | |
| Target API | 36 (Android 16) | required for new apps since 31 Aug 2026 (Google Play Console Help, n.d.) |

## Full description (1271 / 4000 chars)

```text
DevLedger keeps a developer's reusable code and freelance money in one secure, offline-ready app.

SNIPPET VAULT
- Save code, scripts, regex and configuration templates with syntax-aware editing and line numbers
- Organise with folders and tags; rename or delete them from the three-dot menu
- Search, favourite, duplicate, share and archive snippets

FINANCIAL TRACKER
- Record freelance income and hosting or tool expenses with categories and receipts
- Monthly summaries, charts and recurring transactions
- Set a monthly budget and get a push notification when you reach 80% and 100% of it

WORKS WITHOUT A CONNECTION
- Everything you change offline is saved on the phone and syncs automatically when you are back online
- Choose automatic or manual sync, Wi-Fi only, charging only and how often to sync
- Clear status for queued items, failures and conflicts

SECURITY
- Sign in with email, Google or GitHub
- Unlock with your fingerprint or face (protected by the Android Keystore) or a PIN that never leaves your phone
- The PIN pad locks temporarily after repeated wrong entries

YOUR LANGUAGE
- English, Afrikaans and isiXhosa, switchable in the app

Built as a final-year Bachelor of Computing and Applications project at The Independent Institute of Education.
```

## Content rating (IARC questionnaire, suggested answers)
Violence, sexual content, bad language, controlled substances: none. User-generated content shared with other users: no (content is private
to the account). Location sharing: no. Digital purchases: none. Confirm the resulting rating in Play Console.

## Data safety form (answers that match the code)
| Question | Answer |
|---|---|
| Collects or shares user data? | Collects; not shared with third parties for advertising |
| Personal info | Name and email address (account management) |
| Financial info | User-entered transactions and receipts (app functionality; no payment cards) |
| App activity and files | Snippets, folders, tags and attachments the user creates (app functionality) |
| Device or other IDs | Firebase Cloud Messaging token (push notifications) |
| Encrypted in transit? | Yes, HTTPS only (cleartext is disabled in release builds) |
| Deletion requests | On request to the contact email; add the in-app route here if one exists |
| Biometric data | Not collected. Android handles it and the app only receives success or failure |

## Release checklist
1. Push the tag `Final-POE` to run the `Signed release` workflow.
2. Download `devledger-1.0.0-release.aab` and upload it to an Internal testing track.
3. Add screenshots, graphic, listing text, privacy policy URL and data safety answers.
4. If you publish, add the Play App Signing certificate fingerprints to the Google and GitHub OAuth clients.
