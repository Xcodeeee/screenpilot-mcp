package com.ssb.screenpilotmcp.tools;

import com.ssb.screenpilotmcp.util.CoordinateUtil;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.awt.Dimension;
import java.awt.Robot;
import java.awt.event.InputEvent;

@Component
public class MouseControlTool {

    @Value("${screenpilot.capture.target-width:1280}")
    private int targetWidth = 1280;

    /** 按下与释放之间的间隔，让系统正确识别点击 */
    private static final int PRESS_RELEASE_MS = 50;

    /** 双击两次点击之间的间隔 */
    private static final int DOUBLE_CLICK_GAP_MS = 120;

    /** 鼠标移动到目标位置后，稍等让系统确认位置 */
    private static final int MOVE_SETTLE_MS = 50;

    /** 操作完成后的后置等待，让界面完成响应 */
    private static final int AFTER_ACTION_MS = 500;

    // ---------- 单击 ----------
    @McpTool(name = "click_at",
            description = """
             在屏幕指定位置执行左键单击。
             x, y 直接填你在 capture_screen 返回图片中看到的像素坐标，工具会自动换算。
             """)
    public String clickAt(
            @McpToolParam(description = "截图中看到的 X 坐标", required = true) int x,
            @McpToolParam(description = "截图中看到的 Y 坐标", required = true) int y) {
        return doClick(x, y, 1, InputEvent.BUTTON1_DOWN_MASK, "单击");
    }

    // ---------- 双击 ----------
    @McpTool(name = "double_click_at",
            description = "在屏幕指定位置执行左键双击（例如打开文件、进入文件夹）。")
    public String doubleClickAt(
            @McpToolParam(description = "截图中看到的 X 坐标", required = true) int x,
            @McpToolParam(description = "截图中看到的 Y 坐标", required = true) int y) {
        return doClick(x, y, 2, InputEvent.BUTTON1_DOWN_MASK, "双击");
    }

    // ---------- 右键 ----------
    @McpTool(name = "right_click_at",
            description = "在屏幕指定位置执行右键单击（弹出上下文菜单）。")
    public String rightClickAt(
            @McpToolParam(description = "截图中看到的 X 坐标", required = true) int x,
            @McpToolParam(description = "截图中看到的 Y 坐标", required = true) int y) {
        return doClick(x, y, 1, InputEvent.BUTTON3_DOWN_MASK, "右键单击");
    }

    // ---------- 滚动 ----------
    @McpTool(name = "scroll_at",
            description = """
             在指定位置滚动屏幕。
             注意不同平台的滚动方向差异。
             direction 取 "up" 或 "down"；amount 为滚动格数（1 格约 3 行，向下滚动半屏可传 5）。
             位置坐标填 capture_screen 图片中看到的坐标。
             """)
    public String scrollAt(
            @McpToolParam(description = "截图中看到的 X 坐标", required = true) int x,
            @McpToolParam(description = "截图中看到的 Y 坐标", required = true) int y,
            @McpToolParam(description = "滚动方向：up 或 down", required = true) String direction,
            @McpToolParam(description = "滚动格数，建议 1~10", required = true) int amount) {
        try {
            Dimension screen = CoordinateUtil.screenSize();
            int realX = CoordinateUtil.toRealX(x, screen.width, targetWidth);
            int realY = CoordinateUtil.toRealY(y, screen.width, targetWidth);

            int notches = direction.equalsIgnoreCase("up")
                    ? -Math.abs(amount)
                    : Math.abs(amount);

            Robot robot = new Robot();
            robot.setAutoDelay(0);
            robot.mouseMove(realX, realY);
            robot.delay(MOVE_SETTLE_MS);
            robot.mouseWheel(notches);
            robot.delay(AFTER_ACTION_MS);

            return String.format("已在 (%d,%d) 向 %s 滚动 %d 格", x, y, direction, amount);
        } catch (Exception e) {
            return "滚动失败: " + e.getMessage();
        }
    }

    // ---------- 拖拽 ----------
    @McpTool(name = "drag",
            description = """
             从起点拖拽到终点（按住左键移动后释放），常用于拖动滑块、移动窗口、框选内容。
             fromX/fromY 和 toX/toY 都填 capture_screen 图片中看到的坐标。
             """)
    public String drag(
            @McpToolParam(description = "起点 X（截图坐标）", required = true) int fromX,
            @McpToolParam(description = "起点 Y（截图坐标）", required = true) int fromY,
            @McpToolParam(description = "终点 X（截图坐标）", required = true) int toX,
            @McpToolParam(description = "终点 Y（截图坐标）", required = true) int toY) {
        try {
            Dimension screen = CoordinateUtil.screenSize();
            int rx1 = CoordinateUtil.toRealX(fromX, screen.width, targetWidth);
            int ry1 = CoordinateUtil.toRealY(fromY, screen.width, targetWidth);
            int rx2 = CoordinateUtil.toRealX(toX, screen.width, targetWidth);
            int ry2 = CoordinateUtil.toRealY(toY, screen.width, targetWidth);

            Robot robot = new Robot();
            robot.setAutoDelay(0);

            robot.mouseMove(rx1, ry1);
            robot.delay(80);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);

            // 分步移动，避免"瞬移式"拖拽
            int steps = 20;
            for (int i = 1; i <= steps; i++) {
                int mx = rx1 + (rx2 - rx1) * i / steps;
                int my = ry1 + (ry2 - ry1) * i / steps;
                robot.mouseMove(mx, my);
                robot.delay(15);
            }

            robot.delay(80);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            robot.delay(AFTER_ACTION_MS);

            return String.format("已从 (%d,%d) 拖拽到 (%d,%d)", fromX, fromY, toX, toY);
        } catch (Exception e) {
            return "拖拽失败: " + e.getMessage();
        }
    }

    // ---------- 通用点击 ----------
    private String doClick(int x, int y, int times, int buttonMask, String actionName) {
        try {
            Dimension screen = CoordinateUtil.screenSize();
            int realX = CoordinateUtil.toRealX(x, screen.width, targetWidth);
            int realY = CoordinateUtil.toRealY(y, screen.width, targetWidth);

            Robot robot = new Robot();
            robot.setAutoDelay(0);

            robot.mouseMove(realX, realY);
            robot.delay(MOVE_SETTLE_MS);

            for (int i = 0; i < times; i++) {
                robot.mousePress(buttonMask);
                robot.delay(PRESS_RELEASE_MS);
                robot.mouseRelease(buttonMask);
                if (i < times - 1) {
                    robot.delay(DOUBLE_CLICK_GAP_MS);
                }
            }

            robot.delay(AFTER_ACTION_MS);

            return String.format("已%s。图上 (%d,%d) → 屏幕 (%d,%d)",
                    actionName, x, y, realX, realY);
        } catch (Exception e) {
            return actionName + "失败: " + e.getMessage();
        }
    }
}