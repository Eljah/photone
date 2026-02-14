#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
KEYSTORE_PATH="$REPO_ROOT/keystore/release-key.jks"
KEY_ALIAS="${KEY_ALIAS:-release}"
KEYSTORE_PASSWORD="${KEYSTORE_PASSWORD:-Tatarstan1920}"
KEY_PASSWORD="${KEY_PASSWORD:-Tatarstan1920}"
ANDROID_PLATFORM="${ANDROID_PLATFORM:-35}"
APP_VERSION_CODE="${APP_VERSION_CODE:-9}"
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
COMPILED_RES_DIR="$WORK_DIR/compiled-res"
AAR_DEPS_DIR="$WORK_DIR/aar-deps"
AAR_UNPACK_DIR="$WORK_DIR/aar-unpacked"
BASE_APK="$WORK_DIR/base.apk"
MODULE_ZIP="$WORK_DIR/base.zip"
MANIFEST_TEMPLATE="$REPO_ROOT/src/main/AndroidManifest.xml"
MANIFEST_PATH="$WORK_DIR/AndroidManifest.xml"

cp "$MANIFEST_TEMPLATE" "$MANIFEST_PATH"
sed -i -E 's/android:versionCode="[0-9]+"/android:versionCode="'"$APP_VERSION_CODE"'"/' "$MANIFEST_PATH"
sed -i -E 's/android:versionName="[^"]+"/android:versionName="'"$APP_VERSION_NAME"'"/' "$MANIFEST_PATH"

echo "Building AAB with versionCode=$APP_VERSION_CODE versionName=$APP_VERSION_NAME targetSdk=$ANDROID_PLATFORM"

mkdir -p "$COMPILED_RES_DIR"
RES_DIRS=("$REPO_ROOT/src/main/res")
if [[ -d "$REPO_ROOT/target/unpacked-libs" ]]; then
  while IFS= read -r -d '' lib_res; do
    RES_DIRS+=("$lib_res")
  done < <(find "$REPO_ROOT/target/unpacked-libs" -type d -name res -print0)
fi

mkdir -p "$AAR_DEPS_DIR" "$AAR_UNPACK_DIR"
mvn -q -f "$REPO_ROOT/pom.xml" dependency:copy-dependencies \
  -DincludeTypes=aar \
  -DoutputDirectory="$AAR_DEPS_DIR" \
  >/dev/null

while IFS= read -r -d '' aar_file; do
  aar_name="$(basename "$aar_file" .aar)"
  unpack_dir="$AAR_UNPACK_DIR/$aar_name"
  mkdir -p "$unpack_dir"
  unzip -q -o "$aar_file" -d "$unpack_dir"
  if [[ -d "$unpack_dir/res" ]]; then
    RES_DIRS+=("$unpack_dir/res")
  fi
done < <(find "$AAR_DEPS_DIR" -type f -name '*.aar' -print0)

COMPILED_ARGS=()
index=0
for res_dir in "${RES_DIRS[@]}"; do
  if [[ -d "$res_dir" ]]; then
    compiled_zip="$COMPILED_RES_DIR/res-$index.zip"
    "$AAPT2" compile --dir "$res_dir" -o "$compiled_zip"
    COMPILED_ARGS+=("-R" "$compiled_zip")
    index=$((index + 1))
  fi
done

"$AAPT2" link --proto-format \
  -I "$ANDROID_HOME/platforms/android-$ANDROID_PLATFORM/android.jar" \
  --manifest "$MANIFEST_PATH" \
  --auto-add-overlay \
  -o "$BASE_APK" \
  "${COMPILED_ARGS[@]}"

unzip -q "$BASE_APK" -d "$WORK_DIR/apk"
mkdir -p "$WORK_DIR/module/manifest" "$WORK_DIR/module/dex" "$WORK_DIR/module/res"
cp "$WORK_DIR/apk/AndroidManifest.xml" "$WORK_DIR/module/manifest/AndroidManifest.xml"
cp "$WORK_DIR/apk/resources.pb" "$WORK_DIR/module/resources.pb"
if [[ -d "$WORK_DIR/apk/res" ]]; then
  cp -R "$WORK_DIR/apk/res/"* "$WORK_DIR/module/res/"
fi
if compgen -G "$REPO_ROOT/target/classes*.dex" > /dev/null; then
  cp "$REPO_ROOT/target"/classes*.dex "$WORK_DIR/module/dex/"
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

UNIVERSAL_ARTIFACT_BUNDLETOOL_VERSION="${UNIVERSAL_ARTIFACT_BUNDLETOOL_VERSION:-1.18.3}"
UNIVERSAL_ARTIFACT_BUNDLETOOL_JAR="$REPO_ROOT/tools/bundletool-all-${UNIVERSAL_ARTIFACT_BUNDLETOOL_VERSION}.jar"
if [[ ! -f "$UNIVERSAL_ARTIFACT_BUNDLETOOL_JAR" ]]; then
  mkdir -p "$REPO_ROOT/tools"
  curl -sSL -o "$UNIVERSAL_ARTIFACT_BUNDLETOOL_JAR" \
    "https://github.com/google/bundletool/releases/download/${UNIVERSAL_ARTIFACT_BUNDLETOOL_VERSION}/bundletool-all-${UNIVERSAL_ARTIFACT_BUNDLETOOL_VERSION}.jar"
fi

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
