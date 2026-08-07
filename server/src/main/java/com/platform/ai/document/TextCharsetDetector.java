package com.platform.ai.document;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * 文本字符集检测 — TXT/MD 上传可能是 UTF-8 / GBK（中文文档），解析前需确定编码。
 *
 * <p>零第三方依赖启发式：BOM 优先 → 严格 UTF-8 校验通过则 UTF-8 → 含中文高位字节则 GBK → 默认 UTF-8。</p>
 */
public final class TextCharsetDetector {

    /** GBK 字符集 */
    private static final Charset GBK = Charset.forName("GBK");

    /** 工具类，禁止实例化 */
    private TextCharsetDetector() {
    }

    /**
     * 检测字节数组的字符集。
     *
     * @param bytes 源文件字节
     * @return 检测结果字符集（UTF-8 / UTF-16LE / UTF-16BE / GBK / UTF-8 兜底）
     */
    public static Charset detect(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return StandardCharsets.UTF_8;
        }
        // UTF-8 BOM (EF BB BF)
        if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF) {
            return StandardCharsets.UTF_8;
        }
        // UTF-16 LE BOM (FF FE)
        if (bytes.length >= 2 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xFE) {
            return StandardCharsets.UTF_16LE;
        }
        // UTF-16 BE BOM (FE FF)
        if (bytes.length >= 2 && (bytes[0] & 0xFF) == 0xFE && (bytes[1] & 0xFF) == 0xFF) {
            return StandardCharsets.UTF_16BE;
        }
        if (isValidUtf8(bytes)) {
            return StandardCharsets.UTF_8;
        }
        return looksLikeGbk(bytes) ? GBK : StandardCharsets.UTF_8;
    }

    /** 严格 UTF-8 校验：按 UTF-8 序列规则扫描整个字节数组 */
    private static boolean isValidUtf8(byte[] bytes) {
        int i = 0;
        while (i < bytes.length) {
            int b = bytes[i] & 0xFF;
            if (b < 0x80) {
                i++;
                continue;
            }
            int extra;
            int min;
            int max;
            if ((b & 0xE0) == 0xC0) {
                extra = 1;
                min = 0x80;
                max = 0x7FF;
            } else if ((b & 0xF0) == 0xE0) {
                extra = 2;
                min = 0x800;
                max = 0xFFFF;
            } else if ((b & 0xF8) == 0xF0) {
                extra = 3;
                min = 0x10000;
                max = 0x10FFFF;
            } else {
                return false;
            }
            if (i + extra >= bytes.length) {
                return false;
            }
            int code = b & (0xFF >>> (extra + 1));
            for (int j = 1; j <= extra; j++) {
                int cb = bytes[i + j] & 0xFF;
                if ((cb & 0xC0) != 0x80) {
                    return false;
                }
                code = (code << 6) | (cb & 0x3F);
            }
            // 拒绝过短编码、超出 Unicode 上限、以及 UTF-16 代理区
            if (code < min || code > max || (code >= 0xD800 && code <= 0xDFFF)) {
                return false;
            }
            i += extra + 1;
        }
        return true;
    }

    /** GBK 启发式：存在中文高位字节区间（0x81-0xFE）的双字节序列则按 GBK 猜测 */
    private static boolean looksLikeGbk(byte[] bytes) {
        int i = 0;
        while (i < bytes.length) {
            int b = bytes[i] & 0xFF;
            if (b >= 0x81 && b <= 0xFE) {
                // 高位字节后应跟随次字节（0x40-0xFE，不含 0x7F）
                if (i + 1 < bytes.length) {
                    int next = bytes[i + 1] & 0xFF;
                    if (next >= 0x40 && next <= 0xFE && next != 0x7F) {
                        return true;
                    }
                }
                i += 2;
            } else {
                i++;
            }
        }
        return false;
    }
}
