package com.ssb.screenpilotmcp.tools;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.KeyEvent;
import java.util.Map;

@Component
public class KeyboardControlTool {

    private static final boolean IS_MAC =
            System.getProperty("os.name").toLowerCase().contains("mac");
    private static final int MODIFIER = IS_MAC ? KeyEvent.VK_META : KeyEvent.VK_CONTROL;

    private static final Map<String, Integer> KEY_MAP = Map.ofEntries(
            Map.entry("enter",     KeyEvent.VK_ENTER),
            Map.entry("tab",       KeyEvent.VK_TAB),
            Map.entry("escape",    KeyEvent.VK_ESCAPE),
            Map.entry("esc",       KeyEvent.VK_ESCAPE),
            Map.entry("space",     KeyEvent.VK_SPACE),
            Map.entry("backspace", KeyEvent.VK_BACK_SPACE),
            Map.entry("delete",    KeyEvent.VK_DELETE),
            Map.entry("up",        KeyEvent.VK_UP),
            Map.entry("down",      KeyEvent.VK_DOWN),
            Map.entry("left",      KeyEvent.VK_LEFT),
            Map.entry("right",     KeyEvent.VK_RIGHT),
            Map.entry("home",      KeyEvent.VK_HOME),
            Map.entry("end",       KeyEvent.VK_END),
            Map.entry("pageup",    KeyEvent.VK_PAGE_UP),
            Map.entry("pagedown",  KeyEvent.VK_PAGE_DOWN)
    );

    @McpTool(name = "type_text",
            description = """
            在当前获得焦点的输入框中插入文本（支持中文、emoji 等 Unicode 字符）。

            行为说明：
            1. 文本会插入到当前光标位置，不会清除原有内容。
            2. 若要替换原有内容，先发送 ctrl+a（macOS 为 cmd+a）全选，再调用本工具。
            3. 使用前先用 click_at 点击目标输入框，确保光标已落在其中。
            4. 实现方式是写入系统剪贴板后模拟粘贴，会覆盖用户原有剪贴板内容。
            """)
    public String typeText(
            @McpToolParam(description = "要输入的文本", required = true) String text) {
        try {
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            clipboard.setContents(new StringSelection(text), null);

            Robot robot = new Robot();
            robot.setAutoDelay(30);

            robot.keyPress(MODIFIER);
            robot.keyPress(KeyEvent.VK_V);
            robot.keyRelease(KeyEvent.VK_V);
            robot.keyRelease(MODIFIER);

            robot.delay(300);
            return "已输入 " + text.length() + " 个字符";
        } catch (Exception e) {
            return "输入失败: " + e.getMessage();
        }
    }

    @McpTool(name = "key_press",
            description = """
            模拟按下单个按键或组合键。

            用法：
            1. 单键：key 传 "enter"、"tab"、"esc"、"up"、"down"、"pageup" 等。
            2. 组合键：用 "+" 连接，如 "ctrl+c"、"ctrl+v"、"cmd+a"。
               注意 macOS 用 cmd，Windows / Linux 用 ctrl。
            3. 如需重复按键（如按 3 次 down），请多次调用本工具，不要在一次调用里塞重复。
            """)
    public String keyPress(
            @McpToolParam(description = "按键或组合键，如 enter / ctrl+c", required = true) String key) {
        try {
            String[] parts = key.toLowerCase().split("\\+");
            Robot robot = new Robot();
            robot.setAutoDelay(30);

            for (int i = 0; i < parts.length - 1; i++) {
                int mod = resolveModifier(parts[i].trim());
                if (mod == -1) return "不支持的修饰键: " + parts[i];
                robot.keyPress(mod);
            }

            String mainKey = parts[parts.length - 1].trim();
            int mainCode = resolveKey(mainKey);
            if (mainCode == -1) return "不支持的按键: " + mainKey;
            robot.keyPress(mainCode);
            robot.keyRelease(mainCode);

            for (int i = parts.length - 2; i >= 0; i--) {
                robot.keyRelease(resolveModifier(parts[i].trim()));
            }

            robot.delay(300);
            return "已按下: " + key;
        } catch (Exception e) {
            return "按键失败: " + e.getMessage();
        }
    }

    private int resolveModifier(String name) {
        return switch (name) {
            case "ctrl", "control" -> KeyEvent.VK_CONTROL;
            case "cmd", "command", "meta" -> KeyEvent.VK_META;
            case "alt", "option" -> KeyEvent.VK_ALT;
            case "shift" -> KeyEvent.VK_SHIFT;
            default -> -1;
        };
    }

    private int resolveKey(String name) {
        Integer mapped = KEY_MAP.get(name);
        if (mapped != null) return mapped;
        if (name.length() == 1) {
            char c = name.charAt(0);
            if (c >= 'a' && c <= 'z') return KeyEvent.VK_A + (c - 'a');
            if (c >= '0' && c <= '9') return KeyEvent.VK_0 + (c - '0');
        }
        return -1;
    }
}