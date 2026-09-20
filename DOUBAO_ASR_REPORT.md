# 报告：○输入法能否使用「豆包输入法」的 API 做语音识别？

> 结论先行：**豆包输入法这个 App 本身没有开放 API**；但它背后的**豆包/火山引擎语音识别**有公开接口，
> 而且**有一条 OpenAI 兼容通道**——我们现有的「联网 API」引擎**改一处格式就能用**；
> 如果想要"边说边出字"的极低延迟，则要另做一套 WebSocket 适配。

---

## 一、我们现在的联网语音是怎么工作的

| 项 | 现状 |
|---|---|
| 引擎类型 | `web_api` —— 任意 **OpenAI 兼容**的 `/v1/audio/transcriptions` |
| 请求 | `multipart/form-data`，字段 `file` = **WAV（PCM16 / 16kHz / 单声道，44 字节头）**，文件名 `audio.wav` |
| 鉴权 | 自填 `baseUrl` + `apiKey`（`Authorization: Bearer`） |
| 代码位置 | `core/speech/SpeechEngineManager.kt` → `pcmToWav()` + multipart 上传 |

---

## 二、豆包 / 火山引擎的两条接入路径

### 路径 A ✅ OpenAI 兼容（走「边缘大模型网关」）——**不用改协议**

- 官方文档明确：**语音识别（ASR）模型的 HTTP 调用方法与 OpenAI `/v1/audio/transcriptions` 接口一致**
  （见火山引擎「云搜索服务 / 边缘大模型网关」文档，同页还有 TTS 对应 `/v1/audio/speech`、对话对应 `/v1/chat/completions`）
- 适用模型：**Doubao-语音识别**系列（含 Seed-ASR 大模型）
- ⚠️ **关键限制**：**只支持 `pcm_s16le` 格式音频**（不接受 WAV/MP3 容器）
- 我们需要做的（很小）：
  1. 设置里选「联网 API」，`baseUrl` 填网关地址，`apiKey` 填网关 Key，模型填 Doubao 语音识别模型
  2. **新增一个「PCM 直传」开关**：勾上后把裸 PCM（我们本来就是 16k/mono/PCM16）直接作为文件部分上传
     （`filename="audio.pcm"`，`Content-Type: audio/pcm`），不再包 WAV 头
     —— 预计 **20 行以内改动**，不动协议、不动依赖
- 优点：零协议成本；缺点：**一次性（非流式）**，要等说完再上传，没有"边说边出字"

### 路径 B ⚠️ 原生流式（豆包/火山 OpenSpeech 大模型流式 ASR）——**需要专门适配**

| 项 | 值 |
|---|---|
| 接口 | `wss://openspeech.bytedance.com/api/v3/sauc/bigmodel`（双向流式）<br>`…/bigmodel_nostream`（流式输入：音频 >15s 或发完负包后返回，**准确率更高**）<br>`…/bigmodel_async`（优化版双向流式，官方推荐，按需返回） |
| 鉴权（旧版控制台） | Header：`X-Api-App-Key`（App ID）、`X-Api-Access-Key`（Access Token）、`X-Api-Resource-Id`（资源 ID） |
| 鉴权（新版控制台） | 只需 `X-Api-Key` |
| Resource ID | `volc.bigasr.sauc.duration`（流式 1.0 小时版）<br>`volc.bigasr.sauc.concurrent`（1.0 并发版）<br>`volc.seedasr.sauc.duration`（**豆包流式 2.0 小时版**）<br>`volc.seedasr.sauc.concurrent`（2.0 并发版） |
| 协议 | **WebSocket 二进制帧**，音频包 **gzip 压缩**后发送 |
| 分包建议 | 单包 **100~200ms**（双向流式 200ms 性能最优），发包间隔同量级 |
| 延迟 | 流式输入模式下平均 5s 音频 **300~400ms** 内返回；全双工模式最低 ~200ms |
| 还有老接口 | `/api/v2/asr` + `Token: Bearer;{TOKEN}` + `Cluster`（传统流式，非大模型） |

**要在 Oime 里支持它，需要新增一个引擎类型**（例如 `doubao_stream`）：

1. WebSocket 客户端（**得加 OkHttp 依赖**——`java.net.http` 需要 API 26+，我们 minSdk 24 ✗）
2. 二进制帧编解码（火山自定义帧头：4 字节协议头 + payload，含 gzip 解压）
3. PCM 分包发送（100~200ms/包）+ 增量结果解析（分句/最终结果）
4. 鉴权头拼装 + 错误码处理 + 断线重连
5. 设置页新增：App ID / Access Token / Resource ID 三个输入框 + 「豆包流式」引擎选项
6. 预计工作量：**约 300~500 行 Kotlin** + 1 个依赖；需你在火山控制台**开通服务（按小时/并发计费）**

**折中方案**：若不想写 WS 适配，也可以自己起一个转译代理（社区已有类似项目：把豆包 2.0 的 WS 二进制协议
转成 OpenAI 兼容 REST），Oime 侧只需填代理地址 —— 但多了一个需要维护的中间服务。

---

## 三、和其它引擎的对比

| 引擎 | 是否需要网络 | 是否流式 | 接入成本 | 备注 |
|---|---|---|---|---|
| SenseVoice（离线） | 否 | 否 | 已完成 | 中/英/日/韩/粤，本地 int8 模型 |
| 流式 zipformer（离线） | 否 | ✅ | 已完成 | 本地边说边出字 |
| **联网 API（OpenAI 兼容）** | 是 | 否 | 已完成 | 可接任意兼容服务商 |
| **豆包 ASR（网关 · OpenAI 兼容）** | 是 | 否 | **+20 行（PCM 直传）** | ✅ **推荐先试这条** |
| **豆包 ASR（原生流式 WS）** | 是 | ✅ | +300~500 行 + 依赖 | 效果与延迟最好，维护成本最高 |

---

## 四、建议

1. **想尽快用上豆包的效果** → 走**路径 A**：我加一个「PCM 直传」开关，你在网关拿 Key 填进设置即可。
   注意：**网关需支持 `audio/transcriptions`**，且**只吃 pcm_s16le**。
2. **想要"边说边出字 + 豆包 2.0 准确率"** → 走**路径 B**，但这是一次独立开发（WS 协议 + 计费开通），
   建议排在功能冻结之后单独做一轮。
3. 现有的两个**离线**引擎（SenseVoice / zipformer）不受影响，仍是最省心、零成本、零隐私风险的选择。

> 附：豆包语音控制台（拿 App ID / Access Token / Resource ID）——火山引擎「豆包语音」控制台；
> 大模型流式 ASR 文档：`volcengine.com/docs/6561/1354869`（协议/鉴权/帧格式/分包建议）
