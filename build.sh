#!/usr/bin/env bash
# Gradle'sız derleme: sadece Android SDK build-tools + JDK kullanır, internetten bir şey indirmez.
# Ortam değişkenleri (hepsi isteğe bağlı):
#   ANDROID_HOME   Android SDK yolu (varsayılan: ~/Library/Android/sdk veya ~/Android/Sdk)
#   BUILD_TOOLS    build-tools sürümü (varsayılan: 36.0.0)
#   KEYSTORE       imza anahtarı (varsayılan: ~/.android/debug.keystore; yoksa oluşturulur)
set -euo pipefail
cd "$(dirname "$0")"

if [[ -z "${ANDROID_HOME:-}" ]]; then
    for d in "$HOME/Library/Android/sdk" "$HOME/Android/Sdk"; do
        [[ -d "$d" ]] && ANDROID_HOME="$d" && break
    done
fi
: "${ANDROID_HOME:?Android SDK bulunamadı, ANDROID_HOME ayarla}"
BT="$ANDROID_HOME/build-tools/${BUILD_TOOLS:-36.0.0}"
ANDROID_JAR="$ANDROID_HOME/platforms/android-36/android.jar"

if [[ -z "${JAVA_HOME:-}" && -x /usr/libexec/java_home ]]; then
    JAVA_HOME="$(/usr/libexec/java_home)"
fi
if [[ -n "${JAVA_HOME:-}" ]]; then
    export JAVA_HOME PATH="$JAVA_HOME/bin:$PATH"
fi

KEYSTORE="${KEYSTORE:-$HOME/.android/debug.keystore}"
if [[ ! -f "$KEYSTORE" ]]; then
    mkdir -p "$(dirname "$KEYSTORE")"
    keytool -genkeypair -keystore "$KEYSTORE" -storepass android -keypass android \
        -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 \
        -dname "CN=Android Debug,O=Android,C=US" >/dev/null
fi

rm -rf build
mkdir -p build/classes build/dex build/gen

"$BT/aapt2" compile --dir res -o build/res.zip
"$BT/aapt2" link -o build/unsigned.apk -I "$ANDROID_JAR" --manifest AndroidManifest.xml \
    -R build/res.zip --java build/gen --auto-add-overlay
javac --release 17 -Xlint:-options -classpath "$ANDROID_JAR" -d build/classes \
    $(find src build/gen -name '*.java')
"$BT/d8" --release --min-api 34 --lib "$ANDROID_JAR" --output build/dex $(find build/classes -name '*.class')
(cd build/dex && zip -q -j ../unsigned.apk classes.dex)
"$BT/zipalign" -f -p 4 build/unsigned.apk build/aligned.apk
"$BT/apksigner" sign --ks "$KEYSTORE" --ks-pass pass:android --key-pass pass:android \
    --ks-key-alias androiddebugkey --out build/devmode-helper.apk build/aligned.apk
"$BT/apksigner" verify build/devmode-helper.apk
echo "OK: build/devmode-helper.apk"
