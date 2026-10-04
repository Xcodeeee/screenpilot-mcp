package com.ssb.screenpilotmcp.vo;

import com.ssb.screenpilotmcp.dto.OcrItem;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 屏幕截图结果：截图 base64 + OCR 识别出的文字及坐标。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScreenCaptureResult {
    // PNG 图片的 base64 编码，不带 data URI 前缀
    private String imageBase64;

    // OCR 识别出的文字项，按阅读顺序排列
    private List<OcrItem> ocrItems;
}