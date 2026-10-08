package com.nexuslabs.hr.global;

import com.nexuslabs.hr.global.error.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

/** DB 제약 이름 → 에러 코드 매핑. 동시에 두 번 누른 요청이 500이 아니라 업무 에러로 나가야 한다. */
class IntegrityMappingTest {

    @Test
    void 같은_날_근태_두_번이면_ATT_ALREADY_CHECKED_IN() {
        var e = new DataIntegrityViolationException("insert", new SQLException(
                "ERROR: duplicate key value violates unique constraint \"attendance_employee_id_work_date_key\""));
        var response = new GlobalExceptionHandler().handleIntegrity(e);
        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody().error().code()).isEqualTo("ATT_ALREADY_CHECKED_IN");
    }
}
