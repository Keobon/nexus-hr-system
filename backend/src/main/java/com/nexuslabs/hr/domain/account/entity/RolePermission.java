package com.nexuslabs.hr.domain.account.entity;

import com.nexuslabs.hr.global.entity.TenantEntity;
import com.nexuslabs.hr.global.permission.PermissionCode;
import com.nexuslabs.hr.global.permission.PermissionScope;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 역할별 권한(부록 A). 행이 있으면 그 권한을 쓴다. */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RolePermission extends TenantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id")
    private Role role;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(columnDefinition = "permission_code")
    private PermissionCode permissionCode;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(columnDefinition = "perm_scope")
    private PermissionScope scope;

    public RolePermission(Role role, PermissionCode permissionCode, PermissionScope scope) {
        this.role = role;
        this.permissionCode = permissionCode;
        this.scope = scope;
    }
}
