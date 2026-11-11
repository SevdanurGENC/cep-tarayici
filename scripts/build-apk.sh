#!/usr/bin/env bash
# Cep Tarayıcı — Android APK build script (no Gradle, no Android Studio needed)
#
# Requirements: bash, Java 17+ (javac not needed), Node.js/npm, Python 3, zip.
# The Android build tools are downloaded once from npm into .tools/ and verified by SHA-256.
#
# Usage:
#   scripts/build-apk.sh
#
# Environment variables (all optional):
#   VERSION_NAME       e.g. 1.3.0          (default: 1.2.0)
#   VERSION_CODE       integer, must grow  (default: 10200)
#   KEYSTORE_FILE      path to your .jks   (default: a throwaway key in .tools/)
#   KEYSTORE_PASSWORD  keystore & key password
#   KEY_ALIAS          key alias           (default: cep)
#   OUT                output APK path     (default: dist/CepTarayici.apk)
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

VERSION_NAME="${VERSION_NAME:-1.2.0}"
VERSION_CODE="${VERSION_CODE:-10200}"
KEY_ALIAS="${KEY_ALIAS:-cep}"
OUT="${OUT:-dist/CepTarayici.apk}"
TOOLS="$ROOT/.tools"
BUILD="$ROOT/build"

# ---------------------------------------------------------------- tools
fetch() { # name@version sha256
  local pkg="$1" sum="$2" dir="$TOOLS/$(echo "$1" | tr '/@' '__')"
  if [ -d "$dir" ]; then echo "$dir"; return; fi
  mkdir -p "$TOOLS/dl"
  local tgz
  tgz="$(cd "$TOOLS/dl" && npm pack "$pkg" --silent 2>/dev/null | tail -n1)"
  echo "$sum  $TOOLS/dl/$tgz" | sha256sum -c --quiet - >&2 || { echo "Checksum mismatch for $pkg" >&2; exit 1; }
  mkdir -p "$dir" && tar xzf "$TOOLS/dl/$tgz" -C "$dir"
  echo "$dir"
}

echo "› Preparing build tools"
MINAPK="$(fetch @drxiaozhi/minapk@0.4.0 d22fccaba9a909cf02f196c91ca1e45ac9cb4b32c6e16cf0493182d26c071417)/package/tools"
AAPTJS="$(fetch aaptjs3@2.0.2 26031bd1acb577edce91675ae92daf63cf85795d4e2399333edeb994dd9197e9)/package/bin/x64/linux"
AAPT2="$AAPTJS/aapt2"; chmod +x "$AAPT2"
ANDROID_JAR="$MINAPK/android.jar"   # Android 14 (API 34)
D8="$MINAPK/d8.jar"
ECJ="$MINAPK/ecj-3.45.0.jar"
APKSIGNER="$MINAPK/apksigner.jar"

# ---------------------------------------------------------------- signing key
if [ -z "${KEYSTORE_FILE:-}" ]; then
  KEYSTORE_FILE="$TOOLS/local-test.jks"
  KEYSTORE_PASSWORD="local-test-key"
  if [ ! -f "$KEYSTORE_FILE" ]; then
    echo "› No KEYSTORE_FILE given: creating a throwaway test key"
    echo "  (an APK signed with it cannot update an app signed with your real key)"
    keytool -genkeypair -keystore "$KEYSTORE_FILE" -alias "$KEY_ALIAS" -keyalg RSA -keysize 2048 \
      -validity 10000 -storepass "$KEYSTORE_PASSWORD" -keypass "$KEYSTORE_PASSWORD" \
      -dname "CN=Cep Tarayici Test" >/dev/null 2>&1
  fi
fi
: "${KEYSTORE_PASSWORD:?Set KEYSTORE_PASSWORD for $KEYSTORE_FILE}"

# ---------------------------------------------------------------- build
echo "› Building Cep Tarayıcı $VERSION_NAME ($VERSION_CODE)"
rm -rf "$BUILD" && mkdir -p "$BUILD/gen" "$BUILD/classes" "$BUILD/dex" "$BUILD/assets/www"

# The Android app shows the same web app; the service worker is only for the web version.
cp -r web/. "$BUILD/assets/www/"
rm -f "$BUILD/assets/www/sw.js"

"$AAPT2" compile --dir android/res -o "$BUILD/res.zip"
"$AAPT2" link -o "$BUILD/base.apk" -I "$ANDROID_JAR" --manifest android/AndroidManifest.xml \
  --min-sdk-version 24 --target-sdk-version 34 \
  --version-code "$VERSION_CODE" --version-name "$VERSION_NAME" \
  -A "$BUILD/assets" --java "$BUILD/gen" "$BUILD/res.zip"

java -jar "$ECJ" -nowarn -1.8 -bootclasspath "$ANDROID_JAR" -encoding UTF-8 \
  -d "$BUILD/classes" $(find android/src "$BUILD/gen" -name '*.java')

java -cp "$D8" com.android.tools.r8.D8 --release --min-api 24 --lib "$ANDROID_JAR" \
  --output "$BUILD/dex" $(find "$BUILD/classes" -name '*.class')

cp "$BUILD/base.apk" "$BUILD/unaligned.apk"
(cd "$BUILD/dex" && zip -q ../unaligned.apk classes.dex)
python3 scripts/align.py "$BUILD/unaligned.apk" "$BUILD/aligned.apk"

mkdir -p "$(dirname "$OUT")"
java -jar "$APKSIGNER" sign --ks "$KEYSTORE_FILE" --ks-key-alias "$KEY_ALIAS" \
  --ks-pass "pass:$KEYSTORE_PASSWORD" --key-pass "pass:$KEYSTORE_PASSWORD" \
  --out "$OUT" "$BUILD/aligned.apk"
rm -f "$OUT.idsig"
java -jar "$APKSIGNER" verify "$OUT"

echo "✓ $OUT ($(du -h "$OUT" | cut -f1))"
