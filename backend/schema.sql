-- 1. ENUM 타입 생성
CREATE TYPE emp_status AS ENUM ('ACTIVE', 'ON_LEAVE', 'RESIGNED');
CREATE TYPE account_role AS ENUM ('CEO', 'HR_ADMIN', 'TEAM_LEAD', 'EMPLOYEE');
CREATE TYPE attendance_status AS ENUM ('미기록', '출근', '퇴근', '퇴근미기록', '휴가', '휴직');
CREATE TYPE leave_req_status AS ENUM ('작성중', '승인대기', '승인완료', '반려', '취소요청', '취소완료');
CREATE TYPE insurance_type AS ENUM ('국민연금', '건강보험', '장기요양보험', '고용보험');
CREATE TYPE eval_cycle_status AS ENUM ('평가전', '작성중', '제출완료', '확정', '재오픈');

-- 이미 만들어진 DEPARTMENT 테이블에 division_name(본부명) 컬럼 추가
ALTER TABLE DEPARTMENT ADD COLUMN division_name VARCHAR(50);

-- 2. 마스터 테이블 (의존성 없음)
CREATE TABLE DEPARTMENT (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INT NOT NULL,
    budget DECIMAL(15,2) NOT NULL DEFAULT 0,
    lead_employee_id BIGINT
);

