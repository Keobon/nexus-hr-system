package com.nexuslabs.hr.global.file;

import com.nexuslabs.hr.global.entity.CreatedAtEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 업로드 파일 정보(본문은 서버 디스크). 삭제하지 않는다(BR-FILE-001). */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class File extends CreatedAtEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String storageKey;
    private String originalName;
    private String contentType;
    private long sizeBytes;
    private Long uploadedBy;

    public File(String storageKey, String originalName, String contentType, long sizeBytes, Long uploadedBy) {
        this.storageKey = storageKey;
        this.originalName = originalName;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.uploadedBy = uploadedBy;
    }
}
