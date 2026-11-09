#!/usr/bin/env bash
# 手电筒 App 构建脚本（无 Gradle，直接调用 Android 构建工具）
# 依赖：JDK 11+，Android build-tools 34（含 aapt2/d8/zipalign/apksigner），android-34 平台 android.jar
set -e

BT="${BUILD_TOOLS:-$HOME/android/build-tools/34.0.0}"     # build-tools 目录（含 aapt2、d8 等）
AJ="${ANDROID_JAR:-$HOME/android/platforms/android-34/android.jar}"  # android.jar
SRC="$(cd "$(dirname "$0")" && pwd)/app"
OUT="$(dirname "$0")/out"
mkdir -p "$OUT/classes"

echo "[1/5] 编译资源..."
"$BT/aapt2" compile --dir "$SRC/res" -o "$OUT/res.zip"
"$BT/aapt2" link -o "$OUT/base.apk" -I "$AJ" --manifest "$SRC/AndroidManifest.xml" --java "$OUT/gen" "$OUT/res.zip"

echo "[2/5] 编译 Java..."
javac --release 11 -cp "$AJ:$OUT/gen" -d "$OUT/classes" $(find "$SRC" "$OUT/gen" -name "*.java")

echo "[3/5] 生成 dex..."
"$BT/d8" --release --lib "$AJ" --min-api 23 --output "$OUT" $(find "$OUT/classes" -name "*.class")

echo "[4/5] 打包 APK..."
cp "$OUT/base.apk" "$OUT/unsigned.apk"
(cd "$OUT" && zip -qj unsigned.apk classes.dex)
"$BT/zipalign" -f 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"

echo "[5/5] 签名（如无 keystore 会自动生成 debug.keystore）..."
KS="$OUT/debug.keystore"
if [ ! -f "$KS" ]; then
  keytool -genkeypair -keystore "$KS" -alias androiddebugkey -storepass android -keypass android \
    -dname "CN=Android Debug,O=Android,C=US" -keyalg RSA -keysize 2048 -validity 10000
fi
"$BT/apksigner" sign --ks "$KS" --ks-pass pass:android --key-pass pass:android --out "$OUT/Flashlight.apk" "$OUT/aligned.apk"

echo "完成：$OUT/Flashlight.apk"
