package com.nexuslabs.hr.domain.account.entity;

import com.nexuslabs.hr.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 역할. system=true 는 최고 관리자 역할로 수정·삭제할 수 없다. */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Role extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String description;

    @Column(name = "is_system")
    private boolean system = false;

    public Role(String name, String description) {
        this.name = name;
        this.description = description;
    }
}
