# 钢琴教练 — 蓝牙 MIDI 音符验证

技术验证版：通过标准 BLE MIDI 接收电子琴音符，并在屏幕上实时显示。

## 开发环境

本机已通过 winget 安装 **Eclipse Temurin JDK 17**；Android SDK 仍需 Android Studio。

1. 安装 [Android Studio](https://developer.android.com/studio)（或 `winget install Google.AndroidStudio`），自带 Android SDK。
2. 用 Android Studio 打开本目录 `e:\code\music`，等待 Gradle 同步完成。
3. 手机开启**开发者选项**与 **USB 调试**，用数据线连接电脑。

**Gradle Sync 网络问题**：

- `dl.google.com` 超时：工程已用阿里云 Maven 镜像，点 **Sync Project with Gradle Files** 重试。
- `github.com` / `gradle-8.9-src.zip` 超时：这是 IDE 在下载 **Gradle 源码**（Kotlin 构建脚本提示用）。工程已关闭 `downloadSources`；若仍卡住，在 **Settings → Build, Execution, Deployment → Gradle** 取消 **Download Gradle sources**，再 Sync。需要代理时可只对 GitHub 开 VPN 一次。
- Gradle 发行包已改走腾讯云镜像（`gradle-wrapper.properties`）。

命令行编译（需已设置 `ANDROID_HOME`）：

```bash
.\gradlew.bat assembleDebug
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

## 真机验证清单

1. 允许蓝牙权限，打开蓝牙，扫描并连接电子琴。
2. 慢弹单音：音名、键位高亮与松手一致。
3. 和弦：多键同时亮，单独松键正确熄灭。
4. 轻/重弹：力度数字变化。
5. 停手数秒：事件列表无 Active Sensing 刷屏。

琴端请开启 **Bluetooth MIDI**；若扫描为空，先在系统蓝牙里配对后再扫描。

### 不依赖虫虫钢琴直连（特伦斯）

官方说明要求：手机**断开与钢琴的蓝牙音频/媒体连接**（只保留配对），**重启电钢琴**后再连 MIDI。本 App 会按标准 `openBluetoothDevice` + BLE GATT 预唤醒连接。若仍失败，多为音频与 MIDI 抢占或型号限制，可临时用虫虫建立系统 MIDI 或改用 **USB OTG 有线 MIDI**。
