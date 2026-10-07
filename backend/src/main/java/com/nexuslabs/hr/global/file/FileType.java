package com.nexuslabs.hr.global.file;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;

/**
 * 허용 형식(BR-FILE-001). 클라이언트가 보낸 Content-Type이나 확장자는 믿지 않고 파일 앞부분(매직 바이트)으로 판단한다.
 */
public enum FileType {
    JPEG("image/jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}),
    PNG("image/png", new byte[]{(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'}),
    PDF("application/pdf", new byte[]{'%', 'P', 'D', 'F', '-'});

    static final Set<FileType> ALL = Set.of(JPEG, PNG, PDF);
    static final Set<FileType> IMAGES = Set.of(JPEG, PNG);

    private final String contentType;
    private final byte[] magic;

    FileType(String contentType, byte[] magic) {
        this.contentType = contentType;
        this.magic = magic;
    }

    public String contentType() { return contentType; }

    public static Optional<FileType> detect(byte[] head) {
        return Arrays.stream(values())
                .filter(t -> head.length >= t.magic.length
                        && Arrays.equals(head, 0, t.magic.length, t.magic, 0, t.magic.length))
                .findFirst();
    }
}
