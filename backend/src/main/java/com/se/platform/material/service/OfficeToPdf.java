package com.se.platform.material.service;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * PPT、Word 资料先转成 PDF，再按 PDF 处理（读文字、看图），学生端也直接看转好的 PDF。
 *
 * <p>做法：调本机的 Microsoft Office（PowerShell + COM，脚本 resources/scripts/office_to_pdf.ps1），
 * 转好的 PDF 放在原文件旁边，文件名 = 原文件名 + ".pdf"。同一个文件只转一次。
 * 实测（11-27，本机）：39 页的 pptx 约 27 秒，讲稿 docx 约 13 秒。
 * 本机没有 Office（或不是 Windows）时转不了，告诉老师转成 PDF 再上传。
 */
@Slf4j
@Component
public class OfficeToPdf {

    /** 一份文件最多等 3 分钟 */
    static final long TIMEOUT_SECONDS = 180;

    public static boolean isOffice(String fileName) {
        String n = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        return n.endsWith(".ppt") || n.endsWith(".pptx") || n.endsWith(".doc") || n.endsWith(".docx");
    }

    /** 转好的 PDF 放在哪里（不管转没转过） */
    public static Path pdfPath(Path source) {
        return source.resolveSibling(source.getFileName() + ".pdf");
    }

    /** 转成 PDF，返回 PDF 的路径；已经转过的直接用 */
    public Path convert(Path source) throws MaterialExtractor.ExtractException {
        Path pdf = pdfPath(source);
        if (Files.exists(pdf)) {
            return pdf;
        }
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
            throw new MaterialExtractor.ExtractException("这台服务器不能转换 PPT / Word，请转成 PDF 再上传");
        }
        try {
            Path script = Files.createTempFile("office_to_pdf", ".ps1");
            try (InputStream in = new ClassPathResource("scripts/office_to_pdf.ps1").getInputStream()) {
                Files.copy(in, script, StandardCopyOption.REPLACE_EXISTING);
            }
            Process p = new ProcessBuilder("powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File",
                    script.toString(), source.toAbsolutePath().toString(), pdf.toAbsolutePath().toString())
                    .redirectErrorStream(true).start();
            String out = new String(p.getInputStream().readAllBytes());
            boolean done = p.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            Files.deleteIfExists(script);
            if (!done) {
                p.destroyForcibly();
                throw new MaterialExtractor.ExtractException("PPT / Word 转 PDF 超过 3 分钟，请转成 PDF 再上传");
            }
            if (p.exitValue() != 0 || !Files.exists(pdf)) {
                log.warn("Office 转 PDF 失败：{}", out);
                throw new MaterialExtractor.ExtractException("PPT / Word 转 PDF 失败（这台电脑可能没装 Office），请转成 PDF 再上传");
            }
            return pdf;
        } catch (MaterialExtractor.ExtractException e) {
            throw e;
        } catch (Exception e) {
            throw new MaterialExtractor.ExtractException("PPT / Word 转 PDF 失败：" + e.getMessage());
        }
    }
}
