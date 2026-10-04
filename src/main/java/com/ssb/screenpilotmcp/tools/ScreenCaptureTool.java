package com.ssb.screenpilotmcp.tools;

import com.ssb.screenpilotmcp.dto.OcrItem;
import com.ssb.screenpilotmcp.util.ImageResizer;
import com.ssb.screenpilotmcp.util.ScreenOcrUtil;
import com.ssb.screenpilotmcp.vo.ScreenCaptureResult;
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
import java.util.List;

@Component
public class ScreenCaptureTool {

    @Value("${screenpilot.capture.target-width:1280}")
    private int targetWidth = 1280;

    private final ScreenOcrUtil screenOcrUtil;

    public ScreenCaptureTool(ScreenOcrUtil screenOcrUtil) {
        this.screenOcrUtil = screenOcrUtil;
    }

    @McpTool(
            name = "capture_screen",
            description = """
        截取当前全屏幕，返回 PNG 图片的 base64 编码和 OCR 识别结果（文字 + 坐标）。
        请以图片内容为准，OCR 结果仅作参考、可能有误。
        后续调用 click_at / double_click_at / right_click_at / scroll_at / drag 时
        直接传图上看到的坐标，工具会自动换算到真实屏幕位置。
        """
    )
    public ScreenCaptureResult captureScreen() {
        try {
            Robot robot = new Robot();
            Rectangle screenRect = new Rectangle(
                    Toolkit.getDefaultToolkit().getScreenSize());
            BufferedImage image = robot.createScreenCapture(screenRect);

            BufferedImage resized = ImageResizer.resize(image, targetWidth);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(resized, "png", baos);
            String base64 = Base64.getEncoder().encodeToString(baos.toByteArray());

            // OCR 独立 try：失败也不影响图片返回
            List<OcrItem> ocrItems;
            try {
                ocrItems = screenOcrUtil.recognize(resized);
            } catch (Exception e) {
                ocrItems = List.of();
            }

            return new ScreenCaptureResult(base64, ocrItems);

        } catch (Exception e) {
            // 截图本身失败，图都没有，只能返回空
            return new ScreenCaptureResult(null, List.of());
        }
    }
}