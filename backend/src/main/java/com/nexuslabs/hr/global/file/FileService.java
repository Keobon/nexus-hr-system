package com.nexuslabs.hr.global.file;

import com.nexuslabs.hr.global.auth.LoginUser;
import com.nexuslabs.hr.global.error.BusinessException;
import com.nexuslabs.hr.global.error.ErrorCode;
import com.nexuslabs.hr.global.permission.PermissionReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 파일 저장·내려받기(BR-FILE-001). 디스크에는 무작위 이름(storage_key)으로 저장하고 원래 이름은 DB에만 둔다.
 * 올린 직후에는 어디에도 연결되지 않는다 — 이후 API가 fileId 로 연결한다.
 */
@Service
public class FileService {

    static final long MAX_BYTES = 10L * 1024 * 1024;

    private final JdbcTemplate jdbc;
    private final PermissionReader permissionReader;
    private final List<FileAccessChecker> checkers;
    private final Path baseDir;

    public FileService(JdbcTemplate jdbc, PermissionReader permissionReader, List<FileAccessChecker> checkers,
                       @Value("${app.file.dir}") String fileDir) {
        this.jdbc = jdbc;
        this.permissionReader = permissionReader;
        this.checkers = checkers;
        this.baseDir = Path.of(fileDir).toAbsolutePath().normalize();
    }

    public StoredFile.FileInfo upload(LoginUser user, FilePurpose purpose, MultipartFile file) {
        if (purpose.uploadPermission() != null
                && !permissionReader.permissionsOf(user).containsKey(purpose.uploadPermission())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "파일을 선택하세요");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new BusinessException(ErrorCode.FILE_TOO_LARGE);
        }
        FileType type = detectType(file)
                .filter(purpose.allowedTypes()::contains)
                .orElseThrow(() -> new BusinessException(ErrorCode.FILE_TYPE_NOT_ALLOWED));

        String storageKey = UUID.randomUUID().toString();
        Path target = baseDir.resolve(storageKey);
        try {
            Files.createDirectories(baseDir);
            file.transferTo(target);
        } catch (IOException e) {
            throw new IllegalStateException("파일 저장 실패", e);
        }
        try {
            Long id = jdbc.queryForObject("""
                            INSERT INTO file (company_id, storage_key, original_name, content_type, size_bytes, uploaded_by)
                            VALUES (?, ?, ?, ?, ?, ?) RETURNING id
                            """,
                    Long.class, user.companyId(), storageKey, originalName(file), type.contentType(), file.getSize(),
                    user.employeeId());
            return new StoredFile.FileInfo(id, originalName(file), type.contentType(), file.getSize());
        } catch (RuntimeException e) {
            deleteQuietly(target);
            throw e;
        }
    }

    /** 다른 회사 파일이거나 권한이 없으면 404 — 존재 여부를 알려주지 않는다. */
    public Download download(LoginUser user, long fileId) {
        StoredFile file = find(user.companyId(), fileId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (!canRead(user, file)) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        Path path = baseDir.resolve(file.storageKey());
        if (!Files.exists(path)) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return new Download(file, new FileSystemResource(path));
    }

    /** 다른 도메인에서 fileId 를 연결하기 전에 같은 회사 파일인지 확인할 때 쓴다. */
    public Optional<StoredFile> find(long companyId, long fileId) {
        return jdbc.query("""
                        SELECT id, storage_key, original_name, content_type, size_bytes, uploaded_by
                        FROM file WHERE id = ? AND company_id = ?
                        """,
                (rs, i) -> new StoredFile(rs.getLong("id"), rs.getString("storage_key"),
                        rs.getString("original_name"), rs.getString("content_type"), rs.getLong("size_bytes"),
                        rs.getObject("uploaded_by", Long.class)),
                fileId, companyId).stream().findFirst();
    }

    private boolean canRead(LoginUser user, StoredFile file) {
        boolean referenced = false;
        for (FileAccessChecker checker : checkers) {
            Optional<Boolean> decision = checker.canRead(user, file.id());
            if (decision.isPresent()) {
                if (decision.get()) {
                    return true;
                }
                referenced = true;
            }
        }
        return !referenced && file.uploadedBy() != null && file.uploadedBy() == user.employeeId();
    }

    private static Optional<FileType> detectType(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            return FileType.detect(in.readNBytes(8));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private static String originalName(MultipartFile file) {
        String name = file.getOriginalFilename();
        if (name == null || name.isBlank()) {
            return "file";
        }
        name = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1);
        return name.length() > 255 ? name.substring(name.length() - 255) : name;
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // 고아 파일은 남아도 DB에 연결되지 않으므로 노출되지 않는다
        }
    }

    public record Download(StoredFile file, Resource resource) {
    }
}
