package com.nexuslabs.hr.domain.org.service;

import com.nexuslabs.hr.domain.org.dto.OrgSettingItemRequest;
import com.nexuslabs.hr.domain.org.dto.OrgSettingItemResponse;
import com.nexuslabs.hr.domain.org.entity.OrgSettingItem;
import com.nexuslabs.hr.domain.org.repository.OrgSettingItemRepository;
import com.nexuslabs.hr.global.auth.LoginUser;
import com.nexuslabs.hr.global.error.BusinessException;
import com.nexuslabs.hr.global.error.ErrorCode;
import com.nexuslabs.hr.global.response.DeleteResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 직급 · 직책 · 고용형태 관리(F-ORG-03·04). 세 가지는 구조와 규칙이 같아 처리를 여기 한 곳에 두고,
 * 종류마다 다른 것(엔티티 생성 · "사용 중" 판정)만 하위 서비스가 정한다.
 */
public abstract class OrgSettingItemService<T extends OrgSettingItem> {

    private final OrgSettingItemRepository<T> repository;
    private final JdbcTemplate jdbc;

    protected OrgSettingItemService(OrgSettingItemRepository<T> repository, JdbcTemplate jdbc) {
        this.repository = repository;
        this.jdbc = jdbc;
    }

    protected abstract T newItem(String name, int sortOrder);

    /**
     * 다른 테이블이 이 항목을 참조하는지 묻는 SQL(결과는 boolean 한 칸).
     * 다른 영역의 테이블을 직접 읽으므로 EXISTS 마다 company_id 조건을 넣는다 — 물음표는 (company_id, 항목 ID) 쌍의 반복이다.
     */
    protected abstract String usageSql();

    @Transactional(readOnly = true)
    public List<OrgSettingItemResponse> list(boolean activeOnly) {
        List<T> items = activeOnly ? repository.findByActiveTrueOrderBySortOrderAscIdAsc()
                : repository.findAllByOrderBySortOrderAscIdAsc();
        return items.stream().map(OrgSettingItemResponse::from).toList();
    }

    @Transactional
    public OrgSettingItemResponse create(OrgSettingItemRequest request) {
        String name = request.name().trim();
        if (repository.existsByName(name)) {
            throw new BusinessException(ErrorCode.DUPLICATE_NAME);
        }
        T item = newItem(name, request.sortOrder());
        if (Boolean.FALSE.equals(request.active())) {
            item.deactivate();
        }
        return OrgSettingItemResponse.from(repository.save(item));
    }

    @Transactional
    public OrgSettingItemResponse update(long id, OrgSettingItemRequest request) {
        T item = get(id);
        String name = request.name().trim();
        if (repository.existsByNameAndIdNot(name, id)) {
            throw new BusinessException(ErrorCode.DUPLICATE_NAME);
        }
        item.update(name, request.sortOrder(), request.active() != null ? request.active() : item.isActive());
        return OrgSettingItemResponse.from(item);
    }

    /** 쓰인 적 있으면 비활성화(DEACTIVATED), 없으면 삭제(DELETED) — BR-ORG-001. */
    @Transactional
    public DeleteResult delete(LoginUser user, long id) {
        T item = get(id);
        if (inUse(user.companyId(), id)) {
            item.deactivate();
            return DeleteResult.deactivated();
        }
        repository.delete(item);
        return DeleteResult.deleted();
    }

    private T get(long id) {
        return repository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    private boolean inUse(long companyId, long id) {
        String sql = usageSql();
        Object[] args = new Object[(int) sql.chars().filter(c -> c == '?').count()];
        for (int i = 0; i < args.length; i += 2) {
            args[i] = companyId;
            args[i + 1] = id;
        }
        return Boolean.TRUE.equals(jdbc.queryForObject(sql, Boolean.class, args));
    }
}