CREATE TABLE JOBGRADE (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL,
    sort_order INT NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE JOBTITLE (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL,
    sort_order INT NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE LEAVETYPE (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE INSURANCERATE (
    id BIGSERIAL PRIMARY KEY,
    insurance_type insurance_type NOT NULL,
    employee_rate DECIMAL(5,4) NOT NULL,
    company_rate DECIMAL(5,4) NOT NULL,
    cap_amount DECIMAL(15,2),
    floor_amount DECIMAL(15,2)
);

CREATE TABLE TAXBRACKET (
    id BIGSERIAL PRIMARY KEY,
    range_start DECIMAL(15,2) NOT NULL,
    range_end DECIMAL(15,2),
    tax_rate DECIMAL(5,4) NOT NULL,
    deduction_amount DECIMAL(15,2) NOT NULL
);

CREATE TABLE EVALUATIONCYCLE (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    status eval_cycle_status NOT NULL
);

CREATE TABLE EVALUATIONCRITERIA (
    id BIGSERIAL PRIMARY KEY,
    category VARCHAR(50) NOT NULL,
    name VARCHAR(100) NOT NULL,
    weight DECIMAL(5,2) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE EVALUATIONQUESTION (
    id BIGSERIAL PRIMARY KEY,
    criteria_id BIGINT NOT NULL REFERENCES EVALUATIONCRITERIA(id),
    question_text VARCHAR(255) NOT NULL,
    sort_order INT NOT NULL
);


-- 3. 핵심 트랜잭션 테이블 (EMPLOYEE)
CREATE TABLE EMPLOYEE (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL,
    email VARCHAR(100) NOT NULL UNIQUE,
    phone VARCHAR(20),
    hire_date DATE NOT NULL,
    status emp_status NOT NULL DEFAULT 'ACTIVE',
    current_dept_id BIGINT NOT NULL REFERENCES DEPARTMENT(id),
    current_grade_id BIGINT NOT NULL REFERENCES JOBGRADE(id),
    current_title_id BIGINT REFERENCES JOBTITLE(id),
    dependents_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE DEPARTMENT ADD CONSTRAINT fk_dept_lead FOREIGN KEY (lead_employee_id) REFERENCES EMPLOYEE(id);


-- 4. 종속 트랜잭션 & Append-only 테이블
CREATE TABLE EMPLOYMENTSTATUSHISTORY (
    id BIGSERIAL PRIMARY KEY,
    employee_id BIGINT NOT NULL REFERENCES EMPLOYEE(id),
    status emp_status NOT NULL,
    effective_date DATE NOT NULL,
    reason VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE ACCOUNT (
    id BIGSERIAL PRIMARY KEY,
    employee_id BIGINT NOT NULL UNIQUE REFERENCES EMPLOYEE(id),
    password_hash VARCHAR(255) NOT NULL,
    role account_role NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE ATTENDANCE (
    id BIGSERIAL PRIMARY KEY,
    employee_id BIGINT NOT NULL REFERENCES EMPLOYEE(id),
    work_date DATE NOT NULL,
    status attendance_status NOT NULL,
    check_in TIMESTAMP,
    check_out TIMESTAMP,
    UNIQUE (employee_id, work_date)
);

CREATE TABLE LEAVEREQUEST (
    id BIGSERIAL PRIMARY KEY,
    employee_id BIGINT NOT NULL REFERENCES EMPLOYEE(id),
    leave_type_id BIGINT NOT NULL REFERENCES LEAVETYPE(id),
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    req_days INT NOT NULL,
    status leave_req_status NOT NULL,
    approver_id BIGINT REFERENCES EMPLOYEE(id),
    reason VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE ASSIGNMENTHISTORY (
    id BIGSERIAL PRIMARY KEY,
    employee_id BIGINT NOT NULL REFERENCES EMPLOYEE(id),
    type VARCHAR(50) NOT NULL,
    post_dept_id BIGINT NOT NULL REFERENCES DEPARTMENT(id),
    post_grade_id BIGINT NOT NULL REFERENCES JOBGRADE(id),
    post_title_id BIGINT REFERENCES JOBTITLE(id),
    effective_date DATE NOT NULL,
    reason VARCHAR(255),
    correction_of_id BIGINT REFERENCES ASSIGNMENTHISTORY(id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE EMPLOYEESALARY (
    id BIGSERIAL PRIMARY KEY,
    employee_id BIGINT NOT NULL REFERENCES EMPLOYEE(id),
    base_pay DECIMAL(15,2) NOT NULL,
    allowance_total DECIMAL(15,2) NOT NULL,
    effective_date DATE NOT NULL,
    reason VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE PAYSTUB (
    id BIGSERIAL PRIMARY KEY,
    employee_id BIGINT NOT NULL REFERENCES EMPLOYEE(id),
    pay_month VARCHAR(7) NOT NULL,
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    pay_date DATE NOT NULL,
    base_pay_snapshot DECIMAL(15,2) NOT NULL,
    allowance_snapshot DECIMAL(15,2) NOT NULL,
    bonus DECIMAL(15,2) NOT NULL DEFAULT 0,
    gross DECIMAL(15,2) NOT NULL,
    pension_deduction DECIMAL(15,2) NOT NULL,
    health_deduction DECIMAL(15,2) NOT NULL,
    longterm_care_deduction DECIMAL(15,2) NOT NULL,
    employment_ins_deduction DECIMAL(15,2) NOT NULL,
    income_tax DECIMAL(15,2) NOT NULL,
    local_income_tax DECIMAL(15,2) NOT NULL,
    net DECIMAL(15,2) NOT NULL,
    company_ins_total DECIMAL(15,2) NOT NULL DEFAULT 0,
    correction_of_id BIGINT REFERENCES PAYSTUB(id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (employee_id, pay_month)
);

CREATE TABLE EVALUATION (
    id BIGSERIAL PRIMARY KEY,
    cycle_id BIGINT NOT NULL REFERENCES EVALUATIONCYCLE(id),
    target_employee_id BIGINT NOT NULL REFERENCES EMPLOYEE(id),
    evaluator_employee_id BIGINT NOT NULL REFERENCES EMPLOYEE(id),
    status eval_cycle_status NOT NULL,
    overall_comment TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (cycle_id, target_employee_id)
);

CREATE TABLE EVALUATIONANSWER (
    id BIGSERIAL PRIMARY KEY,
    evaluation_id BIGINT NOT NULL REFERENCES EVALUATION(id),
    question_id BIGINT NOT NULL REFERENCES EVALUATIONQUESTION(id),
    score INT NOT NULL CHECK (score >= 1 AND score <= 5),
    UNIQUE (evaluation_id, question_id)
);
