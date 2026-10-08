-- =====================================================================
--  인사관리 SaaS — schema.sql v2 (PostgreSQL 14+)
--  기준: Notion 🧬 ERD 설계서 v2 (테이블 49개) / 📄 기능명세서 v2
--  실행: psql -d nexus_hr -v ON_ERROR_STOP=1 -f schema.sql
--  구성: 0 확장·공통 함수  1 ENUM  2 회사·공통  3 계정·권한  4 조직
--        5 직원  6 근태·연장·출장  7 휴가  8 승인  9 급여  10 발령
--        11 평가  12 지연 FK  13 append-only 보호 트리거
--  원칙: 회사(COMPANY)를 뺀 모든 테이블에 company_id.
--        업무 계산은 애플리케이션이 한다. DB 트리거는 "쓰기 금지" 보호만 둔다.
-- =====================================================================

BEGIN;

-- ---------------------------------------------------------------------
-- 0. 확장 · 공통 함수
-- ---------------------------------------------------------------------
CREATE EXTENSION IF NOT EXISTS btree_gist;   -- 기간 겹침 금지(EXCLUDE)용

-- append-only 테이블의 UPDATE/DELETE 차단
CREATE OR REPLACE FUNCTION fn_block_write() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
  RAISE EXCEPTION '% 테이블은 append-only 입니다 (% 불가)', TG_TABLE_NAME, TG_OP
    USING ERRCODE = 'restrict_violation';
END $$;

-- WORK_SCHEDULE: UPDATE 금지, 시작 전(미래) 행만 DELETE 허용
CREATE OR REPLACE FUNCTION fn_work_schedule_guard() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
  IF TG_OP = 'UPDATE' THEN
    RAISE EXCEPTION 'work_schedule 은 수정할 수 없습니다. 새 적용 시작일로 행을 추가하세요'
      USING ERRCODE = 'restrict_violation';
  END IF;
  IF OLD.effective_from <= CURRENT_DATE THEN
    RAISE EXCEPTION '이미 시작된 근무시간은 삭제할 수 없습니다'
      USING ERRCODE = 'restrict_violation';
  END IF;
  RETURN OLD;
END $$;

-- ---------------------------------------------------------------------
-- 1. ENUM (시스템이 정한 코드만. 회사가 추가하는 값은 테이블 행)
-- ---------------------------------------------------------------------
CREATE TYPE permission_code AS ENUM (
  'COMPANY_MANAGE','AUDIT_READ','ROLE_MANAGE','APPROVAL_MANAGE','ORG_MANAGE',
  'EMPLOYEE_READ','EMPLOYEE_MANAGE','ATTENDANCE_READ','ATTENDANCE_MANAGE',
  'LEAVE_READ','LEAVE_MANAGE','PAYROLL_READ','PAYROLL_MANAGE',
  'ASSIGNMENT_READ','ASSIGNMENT_MANAGE','EVAL_READ','EVAL_MANAGE','DASHBOARD_COMPANY');
CREATE TYPE perm_scope           AS ENUM ('TEAM','ALL');
CREATE TYPE audit_action         AS ENUM ('CREATE','UPDATE','DELETE','EXECUTE','VIEW');
CREATE TYPE company_field        AS ENUM ('NAME','CEO_NAME','BUSINESS_REG_NO','ADDRESS');
CREATE TYPE company_doc_type     AS ENUM ('BUSINESS_REGISTRATION','CORP_REGISTRATION','BANK_ACCOUNT_COPY','OTHER');
CREATE TYPE employee_doc_type    AS ENUM ('LABOR_CONTRACT','BANKBOOK_COPY','RESUME','FAMILY_CERT','HEALTH_CHECK','OTHER');
CREATE TYPE holiday_type         AS ENUM ('PUBLIC','COMPANY','SUBSTITUTE');
CREATE TYPE emp_status           AS ENUM ('ACTIVE','ON_LEAVE','RESIGNED');
CREATE TYPE gender               AS ENUM ('MALE','FEMALE');
CREATE TYPE field_type           AS ENUM ('TEXT','LONG_TEXT','NUMBER','DATE','SELECT');
CREATE TYPE family_relation      AS ENUM ('SPOUSE','CHILD','PARENT','SIBLING','OTHER');
CREATE TYPE attendance_status    AS ENUM ('CHECKED_IN','CHECKED_OUT','MISSING_CHECKOUT','ON_VACATION','ON_BUSINESS_TRIP');
CREATE TYPE work_type            AS ENUM ('OFFICE','REMOTE','FIELD','BUSINESS_TRIP');
CREATE TYPE record_method        AS ENUM ('WEB','MOBILE','ADMIN','SYSTEM');
CREATE TYPE request_status       AS ENUM ('PENDING','APPROVED','REJECTED','CANCELLED');
CREATE TYPE trip_type            AS ENUM ('DOMESTIC','OVERSEAS');
CREATE TYPE leave_status         AS ENUM ('PENDING','APPROVED','REJECTED','CANCEL_REQUESTED','CANCELLED');
CREATE TYPE leave_grant_type     AS ENUM ('REGULAR','HIRE','ADJUSTMENT');
CREATE TYPE approval_work_type   AS ENUM ('LEAVE','LEAVE_CANCEL','OVERTIME','BUSINESS_TRIP','TRIP_EXPENSE');
CREATE TYPE approver_type        AS ENUM ('ORG_LEAD','ORG_LEAD_UP','JOB_TITLE','EMPLOYEE');
CREATE TYPE approval_step_status AS ENUM ('WAITING','PENDING','APPROVED','REJECTED','SKIPPED','CANCELLED');
CREATE TYPE pay_item_kind        AS ENUM ('EARNING','DEDUCTION');
CREATE TYPE pay_calc_method      AS ENUM ('FIXED','BASE_RATE','MANUAL','TAXABLE_RATE','ATTENDANCE','TRIP_EXPENSE');
CREATE TYPE pay_apply_to         AS ENUM ('ALL','SELECTED');
CREATE TYPE attendance_basis     AS ENUM ('OVERTIME','NIGHT','HOLIDAY','HOLIDAY_OVERTIME','ABSENCE');
CREATE TYPE pay_var_code         AS ENUM ('DEPENDENT_DEDUCTION','MULTI_CHILD_DEDUCTION','LOCAL_TAX_RATE','ANNUAL_SPLIT_MONTHS','MONTHLY_STANDARD_HOURS');
CREATE TYPE salary_type          AS ENUM ('MONTHLY','ANNUAL');
CREATE TYPE payroll_status       AS ENUM ('CONFIRMED','PAID');
CREATE TYPE assignment_type      AS ENUM ('TRANSFER','PROMOTION','TITLE_CHANGE');
CREATE TYPE eval_cycle_status    AS ENUM ('SCHEDULED','IN_PROGRESS','CLOSED');
CREATE TYPE evaluation_status    AS ENUM ('NOT_STARTED','IN_PROGRESS','SUBMITTED','CONFIRMED','REOPENED');

