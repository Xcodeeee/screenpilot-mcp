package com.ssb.screenpilotmcp.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 一条 OCR 结果：文字 + 它在屏幕上的位置。
 * x,y = 包围盒左上角；w,h = 宽高；score = 识别置信度。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OcrItem {
    private String text;
    private int x;
    private int y;
    private int w;
    private int h;
    private float score;

    public int getCenterX() { return x + w / 2; }
    public int getCenterY() { return y + h / 2; }
}