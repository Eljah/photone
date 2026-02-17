#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
KEYSTORE_PATH="$REPO_ROOT/keystore/release-key.jks"
KEY_ALIAS="${KEY_ALIAS:-release}"
KEYSTORE_PASSWORD="${KEYSTORE_PASSWORD:-Tatarstan1920}"
KEY_PASSWORD="${KEY_PASSWORD:-Tatarstan1920}"
ANDROID_PLATFORM="${ANDROID_PLATFORM:-35}"
APP_VERSION_CODE="${APP_VERSION_CODE:-13}"
APP_VERSION_NAME="${APP_VERSION_NAME:-2.2}"
BUILD_TOOLS_AAPT2="${BUILD_TOOLS_AAPT2:-35.0.1}"

"$REPO_ROOT/scripts/prepare-keystore.sh"

if [[ -z "${ANDROID_HOME:-}" && -n "${ANDROID_SDK_ROOT:-}" ]]; then
  export ANDROID_HOME="$ANDROID_SDK_ROOT"
fi

if [[ -z "${ANDROID_HOME:-}" ]]; then
  echo "ANDROID_HOME is not set (or ANDROID_SDK_ROOT is missing)." >&2
  exit 1
fi

AAPT2="$ANDROID_HOME/build-tools/$BUILD_TOOLS_AAPT2/aapt2"
if [[ ! -x "$AAPT2" ]]; then
  echo "aapt2 not found at $AAPT2" >&2
  exit 1
fi

JAVA_VERSION_RAW="$(java -version 2>&1 | head -n1)"
JAVA_MAJOR="$(echo "$JAVA_VERSION_RAW" | sed -E 's/.*version "([0-9]+)(\.[0-9]+)?.*/\1/')"
if [[ "$JAVA_MAJOR" == "1" ]]; then
  JAVA_MAJOR="$(echo "$JAVA_VERSION_RAW" | sed -E 's/.*version "1\.([0-9]+).*/\1/')"
fi

if [[ -z "${BUNDLETOOL_VERSION:-}" ]]; then
  if [[ "$JAVA_MAJOR" -ge 11 ]]; then
    BUNDLETOOL_VERSION="1.16.0"
  else
    BUNDLETOOL_VERSION="1.15.6"
  fi
fi

echo "Using Java major=$JAVA_MAJOR and bundletool=$BUNDLETOOL_VERSION"

BUNDLETOOL_JAR="$REPO_ROOT/tools/bundletool-all-${BUNDLETOOL_VERSION}.jar"
BUNDLE_CONFIG="$REPO_ROOT/scripts/bundle-config.json"
if [[ ! -f "$BUNDLETOOL_JAR" ]]; then
  mkdir -p "$REPO_ROOT/tools"
  curl -sSL -o "$BUNDLETOOL_JAR" \
    "https://github.com/google/bundletool/releases/download/${BUNDLETOOL_VERSION}/bundletool-all-${BUNDLETOOL_VERSION}.jar"
fi

WORK_DIR=$(mktemp -d)
MODULE_ZIP="$WORK_DIR/base.zip"
BASE_PROTO_APK="$WORK_DIR/base_proto.apk"

mapfile -t APK_CANDIDATES < <(find "$REPO_ROOT/target" -maxdepth 1 -type f -name '*.apk' \
  ! -name 'universal.apk' ! -name '*unaligned*.apk')

