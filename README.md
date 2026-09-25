# ScreenPilot MCP

> 让大模型拥有眼睛和手，直接操控你的桌面。

ScreenPilot MCP 是一个基于 [Model Context Protocol](https://modelcontextprotocol.io/) 的桌面自动化服务。接入支持 MCP 的客户端后，AI 可以**截取屏幕、点击、输入、滚动、拖拽**，实现"AI 自主操作电脑"。

**技术栈**：Java 21 + Spring Boot 4 + Spring AI 2.0（MCP Server）+ `java.awt.Robot`

---

## ⚠️ 重要安全提醒（请务必阅读）

**本项目当前没有实现任何安全审计机制。**

这意味着：

- AI 每次调用 `capture_screen` 时，**你的整个屏幕内容（包括密码框、私人聊天、邮件、银行页面等）都会被截图并发送给大模型**
- AI 可以调用 `click_at`、`type_text` 等工具，**在你的电脑上执行任意鼠标和键盘操作**
- 目前**没有任何权限确认、操作拦截、敏感信息过滤或行为日志**

**请不要在含有敏感信息的电脑上使用本项目。请自行评估风险。**

我们开源出来，是希望社区一起探讨安全审计的设计方案。当前**没有任何安全保证**，请谨慎使用。

---

## ✨ 功能

共 8 个工具：

| 工具 | 说明 |
|------|------|
| `capture_screen` | 截取全屏幕，返回 PNG 的 base64 |
| `click_at` | 左键单击 |
| `double_click_at` | 左键双击 |
| `right_click_at` | 右键单击 |
| `scroll_at` | 向上/向下滚动 |
| `drag` | 从起点拖拽到终点 |
| `type_text` | 输入文本（支持中文、emoji） |
| `key_press` | 按键或组合键（如 `ctrl+c`） |


---

## 🖥️ 平台支持

| 平台 | 状态 |
|------|------|
| **macOS** | ✅ 已测试通过 |
| **Windows** | ✅ 已测试通过 |
| **Linux (X11)** | 未测试 |
| **Linux (Wayland)** | ❌ 不支持，`java.awt.Robot` 无法在 Wayland 下截屏 |

---

## 🚀 使用方式

ScreenPilot MCP 通过 **STDIO** 传输，由 MCP 客户端作为子进程启动。



### 一、JSON 配置方式

适用于 Claude Desktop、Cursor 等需要编辑配置文件的客户端。

#### npx 启动（尚未发布）

```json
{
  "mcpServers": {
    "screenpilot": {
      "command": "npx",
      "args": ["screenpilot-mcp"]
    }
  }
}
```

#### java 启动（需要Java21+环境）

克隆源码构建：

```bash
git clone https://github.com/Xcodeeee/screenpilot-mcp.git
cd screenpilot-mcp
mvn clean package -DskipTests
```

构建成功后在 `target/` 下生成 `screenpilot-mcp-0.0.1-SNAPSHOT.jar`，然后：

```json
{
  "mcpServers": {
    "screenpilot": {
      "command": "java",
      "args": [
        "-jar",
        "/绝对路径/screenpilot-mcp-0.0.1-SNAPSHOT.jar"
      ]
    }
  }
}
```

⚠️ 路径必须是**绝对路径**，Windows 上用双反斜杠 `\\` 或正斜杠 `/`。

---

### 二、可视化界面方式

适用于提供"添加 MCP 服务器"表单的客户端。在界面中填写以下字段：

#### npx 启动（尚未发布）

| 字段 | 值 |
|------|-----|
| 名称 | `screenpilot`（任意） |
| 传输方式 | `STDIO` |
| 命令 | `npx` |
| 参数 | `screenpilot-mcp` |

#### java 启动（需要Java21+环境）

克隆源码构建：

```bash
git clone https://github.com/你的用户名/screenpilot-mcp.git
cd screenpilot-mcp
mvn clean package -DskipTests
```

构建成功后在 `target/` 下生成 `screenpilot-mcp-0.0.1-SNAPSHOT.jar`，然后在界面中填写：

| 字段 | 值 |
|------|-----|
| 名称 | `screenpilot`（任意） |
| 传输方式 | `STDIO` |
| 命令 | `java` |
| 参数 | `-jar /绝对路径/screenpilot-mcp-0.0.1-SNAPSHOT.jar` |

---

### 验证

配置完成后**彻底退出并重启客户端**（macOS 按 `Cmd + Q`，不是关窗口），在对话框输入：

> 截取当前屏幕，告诉我屏幕上有什么。

若 AI 成功调用 `capture_screen` 并描述画面，即接入成功。

---

## 🔐 平台权限

### macOS（必须配置）

macOS 对屏幕操作有严格的权限管控。ScreenPilot 涉及**截屏**和**模拟鼠标键盘**两类操作，需要以下**三项权限**全部开启：

**1. 屏幕录制（Screen Recording）**

用于 `capture_screen` 截取屏幕画面。

- 打开 **系统设置 → 隐私与安全性 → 屏幕录制**
- 勾选你用来运行 MCP 的**客户端**

**2. 辅助功能（Accessibility）**

用于 `click_at`、`drag`、`type_text` 等鼠标和键盘操作。macOS 10.15 起，`java.awt.Robot` 默认不被允许控制 Mac，必须手动授权。

- 打开 **系统设置 → 隐私与安全性 → 辅助功能**
- 勾选你用来运行 MCP 的**客户端**

**3. 输入监控（Input Monitoring）**

用于 `key_press` 模拟按键输入。

- 打开 **系统设置 → 隐私与安全性 → 输入监控**
- 勾选你用来运行 MCP 的**客户端**

**关键说明**：

- 以上三项权限都是**给启动 MCP 的客户端授权**，不是给 IDEA 或 Java。macOS 的权限是授予启动进程的父进程的。
- 如果权限已勾选但截图仍返回黑屏，尝试**彻底退出客户端**（`Cmd + Q`）后重新打开。macOS 更新有时会重置权限，需重新检查。
- 重置所有相关权限：

```bash
tccutil reset ScreenCapture
tccutil reset Accessibility
```

### Windows

Windows 通常不需要额外授权即可截屏和模拟输入。但有以下注意事项：

- **UAC 提权窗口无法截图**：当 Windows 弹出 UAC（用户账户控制）提示时，系统会锁定桌面，任何程序都无法截取该画面。
- **部分系统级界面需要管理员权限**：如果需要操作以管理员身份运行的窗口，建议以管理员身份启动 MCP 客户端。
- **焦点切换延迟**：Windows 在切换焦点窗口时有延迟，可能导致自动化操作失败。可通过修改注册表 `HKEY_CURRENT_USER\Control Panel\Desktop\ForegroundLockTimeout` 的值为 `0` 来禁用延迟。

### Linux

- **X11 桌面环境**（如 Ubuntu 20.04、CentOS）：开箱即用
- **Wayland 桌面环境**（Ubuntu 22.04+、Fedora 默认）：`java.awt.Robot` 无法工作，截图会返回黑屏。建议在登录界面切换到 **X11 会话**

---

## 🗺️ 未来计划

按优先级排列：

- [ ] **安全审计机制**（最重要）
    - 当前完全没有设计，欢迎社区讨论
    - 需要解决的核心问题：如何让用户感知并确认 AI 的敏感操作？如何过滤截图中的隐私内容？如何记录可审计的操作日志？
- [ ] **Linux X11 测试与适配**
- [ ] **Wayland 支持**（通过 D-Bus portal，工作量大）
- [ ] **多显示器支持**（当前只截主屏）
- [ ] **`get_selected_text`**（读取焦点窗口选中的文本）
- [ ] **截图区域裁剪**（只截指定区域，降低隐私风险）
- [ ] **Windows / macOS / Linux CI 构建矩阵**

---

## 🛠️ 开发与贡献

欢迎提交 Issue 和 Pull Request。

**提交前请确认**：

- 代码通过 `mvn clean package` 构建
- 新加的工具遵循 `@McpTool` 注解规范
- 涉及坐标的工具必须使用 `CoordinateUtil` 换算，禁止自行实现
- 日志使用 SLF4J，禁止 `System.out.println`（STDIO 模式下 stdout 是 MCP 协议专用通道）

---

## 📄 License

[MIT](LICENSE)

---

## 🙏 致谢

- [Model Context Protocol](https://modelcontextprotocol.io/)
- [Spring AI](https://spring.io/projects/spring-ai)

---

**⭐ 如果这个项目对你有帮助，欢迎 Star。**