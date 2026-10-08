package com.ssb.screenpilotmcp.tools;

import com.ssb.screenpilotmcp.dto.OcrItem;
import com.ssb.screenpilotmcp.util.ImageResizer;
import com.ssb.screenpilotmcp.util.ScreenOcrUtil;
import com.ssb.screenpilotmcp.vo.OcrItemView;
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
            截取当前全屏幕，返回两个内容块。

            返回结构：
            1. image 块：截图的 PNG 图片。
            2. text 块：OCR 识别结果的 JSON 数组。每项含：
               - text：识别出的文字内容
               - centerX / centerY：文本框中心点在截图上的坐标
               - score：识别置信度（0~1，越高越可信）

            定位建议（OCR 与视觉同等重要，请交叉验证）：
            1. OCR 提供了文字内容和大致坐标，可作为快速定位依据。
            2. 但 OCR 可能出错（识别错字、漏字、错位），此时请直接看图确认。
            3. 视觉判断也可能出错（小字、相似图标、界面复杂），此时请参考 OCR。
            4. 两者一致时直接使用；冲突时，以更合理的那个为准，并优先选择 score 高的条目。
            5. 若两者都不足以判断，请如实告诉用户"无法定位"，
               不要凭想象编造坐标。编造坐标会导致误点击，后果严重。

            若模型没有视觉能力（仅文本模型）：
            请仅依赖 OCR 结果；若无 OCR 结果或 OCR 结果明显不足，
            请告知用户当前模型无法完成该任务。

            坐标说明：
            1. x、y 是截图上的像素坐标，不是真实屏幕坐标。
            2. 后续调用 click_at / double_click_at / right_click_at / scroll_at / drag 时，
               直接传截图上的坐标即可，工具会自动换算到真实屏幕位置。
            """
    )
    public CallToolResult captureScreen() {
        try {
            Robot robot = new Robot();
            Rectangle screenRect = new Rectangle(
                    Toolkit.getDefaultToolkit().getScreenSize());
            BufferedImage image = robot.createScreenCapture(screenRect);

            // 压缩图像
            BufferedImage resized = ImageResizer.resize(image, targetWidth);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(resized, "png", baos);
            String base64 = Base64.getEncoder().encodeToString(baos.toByteArray());

            List<OcrItem> ocrItems;
            try {
                // OCR 识别图像
                ocrItems = screenOcrUtil.recognize(resized);
            } catch (Exception e) {
                ocrItems = List.of();
            }

            // 转成给模型看的精简视图
            List<OcrItemView> forModel = ocrItems.stream()
                    .map(i -> new OcrItemView(
                            i.getText(),
                            i.getCenterX(),
                            i.getCenterY(),
                            i.getScore()))
                    .toList();

            String ocrJson = jsonMapper.writeValueAsString(forModel);

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