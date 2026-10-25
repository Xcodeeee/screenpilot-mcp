# screenpilot-fast（实验性 · Node 旁路实现）

> 这是 ScreenPilot 的一个**可选旁路实现**，用 Node + FFI 直调 Win32 API，不走 JVM、不做 OCR。
> 目的单一：把"每步操作"的延迟压下来。**不替换**仓库现有的 Java 实现，也不改动 `pom.xml` 与任何 Java 源码。

## 为什么另起一份

现有 Java 实现每帧的固定成本在截图 + 编码 + OCR + JVM 冷启动上；而大多数操作场景（点按钮、填框、切窗口）只需要**一小块画面**，且模型本来就能看图，OCR 并非必需。

实测（Windows 11 / 2560×1600 / Node 22 / JDK 21）：

| 动作 | Java 实现 | 本实现 | 说明 |
|---|---|---|---|
| 启动握手 | 1952–2188 ms | **63 ms** | Java 有 Spring 上下文 + JVM 启动 |
| 全屏截图 1280 宽 | 117 ms / 217 KB | **45–55 ms / 260 KB** | 都是 PNG |
| 区域截图 400×160 | ~120 ms（无此参数） | **6–19 ms / 6 KB** | 只 BitBlt 指定矩形 |
| 增量截图 | 无 | **只回变化 tile** | 静止界面 <10 KB |
| 点击 | ~53 ms | **4 ms** | Java 侧有固定 settle 等待 |
| 中文/emoji 输入 | `type_text` | **23–37 ms** | SendInput `KEYEVENTF_UNICODE` |
| 列窗口 / 等静帧 | 无 | **2 ms / 40 ms×轮** | 模型不用"睡一秒再截" |

## 与 Java 实现的取舍

| | Java 实现 | 本实现 |
|---|---|---|
| 运行环境 | 自带 JRE（单文件产物） | 需要 Node ≥ 18 |
| OCR | 有（PP-OCRv6 Tiny） | 无（交给模型视觉） |
| 平台 | Windows / macOS / Linux(X11) | **仅 Windows** |
| 多显示器 | 主屏 | 主屏（`region` 可覆盖其他屏区域） |
| 结构感知 | 无 | 无（UIA 待做） |
| 分发 | npm 单文件 | 需 `npm i koffi` |

定位：**同类能力的一个性能参照实现**。若维护者认为有价值，可以把 `region` / `wait_stable` / 增量回包这几个思路反向移植进 Java 实现；本目录也可以随时删除。

## 安装

```bash
cd experimental/node-fast
npm install
```

MCP 客户端配置（STDIO）：

```json
{
  "mcpServers": {
    "screenpilot-fast": {
      "type": "stdio",
      "command": "node",
      "args": ["/绝对路径/experimental/node-fast/server.mjs"]
    }
  }
}
```

## 工具

| 工具 | 参数 | 说明 |
|---|---|---|
| `capture` | `region?` `scale?` `max_width?` `diff?` | 返回 PNG（标准 `image` 块）+ JSON 元数据块 |
| `click` | `x` `y` `button?` `count?` | 物理像素坐标 |
| `move` | `x` `y` | |
| `drag` | `from` `to` `button?` | |
| `scroll` | `delta` `x?` `y?` | 120 = 一格 |
| `type_text` | `text` | 支持中文与 emoji |
| `key` | `key` | `enter` / `ctrl+c` / `win+d` / `f5` |
| `wait_stable` | `timeout_ms?` `interval_ms?` `region?` | 连续两帧一致才返回 |
| `window` | `action` `title?` | `list` / `minimize` / `maximize` / `restore` / `focus` / `close` |

### 坐标约定

截图是物理像素，经 `scale` 缩放后返回。还原公式：

```
屏幕坐标 = 图像坐标 × meta.scale + meta.origin
```

`meta` 是 `capture` 返回的第二个（JSON）内容块，字段：`image{ w,h }` `origin{ x,y }` `scale` `screen{ w,h }` `region{ x,y,w,h }`。

`diff=true` 时只返回相对上一帧变化的区域，`meta.changed_rect` 给出该区域在图像坐标系中的位置；无变化时只回 `{ unchanged: true }`。

## 实现要点

- 截屏：`BitBlt(SRCCOPY | CAPTUREBLT)` + `GetDIBits` 取 BGRA，JS 侧降采样，`zlib` level 1 + 手写 PNG 编码（避免引入图像库）。
- 增量：32×32 tile，采样 hash 比对；变化时裁出包围盒编码。
- 输入：鼠标走 `SetCursorPos` + `mouse_event`；文本走 `SendInput` + `KEYEVENTF_UNICODE`（按 UTF-16 code unit 展开，代理对正确）。
- DPI：启动即 `SetProcessDPIAware()`，全程物理像素，不受 125%/150% 缩放影响。
- 窗口：`EnumWindows` + `ShowWindow` / `SetForegroundWindow` / `PostMessage(WM_CLOSE)`。

## 已知限制

- 仅 Windows；仅主屏（`region` 可间接覆盖）。
- 无 OCR、无 UIA 控件树。
- PNG only，未实现 JPEG。
- 需要 Node ≥ 18，`koffi` 带预编译二进制（win32-x64）。

## 许可

本目录代码按仓库现有条款提供。仓库当前没有 LICENSE 文件，合并前请确认授权方式。
