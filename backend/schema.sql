-- 1. ENUM 타입 생성 (한글 -> 영문 코드로 변경됨)
CREATE TYPE emp_status AS ENUM ('ACTIVE', 'ON_LEAVE', 'RESIGNED');
CREATE TYPE account_role AS ENUM ('CEO', 'VP', 'HR_ADMIN', 'TEAM_LEAD', 'EMPLOYEE');
CREATE TYPE attendance_status AS ENUM ('NOT_RECORDED', 'CHECKED_IN', 'CHECKED_OUT', 'MISSING_CHECKOUT', 'ON_VACATION', 'ON_LEAVE');
CREATE TYPE leave_req_status AS ENUM ('DRAFT', 'PENDING', 'APPROVED', 'REJECTED', 'CANCEL_REQUESTED', 'CANCELLED');
CREATE TYPE insurance_type AS ENUM ('NATIONAL_PENSION', 'HEALTH', 'LONG_TERM_CARE', 'EMPLOYMENT');
CREATE TYPE evaluation_status AS ENUM ('NOT_STARTED', 'IN_PROGRESS', 'SUBMITTED', 'CONFIRMED', 'REOPENED');
CREATE TYPE eval_cycle_state AS ENUM ('SCHEDULED', 'IN_PROGRESS', 'CLOSED');
CREATE TYPE assignment_type AS ENUM ('TRANSFER', 'PROMOTION', 'TITLE_CHANGE');


