# Implementation Plan - Registration Screen and Auth Fixes

This plan outlines the steps to implement a comprehensive Registration screen, fix the "Create one" navigation, and address the reported authentication issues. The implementation will include IIE Harvard format references in the code comments as requested.

## User Review Required

> [!IMPORTANT]
> The "Sign in with Google/GitHub" functionality currently points to `10.0.2.2:5000`. This is the emulator loopback address. For these to work, a backend must be running on your host machine at that port. I will ensure the UI reflects this and handles errors gracefully.

## Proposed Changes

### Navigation

#### [MODIFY] [DevLedgerDestinations.kt](file:///C:/Users/user/AndroidStudioProjects/DevLedger/android/app/src/main/java/com/devledger/app/ui/navigation/DevLedgerDestinations.kt)
- Add `REGISTER` constant.

#### [MODIFY] [DevLedgerNavHost.kt](file:///C:/Users/user/AndroidStudioProjects/DevLedger/android/app/src/main/java/com/devledger/app/ui/navigation/DevLedgerNavHost.kt)
- Add the `composable` route for `REGISTER`.
- Wire `onNavigateToRegister` in `AuthScreen` to navigate to `REGISTER`.

---

### UI Components

#### [MODIFY] [CommonComponents.kt](file:///C:/Users/user/AndroidStudioProjects/DevLedger/android/app/src/main/java/com/devledger/app/ui/components/CommonComponents.kt)
- Update `DevLedgerTextField` to support:
  - Leading icons (inline icons for Name, Email, Password).
  - Trailing icons (password toggle, valid checkmarks).
  - Visual validation state (green checkmarks/red borders).
- Add `PasswordStrengthMeter` component with color-coded bars (Weak -> Fair -> Good -> Strong).

---

### Registration Feature

#### [NEW] [RegisterViewModel.kt](file:///C:/Users/user/AndroidStudioProjects/DevLedger/android/app/src/main/java/com/devledger/app/ui/screens/auth/RegisterViewModel.kt)
- Manage registration state: full name, email, password.
- Real-time validation logic with per-field error messages.
- Password strength calculation.
- API call handling for `POST /api/auth/register` with loading and error states.
- **IIE Harvard References**: Include references to Android security and UI best practices in the code.

#### [NEW] [RegisterScreen.kt](file:///C:/Users/user/AndroidStudioProjects/DevLedger/android/app/src/main/java/com/devledger/app/ui/screens/auth/RegisterScreen.kt)
- Implement UI matching the provided image (Dark theme, specific icons).
- Include "Back to sign in" chevron link.
- API error banner for failed calls.
- Automatic navigation to Biometric screen on success.

---

### Authentication Fixes

#### [MODIFY] [AuthScreen.kt](file:///C:/Users/user/AndroidStudioProjects/DevLedger/android/app/src/main/java/com/devledger/app/ui/screens/auth/AuthScreen.kt)
- Update the "Create one" button to trigger `onNavigateToRegister`.
- Ensure styling matches the provided design (e.g., logo placement, button colors).
- Handle connection errors gracefully with user-friendly messages.

## Verification Plan

### Automated Tests
- N/A for this phase, manual verification prioritized.

### Manual Verification
- Deploy to emulator.
- Click "Create one" on Auth screen -> Verify navigation to Register screen.
- Test real-time validation (email format, password length).
- Test password strength meter colors.
- Test show/hide password toggle.
- Attempt registration -> Verify loading state.
- Verify navigation to Biometric screen on success/network error (demo mode).
- Click "Back to sign in" -> Verify return to Auth screen.
