# Piano Muse

Agent 版钢琴陪练：蓝牙 MIDI 接收演奏，DeepSeek 对话教练 + 轻量跟练。

## 功能

- **Studio**：连接蓝牙 / 系统 MIDI 电子琴
- **Muse**：根据最近 60 秒弹奏摘要对话；支持「分析刚才这一段」、停手 2s 提示
- **Play**：瀑布卷帘（左右手分色 + 规则指法 1–5 可开关）；导入 `.mid` 保存并自动 AI 分层练习；谱面试听用 SoundFont 钢琴采样（可调 BPM）；跟练需齐键并按住足够时值（不判定指法对错）
- **设置**：DeepSeek API Key、Base URL、模型、水平自报（仅存本机 DataStore）

## 开发环境

1. 安装 Android Studio，打开本仓库
2. Gradle 同步
3. 真机 USB 调试运行（MIDI 需真机）

## DeepSeek 配置

1. 在 [DeepSeek 开放平台](https://platform.deepseek.com/) 申请 API Key
2. App → **Muse** → **设置** → 填写 Key，默认 `https://api.deepseek.com` + `deepseek-chat`
3. 点击 **测试连接** 验证网络

**安全**：不要把 `secrets.properties` 或含 Key 的文件提交 Git；公开分发 APK 前请改用自有后端代理 Key。

## MIDI 连接

1. 蓝牙琴：手机系统设置中与琴配对，回到 **Studio** → **刷新** → 选择列表中的设备；或选「系统 MIDI 在线」条目
2. USB：OTG 连接支持 USB MIDI 的琴，刷新后出现对应设备
3. 若列表为空：确认琴已开机、蓝牙权限已授予，再点刷新

## 构建

```bash
.\gradlew.bat assembleDebug
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

## 架构（简）

| 包 | 作用 |
|----|------|
| `midi` | BLE / 系统 MIDI 连接 |
| `session` | 音符环形缓冲、停手检测 |
| `coach` | 演奏摘要 JSON、DeepSeek、Agent 提示词 |
| `drill` | 跟练引擎、曲库、指法 |
| `settings` | DataStore 配置 |

播放数据以 **摘要 JSON** 上传模型，非原始全量 MIDI 日志。
