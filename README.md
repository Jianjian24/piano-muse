# 钢琴教练 (Piano Coach)

Agent 版钢琴陪练：蓝牙 MIDI 接收演奏，DeepSeek 对话教练 + 轻量音阶跟练。

## 功能

- **练习**：连接 TERENCE 等电子琴（支持虫虫钢琴建立系统 MIDI 后接入）
- **教练**：根据最近 60 秒弹奏摘要对话；支持「分析刚才这一段」、停手 2s 提示
- **跟练**：瀑布卷帘（左右手分色 + 规则指法 1–5 可开关）；导入 `.mid` 保存并自动 AI 分层练习；谱面试听用 SoundFont 钢琴采样（可调 BPM）；跟练需齐键并按住足够时值（不判定指法对错）
- **设置**：DeepSeek API Key、Base URL、模型、水平自报（仅存本机 DataStore）

## 开发环境

1. 安装 Android Studio，打开 `e:\code\music`
2. Gradle 同步（已配置阿里云镜像，见历史说明）
3. 真机 USB 调试运行（MIDI 需真机）

## DeepSeek 配置

1. 在 [DeepSeek 开放平台](https://platform.deepseek.com/) 申请 API Key
2. App → **教练** Tab → 右上角 **设置** → 填写 Key，默认 `https://api.deepseek.com` + `deepseek-chat`
3. 点击 **测试连接** 验证网络

**安全**：不要把 `secrets.properties` 或含 Key 的文件提交 Git；公开分发 APK 前请改用自有后端代理 Key。

## 特伦斯 + 虫虫钢琴

1. 虫虫连接 `TERENCE PIANO`
2. 打开本 App → **练习** → **刷新** → 选「系统 MIDI 在线」
3. 断开后若列表空：虫虫重连琴 → 回本 App 刷新

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
| `drill` | 音阶跟练状态机 |
| `settings` | DataStore 配置 |

播放数据以 **摘要 JSON** 上传模型，非原始全量 MIDI 日志。
