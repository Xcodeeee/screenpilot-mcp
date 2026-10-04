package com.ssb.screenpilotmcp.util;

import com.ssb.screenpilotmcp.dto.OcrItem;
import io.github.lxw112190.ppocr.imageio.PaddleOcrImageIo;
import io.github.lxw112190.ppocr.ppocr.OcrLineResult;
import io.github.lxw112190.ppocr.ppocr.OcrResult;
import io.github.lxw112190.ppocr.ppocr.PaddleOcr;
import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Component
public class ScreenOcrUtil {

    private static final float MIN_CONFIDENCE = 0.5f;

    private final PaddleOcr ocr;

    public ScreenOcrUtil(PaddleOcr ocr) {
        this.ocr = ocr;
    }

    public List<OcrItem> recognize(BufferedImage image) {
        if (image == null) return List.of();

        try {
            // PaddleOcrImageIo 有 BufferedImage 重载，不用写临时文件
            OcrResult result = PaddleOcrImageIo.recognize(ocr, image);

            List<OcrItem> items = new ArrayList<>();
            for (OcrLineResult line : result.getLines()) {
                if (line.getRecognitionScore() < MIN_CONFIDENCE) continue;

                // getPoints() 是 float[8]：[x0,y0, x1,y1, x2,y2, x3,y3]，四边形四个顶点
                float[] p = line.getBox().getPoints();

                // 四点的最小/最大 x、y，转成轴对齐包围盒
                float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE;
                float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
                for (int i = 0; i < p.length; i += 2) {
                    minX = Math.min(minX, p[i]);
                    minY = Math.min(minY, p[i + 1]);
                    maxX = Math.max(maxX, p[i]);
                    maxY = Math.max(maxY, p[i + 1]);
                }

                int x = Math.round(minX);
                int y = Math.round(minY);
                int w = Math.round(maxX - minX);
                int h = Math.round(maxY - minY);

                items.add(new OcrItem(
                        line.getText(),
                        x, y, w, h,
                        line.getRecognitionScore()
                ));
            }

            // 阅读顺序：先上后下，先左后右
            items.sort(Comparator
                    .comparingInt(OcrItem::getY)
                    .thenComparingInt(OcrItem::getX));

            return items;

        } catch (Exception e) {
            return List.of();   // 静默降级
        }
    }
}