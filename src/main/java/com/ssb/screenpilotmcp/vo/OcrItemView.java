package com.ssb.screenpilotmcp.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 给模型看的 OCR 条目
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OcrItemView {
    //识别到的文字
    private String text;
    //文字中心点坐标
    private int centerX;
    private int centerY;
    //置信度
    private float score;
}