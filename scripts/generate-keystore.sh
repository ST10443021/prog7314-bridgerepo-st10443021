#!/usr/bin/env bash
# Creates the upload keystore used to sign the DevLedger release (run in Git Bash on Windows).
# The keystore and passwords are secrets: they are git-ignored and must never be committed.
# Reference: (Google Developers, n.d.h). See REFERENCES-CODE.md.
set -euo pipefail

cd "$(git rev-parse --show-toplevel)"
KEYSTORE_DIR="keystore"
KEYSTORE="$KEYSTORE_DIR/devledger-upload.jks"
ALIAS="devledger-upload"

if ! command -v keytool >/dev/null 2>&1; then
  echo "keytool not found. Install JDK 21 (it ships with Android Studio) and retry." >&2
  exit 1
fi
if [ -f "$KEYSTORE" ]; then
  echo "$KEYSTORE already exists. Back it up; do not overwrite a keystore you have published with." >&2
  exit 1
fi

mkdir -p "$KEYSTORE_DIR"
keytool -genkeypair -v -keystore "$KEYSTORE" -alias "$ALIAS" \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -dname "CN=DevLedger, OU=PROG7314, O=IIE, C=ZA"

read -r -s -p "Re-enter the keystore password for keystore.properties: " STORE_PASS; echo
read -r -s -p "Re-enter the key password (same as above if you pressed Enter at the prompt): " KEY_PASS; echo

cat > DevLedger/android/keystore.properties <<PROPS
storeFile=../../$KEYSTORE
storePassword=$STORE_PASS
keyAlias=$ALIAS
keyPassword=$KEY_PASS
PROPS

echo
echo "Done. Local release builds now sign automatically:  cd DevLedger/android && ./gradlew :app:assembleRelease"
echo
echo "For the GitHub 'Signed release' workflow add these repository secrets (Settings > Secrets and variables > Actions):"
echo "  KEYSTORE_BASE64   = $(base64 -w0 "$KEYSTORE" | cut -c1-20)...  (run: base64 -w0 $KEYSTORE)"
echo "  KEYSTORE_PASSWORD = the keystore password"
echo "  KEY_ALIAS         = $ALIAS"
echo "  KEY_PASSWORD      = the key password"
echo "  GOOGLE_SERVICES_JSON_B64 = run: base64 -w0 DevLedger/android/app/google-services.json"
echo
echo "BACK UP $KEYSTORE and the passwords somewhere safe (not in Git)."
