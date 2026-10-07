package com.nexuslabs.hr.domain.company.service;

import com.nexuslabs.hr.domain.company.dto.HolidayRequest;
import com.nexuslabs.hr.domain.company.dto.HolidayResponse;
import com.nexuslabs.hr.domain.company.entity.Holiday;
import com.nexuslabs.hr.domain.company.entity.HolidayType;
import com.nexuslabs.hr.domain.company.repository.HolidayRepository;
import com.nexuslabs.hr.global.audit.AuditAction;
import com.nexuslabs.hr.global.audit.AuditLogger;
import com.nexuslabs.hr.global.auth.LoginUser;
import com.nexuslabs.hr.global.error.BusinessException;
import com.nexuslabs.hr.global.error.ErrorCode;
import com.nexuslabs.hr.global.request.PatchRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.MonthDay;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 휴일 관리(F-COMP-05). 매년 반복 휴일은 등록한 해의 날짜로 저장하고, 그해부터 해마다 같은 월·일에 적용한다.
 * 휴일을 바꿔도 이미 신청된 휴가의 일수는 다시 계산하지 않는다(BR-LEAVE-008).
 */
@Service
public class HolidayService {

    private static final Set<String> PATCH_FIELDS = Set.of("holidayDate", "name", "holidayType", "isRecurring");

    private final HolidayRepository holidayRepository;
    private final AuditLogger auditLogger;
    private final Clock clock;

    public HolidayService(HolidayRepository holidayRepository, AuditLogger auditLogger, Clock clock) {
        this.holidayRepository = holidayRepository;
        this.auditLogger = auditLogger;
        this.clock = clock;
    }

    /** 그 해의 휴일(날짜순). 매년 반복 휴일은 그 해 날짜로 펼친다. year 가 null 이면 올해. */
    @Transactional(readOnly = true)
    public List<HolidayResponse> list(Integer year) {
        int target = year != null ? year : LocalDate.now(clock).getYear();
        return holidayRepository.findAll().stream()
                .map(h -> on(h, target))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(HolidayResponse::holidayDate))
                .toList();
    }

    @Transactional
    public HolidayResponse create(LoginUser user, HolidayRequest request) {
        boolean recurring = Boolean.TRUE.equals(request.recurring());
        requireNoOverlap(null, request.holidayDate(), recurring);
        Holiday holiday = holidayRepository.save(new Holiday(request.holidayDate(), request.name().trim(),
                request.holidayType(), recurring));
        HolidayResponse created = HolidayResponse.from(holiday);
        auditLogger.log(user, AuditAction.CREATE, "HOLIDAY", holiday.getId(), null, created);
        return created;
    }

    /** 수정. 보낸 필드만 바꾼다. 네 항목 모두 비울 수 없다. */
    @Transactional
    public HolidayResponse update(LoginUser user, long holidayId, Map<String, Object> patch) {
        PatchRequest.check(patch, PATCH_FIELDS, PATCH_FIELDS);
        Holiday holiday = get(holidayId);
        HolidayResponse before = HolidayResponse.from(holiday);

        LocalDate date = patch.containsKey("holidayDate") ? parseDate(patch.get("holidayDate")) : holiday.getHolidayDate();
        String name = patch.containsKey("name") ? parseName(patch.get("name")) : holiday.getName();
        HolidayType type = patch.containsKey("holidayType") ? parseType(patch.get("holidayType"))
                : holiday.getHolidayType();
        boolean recurring = holiday.isRecurring();
        if (patch.containsKey("isRecurring")) {
            if (!(patch.get("isRecurring") instanceof Boolean value)) {
                throw BusinessException.invalidFields(Map.of("isRecurring", "예 또는 아니오로 입력하세요"));
            }
            recurring = value;
        }
        requireNoOverlap(holidayId, date, recurring);

        holiday.update(date, name, type, recurring);
        HolidayResponse after = HolidayResponse.from(holiday);
        auditLogger.log(user, AuditAction.UPDATE, "HOLIDAY", holidayId, before, after);
        return after;
    }

    @Transactional
    public void delete(LoginUser user, long holidayId) {
        Holiday holiday = get(holidayId);
        HolidayResponse before = HolidayResponse.from(holiday);
        holidayRepository.delete(holiday);
        auditLogger.log(user, AuditAction.DELETE, "HOLIDAY", holidayId, before, null);
    }

    private Holiday get(long holidayId) {
        return holidayRepository.findById(holidayId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    /** 그 해에 적용되는 날짜로 바꾼 응답. 적용되지 않으면 null. */
    private static HolidayResponse on(Holiday holiday, int year) {
        LocalDate date = holiday.getHolidayDate();
        if (!holiday.isRecurring()) {
            return date.getYear() == year ? HolidayResponse.from(holiday) : null;
        }
        MonthDay monthDay = MonthDay.from(date);
        if (year < date.getYear() || !monthDay.isValidYear(year)) {
            return null;
        }
        return HolidayResponse.on(holiday, monthDay.atYear(year));
    }

    /** 같은 날짜가 두 번 휴일이 되면 안 된다 — 매년 반복 휴일은 같은 월·일까지 본다. */
    private void requireNoOverlap(Long exceptId, LocalDate date, boolean recurring) {
        boolean overlaps = holidayRepository.findAll().stream()
                .filter(other -> !other.getId().equals(exceptId))
                .anyMatch(other -> overlaps(date, recurring, other.getHolidayDate(), other.isRecurring()));
        if (overlaps) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "같은 날짜의 휴일이 이미 있습니다");
        }
    }

    static boolean overlaps(LocalDate a, boolean aRecurring, LocalDate b, boolean bRecurring) {
        if (!aRecurring && !bRecurring) {
            return a.equals(b);
        }
        if (!MonthDay.from(a).equals(MonthDay.from(b))) {
            return false;
        }
        if (aRecurring && bRecurring) {
            return true;
        }
        // 한쪽만 반복: 반복 휴일이 시작된 해 이후의 날짜여야 겹친다
        return aRecurring ? b.getYear() >= a.getYear() : a.getYear() >= b.getYear();
    }

    private static LocalDate parseDate(Object value) {
        try {
            return LocalDate.parse(String.valueOf(value));
        } catch (DateTimeParseException e) {
            throw BusinessException.invalidFields(Map.of("holidayDate", "날짜 형식(2026-10-01)으로 입력하세요"));
        }
    }

    private static String parseName(Object value) {
        if (!(value instanceof String text) || text.isBlank() || text.trim().length() > 50) {
            throw BusinessException.invalidFields(Map.of("name", "1자 이상 50자 이하로 입력하세요"));
        }
        return text.trim();
    }

    private static HolidayType parseType(Object value) {
        return Arrays.stream(HolidayType.values()).filter(t -> t.name().equals(value)).findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_ENUM_VALUE,
                        Map.of("allowed", Arrays.stream(HolidayType.values()).map(Enum::name).toList())));
    }
}
