package com.ssb.screenpilotmcp.tools;

import com.ssb.screenpilotmcp.util.ImageResizer;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;

@Component
public class ScreenCaptureTool {

    @Value("${screenpilot.capture.target-width:1280}")
    private int targetWidth = 1280;

    @McpTool(
            name = "capture_screen",
            description = """
            截取当前全屏幕，返回 PNG 图片的 base64 编码。
            你只需要看图并给出图上看到的坐标即可，
            后续调用 click_at / double_click_at / right_click_at / scroll_at / drag 时
            直接传该坐标，工具会自动换算到真实屏幕位置。
            """
    )
    public String captureScreen() {
        try {
            Robot robot = new Robot();
            Rectangle screenRect = new Rectangle(
                    Toolkit.getDefaultToolkit().getScreenSize());
            BufferedImage image = robot.createScreenCapture(screenRect);

            BufferedImage resized = ImageResizer.resize(image, targetWidth);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(resized, "png", baos);
            return Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (Exception e) {
            return "截图失败: " + e.getMessage();
        }
    }
}