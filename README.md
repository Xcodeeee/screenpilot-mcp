# ScreenPilot MCP

> 让大模型拥有眼睛和手，直接操控你的桌面。

ScreenPilot MCP 是一个基于 [Model Context Protocol（MCP）](https://modelcontextprotocol.io/) 的桌面自动化服务。接入支持 MCP 的客户端后，AI 可以截取屏幕、识别文字、点击、输入、滚动和拖拽。

**技术栈：** Java 21、Spring Boot 4、Spring AI 2.0（MCP Server）、`java.awt.Robot`

## 目录

- [项目简介](#项目简介)
- [功能一览](#功能一览)
- [平台支持](#平台支持)
- [安装与配置](#安装与配置)
- [系统权限](#系统权限)
- [验证是否接入成功](#验证是否接入成功)
- [开发与贡献](#开发与贡献)
- [OCR 说明](#ocr-说明)
- [路线图](#路线图)
- [安全说明](#安全说明)
- [致谢](#致谢)

## 项目简介

ScreenPilot MCP 通过 **STDIO** 传输，由 MCP 客户端作为子进程启动。提供屏幕截图、OCR 识别和鼠标键盘控制工具，适用于需要桌面交互能力的 MCP 工作流。

### 重要安全提醒

**当前版本尚未启用安全审计机制。** 屏幕截图可能包含密码、私人聊天、邮件、银行页面等敏感信息；鼠标和键盘工具也可能执行高风险操作。当前版本没有操作前权限确认、操作拦截、敏感信息过滤或行为日志。

请勿在包含敏感信息或重要账户的环境中使用，除非你已充分评估并接受相关风险。详细说明请阅读[安全说明](docs/SECURITY.md)。

## 功能一览

项目提供以下 8 个 MCP 工具：

| 工具 | 功能 |
| --- | --- |
| `capture_screen` | 截取全屏，返回 PNG 图片和 OCR 识别结果（文字及坐标） |
| `click_at` | 鼠标左键单击 |
| `double_click_at` | 鼠标左键双击 |
| `right_click_at` | 鼠标右键单击 |
| `scroll_at` | 向上或向下滚动 |
| `drag` | 从起点拖拽到终点 |
| `type_text` | 输入文本，支持中文和 emoji |
| `key_press` | 按键或组合键，例如 `ctrl+c` |

## 平台支持

| 平台 | 当前状态 | 说明 |
| --- | --- | --- |
| macOS | 已测试 | 需要配置屏幕录制、辅助功能和输入监控权限 |
| Windows | 已测试 | 某些系统级窗口可能受权限或焦点切换影响 |
| Linux（X11） | 未测试 | 项目仍需实际验证 |
| Linux（Wayland） | 不支持 | 当前实现使用的 `java.awt.Robot` 无法在 Wayland 会话中正常截屏 |

## 安装与配置

有两种启动方式：

- **npx：** 推荐给希望直接使用 npm 包的用户；无需单独安装 Java。npm 包可能滞后于 GitHub 源码。
- **源码构建：** 需要 Git、Maven Wrapper 和 Java 21 或更高版本，适合尝试最新源码。

### 方式一：JSON 配置

适用于 Claude Desktop、Cursor 等允许编辑 MCP 配置文件的客户端。

**npx 启动（推荐）**

将以下配置合并到客户端的 MCP 配置中：

```json
{
  "mcpServers": {
    "screenpilot": {
      "command": "npx",
      "args": ["-y", "screenpilot-mcp"]
    }
  }
}
```

参数 `-y` 用于自动确认安装，避免客户端因交互提示而卡住。

**源码构建并使用 Java 启动**

在终端执行：

```bash
git clone https://github.com/Xcodeeee/screenpilot-mcp.git
cd screenpilot-mcp
./mvnw clean package -DskipTests
```

Windows 可使用：

```powershell
.\mvnw.cmd clean package -DskipTests
```

构建成功后，在 `target/` 目录中找到生成的 JAR 文件，再将 MCP 配置为：

```json
{
  "mcpServers": {
    "screenpilot": {
      "command": "java",
      "args": [
        "-jar",
        "/absolute/path/to/screenpilot-mcp-0.0.1-SNAPSHOT.jar"
      ]
    }
  }
}
```

请将示例路径替换为 JAR 文件的**实际绝对路径**。Windows 路径可使用双反斜杠或正斜杠。

### 方式二：客户端可视化配置

如果客户端提供“添加 MCP 服务器”表单，请填写：

| 字段 | npx 启动 | Java 启动 |
| --- | --- | --- |
| 名称 | `screenpilot`（可自定义） | `screenpilot`（可自定义） |
| 传输方式 | `STDIO` | `STDIO` |
| 命令 | `npx` | `java` |
| 参数 | `-y screenpilot-mcp` | `-jar /绝对路径/项目.jar` |

## 系统权限

### macOS

macOS 需要为**启动 MCP 的客户端**开启以下三项权限，而不是只给 IDE 或 Java 授权：

1. **屏幕录制：** 系统设置 → 隐私与安全性 → 屏幕录制。用于 `capture_screen`。
2. **辅助功能：** 系统设置 → 隐私与安全性 → 辅助功能。用于鼠标和键盘控制，例如 `click_at`、`drag`、`type_text`。
3. **输入监控：** 系统设置 → 隐私与安全性 → 输入监控。用于 `key_press` 模拟按键。

如果权限已开启但截图仍为黑屏，请彻底退出客户端（例如按 `Cmd + Q`）后重新启动，并再次检查系统权限。系统更新后权限可能需要重新确认。

如需重置相关 TCC 权限，可在终端执行以下命令；执行前请确认你理解其影响：

```bash
tccutil reset ScreenCapture
tccutil reset Accessibility
```

### Windows

通常无需额外配置即可截屏和模拟输入，但请注意：

- **UAC 安全桌面：** 用户账户控制提权提示期间，普通桌面程序通常无法截取该安全桌面。
- **管理员窗口：** 若目标应用以管理员身份运行，权限级别差异可能导致控制失败。
- **焦点切换：** 切换窗口时的系统延迟可能影响自动化操作。不要为了绕过安全边界而随意更改系统设置。

### Linux

- **X11：** 当前尚未完成项目级测试，使用前请自行验证。
- **Wayland：** 当前实现不支持；在 Wayland 会话下可能出现黑屏或无法操作。切换 X11 会话只是可能的临时规避方式，不代表项目已正式支持 Linux。

## 验证是否接入成功

1. 保存 MCP 配置。
2. **彻底退出并重新启动客户端**，使配置生效。
3. 在客户端中请求：“截取当前屏幕，告诉我屏幕上有什么。”
4. 如果客户端成功调用 `capture_screen` 并返回画面描述，说明基本接入成功。

测试前请关闭含敏感信息的窗口，并确认你理解屏幕截图和输入控制带来的风险。

## 开发与贡献

欢迎通过 Issue 和 Pull Request 参与改进。

提交代码前请检查：

- 使用 Java 21 或更高版本，并尽可能执行 `./mvnw clean package`（Windows 使用 `mvnw.cmd`）。
- 新增 MCP 工具遵循项目使用的 `@McpTool` 注解规范。
- 涉及屏幕坐标的工具必须使用 `CoordinateUtil`，不要自行重复实现坐标换算。
- 使用 SLF4J 记录日志；不要使用 `System.out.println` 输出普通日志，因为 STDIO 模式下标准输出用于 MCP 协议通信。
- 新增依赖时注意 npm 分发包的体积目标（含 JRE 小于 100 MB）；变更后应重新核验实际产物大小。

## OCR 说明

OCR 能力由 [lw.PPOCR.Java](https://github.com/lxw112190/lw.PPOCR.Java) 提供，这是一个纯 Java 的 PP-OCR 推理引擎。项目使用转换为 LWM 格式的 PP-OCRv6 Tiny 模型。

选择纯 Java 实现的原因是需要将 JRE 和依赖一起打包进 npm 分发包，并将总体积控制在 100 MB 以内；与依赖 ONNX Runtime、OpenCV 原生库的方案相比，这有助于减少原生库带来的体积负担。

**已知限制：** 当前 OCR 识别速度仍在优化中。

## 路线图

以下项目反映 README 最近一次整理时记录的计划状态，实际进度可能变化：

- [ ] 上线安全审计机制（最高优先级，开发中）
- [ ] 优化 OCR 性能
- [ ] 完成 Linux X11 测试与适配
- [ ] 通过 D-Bus portal 等方案探索 Wayland 支持
- [ ] 支持多显示器（当前仅截取主屏）
- [ ] 增加 `get_selected_text`（读取焦点窗口选中文本）
- [ ] 增加截图区域裁剪，降低不必要的隐私暴露
- [ ] 建立 Windows、macOS、Linux 的 CI 构建矩阵

## 安全说明

安全审计的设计目标、风险分类、模型方案和当前进度已单独整理到[安全说明文档](docs/SECURITY.md)。**该审计机制尚未集成到当前版本；文档中的设计不能视为已经实现的保护措施。**

## 致谢

- [Model Context Protocol](https://modelcontextprotocol.io/)
- [Spring AI](https://spring.io/projects/spring-ai)
- [lw.PPOCR.Java](https://github.com/lxw112190/lw.PPOCR.Java) — 纯 Java PP-OCR 推理引擎
- [PaddleOCR](https://github.com/PaddlePaddle/PaddleOCR) — PP-OCR 模型项目

---

如果这个项目对你有帮助，欢迎在 GitHub 上 Star。
