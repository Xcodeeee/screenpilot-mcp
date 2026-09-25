package com.ssb.screenpilotmcp.util;

import java.awt.Dimension;
import java.awt.Toolkit;

/**
 * 坐标处理工具类
 * 截图缩放与坐标换算的唯一来源。
 * 截图侧和点击侧必须共用此处的公式，保证严格互逆。
 * 规则：屏幕宽度 <= targetWidth 时不缩放（scale = 1.0），否则缩放到 targetWidth。
 * X、Y 共用同一个 scale（等比缩放）。
 */
public final class CoordinateUtil {

    private CoordinateUtil() {}

    /**
     * 获取缩放比例。
     * 缩放比例由屏幕宽度决定
     * */
    public static double computeScale(int screenWidth, int targetWidth) {
        return screenWidth <= targetWidth ? 1.0 : (double) targetWidth / screenWidth;
    }

    /** 将截图坐标 X 转换成真实屏幕坐标 X */
    public static int toRealX(int x, int screenWidth, int targetWidth) {
        return (int) Math.round(x / computeScale(screenWidth, targetWidth));
    }

    /** 截图坐标 Y 转换成真实屏幕坐标 Y。注意判断依据依然是 screenWidth，不是 screenHeight */
    public static int toRealY(int y, int screenWidth, int targetWidth) {
        return (int) Math.round(y / computeScale(screenWidth, targetWidth));
    }

    public static Dimension screenSize() {
        return Toolkit.getDefaultToolkit().getScreenSize();
    }
}