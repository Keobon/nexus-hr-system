package com.nexuslabs.hr.global.file;

/** FILE 테이블 한 행. 응답에는 storageKey·uploadedBy 를 내보내지 않는다(FileInfo 사용). */
public record StoredFile(long id, String storageKey, String originalName, String contentType, long sizeBytes,
                         Long uploadedBy) {

    public FileInfo toInfo() {
        return new FileInfo(id, originalName, contentType, sizeBytes);
    }

    public record FileInfo(long id, String originalName, String contentType, long sizeBytes) {
    }
}
