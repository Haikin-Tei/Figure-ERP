package com.figureerp.common.storage;

import com.figureerp.common.exception.BusinessException;
import com.figureerp.common.result.ResultCode;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

/**
 * 图片落地与访问。
 * <p>
 * 文件保存位置：{user.dir}/{app.upload.base-dir}/{subDir}/，默认子目录为 products；
 * 通过 WebMvcConfig 里注册的 {@code /uploads/**} 静态资源处理器对外提供访问，
 * 因此返回的 URL 形如 {@code /uploads/products/<uuid>.<ext>}。
 * <p>
 * 校验分两层：content-type 前缀必须是 image/，且扩展名在白名单内；大小不超过配置上限。
 */
@Service
public class FileStorageService {

    private static final List<String> ALLOWED_EXT = List.of("jpg", "jpeg", "png", "gif", "webp");

    @Value("${app.upload.base-dir:uploads}")
    private String baseDir;

    @Value("${app.upload.max-size-mb:5}")
    private long maxSizeMb;

    @Value("${app.upload.allowed-content-types:image/jpeg,image/png,image/gif,image/webp}")
    private List<String> allowedContentTypes;

    /** 上传根目录，形如 server/uploads */
    private Path root;

    @PostConstruct
    void init() {
        // user.dir 在 spring-boot:run 下就是 server/ 目录
        root = Paths.get(System.getProperty("user.dir"), baseDir);
        ensureDir(root.resolve("products"));
    }

    /**
     * 保存一个上传的图片文件，落到默认目录 products，返回可访问的 URL。
     */
    public String store(MultipartFile file) {
        return store(file, "products");
    }

    /**
     * 保存一个上传的图片文件到指定子目录，返回可访问的 URL。
     *
     * @param subDir 子目录名，如 products / acg，只允许字母数字和短横线
     */
    public String store(MultipartFile file, String subDir) {
        String dir = subDir == null || subDir.isBlank() ? "products" : subDir.trim();
        if (!dir.matches("[a-zA-Z0-9_-]+")) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "非法的上传目录：" + dir);
        }
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "上传文件为空");
        }
        if (file.getSize() > maxSizeMb * 1024 * 1024L) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "图片过大，单张不可超过 " + maxSizeMb + " MB");
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/") || !allowedContentTypes.contains(contentType)) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "仅支持 JPG / PNG / GIF / WEBP 图片");
        }

        String original = file.getOriginalFilename();
        String ext = original != null && original.contains(".")
                ? original.substring(original.lastIndexOf('.') + 1).toLowerCase()
                : "jpg";
        if (!ALLOWED_EXT.contains(ext)) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "不支持的图片格式：" + ext);
        }

        String filename = UUID.randomUUID() + "." + ext;
        Path dirPath = ensureDir(root.resolve(dir));
        Path target = dirPath.resolve(filename);
        try {
            file.transferTo(target.toFile());
        } catch (IOException e) {
            throw new BusinessException(ResultCode.SERVER_ERROR, "文件保存失败：" + e.getMessage());
        }
        return "/uploads/" + dir + "/" + filename;
    }

    private Path ensureDir(Path path) {
        try {
            Files.createDirectories(path);
            return path;
        } catch (IOException e) {
            throw new IllegalStateException("无法创建上传目录：" + path, e);
        }
    }
}
