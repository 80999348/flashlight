# 手电筒 App 源码

一个轻量 Android 手电筒应用（纯 Java，无第三方依赖），APK 约 17 KB。

## 功能
- **开关按钮**：一键打开/关闭手电筒（Camera2 `setTorchMode`）
- **四种模式**：手电筒 / SOS 摩尔斯求救（··· ——— ···）/ 爆闪警示 / 屏幕白光补光（点击屏幕退出）
- **亮度拉条**：四种模式均生效
  - 闪光灯类：Android 13+ 且硬件支持多档时按档位调节强度（拉条 1~10 自动映射到硬件最大档）
  - 屏幕光：控制屏幕亮度（约 20%~100%）
- **定时自动关闭**：5/15/30/60 分钟，带倒计时显示
- **退出按钮**：退出前自动关闭所有灯光
- 无闪光灯设备自动禁用闪光灯类功能，屏幕光仍可用

## 目录结构
```
app/
├── AndroidManifest.xml          # 权限（CAMERA/FLASHLIGHT）、两个 Activity 声明
├── src/com/example/flashlight/
│   ├── MainActivity.java        # 主界面：模式切换、亮度、定时、SOS/爆闪节拍
│   └── ScreenLightActivity.java # 全屏白光补光页
└── res/
    ├── layout/activity_main.xml
    ├── layout/activity_screen_light.xml
    ├── drawable/torch_on.xml / torch_off.xml
    └── mipmap/ic_launcher.xml
build.sh                         # 一键构建脚本（aapt2 → javac → d8 → zip → 签名）
```

## 环境要求
- JDK 11+
- Android build-tools 34（aapt2、d8、zipalign、apksigner）
- Android platform android-34（android.jar）

## 构建
```bash
# 设置工具路径后执行
BUILD_TOOLS=/path/to/build-tools/34.0.0 \
ANDROID_JAR=/path/to/platforms/android-34/android.jar \
./build.sh
# 产物：out/Flashlight.apk
```

## 安装
```bash
adb install out/Flashlight.apk
```
或将 APK 传到手机直接安装（需允许"安装未知来源应用"）。

## 兼容性
- 最低 Android 6.0（API 23），目标 Android 14（API 34）
- 亮度强度调节需 Android 13+ 且闪光灯硬件支持多档强度

---
小虫工作室 @2025
