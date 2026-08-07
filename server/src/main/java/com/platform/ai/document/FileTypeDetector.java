package com.platform.ai.document;

import com.platform.common.KnowledgeFileType;

import java.nio.charset.StandardCharsets;

/**
 * 文件类型魔数校验 — 上传时按文件头确认真实类型，防止伪装扩展名（防存储型恶意文件）。
 *
 * <p>规则：PDF 以 {@code %PDF-} 开头；docx/xlsx 为 ZIP 容器（{@code PK} 头）；
 * md/txt/csv 为纯文本（前 8KB 不含 NUL 字节）。魔数与声明扩展名不符即拒绝。</p>
 */
public final class FileTypeDetector {

    /** 工具类，禁止实例化 */
    private FileTypeDetector() {
    }

    /**
     * 按扩展名识别声明类型，并用文件头魔数校验真实类型。
     *
     * @param fileName 原始文件名（含扩展名）
     * @param head     文件头字节（前 8~64 字节即可，最多读 8KB 判文本）
     * @return 校验通过的文件类型
     * @throws IllegalArgumentException 扩展名不在白名单、或魔数与声明类型不符时抛出
     */
    public static String resolveAndValidate(String fileName, byte[] head) {
        String declared = KnowledgeFileType.fromFileName(fileName);
        if (declared == null) {
            throw new IllegalArgumentException("不支持的文件类型，仅支持 md/txt/pdf/docx/xlsx/csv");
        }
        if (head == null || head.length == 0) {
            throw new IllegalArgumentException("文件内容为空");
        }
        boolean matched;
        switch (declared) {
            case KnowledgeFileType.PDF:
                matched = startsWith(head, "%PDF-");
                break;
            case KnowledgeFileType.DOCX:
            case KnowledgeFileType.XLSX:
                // OOXML 文档均为 ZIP 容器（PK 头）；docx/xlsx 细分由解析器读取包内目录确认
                matched = startsWith(head, "PK");
                break;
            default:
                // md/txt/csv 纯文本：含 NUL 字节视为二进制伪装
                matched = !containsNul(head);
                break;
        }
        if (!matched) {
            throw new IllegalArgumentException("文件内容与扩展名不符，请确认文件未损坏或伪装后缀");
        }
        return declared;
    }

    /** 判断字节数组是否以指定 ASCII 前缀开头（逐字节比较） */
    private static boolean startsWith(byte[] data, String prefix) {
        byte[] p = prefix.getBytes(StandardCharsets.US_ASCII);
        if (data.length < p.length) {
            return false;
        }
        for (int i = 0; i < p.length; i++) {
            if (data[i] != p[i]) {
                return false;
            }
        }
        return true;
    }

    /** 前 8KB 内是否含 NUL 字节（纯文本文件不应出现） */
    private static boolean containsNul(byte[] data) {
        int limit = Math.min(data.length, 8192);
        for (int i = 0; i < limit; i++) {
            if (data[i] == 0) {
                return true;
            }
        }
        return false;
    }
}
