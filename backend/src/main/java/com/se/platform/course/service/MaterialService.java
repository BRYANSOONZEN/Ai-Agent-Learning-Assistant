package com.se.platform.course.service;

import com.se.platform.common.BusinessException;
import com.se.platform.course.dto.MaterialView;
import com.se.platform.course.entity.Chapter;
import com.se.platform.course.entity.Material;
import com.se.platform.course.mapper.MaterialMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * 教学资料文件：保存、读取、删除。由视频 / 文档内容项调用（ChapterItemService），不单独对外提供上传。
 *
 * <p>文件存在磁盘的 uploads/yyyy/MM/&lt;uuid&gt;.&lt;扩展名&gt;，不用原文件名存盘，
 * 防止重名覆盖和路径穿越（例如文件名里带 ../）。表里只存相对路径。
 */
@Service
public class MaterialService {

    /** 允许上传的扩展名：课件、文档、图片、视频 */
    private static final Set<String> ALLOWED = Set.of(
            "pdf", "ppt", "pptx", "doc", "docx", "md", "txt", "png", "jpg", "jpeg", "mp4", "webm");

    private final MaterialMapper materialMapper;
    private final ChapterService chapterService;
    private final Path root;

    /** {@code @Value}：从配置读 app.upload-dir，没配就用 uploads（相对后端的启动目录） */
    public MaterialService(MaterialMapper materialMapper, ChapterService chapterService,
                           @Value("${app.upload-dir:uploads}") String uploadDir) {
        this.materialMapper = materialMapper;
        this.chapterService = chapterService;
        this.root = Path.of(uploadDir).toAbsolutePath().normalize();
    }

    /** 保存一个文件。video = true 只收视频，false 只收视频以外的文件。调用方负责检查权限 */
    public MaterialView save(Chapter chapter, MultipartFile file, boolean video) {
        if (file == null || file.isEmpty()) {
            throw BusinessException.badRequest("请选择文件");
        }
        String originalName = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename();
        String ext = extension(originalName);
        if (!ALLOWED.contains(ext)) {
            throw BusinessException.badRequest("不支持的文件类型：." + ext + "，只允许 " + String.join(" ", ALLOWED));
        }
        if (kind(ext).equals("video") != video) {
            throw BusinessException.badRequest(video ? "这里只能上传视频（mp4 / webm）" : "视频请放到视频内容项里");
        }

        LocalDate today = LocalDate.now();
        String relative = String.format("%d/%02d/%s.%s", today.getYear(), today.getMonthValue(), UUID.randomUUID(), ext);
        Path target = root.resolve(relative);
        try (InputStream in = file.getInputStream()) {
            Files.createDirectories(target.getParent());
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("保存文件失败", e);
        }

        Material m = new Material();
        m.setCourseId(chapter.getCourseId());
        m.setChapterId(chapter.getId());
        m.setTitle(stripExtension(originalName));
        m.setOriginalName(originalName);
        m.setFilePath(relative);
        m.setFileSize(file.getSize());
        m.setContentType(contentType(ext, file.getContentType()));
        m.setUploadedBy(chapterService.currentTeacherId());
        materialMapper.insert(m);
        return toView(m);
    }

    public Material require(Long id) {
        Material m = id == null ? null : materialMapper.selectById(id);
        if (m == null) {
            throw BusinessException.notFound("资料");
        }
        return m;
    }

    /** 磁盘上的文件位置；文件丢了也按 404 处理 */
    public Path file(Material m) {
        Path path = root.resolve(m.getFilePath()).normalize();
        if (!path.startsWith(root) || !Files.exists(path)) {
            throw BusinessException.notFound("文件");
        }
        return path;
    }

    /** 文件丢了（被删）就返回 null，内容项显示为"没有文件" */
    public MaterialView view(Long id) {
        Material m = materialMapper.selectById(id);
        return m == null ? null : toView(m);
    }

    /** 逻辑删除，磁盘文件先保留（以后可以恢复）。调用方负责检查权限 */
    public void delete(Long id) {
        materialMapper.deleteById(id);
    }

    private MaterialView toView(Material m) {
        return new MaterialView(m.getId(), m.getChapterId(), m.getTitle(), m.getOriginalName(), m.getFileSize(),
                m.getContentType(), kind(extension(m.getOriginalName())), "/api/materials/" + m.getId() + "/file",
                m.getCreatedAt());
    }

    private static String kind(String ext) {
        return switch (ext) {
            case "mp4", "webm" -> "video";
            case "pdf" -> "pdf";
            case "png", "jpg", "jpeg" -> "image";
            case "md", "txt" -> "text";
            case "ppt", "pptx", "doc", "docx" -> "office";
            default -> "other";
        };
    }

    /** 按扩展名给 Content-Type，浏览器才知道能不能直接预览（不完全相信上传时浏览器报的类型） */
    private static String contentType(String ext, String fallback) {
        return switch (ext) {
            case "pdf" -> "application/pdf";
            case "mp4" -> "video/mp4";
            case "webm" -> "video/webm";
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "txt", "md" -> "text/plain;charset=UTF-8";
            default -> fallback == null ? "application/octet-stream" : fallback;
        };
    }

    private static String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot <= 0 ? name : name.substring(0, dot);
    }
}