-- ---------------------------------------------------------------------
-- 2. 회사 · 공통
-- ---------------------------------------------------------------------
CREATE TABLE company (
  id                      BIGSERIAL PRIMARY KEY,
  name                    VARCHAR(100) NOT NULL,
  name_en                 VARCHAR(100),
  business_reg_no         VARCHAR(12)  NOT NULL UNIQUE CHECK (business_reg_no ~ '^\d{3}-\d{2}-\d{5}$'),
  corp_reg_no             VARCHAR(14)  CHECK (corp_reg_no IS NULL OR corp_reg_no ~ '^\d{6}-\d{7}$'),
  ceo_name                VARCHAR(50)  NOT NULL,
  address                 VARCHAR(255) NOT NULL,
  phone                   VARCHAR(20)  NOT NULL,
  fax                     VARCHAR(20),
  email                   VARCHAR(100) NOT NULL,
  website                 VARCHAR(255),
  business_type           VARCHAR(100),
  business_item           VARCHAR(100),
  founded_date            DATE,
  logo_file_id            BIGINT,                                   -- FK는 12장
  pay_day                 SMALLINT NOT NULL DEFAULT 25 CHECK (pay_day BETWEEN 1 AND 31),
  employee_no_prefix      VARCHAR(10) CHECK (employee_no_prefix IS NULL OR employee_no_prefix ~ '^[A-Za-z0-9-]+$'),
  fiscal_year_start_month SMALLINT NOT NULL DEFAULT 1 CHECK (fiscal_year_start_month BETWEEN 1 AND 12),
  setup_completed         BOOLEAN  NOT NULL DEFAULT FALSE,
  created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);
COMMENT ON TABLE company IS '회사(테넌트). company_id 가 없는 유일한 테이블';

