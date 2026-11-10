[English](README.md) | 中文

---

<p align="center">
  <img src="docs/assets/icon.svg" width="120" alt="Piano Muse 图标" />
</p>

<h1 align="center">Piano Muse</h1>

<p align="center">
  <strong>缪斯激发灵感，教练指引练习</strong><br/>
  Android 钢琴陪练 · AI Agent Harness · 蓝牙 MIDI · 瀑布跟练 · 分层练习
</p>

<p align="center">
  <em>钢琴陪练 · 钢琴教学助手 · AI 钢琴教练 · Harness Engineering · Multi-agent workflow</em>
</p>

<p align="center">
  <a href="https://github.com/Jianjian24">@Jianjian24</a> ·
  <a href="https://github.com/Jianjian24/piano-muse">github.com/Jianjian24/piano-muse</a>
</p>

---

**Piano Muse（缪斯）** 是一款长期维护的 **开源** Android **钢琴陪练**应用：连接电钢琴采集演奏，用 **AI Agent Harness** 做陪练对话、分层练习与瀑布跟练。适合搜索 **钢琴教学**、**钢琴陪练**、**AI 钢琴** 的学习者，以及关注 **大模型 Harness / Agent 工程化** 的开发者。

### 产品气质

- **Muse（缪斯）**：从真实弹奏中提炼上下文，激发练什么、怎么练的灵感。  
- **Coach（教练）**：可执行的引导—对话点评、跟练结果反馈、整曲 **AI 分层计划**（熟悉 → 攻克 → 串联）。

### 功能概要

| 页签 | 说明 |
|------|------|
| **Studio** | 蓝牙 / 系统 MIDI 连接电钢琴 |
| **Muse** | 基于约 **60 秒演奏摘要** 的大模型陪练对话；停手 2 秒提示 |
| **Play** | 瀑布卷帘（左右手分色）、可选指法提示；导入 `.mid` / `.mxl`；SoundFont 试听；跟练齐键并按住时值 |
| **设置** | API Key、Base URL、模型、水平（仅存本机） |

### Agent 与 Harness

**Agent = 大模型 + Harness**，不是 MIDI 套壳聊天。模型的「人设 / 身份」在源码里拼接（`CoachAgent`、`PiecePracticeAnalyzer` 的 system prompt），没有单独的 SOUL 文件。

**核心分工：** Model 负责理解与规划；Harness 负责感知、工具、校验与重试（练习计划 JSON 失败时 **带错误原因重试，最多 3 次**）。

**多 Agent 协作** = 专职角色 + 编排层，不是多机器人闲聊：

| 角色 | 代码 | 输入 | 输出 |
|------|------|------|------|
| **Muse Coach** | `CoachAgent` | 最近演奏摘要 JSON | 流式陪练回复 |
| **Practice Analyst** | `PiecePracticeAnalyzer` | 整曲编码 | 分层 `PracticePlan` |
| **Harness 工具** | `drill` / `midi` 等 | 谱面、MIDI | 跟练、瀑布、指法提示（不走 LLM） |

由 `MainViewModel` 串起数据契约，即本项目的 **multi-agent workflow**。

**Harness 层次：** 感知 → 大模型 → 校验 → 修复循环 → 工具执行 → 反馈回 Coach。  
**隐私：** 上传 **摘要**，不上传 raw 高频 MIDI。  
**不走 LLM 的 Skill：** 连接琴、导入曲谱、瀑布跟练、规则指法。

### 大模型 API（OpenAI 兼容）

陪练对话与 **AI 练习计划** 均请求 `{Base URL}/chat/completions`，协议与 **OpenAI / DeepSeek / 常见中转站** 一致。在 **Muse → 设置** 填写 **API Key、Base URL、模型** 即可切换厂商。

| 示例 | Base URL | 模型示例 |
|------|-----------|----------|
| 内置默认 | `https://api.deepseek.com` | `deepseek-chat` |
| OpenAI | `https://api.openai.com/v1` | `gpt-4o-mini` 等 |
| 中转 / 聚合 | 服务商文档给出的根地址 | 对应模型名 |

保存后点 **测试连接** 验证。需支持 Chat Completions JSON 返回（本应用走非流式 HTTP）。

**请勿** 将 API Key 提交到 Git；公开发布 APK 建议改用服务端代理。

### 开发与构建

1. Android Studio 打开工程 → Gradle 同步  
2. **真机** USB 调试（MIDI / 蓝牙需真机）  
3. 可选：复制 `secrets.properties.example` 为 `secrets.properties`（勿提交密钥）

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

曲谱支持 `.mid` / `.mxl`（MusicXML）。跟练判定时值与齐键，**不判定指法对错**（指法仅为提示）。

### 关键词（便于检索）

`piano-muse` · 钢琴陪练 · 钢琴教学 · AI 钢琴教练 · 钢琴练习助手 · MIDI · MusicXML · waterfall piano · LLM harness · agent harness · OpenAI compatible · Android

## 许可证

[MIT](LICENSE)
