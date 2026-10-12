# 关于 live-translator (PC 桌面端) 功能体验与架构演进的建议方案

## 一、 背景与目标

本方案基于移动端实时翻译项目（Android 平台）在实际运行中打磨沉淀的成熟机制，针对 PC 桌面端开源项目 [`live-translator`](https://github.com/xzm9053-alt/live-translator)（Python 3.10+ / PyQt6 / SenseVoice / WASAPI）提出系统性的升级建议。

目标是：**全面补齐核心使用场景（引入屏幕选区 OCR 截屏翻译）、根治断句碎片化与等待黑洞（引入边说边出草稿流）、扩展任意大模型生态、优化游戏与观影交互（鼠标穿透），使 PC 端体验跃升为全能生肉翻译神器。**

---

## 二、 核心升级模块详解

### 1. 识别体验：从“等说完才出”跃升为“边说边出”（LocalAgreement-2）

#### 当前痛点
目前采用“音频捕获 -> VAD 静音切分 -> 送入 ASR -> 送入大模型 -> 显示”的单一阻塞流。在用户连续说话或视频长句期间，字幕区域处于静止黑屏状态，具有明显的“等待黑洞”。

#### 建议机制
1. **未断句时的流式前缀草稿（InputPartial）**：
   - 当 VAD 判定有人声但未到切分点时，音频每累积 300~500ms 进行一次局部重解码。
   - 提取连续两次解码结果的最长公共前缀（LCP）作为**稳定草稿**，以半透明/淡色形式实时上屏。
   - **只增不减原则**：若模型后续因语境改口导致前缀缩短，草稿保持冻结，绝不倒退吞字。
2. **定稿抢占机制（Priority Preemption）**：
   - 一旦 VAD 判定静音分句（Final），立即废弃排队中的草稿任务，**定稿直接插队执行**，消除 200~400ms 队列等待。
3. **VAD 停顿阈值调优**：
   - `vad_min_silence_duration` 从 `0.2s` 调整为 **`0.4s`**（避免日常口语换气将完整句子拦腰切断）。
   - `vad_max_speech_duration` 从 `2.0s` 放宽至 **`4.5s`**（容纳完整复合句，保障大模型翻译的语境完整性）。

---

### 2. 文本排版：CJK 智能拼接引擎（根除多余空格）

#### 当前痛点
多段识别或拼接时按西文习惯无条件在非空白字符间插入空格，导致中文出现“今天 我 去 超市”这种违和排版。

#### 建议机制
实现 `processing/cjk_spacers.py`：
- **CJK 与 CJK**（汉字、假名、谚文、全角标点）：**严禁插入空格**，紧密无缝连接。
- **西文与西文**：正常插入单个半角空格分隔单词。
- **西文与 CJK**：标准排版插入单个空格（如 `iPhone 客户端`）。

---

### 3. 全新核心场景：桌面屏幕区域实时 OCR 截屏翻译

#### 目标场景
跳出纯音频限制，支持**生肉游戏汉化、生肉漫画、无内嵌字幕的直播与海外软件界面**翻译。

#### 建议架构
```
[快捷键 Ctrl+Shift+S] 
   └──► 呼出全屏半透明选框遮罩 (RegionSelectorDialog)
          └──► 选定区域矩形 (x, y, w, h)
                 ├──► 屏幕保留淡蓝色虚线半透明外框 (全穿透、不抢焦点)
                 └──► 后台守护线程 1~2fps (mss 高速抓屏 + rapidocr-onnxruntime 本地离线识别)
                        └──► 画面文字变动哈希比对 -> 触发流式翻译 -> 浮窗上屏
```
- **低资源消耗**：`mss` 抓屏仅 ~5ms，`rapidocr` 本地推理仅 ~15ms，文字未变化时不触发翻译，CPU 占用基本为 0%。

---

### 4. 翻译引擎：通用 OpenAI 兼容生态 + 思考模式自动降级

#### 当前痛点
目前仅硬编码了 DeepSeek、微软 Edge 和本地 Ollama，无法接入硅基流动、智谱、Gemini、中转站等第三方服务；且遇到不支持思考参数的模型会报 HTTP 400 失败。

#### 建议机制
1. **通用 OpenAI 兼容自定义 API**：
   - 允许用户自由配置 `Base URL` + `API Key` + `Model Name`，无缝接入全球任意大模型。
2. **思考模型（Reasoning / Thinking）自动降级容错**：
   - 实时字幕严禁使用数十秒的长思考。默认发送 `"thinking": {"type": "disabled"}`。
   - 若遇到返回 HTTP 400（不认识该字段）的服务端，**自动剥离思考参数并重新发送**，同时将该引擎标记为“免思考参数”，后续彻底锁死，不再产生多余往返。
3. **免费双引擎兜底**：
   - 微软 Edge 免费接口 + 谷歌 Web 接口双线备份，无 Key 也能稳定使用。
4. **模型可用性并发探针（Model Probe）**：
   - 设置页提供一键“检测可用性”，以 `max_tokens=1` 发起微量请求，精准诊断：`可用` / `Key 无效` / `账户欠费 (402)` / `模型已下架 (404)` / `频率超限 (429)`。

---

### 5. 悬浮窗交互：鼠标穿透（防误触锁定）与快捷工具栏

#### 建议机制
1. **鼠标穿透模式（Click-Through）**：
   - 在全屏游戏或观影时，通过 Win32 API 动态为窗口添加 `WS_EX_TRANSPARENT`。
   - 鼠标点击完全穿透到底层游戏或播放器，绝不影响鼠标操作；支持全局热键（如 `Ctrl+Shift+L`）一键解锁/锁定。
2. **悬浮窗轻量控制栏（Hover Toolbar）**：
   - 鼠标悬停在窗口顶部时自动滑出胶囊按钮：
     - **一键复制**：将最新译文瞬间拷入系统剪贴板。
     - **暂停 / 继续**：一键挂起 WASAPI 音频捕获。
     - **单双语切换**：轮转「双语 / 仅译文 / 仅原文」。
3. **语言双向对调（↔）**：
   - 支持一键互换源语言和目标语言（如日翻中 ↔ 中翻日），方便面对面双向交流。

---

### 6. 设置体验：所见即所得“实时字幕模拟预览”

#### 建议机制
- 在设置页“字幕外观”卡片顶部内置一个微型的仿真字幕卡片。
- 当用户拖动**字体大小**、**背景透明度**、**文字颜色**滑块时，预览卡片随之即时变动，彻底告别“盲调参数后重启悬浮窗查看”的低效流程。
- 提供折叠式“如何获取 API Key”向导，内嵌直达各大模型控制台的官方链接。

---

### 7. 工程健壮性：脱敏日志与全局崩溃捕获

#### 建议机制
1. **内存环形缓冲 + 敏感凭据脱敏日志（`logger.py`）**：
   - 内存维护 300 行循环日志队列。
   - 正则自动过滤 `sk-[a-zA-Z0-9_-]+` 与 `Bearer` 凭据，确保用户向开发者提交日志或 issue 时**绝不泄露 API Key**。
2. **全局崩溃捕获器（`crash_handler.py`）**：
   - 挂载 `sys.excepthook`。程序异常崩溃时，自动将堆栈与崩溃前 300 行脱敏日志写入 `crash.log`，并弹出系统提示框，杜绝 exe 无声闪退。

---

## 三、 关键代码实现参考（Python / PyQt6）

### 1. 稳定草稿流算法 (`speech/stable_prefix.py`)
```python
class StablePrefix:
    """提取两次连续重解码的最长公共前缀，形成单向增长的稳定草稿"""
    def __init__(self):
        self.committed = ""
        self.previous_hyp = ""
        self.seeded = False

    def reset(self):
        self.committed = ""
        self.previous_hyp = ""
        self.seeded = False

    def feed(self, hypothesis: str) -> str | None:
        clean_hyp = hypothesis.strip()
        if not clean_hyp:
            return None

        # 首次解码直接采信输出，缩短第一批字的等待感
        if not self.seeded:
            self.seeded = True
            self.previous_hyp = clean_hyp
            self.committed = clean_hyp
            return self.committed

        # 计算最长公共前缀
        idx = 0
        while idx < len(self.previous_hyp) and idx < len(clean_hyp) and self.previous_hyp[idx] == clean_hyp[idx]:
            idx += 1
        lcp = self.previous_hyp[:idx]
        self.previous_hyp = clean_hyp

        # 只增不减规则：前缀变长才更新上屏，变短则冻结
        if len(lcp) > len(self.committed):
            self.committed = lcp
            return self.committed
        return None
```

### 2. CJK 排版空格判定 (`processing/cjk_spacers.py`)
```python
def should_insert_space(prev_char: str, next_char: str) -> bool:
    """判断两字符拼接时是否需要插入空格"""
    if not prev_char or not next_char or prev_char.isspace() or next_char.isspace():
        return False

    def is_cjk(ch):
        code = ord(ch)
        return (0x4E00 <= code <= 0x9FFF or # 中文汉字
                0x3040 <= code <= 0x30FF or # 日文平假/片假
                0xAC00 <= code <= 0xD7AF or # 韩文谚文
                0x3000 <= code <= 0x303F or # CJK 标点
                0xFF00 <= code <= 0xFFEF)   # 全角标点

    def is_latin_digit(ch):
        return ch.isascii() and ch.isalnum()

    # 汉字或全角标点相互接触，绝不加空格
    if is_cjk(prev_char) or is_cjk(next_char):
        if (is_cjk(prev_char) and is_latin_digit(next_char)) or \
           (is_latin_digit(prev_char) and is_cjk(next_char)):
            return True # 中英文间保留单空格
        return False

    # 纯英文单词相接，保留空格
    return is_latin_digit(prev_char) and is_latin_digit(next_char)
```

### 3. Windows 原生鼠标穿透控制 (`ui/window_utils.py`)
```python
import ctypes

def set_click_through(window, enabled: bool):
    """切换 PyQt6 窗口的鼠标穿透（防误触锁定）模式"""
    hwnd = int(window.winId())
    GWL_EXSTYLE = -20
    WS_EX_TRANSPARENT = 0x00000020
    WS_EX_LAYERED = 0x00080000

    style = ctypes.windll.user32.GetWindowLongW(hwnd, GWL_EXSTYLE)
    if enabled:
        # 添加透明穿透标记：鼠标点击直接作用于底层全屏游戏或视频
        ctypes.windll.user32.SetWindowLongW(hwnd, GWL_EXSTYLE, style | WS_EX_TRANSPARENT | WS_EX_LAYERED)
    else:
        # 移除穿透标记：恢复窗口正常鼠标移动与缩放
        ctypes.windll.user32.SetWindowLongW(hwnd, GWL_EXSTYLE, style & ~WS_EX_TRANSPARENT)
```

---

## 四、 推荐分期落地计划

| 阶段 | 核心任务 | 预期收益 |
| :--- | :--- | :--- |
| **Phase 1<br>(文本与模型)** | 1. 调整 VAD 静音为 0.4s，单句上限 4.5s<br>2. 引入 `cjk_spacers.py`（彻底消灭汉字空格）<br>3. 通用 OpenAI 兼容自定义 API + 思考模式自动降级 | 消除断句碎、排版空格怪，支持接入任意中转与大模型平台。 |
| **Phase 2<br>(悬浮交互)** | 1. 增加快捷键 `Ctrl+Shift+L` 鼠标穿透<br>2. 悬浮窗顶部滑出复制与控制栏<br>3. 设置页增加“实时字幕预览卡片” | 观影与全屏游戏体验大幅提升，悬浮窗彻底不挡鼠标。 |
| **Phase 3<br>(草稿与容灾)** | 1. 引入 LocalAgreement-2 算法实现“边说边出”草稿流<br>2. 定稿优先抢占机制<br>3. 敏感 Key 正则脱敏日志与全局崩溃捕获 | 彻底消除说话等待感，软件具备工业级稳健度。 |
| **Phase 4<br>(新场景突破)** | 引入桌面屏幕选区 OCR 截屏翻译（`mss` + `rapidocr-onnxruntime`） | 开辟 PC 游戏汉化、生肉漫画、无字幕直播全场景。 |