CREATE TABLE file (
  id            BIGSERIAL PRIMARY KEY,
  company_id    BIGINT NOT NULL REFERENCES company(id),
  storage_key   VARCHAR(255) NOT NULL UNIQUE,
  original_name VARCHAR(255) NOT NULL,
  content_type  VARCHAR(100) NOT NULL CHECK (content_type IN ('image/jpeg','image/png','application/pdf')),
  size_bytes    BIGINT NOT NULL CHECK (size_bytes > 0 AND size_bytes <= 10485760),
  uploaded_by   BIGINT,                                             -- FK는 12장
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
COMMENT ON TABLE file IS '업로드 파일 정보(본문은 서버 디스크). 삭제하지 않는다';

CREATE TABLE company_document (
  id          BIGSERIAL PRIMARY KEY,
  company_id  BIGINT NOT NULL REFERENCES company(id),
  doc_type    company_doc_type NOT NULL,
  doc_name    VARCHAR(100),
  file_id     BIGINT NOT NULL REFERENCES file(id),
  issued_date DATE,
  expires_at  DATE,
  version_no  INT NOT NULL CHECK (version_no >= 1),
  is_current  BOOLEAN NOT NULL DEFAULT TRUE,
  memo        VARCHAR(255),
  uploaded_by BIGINT NOT NULL,                                      -- FK는 12장
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK (doc_type <> 'OTHER' OR doc_name IS NOT NULL)
);
CREATE UNIQUE INDEX ux_company_document_current
  ON company_document (company_id, doc_type, COALESCE(doc_name, '')) WHERE is_current;

CREATE TABLE company_change_history (
  id             BIGSERIAL PRIMARY KEY,
  company_id     BIGINT NOT NULL REFERENCES company(id),
  field          company_field NOT NULL,
  old_value      VARCHAR(255),
  new_value      VARCHAR(255) NOT NULL,
  effective_date DATE NOT NULL,
  reason         VARCHAR(255) NOT NULL,
  document_id    BIGINT REFERENCES company_document(id),
  changed_by     BIGINT NOT NULL,                                   -- FK는 12장
  created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_company_change_history ON company_change_history (company_id, created_at DESC);

CREATE TABLE audit_log (
  id           BIGSERIAL PRIMARY KEY,
  company_id   BIGINT NOT NULL REFERENCES company(id),
  actor_id     BIGINT,                                              -- FK는 12장. 시스템 처리면 NULL
  action       audit_action NOT NULL,
  target_type  VARCHAR(50) NOT NULL,
  target_id    BIGINT,
  before_value JSONB,
  after_value  JSONB,
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_audit_log_company_created ON audit_log (company_id, created_at DESC);
CREATE INDEX ix_audit_log_target ON audit_log (company_id, target_type, target_id);

CREATE TABLE work_schedule (
  id                         BIGSERIAL PRIMARY KEY,
  company_id                 BIGINT NOT NULL REFERENCES company(id),
  effective_from             DATE NOT NULL,
  start_time                 TIME NOT NULL,
  end_time                   TIME NOT NULL,
  break_minutes              SMALLINT NOT NULL DEFAULT 60 CHECK (break_minutes >= 0),
  late_grace_minutes         SMALLINT NOT NULL DEFAULT 0  CHECK (late_grace_minutes >= 0),
  night_start                TIME NOT NULL DEFAULT '22:00',
  night_end                  TIME NOT NULL DEFAULT '06:00',
  overtime_approval_required BOOLEAN NOT NULL DEFAULT TRUE,
  work_days                  SMALLINT NOT NULL DEFAULT 31 CHECK (work_days BETWEEN 1 AND 127),  -- 월1 화2 수4 목8 금16 토32 일64
  created_by                 BIGINT,                                -- FK는 12장. 회사 등록 기본 행은 NULL
  created_at                 TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (company_id, effective_from),
  CHECK (start_time < end_time)
);

CREATE TABLE holiday (
  id           BIGSERIAL PRIMARY KEY,
  company_id   BIGINT NOT NULL REFERENCES company(id),
  holiday_date DATE NOT NULL,
  name         VARCHAR(50) NOT NULL,
  holiday_type holiday_type NOT NULL,
  is_recurring BOOLEAN NOT NULL DEFAULT FALSE,
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (company_id, holiday_date)
);

-- ---------------------------------------------------------------------
-- 3. 계정 · 권한
-- ---------------------------------------------------------------------
CREATE TABLE role (
  id          BIGSERIAL PRIMARY KEY,
  company_id  BIGINT NOT NULL REFERENCES company(id),
  name        VARCHAR(50) NOT NULL,
  description VARCHAR(255),
  is_system   BOOLEAN NOT NULL DEFAULT FALSE,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (company_id, name)
);
CREATE UNIQUE INDEX ux_role_system ON role (company_id) WHERE is_system;

CREATE TABLE role_permission (
  id              BIGSERIAL PRIMARY KEY,
  company_id      BIGINT NOT NULL REFERENCES company(id),
  role_id         BIGINT NOT NULL REFERENCES role(id) ON DELETE CASCADE,
  permission_code permission_code NOT NULL,
  scope           perm_scope NOT NULL,
  UNIQUE (role_id, permission_code),
  -- 팀 범위를 고를 수 있는 코드는 5개뿐(부록 A)
  CHECK (scope = 'ALL' OR permission_code IN
        ('EMPLOYEE_READ','ATTENDANCE_READ','LEAVE_READ','ASSIGNMENT_READ','EVAL_READ'))
);

-- ---------------------------------------------------------------------
-- 4. 조직
-- ---------------------------------------------------------------------
CREATE TABLE org_unit (
  id               BIGSERIAL PRIMARY KEY,
  company_id       BIGINT NOT NULL REFERENCES company(id),
  parent_id        BIGINT REFERENCES org_unit(id),
  name             VARCHAR(50) NOT NULL,
  level_name       VARCHAR(30),
  lead_employee_id BIGINT,                                          -- FK는 12장
  monthly_budget   BIGINT CHECK (monthly_budget IS NULL OR monthly_budget >= 0),
  sort_order       INT NOT NULL DEFAULT 0,
  is_active        BOOLEAN NOT NULL DEFAULT TRUE,
  created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK (parent_id IS NULL OR parent_id <> id)
);
CREATE UNIQUE INDEX ux_org_unit_root ON org_unit (company_id) WHERE parent_id IS NULL;
CREATE UNIQUE INDEX ux_org_unit_name ON org_unit (company_id, parent_id, name) WHERE parent_id IS NOT NULL;
CREATE INDEX ix_org_unit_parent ON org_unit (parent_id);

CREATE TABLE job_grade (
  id         BIGSERIAL PRIMARY KEY,
  company_id BIGINT NOT NULL REFERENCES company(id),
  name       VARCHAR(50) NOT NULL,
  sort_order INT NOT NULL,
  is_active  BOOLEAN NOT NULL DEFAULT TRUE,
  UNIQUE (company_id, name)
);

CREATE TABLE job_title (
  id         BIGSERIAL PRIMARY KEY,
  company_id BIGINT NOT NULL REFERENCES company(id),
  name       VARCHAR(50) NOT NULL,
  sort_order INT NOT NULL,
  is_active  BOOLEAN NOT NULL DEFAULT TRUE,
  UNIQUE (company_id, name)
);

CREATE TABLE employment_type (
  id         BIGSERIAL PRIMARY KEY,
  company_id BIGINT NOT NULL REFERENCES company(id),
  name       VARCHAR(50) NOT NULL,
  sort_order INT NOT NULL,
  is_active  BOOLEAN NOT NULL DEFAULT TRUE,
  UNIQUE (company_id, name)
);

-- ---------------------------------------------------------------------
-- 5. 직원
-- ---------------------------------------------------------------------
CREATE TABLE employee (
  id                  BIGSERIAL PRIMARY KEY,
  company_id          BIGINT NOT NULL REFERENCES company(id),
  employee_no         VARCHAR(30)  NOT NULL,
  name                VARCHAR(50)  NOT NULL,
  name_en             VARCHAR(100),
  email               VARCHAR(100) NOT NULL UNIQUE,                 -- 로그인 ID, 서비스 전체 유일
  phone               VARCHAR(20),
  address             VARCHAR(255),
  birth_date          DATE,
  gender              gender,
  emergency_name      VARCHAR(50),
  emergency_relation  VARCHAR(20),
  emergency_phone     VARCHAR(20),
  hire_date           DATE NOT NULL,
  org_unit_id         BIGINT NOT NULL REFERENCES org_unit(id),
  job_grade_id        BIGINT REFERENCES job_grade(id),              -- 회사 등록 관리자만 NULL
  job_title_id        BIGINT REFERENCES job_title(id),
  employment_type_id  BIGINT NOT NULL REFERENCES employment_type(id),
  status              emp_status NOT NULL DEFAULT 'ACTIVE',
  payroll_eligible    BOOLEAN NOT NULL DEFAULT TRUE,
  contract_end_date   DATE,
  probation_end_date  DATE,
  profile_file_id     BIGINT REFERENCES file(id),
  hr_memo             TEXT,
  bank_name           VARCHAR(30),
  bank_account_enc    VARCHAR(255),                                 -- AES 암호문(애플리케이션)
  bank_account_last4  CHAR(4),
  bank_account_holder VARCHAR(50),
  created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (company_id, employee_no),
  CHECK ((bank_account_enc IS NULL) = (bank_account_last4 IS NULL))
);
CREATE INDEX ix_employee_org ON employee (org_unit_id);
CREATE INDEX ix_employee_company_status ON employee (company_id, status);

CREATE TABLE account (
  id                   BIGSERIAL PRIMARY KEY,
  company_id           BIGINT NOT NULL REFERENCES company(id),
  employee_id          BIGINT NOT NULL UNIQUE REFERENCES employee(id),
  password_hash        VARCHAR(100) NOT NULL,
  role_id              BIGINT NOT NULL REFERENCES role(id),
  is_active            BOOLEAN NOT NULL DEFAULT TRUE,
  must_change_password BOOLEAN NOT NULL DEFAULT TRUE,
  failed_login_count   SMALLINT NOT NULL DEFAULT 0 CHECK (failed_login_count >= 0),
  locked_until         TIMESTAMPTZ,
  last_login_at        TIMESTAMPTZ,
  created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_account_role ON account (role_id);

CREATE TABLE employee_no_seq (
  company_id BIGINT NOT NULL REFERENCES company(id),
  hire_year  SMALLINT NOT NULL,
  last_seq   INT NOT NULL DEFAULT 0 CHECK (last_seq >= 0),
  PRIMARY KEY (company_id, hire_year)
);

CREATE TABLE employment_status_history (
  id             BIGSERIAL PRIMARY KEY,
  company_id     BIGINT NOT NULL REFERENCES company(id),
  employee_id    BIGINT NOT NULL REFERENCES employee(id),
  status         emp_status NOT NULL,
  effective_date DATE NOT NULL,
  reason         VARCHAR(255) NOT NULL,
  created_by     BIGINT REFERENCES employee(id),
  created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_emp_status_history ON employment_status_history (employee_id, effective_date);

CREATE TABLE employee_field_def (
  id               BIGSERIAL PRIMARY KEY,
  company_id       BIGINT NOT NULL REFERENCES company(id),
  name             VARCHAR(50) NOT NULL,
  field_type       field_type NOT NULL,
  options          JSONB,
  is_required      BOOLEAN NOT NULL DEFAULT FALSE,
  is_multiple      BOOLEAN NOT NULL DEFAULT FALSE,
  is_self_editable BOOLEAN NOT NULL DEFAULT FALSE,
  sort_order       INT NOT NULL,
  is_active        BOOLEAN NOT NULL DEFAULT TRUE,
  UNIQUE (company_id, name),
  CHECK (field_type <> 'SELECT' OR jsonb_typeof(options) = 'array')
);

CREATE TABLE employee_field_value (
  id           BIGSERIAL PRIMARY KEY,
  company_id   BIGINT NOT NULL REFERENCES company(id),
  employee_id  BIGINT NOT NULL REFERENCES employee(id),
  field_def_id BIGINT NOT NULL REFERENCES employee_field_def(id),
  seq          SMALLINT NOT NULL DEFAULT 1 CHECK (seq >= 1),
  value        TEXT NOT NULL,
  UNIQUE (employee_id, field_def_id, seq)
);

CREATE TABLE employee_family (
  id               BIGSERIAL PRIMARY KEY,
  company_id       BIGINT NOT NULL REFERENCES company(id),
  employee_id      BIGINT NOT NULL REFERENCES employee(id),
  name             VARCHAR(50) NOT NULL,
  relation         family_relation NOT NULL,
  birth_date       DATE,
  is_tax_dependent BOOLEAN NOT NULL DEFAULT FALSE,
  is_disabled      BOOLEAN NOT NULL DEFAULT FALSE,
  is_cohabiting    BOOLEAN NOT NULL DEFAULT TRUE,
  created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_employee_family ON employee_family (employee_id);

CREATE TABLE employee_document (
  id          BIGSERIAL PRIMARY KEY,
  company_id  BIGINT NOT NULL REFERENCES company(id),
  employee_id BIGINT NOT NULL REFERENCES employee(id),
  doc_type    employee_doc_type NOT NULL,
  doc_name    VARCHAR(100),
  file_id     BIGINT NOT NULL REFERENCES file(id),
  issued_date DATE,
  expires_at  DATE,
  version_no  INT NOT NULL CHECK (version_no >= 1),
  is_current  BOOLEAN NOT NULL DEFAULT TRUE,
  memo        VARCHAR(255),
  uploaded_by BIGINT NOT NULL REFERENCES employee(id),
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK (doc_type <> 'OTHER' OR doc_name IS NOT NULL)
);
CREATE UNIQUE INDEX ux_employee_document_current
  ON employee_document (employee_id, doc_type, COALESCE(doc_name, '')) WHERE is_current;

-- ---------------------------------------------------------------------
-- 6. 근태 · 연장근무 · 출장
-- ---------------------------------------------------------------------
CREATE TABLE overtime_request (
  id                BIGSERIAL PRIMARY KEY,
  company_id        BIGINT NOT NULL REFERENCES company(id),
  employee_id       BIGINT NOT NULL REFERENCES employee(id),
  work_date         DATE NOT NULL,
  planned_start     TIMESTAMPTZ NOT NULL,
  planned_end       TIMESTAMPTZ NOT NULL,
  requested_minutes INT NOT NULL CHECK (requested_minutes > 0),
  approved_minutes  INT CHECK (approved_minutes IS NULL OR approved_minutes BETWEEN 0 AND requested_minutes),
  reason            VARCHAR(255) NOT NULL,
  status            request_status NOT NULL DEFAULT 'PENDING',
  cancel_reason     VARCHAR(255),
  created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK (planned_start < planned_end)
);
CREATE UNIQUE INDEX ux_overtime_active ON overtime_request (employee_id, work_date)
  WHERE status IN ('PENDING','APPROVED');

CREATE TABLE business_trip (
  id             BIGSERIAL PRIMARY KEY,
  company_id     BIGINT NOT NULL REFERENCES company(id),
  employee_id    BIGINT NOT NULL REFERENCES employee(id),
  trip_type      trip_type NOT NULL,
  destination    VARCHAR(100) NOT NULL,
  purpose        VARCHAR(500) NOT NULL,
  start_date     DATE NOT NULL,
  end_date       DATE NOT NULL,
  estimated_cost BIGINT CHECK (estimated_cost IS NULL OR estimated_cost >= 0),
  report_text    TEXT,
  reported_at    TIMESTAMPTZ,
  status         request_status NOT NULL DEFAULT 'PENDING',
  cancel_reason  VARCHAR(255),
  created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK (start_date <= end_date),
  -- 같은 직원의 진행 중 출장끼리 기간 겹침 금지(휴가와의 겹침은 서비스에서 검사)
  EXCLUDE USING gist (employee_id WITH =, daterange(start_date, end_date, '[]') WITH &&)
    WHERE (status IN ('PENDING','APPROVED'))
);

CREATE TABLE expense_type (
  id               BIGSERIAL PRIMARY KEY,
  company_id       BIGINT NOT NULL REFERENCES company(id),
  name             VARCHAR(50) NOT NULL,
  receipt_required BOOLEAN NOT NULL DEFAULT TRUE,
  sort_order       INT NOT NULL,
  is_active        BOOLEAN NOT NULL DEFAULT TRUE,
  UNIQUE (company_id, name)
);

-- leave_request 는 7장에서 만든다. attendance 가 참조하므로 먼저 만든다.
CREATE TABLE leave_type (
  id                       BIGSERIAL PRIMARY KEY,
  company_id               BIGINT NOT NULL REFERENCES company(id),
  name                     VARCHAR(50) NOT NULL,
  annual_days              SMALLINT NOT NULL CHECK (annual_days >= 0),
  deducts_balance          BOOLEAN NOT NULL,
  is_paid                  BOOLEAN NOT NULL DEFAULT TRUE,
  prorate_first_year       BOOLEAN NOT NULL DEFAULT FALSE,
  seniority_start_years    SMALLINT CHECK (seniority_start_years IS NULL OR seniority_start_years >= 1),
  seniority_interval_years SMALLINT CHECK (seniority_interval_years IS NULL OR seniority_interval_years >= 1),
  seniority_add_days       SMALLINT CHECK (seniority_add_days IS NULL OR seniority_add_days >= 1),
  seniority_max_days       SMALLINT,
  sort_order               INT NOT NULL,
  is_active                BOOLEAN NOT NULL DEFAULT TRUE,
  UNIQUE (company_id, name),
  -- 근속 가산 4개 값은 모두 있거나 모두 없다
  CHECK ((seniority_start_years IS NULL) = (seniority_interval_years IS NULL)
     AND (seniority_start_years IS NULL) = (seniority_add_days IS NULL)
     AND (seniority_start_years IS NULL) = (seniority_max_days IS NULL)),
  CHECK (seniority_max_days IS NULL OR seniority_max_days >= annual_days)
);

CREATE TABLE leave_request (
  id            BIGSERIAL PRIMARY KEY,
  company_id    BIGINT NOT NULL REFERENCES company(id),
  employee_id   BIGINT NOT NULL REFERENCES employee(id),
  leave_type_id BIGINT NOT NULL REFERENCES leave_type(id),
  leave_year    SMALLINT NOT NULL,
  start_date    DATE NOT NULL,
  end_date      DATE NOT NULL,
  days          SMALLINT NOT NULL CHECK (days > 0),
  reason        VARCHAR(255),
  status        leave_status NOT NULL DEFAULT 'PENDING',
  cancel_reason VARCHAR(255),
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK (start_date <= end_date),
  -- 같은 직원의 진행 중 휴가끼리 기간 겹침 금지(BR-LEAVE-008)
  EXCLUDE USING gist (employee_id WITH =, daterange(start_date, end_date, '[]') WITH &&)
    WHERE (status IN ('PENDING','APPROVED','CANCEL_REQUESTED'))
);
CREATE INDEX ix_leave_request_type_year ON leave_request (employee_id, leave_type_id, leave_year);

CREATE TABLE attendance (
  id                BIGSERIAL PRIMARY KEY,
  company_id        BIGINT NOT NULL REFERENCES company(id),
  employee_id       BIGINT NOT NULL REFERENCES employee(id),
  work_date         DATE NOT NULL,
  status            attendance_status NOT NULL,
  work_type         work_type,
  check_in_at       TIMESTAMPTZ,
  check_out_at      TIMESTAMPTZ,
  check_in_method   record_method,
  check_out_method  record_method,
  check_in_lat      DECIMAL(9,6),
  check_in_lng      DECIMAL(9,6),
  check_out_lat     DECIMAL(9,6),
  check_out_lng     DECIMAL(9,6),
  place_memo        VARCHAR(100),
  leave_request_id  BIGINT REFERENCES leave_request(id),
  business_trip_id  BIGINT REFERENCES business_trip(id),
  correction_reason VARCHAR(255),
  corrected_by      BIGINT REFERENCES employee(id),
  corrected_at      TIMESTAMPTZ,
  created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (employee_id, work_date),
  CHECK (check_out_at IS NULL OR check_in_at IS NULL OR check_in_at < check_out_at),
  CHECK (status <> 'ON_VACATION'      OR leave_request_id IS NOT NULL),
  CHECK (status <> 'ON_BUSINESS_TRIP' OR business_trip_id IS NOT NULL),
  CHECK (status NOT IN ('CHECKED_IN','CHECKED_OUT','MISSING_CHECKOUT') OR check_in_at IS NOT NULL),
  CHECK (status <> 'CHECKED_OUT' OR check_out_at IS NOT NULL)
);
CREATE INDEX ix_attendance_company_date ON attendance (company_id, work_date);

CREATE TABLE expense_claim (
  id               BIGSERIAL PRIMARY KEY,
  company_id       BIGINT NOT NULL REFERENCES company(id),
  business_trip_id BIGINT NOT NULL REFERENCES business_trip(id),
  employee_id      BIGINT NOT NULL REFERENCES employee(id),
  status           request_status NOT NULL DEFAULT 'PENDING',
  paystub_id       BIGINT,                                          -- FK는 12장. NULL = 정산 미반영
  cancel_reason    VARCHAR(255),
  created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK (paystub_id IS NULL OR status = 'APPROVED')
);
CREATE UNIQUE INDEX ux_expense_claim_active ON expense_claim (business_trip_id)
  WHERE status IN ('PENDING','APPROVED');
CREATE INDEX ix_expense_claim_unsettled ON expense_claim (company_id, employee_id)
  WHERE status = 'APPROVED' AND paystub_id IS NULL;

CREATE TABLE expense_claim_line (
  id               BIGSERIAL PRIMARY KEY,
  company_id       BIGINT NOT NULL REFERENCES company(id),
  expense_claim_id BIGINT NOT NULL REFERENCES expense_claim(id) ON DELETE CASCADE,
  expense_type_id  BIGINT NOT NULL REFERENCES expense_type(id),
  used_date        DATE NOT NULL,
  amount           BIGINT NOT NULL CHECK (amount > 0),
  description      VARCHAR(255),
  receipt_file_id  BIGINT REFERENCES file(id)
);
CREATE INDEX ix_expense_claim_line ON expense_claim_line (expense_claim_id);

-- ---------------------------------------------------------------------
-- 7. 휴가 (leave_type · leave_request 는 6장에서 만듦)
-- ---------------------------------------------------------------------
CREATE TABLE leave_grant (
  id            BIGSERIAL PRIMARY KEY,
  company_id    BIGINT NOT NULL REFERENCES company(id),
  employee_id   BIGINT NOT NULL REFERENCES employee(id),
  leave_type_id BIGINT NOT NULL REFERENCES leave_type(id),
  leave_year    SMALLINT NOT NULL,
  grant_type    leave_grant_type NOT NULL,
  days          SMALLINT NOT NULL,
  reason        VARCHAR(255),
  created_by    BIGINT REFERENCES employee(id),                    -- 자동 부여는 NULL
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK (grant_type = 'ADJUSTMENT' OR days >= 0),
  CHECK (grant_type <> 'ADJUSTMENT' OR reason IS NOT NULL)
);
-- 정기·입사 부여는 직원·종류·연도마다 합쳐서 1건(BR-LEAVE-007)
CREATE UNIQUE INDEX ux_leave_grant_once ON leave_grant (employee_id, leave_type_id, leave_year)
  WHERE grant_type IN ('REGULAR','HIRE');

-- ---------------------------------------------------------------------
-- 8. 승인
-- ---------------------------------------------------------------------
CREATE TABLE approval_line (
  id                BIGSERIAL PRIMARY KEY,
  company_id        BIGINT NOT NULL REFERENCES company(id),
  name              VARCHAR(50) NOT NULL,
  work_type         approval_work_type NOT NULL CHECK (work_type <> 'LEAVE_CANCEL'),
  cond_job_title_id BIGINT REFERENCES job_title(id),
  cond_role_id      BIGINT REFERENCES role(id),
  is_default        BOOLEAN NOT NULL DEFAULT FALSE,
  priority          INT NOT NULL DEFAULT 0,
  is_active         BOOLEAN NOT NULL DEFAULT TRUE,
  created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK (cond_job_title_id IS NULL OR cond_role_id IS NULL),
  -- 기본 승인선은 조건이 없고 항상 활성
  CHECK (NOT is_default OR (cond_job_title_id IS NULL AND cond_role_id IS NULL AND is_active))
);
CREATE UNIQUE INDEX ux_approval_line_default ON approval_line (company_id, work_type) WHERE is_default;

CREATE TABLE approval_line_step (
  id               BIGSERIAL PRIMARY KEY,
  company_id       BIGINT NOT NULL REFERENCES company(id),
  approval_line_id BIGINT NOT NULL REFERENCES approval_line(id) ON DELETE CASCADE,
  step_order       SMALLINT NOT NULL CHECK (step_order BETWEEN 1 AND 5),
  approver_type    approver_type NOT NULL,
  up_levels        SMALLINT CHECK (up_levels IS NULL OR up_levels >= 1),
  job_title_id     BIGINT REFERENCES job_title(id),
  employee_id      BIGINT REFERENCES employee(id),
  UNIQUE (approval_line_id, step_order),
  CHECK (approver_type <> 'ORG_LEAD_UP' OR up_levels IS NOT NULL),
  CHECK (approver_type <> 'JOB_TITLE'   OR job_title_id IS NOT NULL),
  CHECK (approver_type <> 'EMPLOYEE'    OR employee_id IS NOT NULL)
);

CREATE TABLE approval_step (
  id               BIGSERIAL PRIMARY KEY,
  company_id       BIGINT NOT NULL REFERENCES company(id),
  work_type        approval_work_type NOT NULL,
  target_id        BIGINT NOT NULL,                                 -- 다형 참조(FK 없음)
  approval_line_id BIGINT REFERENCES approval_line(id),             -- 휴가 취소는 NULL
  round            SMALLINT NOT NULL DEFAULT 1 CHECK (round >= 1),
  step_order       SMALLINT NOT NULL CHECK (step_order BETWEEN 1 AND 5),
  approver_id      BIGINT REFERENCES employee(id),
  status           approval_step_status NOT NULL,
  approved_minutes INT CHECK (approved_minutes IS NULL OR approved_minutes >= 0),
  comment          VARCHAR(500),
  acted_at         TIMESTAMPTZ,
  needs_reassign   BOOLEAN NOT NULL DEFAULT FALSE,
  created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (work_type, target_id, round, step_order),
  CHECK (approved_minutes IS NULL OR work_type = 'OVERTIME')
);
CREATE INDEX ix_approval_step_inbox  ON approval_step (approver_id, status);
CREATE INDEX ix_approval_step_target ON approval_step (work_type, target_id);

-- ---------------------------------------------------------------------
-- 9. 급여
-- ---------------------------------------------------------------------
CREATE TABLE pay_item (
  id                BIGSERIAL PRIMARY KEY,
  company_id        BIGINT NOT NULL REFERENCES company(id),
  name              VARCHAR(50) NOT NULL,
  item_kind         pay_item_kind NOT NULL,
  is_taxable        BOOLEAN NOT NULL DEFAULT TRUE,
  non_taxable_limit BIGINT CHECK (non_taxable_limit IS NULL OR non_taxable_limit >= 0),
  calc_method       pay_calc_method NOT NULL,
  apply_to          pay_apply_to NOT NULL DEFAULT 'ALL',
  default_amount    BIGINT CHECK (default_amount IS NULL OR default_amount >= 0),
  base_rate         DECIMAL(7,4),
  employee_rate     DECIMAL(7,4),
  company_rate      DECIMAL(7,4),
  base_upper_limit  BIGINT,
  base_lower_limit  BIGINT,
  attendance_basis  attendance_basis,
  multiplier        DECIMAL(4,2) CHECK (multiplier IS NULL OR multiplier > 0),
  in_ordinary_wage  BOOLEAN NOT NULL DEFAULT FALSE,
  sort_order        INT NOT NULL,
  is_active         BOOLEAN NOT NULL DEFAULT TRUE,
  created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (company_id, name),
  CHECK (calc_method <> 'BASE_RATE'    OR base_rate IS NOT NULL),
  CHECK (calc_method <> 'TAXABLE_RATE' OR (item_kind = 'DEDUCTION' AND employee_rate IS NOT NULL AND company_rate IS NOT NULL)),
  CHECK (calc_method <> 'ATTENDANCE'   OR (attendance_basis IS NOT NULL AND multiplier IS NOT NULL)),
  CHECK (attendance_basis IS NULL      OR calc_method = 'ATTENDANCE'),
  CHECK (attendance_basis IS DISTINCT FROM 'ABSENCE' OR item_kind = 'DEDUCTION'),
  CHECK (attendance_basis IS NULL OR attendance_basis = 'ABSENCE' OR item_kind = 'EARNING'),
  CHECK (calc_method <> 'TRIP_EXPENSE' OR item_kind = 'EARNING'),
  CHECK (NOT in_ordinary_wage OR (item_kind = 'EARNING' AND calc_method IN ('FIXED','BASE_RATE'))),
  CHECK (non_taxable_limit IS NULL OR (item_kind = 'EARNING' AND NOT is_taxable)),
  CHECK (base_upper_limit IS NULL OR base_lower_limit IS NULL OR base_lower_limit <= base_upper_limit)
);
CREATE UNIQUE INDEX ux_pay_item_attendance ON pay_item (company_id, attendance_basis)
  WHERE is_active AND calc_method = 'ATTENDANCE';
CREATE UNIQUE INDEX ux_pay_item_trip_expense ON pay_item (company_id)
  WHERE is_active AND calc_method = 'TRIP_EXPENSE';

CREATE TABLE pay_variable (
  id             BIGSERIAL PRIMARY KEY,
  company_id     BIGINT NOT NULL REFERENCES company(id),
  var_code       pay_var_code NOT NULL,
  value          DECIMAL(15,4) NOT NULL CHECK (value >= 0),
  effective_from DATE NOT NULL,
  created_by     BIGINT REFERENCES employee(id),
  created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
-- 같은 적용 시작일 행이 여러 개면 나중에 만든 행이 정정본(effective_from DESC, created_at DESC, id DESC) — employee_salary 와 같다
CREATE INDEX ix_pay_variable ON pay_variable (company_id, var_code, effective_from, created_at);

CREATE TABLE tax_bracket (
  id                    BIGSERIAL PRIMARY KEY,
  company_id            BIGINT NOT NULL REFERENCES company(id),
  lower_bound           BIGINT NOT NULL CHECK (lower_bound >= 0),
  upper_bound           BIGINT,
  rate                  DECIMAL(5,2) NOT NULL CHECK (rate >= 0),
  progressive_deduction BIGINT NOT NULL DEFAULT 0 CHECK (progressive_deduction >= 0),
  UNIQUE (company_id, lower_bound),
  CHECK (upper_bound IS NULL OR upper_bound > lower_bound)
);

CREATE TABLE employee_salary (
  id             BIGSERIAL PRIMARY KEY,
  company_id     BIGINT NOT NULL REFERENCES company(id),
  employee_id    BIGINT NOT NULL REFERENCES employee(id),
  salary_type    salary_type NOT NULL,
  annual_salary  BIGINT CHECK (annual_salary IS NULL OR annual_salary > 0),
  monthly_base   BIGINT NOT NULL CHECK (monthly_base > 0),
  effective_from DATE NOT NULL,
  reason         VARCHAR(255) NOT NULL,
  created_by     BIGINT NOT NULL REFERENCES employee(id),
  created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK (salary_type <> 'ANNUAL' OR annual_salary IS NOT NULL)
);
CREATE INDEX ix_employee_salary ON employee_salary (employee_id, effective_from, created_at);

CREATE TABLE employee_pay_item (
  id             BIGSERIAL PRIMARY KEY,
  company_id     BIGINT NOT NULL REFERENCES company(id),
  employee_id    BIGINT NOT NULL REFERENCES employee(id),
  pay_item_id    BIGINT NOT NULL REFERENCES pay_item(id),
  amount         BIGINT NOT NULL CHECK (amount >= 0),
  effective_from DATE NOT NULL,
  reason         VARCHAR(255),
  created_by     BIGINT NOT NULL REFERENCES employee(id),
  created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_employee_pay_item ON employee_pay_item (employee_id, pay_item_id, effective_from, created_at);

CREATE TABLE payroll_run (
  id                   BIGSERIAL PRIMARY KEY,
  company_id           BIGINT NOT NULL REFERENCES company(id),
  pay_month            CHAR(7) NOT NULL CHECK (pay_month ~ '^\d{4}-(0[1-9]|1[0-2])$'),
  pay_date             DATE NOT NULL,
  status               payroll_status NOT NULL DEFAULT 'CONFIRMED',
  company_name_snap    VARCHAR(100) NOT NULL,
  ceo_name_snap        VARCHAR(50)  NOT NULL,
  business_reg_no_snap VARCHAR(12)  NOT NULL,
  company_address_snap VARCHAR(255) NOT NULL,
  confirmed_by         BIGINT NOT NULL REFERENCES employee(id),
  confirmed_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
  paid_by              BIGINT REFERENCES employee(id),
  paid_at              TIMESTAMPTZ,
  UNIQUE (company_id, pay_month),
  CHECK ((status = 'PAID') = (paid_at IS NOT NULL)),
  CHECK ((paid_at IS NULL) = (paid_by IS NULL))
);

CREATE TABLE paystub (
  id                       BIGSERIAL PRIMARY KEY,
  company_id               BIGINT NOT NULL REFERENCES company(id),
  payroll_run_id           BIGINT NOT NULL REFERENCES payroll_run(id),
  employee_id              BIGINT NOT NULL REFERENCES employee(id),
  employee_no_snap         VARCHAR(30)  NOT NULL,
  employee_name_snap       VARCHAR(50)  NOT NULL,
  org_unit_id_snap         BIGINT       NOT NULL,                   -- 정산 당시 소속(FK 아님: 스냅샷)
  org_name_snap            VARCHAR(50)  NOT NULL,
  bank_account_masked_snap VARCHAR(60),
  base_pay                 BIGINT NOT NULL CHECK (base_pay >= 0),
  ordinary_hourly_wage     BIGINT NOT NULL CHECK (ordinary_hourly_wage >= 0),
  dependents_count         SMALLINT NOT NULL CHECK (dependents_count >= 0),
  children_count           SMALLINT NOT NULL CHECK (children_count >= 0),
  worked_days              SMALLINT NOT NULL CHECK (worked_days >= 0),
  month_days               SMALLINT NOT NULL CHECK (month_days BETWEEN 28 AND 31),
  gross_pay                BIGINT NOT NULL CHECK (gross_pay >= 0),
  taxable_pay              BIGINT NOT NULL CHECK (taxable_pay >= 0),
  income_tax               BIGINT NOT NULL CHECK (income_tax >= 0),
  local_income_tax         BIGINT NOT NULL CHECK (local_income_tax >= 0),
  total_deduction          BIGINT NOT NULL CHECK (total_deduction >= 0),
  net_pay                  BIGINT NOT NULL,
  company_burden_total     BIGINT NOT NULL CHECK (company_burden_total >= 0),
  created_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (payroll_run_id, employee_id),
  CHECK (worked_days <= month_days),
  CHECK (net_pay = gross_pay - total_deduction)
);
CREATE INDEX ix_paystub_employee ON paystub (employee_id);

CREATE TABLE paystub_line (
  id                 BIGSERIAL PRIMARY KEY,
  company_id         BIGINT NOT NULL REFERENCES company(id),
  paystub_id         BIGINT NOT NULL REFERENCES paystub(id),
  pay_item_id        BIGINT NOT NULL REFERENCES pay_item(id),
  item_name_snap     VARCHAR(50) NOT NULL,
  item_kind          pay_item_kind NOT NULL,
  calc_method        pay_calc_method NOT NULL,
  amount             BIGINT NOT NULL CHECK (amount >= 0),
  taxable_amount     BIGINT NOT NULL DEFAULT 0 CHECK (taxable_amount >= 0),
  non_taxable_amount BIGINT NOT NULL DEFAULT 0 CHECK (non_taxable_amount >= 0),
  company_amount     BIGINT CHECK (company_amount IS NULL OR company_amount >= 0),
  quantity           DECIMAL(8,2),
  unit_price         BIGINT,
  formula_note       VARCHAR(255),
  sort_order         INT NOT NULL,
  CHECK (item_kind = 'DEDUCTION' OR taxable_amount + non_taxable_amount = amount)
);
CREATE INDEX ix_paystub_line ON paystub_line (paystub_id);

-- ---------------------------------------------------------------------
-- 10. 인사발령
-- ---------------------------------------------------------------------
CREATE TABLE assignment_history (
  id                BIGSERIAL PRIMARY KEY,
  company_id        BIGINT NOT NULL REFERENCES company(id),
  employee_id       BIGINT NOT NULL REFERENCES employee(id),
  assignment_type   assignment_type NOT NULL,
  from_org_unit_id  BIGINT REFERENCES org_unit(id),
  to_org_unit_id    BIGINT NOT NULL REFERENCES org_unit(id),
  from_job_grade_id BIGINT REFERENCES job_grade(id),
  to_job_grade_id   BIGINT NOT NULL REFERENCES job_grade(id),
  from_job_title_id BIGINT REFERENCES job_title(id),
  to_job_title_id   BIGINT REFERENCES job_title(id),
  reason            VARCHAR(255) NOT NULL,
  effective_date    DATE NOT NULL DEFAULT CURRENT_DATE,
  correction_of_id  BIGINT REFERENCES assignment_history(id),
  created_by        BIGINT NOT NULL REFERENCES employee(id),
  created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK (correction_of_id IS NULL OR correction_of_id <> id)
);
CREATE INDEX ix_assignment_history ON assignment_history (employee_id, created_at);

-- ---------------------------------------------------------------------
-- 11. 평가
-- ---------------------------------------------------------------------
CREATE TABLE eval_template (
  id             BIGSERIAL PRIMARY KEY,
  company_id     BIGINT NOT NULL REFERENCES company(id),
  name           VARCHAR(50) NOT NULL,
  copied_from_id BIGINT REFERENCES eval_template(id),
  is_active      BOOLEAN NOT NULL DEFAULT TRUE,
  created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (company_id, name)
);

CREATE TABLE eval_criteria (
  id               BIGSERIAL PRIMARY KEY,
  company_id       BIGINT NOT NULL REFERENCES company(id),
  eval_template_id BIGINT NOT NULL REFERENCES eval_template(id) ON DELETE CASCADE,
  category         VARCHAR(30) NOT NULL,
  name             VARCHAR(50) NOT NULL,
  weight           SMALLINT NOT NULL CHECK (weight BETWEEN 1 AND 100),
  sort_order       INT NOT NULL
);
CREATE INDEX ix_eval_criteria ON eval_criteria (eval_template_id);

CREATE TABLE eval_question (
  id               BIGSERIAL PRIMARY KEY,
  company_id       BIGINT NOT NULL REFERENCES company(id),
  eval_criteria_id BIGINT NOT NULL REFERENCES eval_criteria(id) ON DELETE CASCADE,
  content          VARCHAR(500) NOT NULL,
  sort_order       INT NOT NULL
);
CREATE INDEX ix_eval_question ON eval_question (eval_criteria_id);

CREATE TABLE eval_cycle (
  id         BIGSERIAL PRIMARY KEY,
  company_id BIGINT NOT NULL REFERENCES company(id),
  name       VARCHAR(50) NOT NULL,
  start_date DATE NOT NULL,
  end_date   DATE NOT NULL,
  status     eval_cycle_status NOT NULL DEFAULT 'SCHEDULED',
  started_at TIMESTAMPTZ,
  closed_at  TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK (start_date <= end_date)
);

CREATE TABLE eval_cycle_target (
  id                      BIGSERIAL PRIMARY KEY,
  company_id              BIGINT NOT NULL REFERENCES company(id),
  eval_cycle_id           BIGINT NOT NULL REFERENCES eval_cycle(id) ON DELETE CASCADE,
  priority                INT NOT NULL,
  cond_is_org_lead        BOOLEAN,
  cond_job_title_id       BIGINT REFERENCES job_title(id),
  cond_job_grade_id       BIGINT REFERENCES job_grade(id),
  cond_employment_type_id BIGINT REFERENCES employment_type(id),
  eval_template_id        BIGINT REFERENCES eval_template(id),     -- NULL = 평가 제외
  UNIQUE (eval_cycle_id, priority)
);

CREATE TABLE evaluation (
  id                 BIGSERIAL PRIMARY KEY,
  company_id         BIGINT NOT NULL REFERENCES company(id),
  eval_cycle_id      BIGINT NOT NULL REFERENCES eval_cycle(id),
  target_employee_id BIGINT NOT NULL REFERENCES employee(id),
  evaluator_id       BIGINT NOT NULL REFERENCES employee(id),
  eval_template_id   BIGINT NOT NULL REFERENCES eval_template(id),
  status             evaluation_status NOT NULL DEFAULT 'NOT_STARTED',
  overall_comment    TEXT,
  submitted_at       TIMESTAMPTZ,
  confirmed_by       BIGINT REFERENCES employee(id),
  confirmed_at       TIMESTAMPTZ,
  reopen_reason      VARCHAR(255),
  created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (eval_cycle_id, target_employee_id),
  CHECK (evaluator_id <> target_employee_id)
);
CREATE INDEX ix_evaluation_evaluator ON evaluation (evaluator_id, status);

CREATE TABLE eval_answer (
  id               BIGSERIAL PRIMARY KEY,
  company_id       BIGINT NOT NULL REFERENCES company(id),
  evaluation_id    BIGINT NOT NULL REFERENCES evaluation(id),
  eval_question_id BIGINT NOT NULL REFERENCES eval_question(id),
  score            SMALLINT NOT NULL CHECK (score BETWEEN 1 AND 5),
  UNIQUE (evaluation_id, eval_question_id)
);

-- ---------------------------------------------------------------------
-- 12. 지연 FK (서로 참조하는 테이블)
-- ---------------------------------------------------------------------
ALTER TABLE company                ADD CONSTRAINT fk_company_logo        FOREIGN KEY (logo_file_id)     REFERENCES file(id);
ALTER TABLE file                   ADD CONSTRAINT fk_file_uploaded_by    FOREIGN KEY (uploaded_by)      REFERENCES employee(id);
ALTER TABLE company_document       ADD CONSTRAINT fk_cdoc_uploaded_by    FOREIGN KEY (uploaded_by)      REFERENCES employee(id);
ALTER TABLE company_change_history ADD CONSTRAINT fk_cch_changed_by      FOREIGN KEY (changed_by)       REFERENCES employee(id);
ALTER TABLE audit_log              ADD CONSTRAINT fk_audit_actor         FOREIGN KEY (actor_id)         REFERENCES employee(id);
ALTER TABLE work_schedule          ADD CONSTRAINT fk_ws_created_by       FOREIGN KEY (created_by)       REFERENCES employee(id);
ALTER TABLE org_unit               ADD CONSTRAINT fk_org_unit_lead       FOREIGN KEY (lead_employee_id) REFERENCES employee(id);
ALTER TABLE expense_claim          ADD CONSTRAINT fk_expense_claim_stub  FOREIGN KEY (paystub_id)       REFERENCES paystub(id);

-- ---------------------------------------------------------------------
-- 13. append-only 보호 (애플리케이션 실수로 이력이 바뀌는 것을 DB가 막는다)
-- ---------------------------------------------------------------------
DO $$
DECLARE t TEXT;
BEGIN
  FOREACH t IN ARRAY ARRAY[
    'company_change_history','audit_log','pay_variable','employment_status_history',
    'assignment_history','leave_grant','employee_salary','employee_pay_item',
    'paystub','paystub_line']
  LOOP
    EXECUTE format('CREATE TRIGGER trg_%s_append_only BEFORE UPDATE OR DELETE ON %I
                    FOR EACH ROW EXECUTE FUNCTION fn_block_write()', t, t);
  END LOOP;
END $$;

-- payroll_run: 삭제 금지(상태·지급 정보 UPDATE 는 허용)
CREATE TRIGGER trg_payroll_run_no_delete BEFORE DELETE ON payroll_run
  FOR EACH ROW EXECUTE FUNCTION fn_block_write();
-- file: 삭제 금지
CREATE TRIGGER trg_file_no_delete BEFORE DELETE ON file
  FOR EACH ROW EXECUTE FUNCTION fn_block_write();
-- work_schedule: 수정 금지, 시작 전 행만 삭제
CREATE TRIGGER trg_work_schedule_guard BEFORE UPDATE OR DELETE ON work_schedule
  FOR EACH ROW EXECUTE FUNCTION fn_work_schedule_guard();

COMMIT;
