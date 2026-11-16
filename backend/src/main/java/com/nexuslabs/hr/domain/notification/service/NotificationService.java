package com.nexuslabs.hr.domain.notification.service;

import com.nexuslabs.hr.domain.approval.service.ApprovalStepActivated;
import com.nexuslabs.hr.domain.approval.service.ApprovalWorkType;
import com.nexuslabs.hr.domain.notification.dto.NotificationItem;
import com.nexuslabs.hr.domain.notification.entity.Notification;
import com.nexuslabs.hr.domain.notification.repository.NotificationRepository;
import com.nexuslabs.hr.global.auth.LoginUser;
import com.nexuslabs.hr.global.config.ClockConfig;
import com.nexuslabs.hr.global.error.BusinessException;
import com.nexuslabs.hr.global.error.ErrorCode;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * 결재 알림(B1, 2026-11-16). 결재 단계가 승인 차례가 되면({@link ApprovalStepActivated}) 승인자에게 알림 행을 저장한다 —
 * 결재와 같은 트랜잭션이라 함께 커밋 · 롤백된다. 실시간 전송은 커밋 뒤 {@link NotificationPushListener}가 한다.
 */
@Service
public class NotificationService {

    private final NotificationRepository repository;
    private final NotificationMessages messages;
    private final SseEmitterRegistry registry;
    private final ApplicationEventPublisher events;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public NotificationService(NotificationRepository repository, NotificationMessages messages,
                               SseEmitterRegistry registry, ApplicationEventPublisher events, JdbcTemplate jdbc,
                               Clock clock) {
        this.repository = repository;
        this.messages = messages;
        this.registry = registry;
        this.events = events;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /**
     * 결재 트랜잭션 안에서(동기) 알림 행을 저장한다. 회사는 TenantContext 가 아니라 이벤트의 companyId.
     * 저장한 알림은 {@link NotificationCreated}로 다시 알려, 커밋된 뒤에만 SSE 로 나가게 한다.
     */
    @EventListener
    public void onStepActivated(ApprovalStepActivated e) {
        String linkPath = "/approvals?stepId=" + e.stepId();
        NotificationItem item = jdbc.queryForObject("""
                        INSERT INTO notification (company_id, recipient_id, type, work_type, target_id, approval_step_id,
                                                  title, body, link_path)
                        VALUES (?, ?, ?, ?::approval_work_type, ?, ?, ?, ?, ?)
                        RETURNING""" + COLUMNS,
                MAPPER, e.companyId(), e.approverEmployeeId(), Notification.APPROVAL_REQUESTED, e.workType().name(),
                e.targetId(), e.stepId(), messages.title(e.workType()), messages.body(e), linkPath);
        events.publishEvent(new NotificationCreated(e.companyId(), e.approverEmployeeId(), item));
    }

    /** 내 알림. 안 읽은 것 먼저, 그 안에서 최신 먼저. */
    @Transactional(readOnly = true)
    public PageImpl<NotificationItem> mine(LoginUser user, boolean unreadOnly, Pageable pageable) {
        String where = " WHERE company_id = ? AND recipient_id = ?" + (unreadOnly ? " AND read_at IS NULL" : "");
        long total = jdbc.queryForObject("SELECT count(*) FROM notification" + where, Long.class,
                user.companyId(), user.employeeId());
        List<NotificationItem> rows = jdbc.query("SELECT" + COLUMNS + " FROM notification" + where
                        + " ORDER BY (read_at IS NULL) DESC, created_at DESC, id DESC LIMIT ? OFFSET ?",
                MAPPER, user.companyId(), user.employeeId(), pageable.getPageSize(), pageable.getOffset());
        return new PageImpl<>(rows, pageable, total);
    }

    /** 읽음(멱등 — 이미 읽었으면 처음 읽은 시각 그대로). 남의 알림 · 다른 회사 알림은 404. */
    @Transactional
    public NotificationItem markRead(LoginUser user, long id) {
        Notification n = repository.findById(id)
                .filter(x -> x.getRecipientId() == user.employeeId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        n.markRead(OffsetDateTime.now(clock));
        repository.flush();
        return jdbc.queryForObject("SELECT" + COLUMNS + " FROM notification WHERE id = ? AND company_id = ?",
                MAPPER, id, user.companyId());
    }

    /** 알림 스트림 연결. 연결 직후 안 읽은 개수를 한 번 보낸다(event: unread-count). */
    public SseEmitter connect(LoginUser user) {
        long unread = jdbc.queryForObject(
                "SELECT count(*) FROM notification WHERE company_id = ? AND recipient_id = ? AND read_at IS NULL",
                Long.class, user.companyId(), user.employeeId());
        SseEmitter emitter = registry.connect(user.companyId(), user.employeeId());
        registry.sendTo(user.companyId(), user.employeeId(), emitter, SseEmitterRegistry.EVENT_UNREAD_COUNT,
                Map.of("unreadCount", unread));
        return emitter;
    }

    /** 앞에 공백 하나 — "SELECT" · "RETURNING" 바로 뒤에 붙인다. */
    private static final String COLUMNS = " id, type, work_type::text AS work_type, target_id, approval_step_id, title,"
            + " body, link_path, read_at, created_at";

    private static final RowMapper<NotificationItem> MAPPER = (rs, i) -> {
        String workType = rs.getString("work_type");
        OffsetDateTime readAt = seoul(rs.getObject("read_at", OffsetDateTime.class));
        return new NotificationItem(rs.getLong("id"), rs.getString("type"),
                workType == null ? null : ApprovalWorkType.valueOf(workType),
                rs.getObject("target_id", Long.class), rs.getObject("approval_step_id", Long.class),
                rs.getString("title"), rs.getString("body"), rs.getString("link_path"), readAt != null, readAt,
                seoul(rs.getObject("created_at", OffsetDateTime.class)));
    };

    private static OffsetDateTime seoul(OffsetDateTime t) {
        return t == null ? null : t.atZoneSameInstant(ClockConfig.ZONE).toOffsetDateTime();
    }
}