-- 2. 마스터 테이블 (의존성 없음)
CREATE TABLE DIVISION (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL,
    sort_order INT NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE DEPARTMENT (
    id BIGSERIAL PRIMARY KEY,
    division_id BIGINT NOT NULL REFERENCES DIVISION(id),
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
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    deducts_balance BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE INSURANCERATE (
    id BIGSERIAL PRIMARY KEY,
    insurance_type insurance_type NOT NULL,
    employee_rate DECIMAL(6,4) NOT NULL,   -- 단위: % (4.5000 = 4.5%). 계산 시 ÷100 필수
    company_rate  DECIMAL(6,4) NOT NULL,   -- 단위: % (동일)
    cap_amount DECIMAL(15,2),
    floor_amount DECIMAL(15,2)
);

CREATE TABLE TAXBRACKET (
    id BIGSERIAL PRIMARY KEY,
    range_start DECIMAL(15,2) NOT NULL,
    range_end DECIMAL(15,2),
    tax_rate DECIMAL(6,4) NOT NULL,        -- 단위: % (38.0000 = 38%). 계산 시 ÷100 필수
    deduction_amount DECIMAL(15,2) NOT NULL
);

CREATE TABLE EVALUATIONCYCLE (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    status eval_cycle_state NOT NULL
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
    type assignment_type NOT NULL,
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
    status evaluation_status NOT NULL,
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



-- ==========================================
-- 💡 기초 시드 데이터 (INSERT 스크립트)
-- ==========================================

-- 1. DIVISION (5개 본부)
INSERT INTO DIVISION (name, sort_order) VALUES
('경영지원본부', 1), ('R&D본부', 2), ('서비스기획본부', 3), ('비즈니스본부', 4), ('운영본부', 5);

-- 2. JOBGRADE (6개 직급) / JOBTITLE (3개 직책) / LEAVETYPE (3개 휴가)
INSERT INTO JOBGRADE (name, sort_order) VALUES
('사원', 1), ('주임', 2), ('대리', 3), ('과장', 4), ('부장', 5), ('임원', 6);

INSERT INTO JOBTITLE (name, sort_order) VALUES
('팀장', 1), ('부사장', 2), ('대표이사', 3);

INSERT INTO LEAVETYPE (name, deducts_balance) VALUES
('연차', TRUE), ('병가', FALSE), ('경조사', FALSE);

-- 3. DEPARTMENT (12개 실무 부서, lead_employee_id는 일단 생략)
INSERT INTO DEPARTMENT (division_id, name, sort_order, budget) VALUES
(1, '인사총무팀', 1, 27000000), (1, '재무회계팀', 2, 22000000), (1, '법무감사팀', 3, 14000000),
(2, '프론트엔드개발팀', 4, 37000000), (2, '백엔드개발팀', 5, 43000000), (2, '인프라보안팀', 6, 24000000),
(3, '프로덕트기획팀', 7, 26000000), (3, 'UI/UX디자인팀', 8, 21000000),
(4, 'B2B영업팀', 9, 34000000), (4, '마케팅홍보팀', 10, 21000000),
(5, '품질보증팀', 11, 17000000), (5, '고객지원팀', 12, 12000000);


-- 4. EMPLOYEE & ACCOUNT 필수 생성 (CEO, VP, 각 팀장 12명 + HR_ADMIN = 총 15명)
-- 4.1. CEO (인사총무팀 소속 / 임원 / 대표이사)
INSERT INTO EMPLOYEE (id, name, email, hire_date, current_dept_id, current_grade_id, current_title_id) 
VALUES (1, '대표이사', 'ceo@nexuslabs.com', CURRENT_DATE, 1, 6, 3);
INSERT INTO ACCOUNT (employee_id, password_hash, role) VALUES (1, 'hashed_pw', 'CEO');

-- 4.2. VP (인사총무팀 소속 / 임원 / 부사장)
INSERT INTO EMPLOYEE (id, name, email, hire_date, current_dept_id, current_grade_id, current_title_id) 
VALUES (2, '부사장', 'vp@nexuslabs.com', CURRENT_DATE, 1, 6, 2);
INSERT INTO ACCOUNT (employee_id, password_hash, role) VALUES (2, 'hashed_pw', 'VP');

-- 4.3. 12개 부서의 팀장 12명 (부장 / 팀장)
INSERT INTO EMPLOYEE (id, name, email, hire_date, current_dept_id, current_grade_id, current_title_id) VALUES 
(3, '인사팀장', 'hr_lead@nexuslabs.com', CURRENT_DATE, 1, 5, 1),
(4, '재무팀장', 'fin_lead@nexuslabs.com', CURRENT_DATE, 2, 5, 1),
(5, '법무팀장', 'legal_lead@nexuslabs.com', CURRENT_DATE, 3, 5, 1),
(6, 'FE팀장', 'fe_lead@nexuslabs.com', CURRENT_DATE, 4, 5, 1),
(7, 'BE팀장', 'be_lead@nexuslabs.com', CURRENT_DATE, 5, 5, 1),
(8, '인프라팀장', 'infra_lead@nexuslabs.com', CURRENT_DATE, 6, 5, 1),
(9, '기획팀장', 'pm_lead@nexuslabs.com', CURRENT_DATE, 7, 5, 1),
(10, '디자인팀장', 'design_lead@nexuslabs.com', CURRENT_DATE, 8, 5, 1),
(11, '영업팀장', 'sales_lead@nexuslabs.com', CURRENT_DATE, 9, 5, 1),
(12, '마케팅팀장', 'mkt_lead@nexuslabs.com', CURRENT_DATE, 10, 5, 1),
(13, 'QA팀장', 'qa_lead@nexuslabs.com', CURRENT_DATE, 11, 5, 1),
(14, 'CS팀장', 'cs_lead@nexuslabs.com', CURRENT_DATE, 12, 5, 1);

INSERT INTO ACCOUNT (employee_id, password_hash, role) 
SELECT id, 'hashed_pw', 'TEAM_LEAD' FROM EMPLOYEE WHERE id >= 3 AND id <= 14;

-- 4.4. HR_ADMIN (인사총무팀 소속 / 과장 / 직책 없음)
INSERT INTO EMPLOYEE (id, name, email, hire_date, current_dept_id, current_grade_id, current_title_id)
VALUES (15, '인사담당자', 'hr_admin@nexuslabs.com', CURRENT_DATE, 1, 4, NULL);
INSERT INTO ACCOUNT (employee_id, password_hash, role) VALUES (15, 'hashed_pw', 'HR_ADMIN');

-- 시퀀스 수동 동기화
SELECT setval('employee_id_seq', 15); 


-- 5. DEPARTMENT 팀장 지정 (결재 라우팅 핵심!)
UPDATE DEPARTMENT SET lead_employee_id = 3 WHERE id = 1;
UPDATE DEPARTMENT SET lead_employee_id = 4 WHERE id = 2;
UPDATE DEPARTMENT SET lead_employee_id = 5 WHERE id = 3;
UPDATE DEPARTMENT SET lead_employee_id = 6 WHERE id = 4;
UPDATE DEPARTMENT SET lead_employee_id = 7 WHERE id = 5;
UPDATE DEPARTMENT SET lead_employee_id = 8 WHERE id = 6;
UPDATE DEPARTMENT SET lead_employee_id = 9 WHERE id = 7;
UPDATE DEPARTMENT SET lead_employee_id = 10 WHERE id = 8;
UPDATE DEPARTMENT SET lead_employee_id = 11 WHERE id = 9;
UPDATE DEPARTMENT SET lead_employee_id = 12 WHERE id = 10;
UPDATE DEPARTMENT SET lead_employee_id = 13 WHERE id = 11;
UPDATE DEPARTMENT SET lead_employee_id = 14 WHERE id = 12;


-- 6. 보험 요율 (영문 ENUM 코드로 변경됨)
INSERT INTO INSURANCERATE (insurance_type, employee_rate, company_rate) VALUES
('NATIONAL_PENSION', 4.5000, 4.5000),
('HEALTH',           3.5450, 3.5450),
('LONG_TERM_CARE',   0.4591, 0.4591),   -- 3.545% × 12.95%, Gross 기준으로 환산
('EMPLOYMENT',       0.9000, 1.1500);

-- 7. TAXBRACKET (월 과세표준 기준, 단순화 누진세율 5구간)
INSERT INTO TAXBRACKET (range_start, range_end, tax_rate, deduction_amount) VALUES
(0,          1200000,   6.0000,  0),
(1200000,    4200000,  15.0000,  108000),
(4200000,    7400000,  24.0000,  486000),
(7400000,   12500000,  35.0000,  1300000),
(12500000,  NULL,      38.0000,  1675000);
