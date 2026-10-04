package com.ssb.screenpilotmcp.tools;

import com.ssb.screenpilotmcp.dto.OcrItem;
import com.ssb.screenpilotmcp.util.ImageResizer;
import com.ssb.screenpilotmcp.util.ScreenOcrUtil;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.ImageContent;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

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
    private int targetWidth;

    private final ScreenOcrUtil screenOcrUtil;

    // 不走 Spring 注入，避免和 MCP 的 mcpServerJsonMapper 冲突
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    public ScreenCaptureTool(ScreenOcrUtil screenOcrUtil) {
        this.screenOcrUtil = screenOcrUtil;
    }

    @McpTool(
            name = "capture_screen",
            description = """
        截取当前全屏幕，返回图片（模型可直接查看）和 OCR 识别结果。

        返回结构：
        - 一个 image 内容块：截图的 PNG 图片，多模态模型可以直接“看到”并分析画面内容
        - 一个 text 内容块：JSON 字符串，包含 OCR 识别结果：
            - ocrItems: 识别到的文本列表，每项包含：
                - text:   识别出的文字内容
                - x, y:   文本框左上角在图片上的坐标
                - w, h:   文本框的宽高
                - score:  识别置信度（0~1，越高越可信）
                - centerX, centerY: 文本框中心点在图片上的坐标
                - right, bottom: 文本框右下角在图片上的坐标

        使用说明：
        - 请以图片内容为准，OCR 结果仅供参考、可能有误
        - 坐标以返回的这张图为准，不是真实屏幕坐标（单位：像素）
        - 若 ocrItems 中没有你要找的文字，请不要凭想象编造坐标，改为直接看图判断
        - 后续调用 click_at / double_click_at / right_click_at / scroll_at / drag 时，
          直接传图上看到的坐标，工具会自动换算到真实屏幕位置
        """
    )
    public CallToolResult captureScreen() {
        try {
            Robot robot = new Robot();
            Rectangle screenRect = new Rectangle(
                    Toolkit.getDefaultToolkit().getScreenSize());
            BufferedImage image = robot.createScreenCapture(screenRect);

            //压缩图像
            BufferedImage resized = ImageResizer.resize(image, targetWidth);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(resized, "png", baos);
            String base64 = Base64.getEncoder().encodeToString(baos.toByteArray());

            List<OcrItem> ocrItems;
            try {
                //OCR识别图像
                ocrItems = screenOcrUtil.recognize(resized);
            } catch (Exception e) {
                ocrItems = List.of();
            }

            String ocrJson = jsonMapper.writeValueAsString(ocrItems);

            return CallToolResult.builder()
                    .addTextContent(ocrJson)
                    .addContent(ImageContent.builder(base64, "image/png").build())
                    .build();

        } catch (Exception e) {
            return CallToolResult.builder()
                    .isError(true)
                    .addTextContent("截图失败: " + e.getMessage())
                    .build();
        }
    }
}