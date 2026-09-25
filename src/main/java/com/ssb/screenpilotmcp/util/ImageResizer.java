package com.ssb.screenpilotmcp.util;

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;

public final class ImageResizer {

    private ImageResizer() {}

    /**
     * 图像处理。
     * 屏幕宽度 <= targetWidth 时返回原图，否则等比缩放到 targetWidth。
     * 判断条件必须和 CoordinateUtil.computeScale 一致。
     */
    public static BufferedImage resize(BufferedImage original, int targetWidth) {
        if (original.getWidth() <= targetWidth) {
            return original;
        }
        int targetHeight = (int) Math.round(
                (double) targetWidth / original.getWidth() * original.getHeight());

        Image scaled = original.getScaledInstance(
                targetWidth, targetHeight, Image.SCALE_SMOOTH);
        BufferedImage result = new BufferedImage(
                targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = result.createGraphics();
        g.drawImage(scaled, 0, 0, null);
        g.dispose();
        return result;
    }
}