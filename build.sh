#!/data/data/com.termux/files/usr/bin/bash
# Compila e assina o APK do Pong Causalidade (Termux, sem Gradle, com aapt2)
set -e
BASE=$HOME/pongcausa
SRC=$BASE/src
BUILD=$BASE/build
PLATFORM=$HOME/android-sdk/platforms/android-34/android.jar
OUT=$BASE/apk/pong-causalidade.apk

rm -rf "$BUILD"
mkdir -p "$BUILD/classes" "$BUILD/apk"

echo "[1/6] aapt2 compile"
aapt2 compile --dir "$BASE/res" -o "$BUILD/res.zip"

echo "[2/6] aapt2 link -> base.apk + R.java"
aapt2 link -o "$BUILD/apk/base.apk" -I "$PLATFORM" --manifest "$SRC/AndroidManifest.xml" \
  --java "$SRC" --auto-add-overlay "$BUILD/res.zip"

echo "[3/6] javac"
find "$SRC/com/pongcausa" -name "*.java" > "$BUILD/sources.txt"
find "$SRC" -maxdepth 1 -name "R.java" >> "$BUILD/sources.txt" || true
javac --release 8 -classpath "$PLATFORM" -d "$BUILD/classes" \
  $(cat "$BUILD/sources.txt" | tr '\n' ' ')

echo "[4/6] d8 -> classes.dex"
mkdir -p "$BUILD/dexroot"
find "$BUILD/classes" -name "*.class" | tr '\n' ' ' > "$BUILD/classlist.txt"
d8 --release --lib "$PLATFORM" --min-api 24 --output "$BUILD/dexroot" $(cat "$BUILD/classlist.txt")
cd "$BUILD/dexroot" && zip -q -r "$BUILD/apk/base.apk" classes.dex && cd "$HOME"

echo "[5/6] zipalign"
mkdir -p "$BASE/apk"
zipalign -f 4 "$BUILD/apk/base.apk" "$OUT"

echo "[6/6] apksigner"
KS=$BASE/pong.keystore
if [ ! -f "$KS" ]; then
  keytool -genkeypair -v -keystore "$KS" -alias pong -keyalg RSA -keysize 2048 -validity 10000 \
    -storepass pong123 -keypass pong123 -dname "CN=Pong Causalidade, OU=Experimento, O=USP, C=BR"
fi
apksigner sign --ks "$KS" --ks-pass pass:pong123 --key-pass pass:pong123 "$OUT"
apksigner verify "$OUT" && echo "APK assinado: $OUT ($(du -h $OUT | cut -f1))"