if [[ ${#APK_CANDIDATES[@]} -eq 0 ]]; then
  echo "No base APK found in $REPO_ROOT/target. Build APK first (mvn package)." >&2
  exit 1
fi

WORKING_APK="$(find "$REPO_ROOT/target" -maxdepth 1 -type f -name '*.apk' \
  ! -name 'universal.apk' ! -name '*unaligned*.apk' -printf '%T@ %p\n' \
  | sort -n | tail -1 | cut -d' ' -f2-)"

if [[ -z "$WORKING_APK" || ! -f "$WORKING_APK" ]]; then
  echo "Failed to pick base APK from $REPO_ROOT/target." >&2
  exit 1
fi

echo "Using base APK for AAB conversion: $WORKING_APK"

echo "Building AAB with versionCode=$APP_VERSION_CODE versionName=$APP_VERSION_NAME targetSdk=$ANDROID_PLATFORM"

# Keep resource IDs stable by converting an already-built APK to proto format.
"$AAPT2" convert --output-format proto -o "$BASE_PROTO_APK" "$WORKING_APK"

unzip -q "$BASE_PROTO_APK" -d "$WORK_DIR/apk"
mkdir -p "$WORK_DIR/module/manifest" "$WORK_DIR/module/dex" "$WORK_DIR/module/res"

if [[ ! -f "$WORK_DIR/apk/AndroidManifest.xml" || ! -f "$WORK_DIR/apk/resources.pb" ]]; then
  echo "Converted proto APK is missing AndroidManifest.xml or resources.pb" >&2
  exit 1
fi

cp "$WORK_DIR/apk/AndroidManifest.xml" "$WORK_DIR/module/manifest/AndroidManifest.xml"
cp "$WORK_DIR/apk/resources.pb" "$WORK_DIR/module/resources.pb"

if [[ -d "$WORK_DIR/apk/res" ]]; then
  cp -R "$WORK_DIR/apk/res/"* "$WORK_DIR/module/res/"
fi

if compgen -G "$WORK_DIR/apk/*.dex" > /dev/null; then
  cp "$WORK_DIR/apk"/*.dex "$WORK_DIR/module/dex/"
elif compgen -G "$REPO_ROOT/target/classes*.dex" > /dev/null; then
  cp "$REPO_ROOT/target"/classes*.dex "$WORK_DIR/module/dex/"
else
  echo "No dex files found in converted APK or target/classes*.dex" >&2
  exit 1
fi

(
  cd "$WORK_DIR/module"
  zip -qr "$MODULE_ZIP" .
)

mkdir -p "$REPO_ROOT/target"
AAB_PATH="$REPO_ROOT/target/app-release-v${APP_VERSION_CODE}.aab"
LATEST_AAB_PATH="$REPO_ROOT/target/app-release.aab"
java -jar "$BUNDLETOOL_JAR" build-bundle \
  --modules="$MODULE_ZIP" \
  --config="$BUNDLE_CONFIG" \
  --output="$AAB_PATH" \
  --overwrite

cp "$AAB_PATH" "$LATEST_AAB_PATH"

APKS_PATH="$WORK_DIR/app.apks"
UNIVERSAL_APK="$WORK_DIR/universal.apk"

java -jar "$BUNDLETOOL_JAR" build-apks \
  --bundle="$AAB_PATH" \
  --output="$APKS_PATH" \
  --mode=universal \
  --overwrite

if unzip -Z1 "$APKS_PATH" | grep -q '^universal\.apk$'; then
  unzip -q -o "$APKS_PATH" universal.apk -d "$WORK_DIR"

  if unzip -l "$UNIVERSAL_APK" | grep -q 'AndroidManifest.xml'; then
    echo "AAB smoke-check: universal.apk contains AndroidManifest.xml"
  else
    echo "AAB smoke-check warning: universal.apk does not list AndroidManifest.xml (continuing)" >&2
  fi

  if unzip -l "$UNIVERSAL_APK" | grep -q 'res/.*/abc_vector_test.xml'; then
    echo "AAB smoke-check: found abc_vector_test.xml in generated universal.apk"
  else
    echo "AAB smoke-check warning: abc_vector_test.xml not found in generated universal.apk (continuing)" >&2
  fi
else
  echo "AAB smoke-check warning: universal.apk entry not found in app.apks (continuing)" >&2
fi

jarsigner \
  -keystore "$KEYSTORE_PATH" \
  -storepass "$KEYSTORE_PASSWORD" \
  -keypass "$KEY_PASSWORD" \
  "$AAB_PATH" "$KEY_ALIAS"

if [[ -z "${UNIVERSAL_ARTIFACT_BUNDLETOOL_VERSION:-}" ]]; then
  if [[ "$JAVA_MAJOR" -ge 11 ]]; then
    UNIVERSAL_ARTIFACT_BUNDLETOOL_VERSION="1.18.3"
  else
    UNIVERSAL_ARTIFACT_BUNDLETOOL_VERSION="$BUNDLETOOL_VERSION"
  fi
fi

UNIVERSAL_ARTIFACT_BUNDLETOOL_JAR="$REPO_ROOT/tools/bundletool-all-${UNIVERSAL_ARTIFACT_BUNDLETOOL_VERSION}.jar"
if [[ ! -f "$UNIVERSAL_ARTIFACT_BUNDLETOOL_JAR" ]]; then
  mkdir -p "$REPO_ROOT/tools"
  curl -sSL -o "$UNIVERSAL_ARTIFACT_BUNDLETOOL_JAR" \
    "https://github.com/google/bundletool/releases/download/${UNIVERSAL_ARTIFACT_BUNDLETOOL_VERSION}/bundletool-all-${UNIVERSAL_ARTIFACT_BUNDLETOOL_VERSION}.jar"
fi

echo "Using universal APK bundletool=$UNIVERSAL_ARTIFACT_BUNDLETOOL_VERSION"

UNIVERSAL_APKS_PATH="$REPO_ROOT/target/app_universal.apks"
UNIVERSAL_ARTIFACT_APK_PATH="$REPO_ROOT/target/universal.apk"
java -jar "$UNIVERSAL_ARTIFACT_BUNDLETOOL_JAR" build-apks \
  --bundle="$AAB_PATH" \
  --output="$UNIVERSAL_APKS_PATH" \
  --mode=universal \
  --overwrite

unzip -q -o "$UNIVERSAL_APKS_PATH" universal.apk -d "$REPO_ROOT/target"
rm -f "$UNIVERSAL_APKS_PATH"

if [[ ! -f "$UNIVERSAL_ARTIFACT_APK_PATH" ]]; then
  echo "Failed to produce universal.apk in target directory" >&2
  exit 1
fi

rm -rf "$WORK_DIR"
