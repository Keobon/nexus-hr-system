-- =====================================================================
--  인사관리 SaaS — seed_demo.sql v2 (데모 회사 2개)
--  실행: schema.sql 다음에  psql -d nexus_hr -v ON_ERROR_STOP=1 -f seed_demo.sql
--  ① NEXUS LABS  — IT 회사. 회사→본부→팀, 09–18시, 연장 승인 필요, 휴가 2단계 승인
--  ② 한빛푸드    — 식품 제조. 회사→본사/공장→부서·조, 08–17시 월–토, 연장 승인 불필요,
--                  리프레시휴가·식대·생산수당, 휴가 승인 [조직장 → 대표]
--  모든 계정 비밀번호: Passw0rd!   (로그인 이메일은 각 직원 행 참고)
--  ID 규칙: NEXUS LABS 는 1~, 한빛푸드는 11~ 또는 101~ (마지막에 시퀀스를 맞춘다)
--  회사 등록 기본값(ERD 8장)과 같은 구성 + 데모용 직원·근태·신청 데이터
-- =====================================================================

BEGIN;

-- ---------------------------------------------------------------------
-- 회사
-- ---------------------------------------------------------------------
INSERT INTO company (id, name, name_en, business_reg_no, corp_reg_no, ceo_name, address, phone, email,
                     website, business_type, business_item, founded_date, pay_day, employee_no_prefix,
                     fiscal_year_start_month, setup_completed) VALUES
 (1, '넥서스랩스', 'NEXUS LABS', '214-88-12345', '110111-1234567', '박서준', '서울특별시 강남구 테헤란로 123, 7층',
  '02-555-0100', 'contact@nexuslabs.example', 'https://nexuslabs.example', '서비스업', '소프트웨어 개발',
  '2015-03-02', 25, 'NX-', 1, TRUE),
 (2, '한빛푸드', 'HANBIT FOOD', '312-81-67890', '160111-7654321', '김한빛', '충청남도 천안시 서북구 공단로 45',
  '041-550-2200', 'info@hanbitfood.example', NULL, '제조업', '식품 제조',
  '2012-04-02', 10, 'HB-', 1, TRUE);

-- ---------------------------------------------------------------------
-- 역할 · 권한 (기본 역할 4개 — 부록 B)
-- ---------------------------------------------------------------------
INSERT INTO role (id, company_id, name, description, is_system) VALUES
 (1, 1, '최고 관리자', '모든 권한. 시스템 역할', TRUE),
 (2, 1, '인사 담당',   '인사·근태·휴가·급여·발령·평가 관리', FALSE),
 (3, 1, '경영진',      '전사 조회와 대시보드', FALSE),
 (4, 1, '직원',        '본인 셀프서비스와 팀 범위 조회', FALSE),
 (11, 2, '최고 관리자', '모든 권한. 시스템 역할', TRUE),
 (12, 2, '인사 담당',   '인사·근태·휴가·급여·발령·평가 관리', FALSE),
 (13, 2, '경영진',      '전사 조회와 대시보드', FALSE),
 (14, 2, '직원',        '본인 셀프서비스와 팀 범위 조회', FALSE);

-- 최고 관리자: 18개 전부 ALL
INSERT INTO role_permission (company_id, role_id, permission_code, scope)
SELECT r.company_id, r.id, p, 'ALL'
FROM role r CROSS JOIN unnest(enum_range(NULL::permission_code)) AS p
WHERE r.is_system;

-- 인사 담당: 14개
INSERT INTO role_permission (company_id, role_id, permission_code, scope)
SELECT r.company_id, r.id, p::permission_code, 'ALL'
FROM role r CROSS JOIN unnest(ARRAY['ORG_MANAGE','EMPLOYEE_READ','EMPLOYEE_MANAGE','ATTENDANCE_READ',
  'ATTENDANCE_MANAGE','LEAVE_READ','LEAVE_MANAGE','PAYROLL_READ','PAYROLL_MANAGE','ASSIGNMENT_READ',
  'ASSIGNMENT_MANAGE','EVAL_READ','EVAL_MANAGE','DASHBOARD_COMPANY']) AS p
WHERE r.name = '인사 담당';

-- 경영진: 전사 조회 6개 + 대시보드
INSERT INTO role_permission (company_id, role_id, permission_code, scope)
SELECT r.company_id, r.id, p::permission_code, 'ALL'
FROM role r CROSS JOIN unnest(ARRAY['EMPLOYEE_READ','ATTENDANCE_READ','LEAVE_READ','PAYROLL_READ',
  'ASSIGNMENT_READ','EVAL_READ','DASHBOARD_COMPANY']) AS p
WHERE r.name = '경영진';

-- 직원: 팀 범위 조회 5개
INSERT INTO role_permission (company_id, role_id, permission_code, scope)
SELECT r.company_id, r.id, p::permission_code, 'TEAM'
FROM role r CROSS JOIN unnest(ARRAY['EMPLOYEE_READ','ATTENDANCE_READ','LEAVE_READ',
  'ASSIGNMENT_READ','EVAL_READ']) AS p
WHERE r.name = '직원';

-- ---------------------------------------------------------------------
-- 조직 (조직장은 직원을 만든 뒤 지정)
-- ---------------------------------------------------------------------
INSERT INTO org_unit (id, company_id, parent_id, name, level_name, monthly_budget, sort_order) VALUES
 (1, 1, NULL, '넥서스랩스',   '회사', NULL, 0),
 (2, 1, 1,    '경영지원본부', '본부', 60000000, 1),
 (3, 1, 1,    '개발본부',     '본부', 90000000, 2),
 (4, 1, 2,    '인사팀',       '팀',   25000000, 1),
 (5, 1, 2,    '재무팀',       '팀',   20000000, 2),
 (6, 1, 3,    '백엔드팀',     '팀',   40000000, 1),
 (7, 1, 3,    '프론트엔드팀', '팀',   30000000, 2),
 (11, 2, NULL, '한빛푸드',   '회사', NULL, 0),
 (12, 2, 11,   '본사',       '사업장', NULL, 1),
 (13, 2, 11,   '천안공장',   '사업장', 80000000, 2),
 (14, 2, 12,   '경영지원부', '부서', 20000000, 1),
 (15, 2, 12,   '영업부',     '부서', 20000000, 2),
 (16, 2, 13,   '생산1조',    '조',   NULL, 1),
 (17, 2, 13,   '생산2조',    '조',   NULL, 2),
 (18, 2, 13,   '품질관리부', '부서', 10000000, 3);

-- 직급 · 직책 · 고용형태
INSERT INTO job_grade (id, company_id, name, sort_order) VALUES
 (1,1,'사원',1),(2,1,'대리',2),(3,1,'과장',3),(4,1,'차장',4),(5,1,'부장',5),(6,1,'이사',6),
 (11,2,'사원',1),(12,2,'주임',2),(13,2,'대리',3),(14,2,'과장',4),(15,2,'부장',5);
INSERT INTO job_title (id, company_id, name, sort_order) VALUES
 (1,1,'대표이사',1),(2,1,'본부장',2),(3,1,'팀장',3),
 (11,2,'대표',1),(12,2,'공장장',2),(13,2,'부서장',3),(14,2,'조장',4);
INSERT INTO employment_type (id, company_id, name, sort_order) VALUES
 (1,1,'정규직',1),(2,1,'계약직',2),(3,1,'인턴',3),
 (11,2,'정규직',1),(12,2,'계약직',2),(13,2,'인턴',3),(14,2,'파트타임',4);

-- ---------------------------------------------------------------------
-- 직원 (급여 계좌는 비워 둔다 — 암호화 키가 애플리케이션에 있으므로 앱에서 입력)
-- ---------------------------------------------------------------------
INSERT INTO employee (id, company_id, employee_no, name, name_en, email, phone, birth_date, gender,
                      hire_date, org_unit_id, job_grade_id, job_title_id, employment_type_id,
                      contract_end_date, emergency_name, emergency_relation, emergency_phone) VALUES
 (1, 1,'NX-2015-0001','박서준','Seojun Park','ceo@nexuslabs.example','010-1000-0001','1978-04-12','MALE','2015-03-02',1,6,1,1,NULL,NULL,NULL,NULL),
 (2, 1,'NX-2020-0001','이지원','Jiwon Lee','admin@nexuslabs.example','010-1000-0002','1990-08-21','FEMALE','2020-07-01',4,3,NULL,1,NULL,'이민수','배우자','010-2000-0002'),
 (3, 1,'NX-2016-0001','최민호','Minho Choi','minho.choi@nexuslabs.example','010-1000-0003','1980-02-03','MALE','2016-01-04',2,5,2,1,NULL,NULL,NULL,NULL),
 (4, 1,'NX-2018-0001','정수아','Sua Jung','sua.jung@nexuslabs.example','010-1000-0004','1985-11-30','FEMALE','2018-05-14',4,4,3,1,NULL,NULL,NULL,NULL),
 (5, 1,'NX-2019-0001','한도윤','Doyun Han','doyun.han@nexuslabs.example','010-1000-0005','1986-06-17','MALE','2019-09-02',5,4,3,1,NULL,NULL,NULL,NULL),
 (6, 1,'NX-2017-0001','강하늘','Haneul Kang','haneul.kang@nexuslabs.example','010-1000-0006','1982-09-09','MALE','2017-02-01',3,5,2,1,NULL,NULL,NULL,NULL),
 (7, 1,'NX-2021-0001','윤서연','Seoyeon Yoon','seoyeon.yoon@nexuslabs.example','010-1000-0007','1989-01-25','FEMALE','2021-03-02',6,3,3,1,NULL,'김도현','배우자','010-2000-0007'),
 (8, 1,'NX-2023-0001','임태현','Taehyun Lim','taehyun.lim@nexuslabs.example','010-1000-0008','1995-05-05','MALE','2023-01-02',6,2,NULL,1,NULL,NULL,NULL,NULL),
 (9, 1,'NX-2026-0001','오지민','Jimin Oh','jimin.oh@nexuslabs.example','010-1000-0009','1999-12-01','FEMALE','2026-03-03',6,1,NULL,2,'2027-03-02',NULL,NULL,NULL),
 (10,1,'NX-2022-0001','서예린','Yerin Seo','yerin.seo@nexuslabs.example','010-1000-0010','1991-03-14','FEMALE','2022-08-01',7,3,3,1,NULL,NULL,NULL,NULL),
 (11,1,'NX-2025-0001','조현우','Hyunwoo Cho','hyunwoo.cho@nexuslabs.example','010-1000-0011','1998-07-22','MALE','2025-11-03',7,1,NULL,1,NULL,NULL,NULL,NULL),
 (12,1,'NX-2024-0001','신유나','Yuna Shin','yuna.shin@nexuslabs.example','010-1000-0012','1996-10-10','FEMALE','2024-04-01',5,2,NULL,1,NULL,NULL,NULL,NULL),
 (101,2,'HB-2012-0001','김한빛',NULL,'ceo@hanbitfood.example','010-3000-0101','1970-05-20','MALE','2012-04-02',11,15,11,11,NULL,NULL,NULL,NULL),
 (102,2,'HB-2014-0001','이정민',NULL,'jm.lee@hanbitfood.example','010-3000-0102','1979-12-11','FEMALE','2014-06-02',14,15,13,11,NULL,NULL,NULL,NULL),
 (103,2,'HB-2016-0001','박영수',NULL,'ys.park@hanbitfood.example','010-3000-0103','1975-03-03','MALE','2016-09-01',13,15,12,11,NULL,NULL,NULL,NULL),
 (104,2,'HB-2019-0001','최은지',NULL,'ej.choi@hanbitfood.example','010-3000-0104','1987-08-08','FEMALE','2019-03-04',15,14,13,11,NULL,NULL,NULL,NULL),
 (105,2,'HB-2020-0001','정우성',NULL,'ws.jung@hanbitfood.example','010-3000-0105','1988-02-28','MALE','2020-01-06',16,13,14,11,NULL,NULL,NULL,NULL),
 (106,2,'HB-2025-0001','강민지',NULL,'mj.kang@hanbitfood.example','010-3000-0106','2000-06-06','FEMALE','2025-06-02',16,11,NULL,12,'2026-12-31',NULL,NULL,NULL),
 (107,2,'HB-2021-0001','윤재호',NULL,'jh.yoon@hanbitfood.example','010-3000-0107','1990-10-01','MALE','2021-05-03',17,13,14,11,NULL,NULL,NULL,NULL),
 (108,2,'HB-2018-0001','장서윤',NULL,'sy.jang@hanbitfood.example','010-3000-0108','1984-04-04','FEMALE','2018-11-01',18,14,13,11,NULL,NULL,NULL,NULL),
 (109,2,'HB-2026-0001','송다은',NULL,'de.song@hanbitfood.example','010-3000-0109','2002-01-15','FEMALE','2026-07-01',17,11,NULL,14,NULL,NULL,NULL,NULL);

UPDATE employee SET hr_memo = '사내 기술 세미나 운영 담당' WHERE id = 8;

INSERT INTO employee_no_seq (company_id, hire_year, last_seq)
SELECT company_id, EXTRACT(YEAR FROM hire_date)::smallint, COUNT(*)
FROM employee GROUP BY company_id, EXTRACT(YEAR FROM hire_date);

-- 조직장 지정
UPDATE org_unit o SET lead_employee_id = v.emp
FROM (VALUES (1,1),(2,3),(3,6),(4,4),(5,5),(6,7),(7,10),
             (11,101),(12,102),(13,103),(14,102),(15,104),(16,105),(17,107),(18,108)) AS v(org, emp)
WHERE o.id = v.org;

-- 재직상태 이력(입사) · 계정
INSERT INTO employment_status_history (company_id, employee_id, status, effective_date, reason)
SELECT company_id, id, 'ACTIVE', hire_date, '입사' FROM employee;

INSERT INTO account (company_id, employee_id, password_hash, role_id, must_change_password)
SELECT e.company_id, e.id, '$2a$10$7ax9pE9rjoU90i1TBio1aOH3cVItMdDcx.y6yga0rsfeKEXHm.Ady',
       CASE WHEN e.id IN (2,101) THEN CASE e.company_id WHEN 1 THEN 1 ELSE 11 END   -- 최고 관리자
            WHEN e.id IN (4,102) THEN CASE e.company_id WHEN 1 THEN 2 ELSE 12 END   -- 인사 담당
            WHEN e.id IN (1,3)   THEN 3                                            -- 경영진
            ELSE CASE e.company_id WHEN 1 THEN 4 ELSE 14 END END,                   -- 직원
       FALSE
FROM employee e;

-- 가족 정보(부양가족 수·자녀 수는 여기서 센다)
INSERT INTO employee_family (company_id, employee_id, name, relation, birth_date, is_tax_dependent) VALUES
 (1, 7, '김도현', 'SPOUSE', '1988-03-03', TRUE),
 (1, 7, '김하은', 'CHILD',  '2019-05-01', TRUE),
 (1, 7, '김하준', 'CHILD',  '2022-09-15', TRUE),
 (1, 2, '이민수', 'SPOUSE', '1989-02-02', FALSE),
 (2, 104,'박서진','CHILD',  '2017-11-20', TRUE);

-- 회사별 추가 항목
INSERT INTO employee_field_def (id, company_id, name, field_type, is_multiple, is_self_editable, sort_order) VALUES
 (1, 1, '기술 스택',     'TEXT', TRUE,  TRUE,  1),
 (2, 1, '학력',          'TEXT', TRUE,  TRUE,  2),
 (11, 2, '보건증 만료일', 'DATE', FALSE, FALSE, 1);
INSERT INTO employee_field_value (company_id, employee_id, field_def_id, seq, value) VALUES
 (1, 8, 1, 1, 'Java / Spring Boot'), (1, 8, 1, 2, 'PostgreSQL'),
 (1, 8, 2, 1, '한국대학교 컴퓨터공학과 졸업 2022'),
 (2, 105, 11, 1, '2027-02-28'), (2, 106, 11, 1, '2026-11-30'), (2, 107, 11, 1, '2027-05-31');

-- ---------------------------------------------------------------------
-- 근무시간 · 휴일
-- ---------------------------------------------------------------------
INSERT INTO work_schedule (company_id, effective_from, start_time, end_time, break_minutes, late_grace_minutes,
                           night_start, night_end, overtime_approval_required, work_days) VALUES
 (1, '2026-01-01', '09:00', '18:00', 60, 0,  '22:00', '06:00', TRUE,  31),
 (2, '2026-01-01', '08:00', '17:00', 60, 10, '22:00', '06:00', FALSE, 63);   -- 월–토

INSERT INTO holiday (company_id, holiday_date, name, holiday_type, is_recurring)
SELECT c.id, h.d::date, h.n, h.t::holiday_type, h.r
FROM company c CROSS JOIN (VALUES
 ('2026-01-01','신정','PUBLIC',TRUE), ('2026-02-16','설날 연휴','PUBLIC',FALSE),
 ('2026-02-17','설날','PUBLIC',FALSE), ('2026-02-18','설날 연휴','PUBLIC',FALSE),
 ('2026-03-01','삼일절','PUBLIC',TRUE), ('2026-03-02','삼일절 대체공휴일','SUBSTITUTE',FALSE),
 ('2026-05-05','어린이날','PUBLIC',TRUE), ('2026-05-24','부처님오신날','PUBLIC',FALSE),
 ('2026-05-25','부처님오신날 대체공휴일','SUBSTITUTE',FALSE), ('2026-06-06','현충일','PUBLIC',TRUE),
 ('2026-08-15','광복절','PUBLIC',TRUE), ('2026-08-17','광복절 대체공휴일','SUBSTITUTE',FALSE),
 ('2026-09-24','추석 연휴','PUBLIC',FALSE), ('2026-09-25','추석','PUBLIC',FALSE),
 ('2026-09-26','추석 연휴','PUBLIC',FALSE), ('2026-10-03','개천절','PUBLIC',TRUE),
 ('2026-10-05','개천절 대체공휴일','SUBSTITUTE',FALSE), ('2026-10-09','한글날','PUBLIC',TRUE),
 ('2026-12-25','성탄절','PUBLIC',TRUE)) AS h(d, n, t, r);
INSERT INTO holiday (company_id, holiday_date, name, holiday_type, is_recurring) VALUES
 (1, '2026-04-15', '창립기념일', 'COMPANY', TRUE);

-- ---------------------------------------------------------------------
-- 휴가 종류 · 부여(2026 휴가 연도)
-- ---------------------------------------------------------------------
INSERT INTO leave_type (id, company_id, name, annual_days, deducts_balance, is_paid, prorate_first_year,
                        seniority_start_years, seniority_interval_years, seniority_add_days, seniority_max_days, sort_order) VALUES
 (1, 1, '연차',   15, TRUE,  TRUE, TRUE,  3, 2, 1, 25, 1),
 (2, 1, '병가',    0, FALSE, TRUE, FALSE, NULL,NULL,NULL,NULL, 2),
 (3, 1, '경조사',  0, FALSE, TRUE, FALSE, NULL,NULL,NULL,NULL, 3),
 (11, 2, '연차',   15, TRUE,  TRUE, TRUE,  3, 2, 1, 25, 1),
 (12, 2, '병가',    0, FALSE, TRUE, FALSE, NULL,NULL,NULL,NULL, 2),
 (13, 2, '경조사',  0, FALSE, TRUE, FALSE, NULL,NULL,NULL,NULL, 3),
 (14, 2, '리프레시휴가', 5, TRUE, TRUE, FALSE, NULL,NULL,NULL,NULL, 4);

-- 연차: 근속 가산 = (⌊(근속연수−3)÷2⌋+1)×1, 2026 중 입사자는 입사 부여(남은 개월÷12, 내림)
INSERT INTO leave_grant (company_id, employee_id, leave_type_id, leave_year, grant_type, days, reason) VALUES
 (1,1,1,2026,'REGULAR',19,NULL),(1,2,1,2026,'REGULAR',17,NULL),(1,3,1,2026,'REGULAR',19,NULL),
 (1,4,1,2026,'REGULAR',18,NULL),(1,5,1,2026,'REGULAR',17,NULL),(1,6,1,2026,'REGULAR',18,NULL),
 (1,7,1,2026,'REGULAR',16,NULL),(1,8,1,2026,'REGULAR',15,NULL),(1,9,1,2026,'HIRE',12,NULL),
 (1,10,1,2026,'REGULAR',16,NULL),(1,11,1,2026,'REGULAR',15,NULL),(1,12,1,2026,'REGULAR',15,NULL),
 (2,101,11,2026,'REGULAR',21,NULL),(2,102,11,2026,'REGULAR',20,NULL),(2,103,11,2026,'REGULAR',19,NULL),
 (2,104,11,2026,'REGULAR',17,NULL),(2,105,11,2026,'REGULAR',17,NULL),(2,106,11,2026,'REGULAR',15,NULL),
 (2,107,11,2026,'REGULAR',16,NULL),(2,108,11,2026,'REGULAR',18,NULL),(2,109,11,2026,'HIRE',7,NULL);
INSERT INTO leave_grant (company_id, employee_id, leave_type_id, leave_year, grant_type, days)
SELECT 2, id, 14, 2026, CASE WHEN id = 109 THEN 'HIRE' ELSE 'REGULAR' END::leave_grant_type, 5
FROM employee WHERE company_id = 2;
INSERT INTO leave_grant (company_id, employee_id, leave_type_id, leave_year, grant_type, days, reason, created_by) VALUES
 (1, 8, 1, 2026, 'ADJUSTMENT', 1, '사내 해커톤 우수상 포상', 2);

-- ---------------------------------------------------------------------
-- 출장 경비 종류
-- ---------------------------------------------------------------------
INSERT INTO expense_type (company_id, name, receipt_required, sort_order)
SELECT c.id, t.n, t.r, t.o FROM company c CROSS JOIN (VALUES
 ('교통비',TRUE,1),('숙박비',TRUE,2),('식비',TRUE,3),('일비',FALSE,4),('기타',TRUE,5)) AS t(n, r, o);

-- ---------------------------------------------------------------------
-- 승인선
-- ---------------------------------------------------------------------
INSERT INTO approval_line (id, company_id, name, work_type, cond_job_title_id, is_default, priority) VALUES
 (1, 1, '휴가 기본',      'LEAVE',         NULL, TRUE,  100),
 (2, 1, '연장근무 기본',  'OVERTIME',      NULL, TRUE,  100),
 (3, 1, '출장 기본',      'BUSINESS_TRIP', NULL, TRUE,  100),
 (4, 1, '출장 경비 기본', 'TRIP_EXPENSE',  NULL, TRUE,  100),
 (5, 1, '본부장 휴가',    'LEAVE',         2,    FALSE, 1),
 (11, 2, '휴가 기본',      'LEAVE',         NULL, TRUE,  100),
 (12, 2, '연장근무 기본',  'OVERTIME',      NULL, TRUE,  100),
 (13, 2, '출장 기본',      'BUSINESS_TRIP', NULL, TRUE,  100),
 (14, 2, '출장 경비 기본', 'TRIP_EXPENSE',  NULL, TRUE,  100);
INSERT INTO approval_line_step (company_id, approval_line_id, step_order, approver_type, up_levels, job_title_id) VALUES
 (1, 1, 1, 'ORG_LEAD', NULL, NULL), (1, 1, 2, 'ORG_LEAD_UP', 1, NULL),   -- NEXUS 휴가: 팀장 → 본부장
 (1, 2, 1, 'ORG_LEAD', NULL, NULL), (1, 3, 1, 'ORG_LEAD', NULL, NULL), (1, 4, 1, 'ORG_LEAD', NULL, NULL),
 (1, 5, 1, 'JOB_TITLE', NULL, 1),                                       -- 본부장 휴가: 대표이사
 (2, 11, 1, 'ORG_LEAD', NULL, NULL), (2, 11, 2, 'JOB_TITLE', NULL, 11), -- 한빛 휴가: 조직장 → 대표
 (2, 12, 1, 'ORG_LEAD', NULL, NULL), (2, 13, 1, 'ORG_LEAD', NULL, NULL), (2, 14, 1, 'ORG_LEAD', NULL, NULL);

-- ---------------------------------------------------------------------
-- 급여 항목 · 계산 변수 · 세율 구간
-- ---------------------------------------------------------------------
INSERT INTO pay_item (id, company_id, name, item_kind, is_taxable, calc_method, apply_to, employee_rate, company_rate, sort_order)
SELECT c.id * 20 - 19 + i.o - 1, c.id, i.n, 'DEDUCTION', TRUE, 'TAXABLE_RATE', 'ALL', i.er, i.cr, 100 + i.o
FROM company c CROSS JOIN (VALUES
 ('국민연금',4.5,4.5,1),('건강보험',3.545,3.545,2),('장기요양보험',0.4591,0.4591,3),('고용보험',0.9,1.15,4)) AS i(n, er, cr, o);
-- id: NEXUS 1–4, 한빛 21–24

INSERT INTO pay_item (id, company_id, name, item_kind, is_taxable, calc_method, attendance_basis, multiplier, sort_order)
SELECT c.id * 20 - 15 + i.o - 1, c.id, i.n, i.k::pay_item_kind, TRUE, 'ATTENDANCE', i.b::attendance_basis, i.m, 50 + i.o
FROM company c CROSS JOIN (VALUES
 ('연장근로수당','EARNING','OVERTIME',1.5,1),('야간근로수당','EARNING','NIGHT',0.5,2),
 ('휴일근로수당','EARNING','HOLIDAY',1.5,3),('휴일연장근로수당','EARNING','HOLIDAY_OVERTIME',2.0,4),
 ('결근 공제','DEDUCTION','ABSENCE',1.0,5)) AS i(n, k, b, m, o);
-- id: NEXUS 5–9, 한빛 25–29

INSERT INTO pay_item (id, company_id, name, item_kind, is_taxable, calc_method, sort_order)
SELECT c.id * 20 - 10, c.id, '출장비 정산', 'EARNING', FALSE, 'TRIP_EXPENSE', 60 FROM company c;
-- id: NEXUS 10, 한빛 30

INSERT INTO pay_item (id, company_id, name, item_kind, is_taxable, non_taxable_limit, calc_method, apply_to,
                      default_amount, in_ordinary_wage, sort_order) VALUES
 (11, 1, '식대',     'EARNING', FALSE, 200000, 'FIXED', 'ALL',      200000, TRUE,  10),
 (12, 1, '직책수당', 'EARNING', TRUE,  NULL,   'FIXED', 'SELECTED', NULL,   TRUE,  11),
 (13, 1, '성과급',   'EARNING', TRUE,  NULL,   'MANUAL','ALL',      NULL,   FALSE, 20),
 (14, 1, '소급 조정','EARNING', TRUE,  NULL,   'MANUAL','ALL',      NULL,   FALSE, 21),
 (31, 2, '식대',     'EARNING', FALSE, 200000, 'FIXED', 'ALL',      250000, TRUE,  10),  -- 한도 초과 5만원은 과세
 (32, 2, '생산수당', 'EARNING', TRUE,  NULL,   'FIXED', 'SELECTED', NULL,   TRUE,  11),
 (33, 2, '소급 조정','EARNING', TRUE,  NULL,   'MANUAL','ALL',      NULL,   FALSE, 21);

-- 한빛푸드는 다자녀 추가 공제를 2만원으로 쓴다(기본값 0원)
INSERT INTO pay_variable (company_id, var_code, value, effective_from)
SELECT c.id, v.code::pay_var_code,
       CASE WHEN c.id = 2 AND v.code = 'MULTI_CHILD_DEDUCTION' THEN 20000 ELSE v.val END,
       '2026-01-01'
FROM company c CROSS JOIN (VALUES
 ('DEPENDENT_DEDUCTION',125000),('MULTI_CHILD_DEDUCTION',0),('LOCAL_TAX_RATE',10),
 ('ANNUAL_SPLIT_MONTHS',12),('MONTHLY_STANDARD_HOURS',209)) AS v(code, val);

INSERT INTO tax_bracket (company_id, lower_bound, upper_bound, rate, progressive_deduction)
SELECT c.id, b.lo, b.hi, b.r, b.d FROM company c CROSS JOIN (VALUES
 (0::bigint, 1200000::bigint, 6, 0), (1200000, 4200000, 15, 108000), (4200000, 7400000, 24, 486000),
 (7400000, 12500000, 35, 1300000), (12500000, NULL, 38, 1675000)) AS b(lo, hi, r, d);

-- ---------------------------------------------------------------------
-- 직원 급여 · 직원별 항목
-- ---------------------------------------------------------------------
INSERT INTO employee_salary (company_id, employee_id, salary_type, annual_salary, monthly_base, effective_from, reason, created_by) VALUES
 (1,1,'ANNUAL',120000000,10000000,'2026-01-01','2026 연봉 계약',2),
 (1,2,'ANNUAL', 54000000, 4500000,'2026-01-01','2026 연봉 계약',2),
 (1,3,'ANNUAL', 90000000, 7500000,'2026-01-01','2026 연봉 계약',2),
 (1,4,'ANNUAL', 66000000, 5500000,'2026-01-01','2026 연봉 계약',2),
 (1,5,'ANNUAL', 64800000, 5400000,'2026-01-01','2026 연봉 계약',2),
 (1,6,'ANNUAL', 96000000, 8000000,'2026-01-01','2026 연봉 계약',2),
 (1,7,'ANNUAL', 60000000, 5000000,'2026-01-01','2026 연봉 계약',2),
 (1,8,'ANNUAL', 45600000, 3800000,'2026-01-01','2026 연봉 계약',2),
 (1,9,'MONTHLY',NULL,     2800000,'2026-03-03','입사',2),
 (1,10,'ANNUAL',58800000, 4900000,'2026-01-01','2026 연봉 계약',2),
 (1,11,'ANNUAL',38400000, 3200000,'2026-01-01','2026 연봉 계약',2),
 (1,12,'ANNUAL',42000000, 3500000,'2026-01-01','2026 연봉 계약',2),
 (2,101,'MONTHLY',NULL,8000000,'2026-01-01','2026 급여',101),
 (2,102,'MONTHLY',NULL,5200000,'2026-01-01','2026 급여',101),
 (2,103,'MONTHLY',NULL,5500000,'2026-01-01','2026 급여',101),
 (2,104,'MONTHLY',NULL,4200000,'2026-01-01','2026 급여',101),
 (2,105,'MONTHLY',NULL,3300000,'2026-01-01','2026 급여',101),
 (2,106,'MONTHLY',NULL,2500000,'2026-01-01','2026 급여',101),
 (2,107,'MONTHLY',NULL,3200000,'2026-01-01','2026 급여',101),
 (2,108,'MONTHLY',NULL,4000000,'2026-01-01','2026 급여',101),
 (2,109,'MONTHLY',NULL,2156880,'2026-07-01','입사(파트타임)',101);
-- 연봉 인상 이력 예시(append-only: 새 행)
INSERT INTO employee_salary (company_id, employee_id, salary_type, annual_salary, monthly_base, effective_from, reason, created_by) VALUES
 (1,8,'ANNUAL',48000000,4000000,'2026-07-01','하반기 대리 승진 연봉 조정',2);

INSERT INTO employee_pay_item (company_id, employee_id, pay_item_id, amount, effective_from, reason, created_by) VALUES
 (1,3,12,400000,'2026-01-01','본부장',2),(1,6,12,400000,'2026-01-01','본부장',2),
 (1,4,12,200000,'2026-01-01','팀장',2),(1,5,12,200000,'2026-01-01','팀장',2),
 (1,7,12,200000,'2026-01-01','팀장',2),(1,10,12,200000,'2026-01-01','팀장',2),
 (2,105,32,150000,'2026-01-01','생산 조장',101),(2,106,32,100000,'2026-01-01','생산직',101),
 (2,107,32,150000,'2026-01-01','생산 조장',101),(2,109,32,100000,'2026-07-01','생산직',101);

-- ---------------------------------------------------------------------
-- 인사발령 이력 예시
-- ---------------------------------------------------------------------
INSERT INTO assignment_history (company_id, employee_id, assignment_type, from_org_unit_id, to_org_unit_id,
  from_job_grade_id, to_job_grade_id, from_job_title_id, to_job_title_id, reason, effective_date, created_by) VALUES
 (1, 8, 'PROMOTION', 6, 6, 1, 2, NULL, NULL, '2026 하반기 정기 승진', '2026-07-01', 2),
 (1, 10,'TITLE_CHANGE', 7, 7, 3, 3, NULL, 3, '프론트엔드팀장 임명', '2025-01-02', 2);

-- ---------------------------------------------------------------------
-- 근태 · 연장근무 · 휴가 · 출장 데모 (NEXUS LABS, 2026-10)
-- ---------------------------------------------------------------------
-- 휴가: 조현우 10/1–10/2 연차 2일, 승인완료(팀장 → 본부장)
INSERT INTO leave_request (id, company_id, employee_id, leave_type_id, leave_year, start_date, end_date, days, reason, status) VALUES
 (1, 1, 11, 1, 2026, '2026-10-01', '2026-10-02', 2, '개인 사유', 'APPROVED'),
 (2, 1, 12, 1, 2026, '2026-10-12', '2026-10-13', 2, '가족 여행', 'PENDING');
INSERT INTO approval_step (company_id, work_type, target_id, approval_line_id, step_order, approver_id, status, comment, acted_at) VALUES
 (1,'LEAVE',1,1,1,10,'APPROVED','확인했습니다','2026-09-28 10:00+09'),
 (1,'LEAVE',1,1,2,6, 'APPROVED',NULL,'2026-09-28 14:30+09'),
 (1,'LEAVE',2,1,1,5, 'PENDING', NULL,NULL),
 (1,'LEAVE',2,1,2,3, 'WAITING', NULL,NULL);

-- 출장: 윤서연 10/6–10/7 부산, 승인완료(본인이 팀장이라 상위 조직장인 개발본부장이 승인)
INSERT INTO business_trip (id, company_id, employee_id, trip_type, destination, purpose, start_date, end_date, estimated_cost, status) VALUES
 (1, 1, 7, 'DOMESTIC', '부산 해운대구 고객사', 'HR 시스템 도입 미팅', '2026-10-06', '2026-10-07', 450000, 'APPROVED');
INSERT INTO approval_step (company_id, work_type, target_id, approval_line_id, step_order, approver_id, status, acted_at) VALUES
 (1,'BUSINESS_TRIP',1,3,1,6,'APPROVED','2026-10-02 11:00+09');

-- 연장근무: 임태현 10/1 18:00–20:00 신청, 팀장이 1시간 30분으로 줄여 승인
INSERT INTO overtime_request (id, company_id, employee_id, work_date, planned_start, planned_end, requested_minutes, approved_minutes, reason, status) VALUES
 (1, 1, 8, '2026-10-01', '2026-10-01 18:00+09', '2026-10-01 20:00+09', 120, 90, '배포 대응', 'APPROVED');
INSERT INTO approval_step (company_id, work_type, target_id, approval_line_id, step_order, approver_id, status, approved_minutes, comment, acted_at) VALUES
 (1,'OVERTIME',1,2,1,7,'APPROVED',90,'배포는 19:30까지로 충분합니다','2026-10-01 15:00+09');

-- 근태
INSERT INTO attendance (company_id, employee_id, work_date, status, work_type, check_in_at, check_out_at,
                        check_in_method, check_out_method, place_memo, leave_request_id, business_trip_id) VALUES
 (1, 8, '2026-10-01','CHECKED_OUT','OFFICE','2026-10-01 08:55+09','2026-10-01 22:40+09','WEB','WEB',NULL,NULL,NULL), -- 연장 90분 인정·야간 40분
 (1, 8, '2026-10-02','CHECKED_OUT','REMOTE','2026-10-02 09:12+09','2026-10-02 18:05+09','WEB','WEB',NULL,NULL,NULL), -- 지각
 (1, 7, '2026-10-01','CHECKED_OUT','OFFICE','2026-10-01 08:50+09','2026-10-01 18:10+09','MOBILE','MOBILE',NULL,NULL,NULL),
 (1, 7, '2026-10-02','CHECKED_OUT','FIELD', '2026-10-02 09:00+09','2026-10-02 17:30+09','MOBILE','MOBILE','판교 협력사 미팅',NULL,NULL), -- 조퇴
 (1, 10,'2026-10-01','CHECKED_OUT','OFFICE','2026-10-01 08:58+09','2026-10-01 18:02+09','WEB','WEB',NULL,NULL,NULL),
 (1, 10,'2026-10-02','MISSING_CHECKOUT','OFFICE','2026-10-02 09:01+09',NULL,'WEB',NULL,NULL,NULL,NULL),            -- 퇴근미기록
 (1, 11,'2026-10-01','ON_VACATION',NULL,NULL,NULL,'SYSTEM',NULL,NULL,1,NULL),
 (1, 11,'2026-10-02','ON_VACATION',NULL,NULL,NULL,'SYSTEM',NULL,NULL,1,NULL),
 (1, 7, '2026-10-06','ON_BUSINESS_TRIP','BUSINESS_TRIP',NULL,NULL,'SYSTEM',NULL,NULL,NULL,1),
 (1, 7, '2026-10-07','ON_BUSINESS_TRIP','BUSINESS_TRIP',NULL,NULL,'SYSTEM',NULL,NULL,NULL,1),
 (2, 105,'2026-10-02','CHECKED_OUT','OFFICE','2026-10-02 07:52+09','2026-10-02 19:00+09','MOBILE','MOBILE',NULL,NULL,NULL),
 (2, 106,'2026-10-02','CHECKED_OUT','OFFICE','2026-10-02 08:07+09','2026-10-02 17:01+09','MOBILE','MOBILE',NULL,NULL,NULL);

-- ---------------------------------------------------------------------
-- 평가 템플릿 · 평가 기간(예정)
-- ---------------------------------------------------------------------
INSERT INTO eval_template (id, company_id, name) VALUES
 (1, 1, '일반 평가'), (2, 1, '리더 평가'), (11, 2, '기본 평가');
INSERT INTO eval_criteria (id, company_id, eval_template_id, category, name, weight, sort_order) VALUES
 (1, 1, 1, '성과', '업무 성과', 50, 1), (2, 1, 1, '역량', '직무 역량', 30, 2), (3, 1, 1, '태도', '협업 태도', 20, 3),
 (4, 1, 2, '성과', '조직 성과', 40, 1), (5, 1, 2, '역량', '리더십',    40, 2), (6, 1, 2, '태도', '소통',      20, 3),
 (11, 2, 11, '성과', '생산·업무 성과', 60, 1), (12, 2, 11, '태도', '안전·위생 준수', 40, 2);
INSERT INTO eval_question (company_id, eval_criteria_id, content, sort_order) VALUES
 (1,1,'목표한 업무를 기한 안에 완료했는가',1),(1,1,'결과물의 품질이 기대 수준 이상인가',2),
 (1,2,'업무에 필요한 지식과 기술을 갖추고 있는가',1),
 (1,3,'동료와 적극적으로 협력하는가',1),
 (1,4,'조직 목표를 달성했는가',1),(1,5,'구성원의 성장을 돕는가',1),(1,5,'의사결정이 명확하고 빠른가',2),
 (1,6,'정보를 투명하게 공유하는가',1),
 (2,11,'생산 목표량을 달성했는가',1),(2,11,'불량률을 기준 이하로 유지했는가',2),
 (2,12,'안전 수칙과 위생 기준을 지키는가',1);
INSERT INTO eval_cycle (id, company_id, name, start_date, end_date) VALUES
 (1, 1, '2026 하반기 평가', '2026-12-01', '2026-12-15');
INSERT INTO eval_cycle_target (company_id, eval_cycle_id, priority, cond_is_org_lead, cond_employment_type_id, eval_template_id) VALUES
 (1, 1, 1, TRUE, NULL, 2),     -- 조직장 → 리더 평가
 (1, 1, 2, NULL, 3,    NULL),  -- 인턴 → 평가 제외
 (1, 1, 3, NULL, NULL, 1);     -- 나머지 → 일반 평가

-- ---------------------------------------------------------------------
-- 화면 확인용 보강 (NEXUS LABS, 2026-10-21) — 모든 화면에 보여 줄 데이터가 있게
--   아래 "직접 작성" 부분은 손으로 쓴 데이터, "API로 만든 결과" 부분은 실제 API로 신청 · 승인 · 정산 · 평가를
--   돌린 뒤 생긴 행을 옮긴 것이다(급여 계산값 · 승인 단계 · 감사 로그가 서버 계산과 같다)
--   파일(로고 · 사진 · 서류 · 영수증)은 uploads/ 에 있어야 해서 넣지 않았다 — 화면에서 직접 올린다
-- ---------------------------------------------------------------------

-- 결근은 max(입사일, 직원 등록일)부터 센다(AttendanceCalculator) — 등록일을 8/1로 두어 8월부터 결근 · 결근 공제가 보이게
UPDATE employee SET created_at = '2026-08-01 09:00+09', updated_at = '2026-08-01 09:00+09' WHERE company_id = 1;

-- 주소 · 비상 연락처(직원 상세 · 연락처 탭)
UPDATE employee SET address = v.address FROM (VALUES
 (1, '서울특별시 강남구 도곡로 21'), (2, '서울특별시 송파구 올림픽로 300'), (3, '경기도 성남시 분당구 판교로 45'),
 (4, '서울특별시 마포구 월드컵북로 12'), (5, '서울특별시 동작구 상도로 77'), (6, '경기도 용인시 수지구 포은대로 88'),
 (7, '서울특별시 관악구 관악로 150'), (8, '서울특별시 마포구 양화로 45'), (9, '서울특별시 은평구 통일로 600'),
 (10, '경기도 고양시 일산동구 중앙로 1200'), (11, '서울특별시 강서구 공항대로 210'), (12, '인천광역시 연수구 센트럴로 160')
) AS v(id, address) WHERE employee.id = v.id;
UPDATE employee SET emergency_name = '박지은', emergency_relation = '부모', emergency_phone = '010-2000-0008' WHERE id = 8;

-- 퇴직자 1명(퇴직자 보기 · 계정 자동 비활성 · 8월 급여만)
INSERT INTO employee (id, company_id, employee_no, name, name_en, email, phone, address, birth_date, gender,
                      hire_date, org_unit_id, job_grade_id, job_title_id, employment_type_id, status, created_at, updated_at) VALUES
 (13, 1, 'NX-2024-0002', '김도현', 'Dohyun Kim', 'dohyun.kim@nexuslabs.example', '010-1000-0013',
  '서울특별시 성동구 왕십리로 50', '1997-02-14', 'MALE', '2024-03-04', 7, 1, NULL, 1, 'RESIGNED',
  '2026-08-01 09:00+09', '2026-09-01 09:00+09');
UPDATE employee_no_seq SET last_seq = 2 WHERE company_id = 1 AND hire_year = 2024;
INSERT INTO employment_status_history (company_id, employee_id, status, effective_date, reason, created_by) VALUES
 (1, 13, 'ACTIVE', '2024-03-04', '입사', NULL),
 (1, 13, 'RESIGNED', '2026-09-01', '개인 사유(이직)', 4);
INSERT INTO account (company_id, employee_id, password_hash, role_id, is_active, must_change_password)
SELECT 1, 13, password_hash, 4, FALSE, FALSE FROM account WHERE employee_id = 2;
INSERT INTO employee_salary (company_id, employee_id, salary_type, annual_salary, monthly_base, effective_from, reason, created_by) VALUES
 (1, 13, 'ANNUAL', 38400000, 3200000, '2026-01-01', '2026 연봉 계약', 2);
INSERT INTO leave_grant (company_id, employee_id, leave_type_id, leave_year, grant_type, days, reason) VALUES
 (1, 13, 1, 2026, 'REGULAR', 15, NULL);

-- 휴직 · 복직 이력(신유나 육아휴직 2025-12 ~ 2026-02)
INSERT INTO employment_status_history (company_id, employee_id, status, effective_date, reason, created_by) VALUES
 (1, 12, 'ON_LEAVE', '2025-12-01', '육아휴직', 4),
 (1, 12, 'ACTIVE',   '2026-03-02', '육아휴직 복직', 4);

-- 인사발령 이력 — 마지막 발령 결과가 지금 소속 · 직급 · 직책과 같게
INSERT INTO assignment_history (company_id, employee_id, assignment_type, from_org_unit_id, to_org_unit_id,
  from_job_grade_id, to_job_grade_id, from_job_title_id, to_job_title_id, reason, effective_date, created_by) VALUES
 (1, 7,  'PROMOTION',    6, 6, 2, 3, NULL, NULL, '2025 정기 승진',          '2025-01-01', 2),
 (1, 7,  'TITLE_CHANGE', 6, 6, 3, 3, NULL, 3,    '백엔드팀장 임명',          '2025-07-01', 2),
 (1, 12, 'TRANSFER',     4, 5, 2, 2, NULL, NULL, '재무 인력 보강',           '2025-07-01', 2),
 (1, 4,  'PROMOTION',    4, 4, 3, 4, 3, 3,       '2026 상반기 정기 승진',     '2026-01-01', 2),
 (1, 13, 'TRANSFER',     6, 7, 1, 1, NULL, NULL, '프론트엔드 인력 재배치',     '2025-03-03', 2);

-- 회사 정보 변경 이력(사옥 이전 · 대표 변경 — 새 값이 지금 회사 정보와 같다)
INSERT INTO company_change_history (company_id, field, old_value, new_value, effective_date, reason, changed_by, created_at) VALUES
 (1, 'ADDRESS',  '서울특별시 서초구 강남대로 311, 4층', '서울특별시 강남구 테헤란로 123, 7층', '2023-04-01', '사옥 이전', 2, '2023-03-27 10:00+09'),
 (1, 'CEO_NAME', '최정훈', '박서준', '2021-01-01', '대표이사 선임(주주총회 결의)', 2, '2020-12-28 15:00+09');

-- 근태 2026-08-01 ~ 10-20 — 근무일마다 출퇴근. 지각 · 조퇴 · 재택 · 외근 · 결근 · 퇴근미기록이 섞이게.
-- 휴가 · 출장으로 비는 날(아래 API로 만든 결과에서 승인)과 이미 있는 날은 건너뛴다
WITH away(employee_id, work_date) AS (VALUES
  (8, date '2026-09-14'), (8, '2026-09-15'), (4, '2026-09-07'), (9, '2026-08-24'),
  (5, '2026-10-14'), (5, '2026-10-15'), (5, '2026-10-16'), (6, '2026-09-08'), (6, '2026-09-09'),
  (10, '2026-10-19'), (10, '2026-10-20'),
  (12, '2026-09-03'), (9, '2026-10-08')),                             -- 마지막 두 줄: 결근(기록 없음)
special(employee_id, work_date, check_out) AS (VALUES                 -- 연장근무 한 날 · 퇴근미기록
  (8, date '2026-09-10', time '21:10'), (9, '2026-09-22', '20:05'), (10, '2026-09-30', '22:40'),
  (11, '2026-10-15', '18:30'), (11, '2026-10-13', NULL), (5, '2026-09-17', NULL)),
days AS (
  SELECT e.id AS employee_id, d::date AS work_date,
         (e.id * 31 + EXTRACT(DOY FROM d))::int AS n, EXTRACT(ISODOW FROM d)::int AS dow
  FROM employee e CROSS JOIN generate_series(date '2026-08-01', date '2026-10-20', interval '1 day') d
  WHERE e.company_id = 1 AND e.hire_date <= d::date
    AND (e.id <> 13 OR d::date < date '2026-09-01')
    AND EXTRACT(ISODOW FROM d) <= 5
    AND NOT EXISTS (SELECT 1 FROM holiday h WHERE h.company_id = 1 AND h.holiday_date = d::date)
    AND NOT EXISTS (SELECT 1 FROM attendance a WHERE a.employee_id = e.id AND a.work_date = d::date)
    AND NOT EXISTS (SELECT 1 FROM away w WHERE w.employee_id = e.id AND w.work_date = d::date)),
t AS (
  SELECT d.*, s.work_date IS NOT NULL AS special, s.check_out AS special_out,
         CASE WHEN d.n % 19 = 0 THEN time '09:00' + make_interval(mins => 3 + d.n % 13)
              ELSE time '08:30' + make_interval(mins => d.n % 28) END AS check_in,
         CASE WHEN d.n % 23 = 0 THEN time '17:20' + make_interval(mins => d.n % 30)
              ELSE time '18:00' + make_interval(mins => d.n % 40) END AS check_out,
         CASE WHEN d.dow = 5 AND d.employee_id IN (8, 9, 11, 12) AND d.n % 2 = 0 THEN 'REMOTE'
              WHEN d.employee_id IN (3, 6) AND d.n % 11 = 0 THEN 'FIELD'
              ELSE 'OFFICE' END AS work_type
  FROM days d LEFT JOIN special s ON s.employee_id = d.employee_id AND s.work_date = d.work_date)
INSERT INTO attendance (company_id, employee_id, work_date, status, work_type, check_in_at, check_out_at,
                        check_in_method, check_out_method, place_memo)
SELECT 1, employee_id, work_date,
       CASE WHEN special AND special_out IS NULL THEN 'MISSING_CHECKOUT' ELSE 'CHECKED_OUT' END::attendance_status,
       work_type::work_type,
       (work_date + check_in) AT TIME ZONE 'Asia/Seoul',
       CASE WHEN special AND special_out IS NULL THEN NULL
            ELSE (work_date + COALESCE(special_out, check_out)) AT TIME ZONE 'Asia/Seoul' END,
       CASE WHEN n % 3 = 0 THEN 'MOBILE' ELSE 'WEB' END::record_method,
       CASE WHEN special AND special_out IS NULL THEN NULL WHEN n % 3 = 0 THEN 'MOBILE' ELSE 'WEB' END::record_method,
       CASE WHEN work_type = 'FIELD' THEN '고객사 미팅' END
FROM t;

-- ===== API로 만든 결과 (신청 → 승인/반려, 결과 보고, 평가 입력 · 확정 · 마감, 8 · 9월 정산 — 실제 API로 돌린 뒤 옮김) =====
-- 휴가 신청
INSERT INTO leave_request (id, company_id, employee_id, leave_type_id, leave_year, start_date, end_date, days, reason, status, cancel_reason, created_at, updated_at) VALUES
 (3, 1, 8, 1, 2026, '2026-09-14', '2026-09-15', 2, '가족 행사', 'APPROVED', NULL, '2026-09-07 10:00:00+09', '2026-09-08 10:00:00+09'),
 (4, 1, 4, 1, 2026, '2026-09-07', '2026-09-07', 1, '개인 일정', 'APPROVED', NULL, '2026-08-31 10:00:00+09', '2026-09-01 10:00:00+09'),
 (5, 1, 9, 2, 2026, '2026-08-24', '2026-08-24', 1, '감기 몸살', 'APPROVED', NULL, '2026-08-17 10:00:00+09', '2026-08-18 10:00:00+09'),
 (6, 1, 5, 1, 2026, '2026-10-14', '2026-10-16', 3, '가족 여행', 'APPROVED', NULL, '2026-10-07 10:00:00+09', '2026-10-08 10:00:00+09'),
 (7, 1, 2, 1, 2026, '2026-10-26', '2026-10-27', 2, '이사', 'PENDING', NULL, '2026-10-16 10:00:00+09', '2026-10-17 10:00:00+09'),
 (8, 1, 9, 1, 2026, '2026-11-20', '2026-11-20', 1, '개인 일정', 'PENDING', NULL, '2026-10-16 10:00:00+09', '2026-10-17 10:00:00+09'),
 (9, 1, 11, 1, 2026, '2026-09-18', '2026-09-18', 1, '개인 사유', 'REJECTED', NULL, '2026-09-11 10:00:00+09', '2026-09-12 10:00:00+09'),
 (10, 1, 12, 1, 2026, '2026-11-05', '2026-11-06', 2, '여행', 'CANCELLED', NULL, '2026-10-16 10:00:00+09', '2026-10-17 10:00:00+09'),
 (11, 1, 7, 3, 2026, '2026-11-12', '2026-11-12', 1, '동생 결혼식', 'CANCEL_REQUESTED', '결혼식 날짜가 바뀌었습니다', '2026-10-16 10:00:00+09', '2026-10-17 10:00:00+09');
-- 연장근무 신청
INSERT INTO overtime_request (id, company_id, employee_id, work_date, planned_start, planned_end, requested_minutes, approved_minutes, reason, status, cancel_reason, created_at, updated_at) VALUES
 (2, 1, 8, '2026-09-10', '2026-09-10 18:00:00+09', '2026-09-10 21:00:00+09', 180, 180, '결제 모듈 배포', 'APPROVED', NULL, '2026-09-09 14:00:00+09', '2026-09-09 16:00:00+09'),
 (3, 1, 9, '2026-09-22', '2026-09-22 18:00:00+09', '2026-09-22 20:00:00+09', 120, 60, '테스트 보완', 'APPROVED', NULL, '2026-09-21 14:00:00+09', '2026-09-21 16:00:00+09'),
 (4, 1, 10, '2026-09-30', '2026-09-30 18:00:00+09', '2026-09-30 22:30:00+09', 270, 270, '분기 마감 배포', 'APPROVED', NULL, '2026-09-29 14:00:00+09', '2026-09-29 16:00:00+09'),
 (5, 1, 11, '2026-10-15', '2026-10-15 18:00:00+09', '2026-10-15 20:00:00+09', 120, NULL, '화면 수정', 'REJECTED', NULL, '2026-10-14 14:00:00+09', '2026-10-14 16:00:00+09'),
 (6, 1, 12, '2026-10-22', '2026-10-22 18:00:00+09', '2026-10-22 20:00:00+09', 120, NULL, '월말 결산 준비', 'PENDING', NULL, '2026-10-19 14:00:00+09', '2026-10-19 16:00:00+09');
-- 출장 신청
INSERT INTO business_trip (id, company_id, employee_id, trip_type, destination, purpose, start_date, end_date, estimated_cost, report_text, reported_at, status, cancel_reason, created_at, updated_at) VALUES
 (2, 1, 6, 'DOMESTIC', '대전 유성구 고객사', '고객사 시스템 점검', '2026-09-08', '2026-09-09', 300000, '고객사 서버 점검 완료. 장애 원인(디스크 부족) 정리 후 개선안을 공유했다.', '2026-09-10 17:00:00+09', 'APPROVED', NULL, '2026-09-03 11:00:00+09', '2026-09-03 15:00:00+09'),
 (3, 1, 10, 'DOMESTIC', '제주 서귀포시', '디자인 시스템 워크숍', '2026-10-19', '2026-10-20', 500000, '디자인 토큰 정리 방향을 확정하고 컴포넌트 우선순위를 정했다.', '2026-10-21 17:00:00+09', 'APPROVED', NULL, '2026-10-14 11:00:00+09', '2026-10-14 15:00:00+09'),
 (4, 1, 8, 'OVERSEAS', '일본 도쿄', 'AWS 컨퍼런스 참석', '2026-11-02', '2026-11-04', 1800000, NULL, NULL, 'PENDING', NULL, '2026-10-15 11:00:00+09', '2026-10-15 15:00:00+09'),
 (5, 1, 4, 'DOMESTIC', '부산 벡스코', '채용 박람회 참가', '2026-09-29', '2026-09-29', 200000, NULL, NULL, 'REJECTED', NULL, '2026-09-24 11:00:00+09', '2026-09-24 15:00:00+09');
-- 휴가 · 출장 승인으로 생긴 근태
INSERT INTO attendance (id, company_id, employee_id, work_date, status, work_type, check_in_at, check_out_at, check_in_method, check_out_method, check_in_lat, check_in_lng, check_out_lat, check_out_lng, place_memo, leave_request_id, business_trip_id, correction_reason, corrected_by, corrected_at, created_at, updated_at) VALUES
 (634, 1, 8, '2026-09-14', 'ON_VACATION', NULL, NULL, NULL, 'SYSTEM', NULL, NULL, NULL, NULL, NULL, NULL, 3, NULL, NULL, NULL, NULL, '2026-10-21 16:22:15.922538+09', '2026-10-21 16:22:15.922538+09'),
 (635, 1, 8, '2026-09-15', 'ON_VACATION', NULL, NULL, NULL, 'SYSTEM', NULL, NULL, NULL, NULL, NULL, NULL, 3, NULL, NULL, NULL, NULL, '2026-10-21 16:22:15.922538+09', '2026-10-21 16:22:15.922538+09'),
 (636, 1, 4, '2026-09-07', 'ON_VACATION', NULL, NULL, NULL, 'SYSTEM', NULL, NULL, NULL, NULL, NULL, NULL, 4, NULL, NULL, NULL, NULL, '2026-10-21 16:22:16.200206+09', '2026-10-21 16:22:16.200206+09'),
 (637, 1, 9, '2026-08-24', 'ON_VACATION', NULL, NULL, NULL, 'SYSTEM', NULL, NULL, NULL, NULL, NULL, NULL, 5, NULL, NULL, NULL, NULL, '2026-10-21 16:22:16.428036+09', '2026-10-21 16:22:16.428036+09'),
 (638, 1, 5, '2026-10-14', 'ON_VACATION', NULL, NULL, NULL, 'SYSTEM', NULL, NULL, NULL, NULL, NULL, NULL, 6, NULL, NULL, NULL, NULL, '2026-10-21 16:22:16.600973+09', '2026-10-21 16:22:16.600973+09'),
 (639, 1, 5, '2026-10-15', 'ON_VACATION', NULL, NULL, NULL, 'SYSTEM', NULL, NULL, NULL, NULL, NULL, NULL, 6, NULL, NULL, NULL, NULL, '2026-10-21 16:22:16.600973+09', '2026-10-21 16:22:16.600973+09'),
 (640, 1, 5, '2026-10-16', 'ON_VACATION', NULL, NULL, NULL, 'SYSTEM', NULL, NULL, NULL, NULL, NULL, NULL, 6, NULL, NULL, NULL, NULL, '2026-10-21 16:22:16.600973+09', '2026-10-21 16:22:16.600973+09'),
 (641, 1, 7, '2026-11-12', 'ON_VACATION', NULL, NULL, NULL, 'SYSTEM', NULL, NULL, NULL, NULL, NULL, NULL, 11, NULL, NULL, NULL, NULL, '2026-10-21 16:22:17.202603+09', '2026-10-21 16:22:17.202603+09'),
 (642, 1, 6, '2026-09-08', 'ON_BUSINESS_TRIP', 'BUSINESS_TRIP', NULL, NULL, 'SYSTEM', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 2, NULL, NULL, NULL, '2026-10-21 16:22:17.747578+09', '2026-10-21 16:22:17.747578+09'),
 (643, 1, 6, '2026-09-09', 'ON_BUSINESS_TRIP', 'BUSINESS_TRIP', NULL, NULL, 'SYSTEM', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 2, NULL, NULL, NULL, '2026-10-21 16:22:17.747578+09', '2026-10-21 16:22:17.747578+09'),
 (644, 1, 10, '2026-10-19', 'ON_BUSINESS_TRIP', 'BUSINESS_TRIP', NULL, NULL, 'SYSTEM', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 3, NULL, NULL, NULL, '2026-10-21 16:22:17.907009+09', '2026-10-21 16:22:17.907009+09'),
 (645, 1, 10, '2026-10-20', 'ON_BUSINESS_TRIP', 'BUSINESS_TRIP', NULL, NULL, 'SYSTEM', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 3, NULL, NULL, NULL, '2026-10-21 16:22:17.907009+09', '2026-10-21 16:22:17.907009+09');
-- 급여 정산(8월 지급완료 · 9월 확정)
INSERT INTO payroll_run (id, company_id, pay_month, pay_date, status, company_name_snap, ceo_name_snap, business_reg_no_snap, company_address_snap, confirmed_by, confirmed_at, paid_by, paid_at) VALUES
 (1, 1, '2026-08', '2026-09-25', 'PAID', '넥서스랩스', '박서준', '214-88-12345', '서울특별시 강남구 테헤란로 123, 7층', 4, '2026-09-04 15:00:00+09', 4, '2026-09-25 10:00:00+09'),
 (2, 1, '2026-09', '2026-10-23', 'CONFIRMED', '넥서스랩스', '박서준', '214-88-12345', '서울특별시 강남구 테헤란로 123, 7층', 4, '2026-10-02 15:00:00+09', NULL, NULL);
-- 급여명세서
INSERT INTO paystub (id, company_id, payroll_run_id, employee_id, employee_no_snap, employee_name_snap, org_unit_id_snap, org_name_snap, bank_account_masked_snap, base_pay, ordinary_hourly_wage, dependents_count, children_count, worked_days, month_days, gross_pay, taxable_pay, income_tax, local_income_tax, total_deduction, net_pay, company_burden_total, created_at) VALUES
 (1, 1, 1, 1, 'NX-2015-0001', '박서준', 1, '넥서스랩스', NULL, 10000000, 48803, 0, 0, 31, 31, 10200000, 10000000, 2200000, 220000, 3360410, 6839590, 965410, '2026-09-04 15:00:00+09'),
 (2, 1, 1, 3, 'NX-2016-0001', '최민호', 2, '경영지원본부', NULL, 7500000, 38755, 0, 0, 31, 31, 8100000, 7900000, 1465000, 146500, 2354423, 5745577, 762673, '2026-09-04 15:00:00+09'),
 (3, 1, 1, 6, 'NX-2017-0001', '강하늘', 3, '개발본부', NULL, 8000000, 41148, 0, 0, 31, 31, 8600000, 8400000, 1640000, 164000, 2593944, 6006056, 810944, '2026-09-04 15:00:00+09'),
 (4, 1, 1, 4, 'NX-2018-0001', '정수아', 4, '인사팀', NULL, 5500000, 28229, 0, 0, 31, 31, 5900000, 5700000, 882000, 88200, 1506233, 4393767, 550283, '2026-09-04 15:00:00+09'),
 (5, 1, 1, 5, 'NX-2019-0001', '한도윤', 5, '재무팀', NULL, 5400000, 27751, 0, 0, 31, 31, 5800000, 5600000, 858000, 85800, 1470429, 4329571, 540629, '2026-09-04 15:00:00+09'),
 (6, 1, 1, 2, 'NX-2020-0001', '이지원', 4, '인사팀', NULL, 4500000, 22488, 0, 0, 31, 31, 4700000, 4500000, 594000, 59400, 1076584, 3623416, 434434, '2026-09-04 15:00:00+09'),
 (7, 1, 1, 7, 'NX-2021-0001', '윤서연', 6, '백엔드팀', NULL, 5000000, 25837, 3, 2, 31, 31, 5400000, 5200000, 672000, 67200, 1228213, 4171787, 502013, '2026-09-04 15:00:00+09'),
 (8, 1, 1, 10, 'NX-2022-0001', '서예린', 7, '프론트엔드팀', NULL, 4900000, 25358, 0, 0, 31, 31, 5300000, 5100000, 738000, 73800, 1291409, 4008591, 492359, '2026-09-04 15:00:00+09'),
 (9, 1, 1, 8, 'NX-2023-0001', '임태현', 6, '백엔드팀', NULL, 4000000, 20095, 0, 0, 31, 31, 4200000, 4000000, 492000, 49200, 917364, 3282636, 386164, '2026-09-04 15:00:00+09'),
 (10, 1, 1, 12, 'NX-2024-0001', '신유나', 5, '재무팀', NULL, 3500000, 17703, 0, 0, 31, 31, 3700000, 3500000, 417000, 41700, 787843, 2912157, 337893, '2026-09-04 15:00:00+09'),
 (11, 1, 1, 13, 'NX-2024-0002', '김도현', 7, '프론트엔드팀', NULL, 3200000, 16267, 0, 0, 31, 31, 3400000, 3200000, 372000, 37200, 710131, 2689869, 308931, '2026-09-04 15:00:00+09'),
 (12, 1, 1, 11, 'NX-2025-0001', '조현우', 7, '프론트엔드팀', NULL, 3200000, 16267, 0, 0, 31, 31, 3400000, 3200000, 372000, 37200, 710131, 2689869, 308931, '2026-09-04 15:00:00+09'),
 (13, 1, 1, 9, 'NX-2026-0001', '오지민', 6, '백엔드팀', NULL, 2800000, 14354, 0, 0, 31, 31, 3000000, 2800000, 312000, 31200, 606514, 2393486, 270314, '2026-09-04 15:00:00+09'),
 (14, 1, 2, 1, 'NX-2015-0001', '박서준', 1, '넥서스랩스', NULL, 10000000, 48803, 0, 0, 30, 30, 10200000, 10000000, 2200000, 220000, 3360410, 6839590, 965410, '2026-10-02 15:00:00+09'),
 (15, 1, 2, 3, 'NX-2016-0001', '최민호', 2, '경영지원본부', NULL, 7500000, 38755, 0, 0, 30, 30, 8100000, 7900000, 1465000, 146500, 2354423, 5745577, 762673, '2026-10-02 15:00:00+09'),
 (16, 1, 2, 6, 'NX-2017-0001', '강하늘', 3, '개발본부', NULL, 8000000, 41148, 0, 0, 30, 30, 8660000, 8400000, 1640000, 164000, 2593944, 6066056, 810944, '2026-10-02 15:00:00+09'),
 (17, 1, 2, 4, 'NX-2018-0001', '정수아', 4, '인사팀', NULL, 5500000, 28229, 0, 0, 30, 30, 5900000, 5700000, 882000, 88200, 1506233, 4393767, 550283, '2026-10-02 15:00:00+09'),
 (18, 1, 2, 5, 'NX-2019-0001', '한도윤', 5, '재무팀', NULL, 5400000, 27751, 0, 0, 30, 30, 5800000, 5600000, 858000, 85800, 1470429, 4329571, 540629, '2026-10-02 15:00:00+09'),
 (19, 1, 2, 2, 'NX-2020-0001', '이지원', 4, '인사팀', NULL, 4500000, 22488, 0, 0, 30, 30, 4700000, 4500000, 594000, 59400, 1076584, 3623416, 434434, '2026-10-02 15:00:00+09'),
 (20, 1, 2, 7, 'NX-2021-0001', '윤서연', 6, '백엔드팀', NULL, 5000000, 25837, 3, 2, 30, 30, 5700000, 5500000, 744000, 74400, 1335625, 4364375, 530975, '2026-10-02 15:00:00+09'),
 (21, 1, 2, 10, 'NX-2022-0001', '서예린', 7, '프론트엔드팀', NULL, 4900000, 25358, 0, 0, 30, 30, 5479618, 5279618, 781108, 78110, 1355716, 4123902, 509697, '2026-10-02 15:00:00+09'),
 (22, 1, 2, 8, 'NX-2023-0001', '임태현', 6, '백엔드팀', NULL, 4000000, 20095, 0, 0, 30, 30, 4790427, 4590427, 615702, 61570, 1108958, 3681469, 443162, '2026-10-02 15:00:00+09'),
 (23, 1, 2, 12, 'NX-2024-0001', '신유나', 5, '재무팀', NULL, 3500000, 17703, 0, 0, 30, 30, 3700000, 3500000, 417000, 41700, 929467, 2770533, 337893, '2026-10-02 15:00:00+09'),
 (24, 1, 2, 11, 'NX-2025-0001', '조현우', 7, '프론트엔드팀', NULL, 3200000, 16267, 0, 0, 30, 30, 3400000, 3200000, 372000, 37200, 710131, 2689869, 308931, '2026-10-02 15:00:00+09'),
 (25, 1, 2, 9, 'NX-2026-0001', '오지민', 6, '백엔드팀', NULL, 2800000, 14354, 0, 0, 30, 30, 3021531, 2821531, 315229, 31522, 612088, 2409443, 272391, '2026-10-02 15:00:00+09');
-- 급여명세서 항목
INSERT INTO paystub_line (id, company_id, paystub_id, pay_item_id, item_name_snap, item_kind, calc_method, amount, taxable_amount, non_taxable_amount, company_amount, quantity, unit_price, formula_note, sort_order) VALUES
 (1, 1, 1, 11, '식대', 'EARNING', 'FIXED', 200000, 0, 200000, NULL, NULL, NULL, NULL, 10),
 (2, 1, 1, 1, '국민연금', 'DEDUCTION', 'TAXABLE_RATE', 450000, 0, 0, 450000, NULL, NULL, NULL, 101),
 (3, 1, 1, 2, '건강보험', 'DEDUCTION', 'TAXABLE_RATE', 354500, 0, 0, 354500, NULL, NULL, NULL, 102),
 (4, 1, 1, 3, '장기요양보험', 'DEDUCTION', 'TAXABLE_RATE', 45910, 0, 0, 45910, NULL, NULL, NULL, 103),
 (5, 1, 1, 4, '고용보험', 'DEDUCTION', 'TAXABLE_RATE', 90000, 0, 0, 115000, NULL, NULL, NULL, 104),
 (6, 1, 2, 11, '식대', 'EARNING', 'FIXED', 200000, 0, 200000, NULL, NULL, NULL, NULL, 10),
 (7, 1, 2, 12, '직책수당', 'EARNING', 'FIXED', 400000, 400000, 0, NULL, NULL, NULL, NULL, 11),
 (8, 1, 2, 1, '국민연금', 'DEDUCTION', 'TAXABLE_RATE', 355500, 0, 0, 355500, NULL, NULL, NULL, 101),
 (9, 1, 2, 2, '건강보험', 'DEDUCTION', 'TAXABLE_RATE', 280055, 0, 0, 280055, NULL, NULL, NULL, 102),
 (10, 1, 2, 3, '장기요양보험', 'DEDUCTION', 'TAXABLE_RATE', 36268, 0, 0, 36268, NULL, NULL, NULL, 103),
 (11, 1, 2, 4, '고용보험', 'DEDUCTION', 'TAXABLE_RATE', 71100, 0, 0, 90850, NULL, NULL, NULL, 104),
 (12, 1, 3, 11, '식대', 'EARNING', 'FIXED', 200000, 0, 200000, NULL, NULL, NULL, NULL, 10),
 (13, 1, 3, 12, '직책수당', 'EARNING', 'FIXED', 400000, 400000, 0, NULL, NULL, NULL, NULL, 11),
 (14, 1, 3, 1, '국민연금', 'DEDUCTION', 'TAXABLE_RATE', 378000, 0, 0, 378000, NULL, NULL, NULL, 101),
 (15, 1, 3, 2, '건강보험', 'DEDUCTION', 'TAXABLE_RATE', 297780, 0, 0, 297780, NULL, NULL, NULL, 102),
 (16, 1, 3, 3, '장기요양보험', 'DEDUCTION', 'TAXABLE_RATE', 38564, 0, 0, 38564, NULL, NULL, NULL, 103),
 (17, 1, 3, 4, '고용보험', 'DEDUCTION', 'TAXABLE_RATE', 75600, 0, 0, 96600, NULL, NULL, NULL, 104),
 (18, 1, 4, 11, '식대', 'EARNING', 'FIXED', 200000, 0, 200000, NULL, NULL, NULL, NULL, 10),
 (19, 1, 4, 12, '직책수당', 'EARNING', 'FIXED', 200000, 200000, 0, NULL, NULL, NULL, NULL, 11),
 (20, 1, 4, 1, '국민연금', 'DEDUCTION', 'TAXABLE_RATE', 256500, 0, 0, 256500, NULL, NULL, NULL, 101),
 (21, 1, 4, 2, '건강보험', 'DEDUCTION', 'TAXABLE_RATE', 202065, 0, 0, 202065, NULL, NULL, NULL, 102),
 (22, 1, 4, 3, '장기요양보험', 'DEDUCTION', 'TAXABLE_RATE', 26168, 0, 0, 26168, NULL, NULL, NULL, 103),
 (23, 1, 4, 4, '고용보험', 'DEDUCTION', 'TAXABLE_RATE', 51300, 0, 0, 65550, NULL, NULL, NULL, 104),
 (24, 1, 5, 11, '식대', 'EARNING', 'FIXED', 200000, 0, 200000, NULL, NULL, NULL, NULL, 10),
 (25, 1, 5, 12, '직책수당', 'EARNING', 'FIXED', 200000, 200000, 0, NULL, NULL, NULL, NULL, 11),
 (26, 1, 5, 1, '국민연금', 'DEDUCTION', 'TAXABLE_RATE', 252000, 0, 0, 252000, NULL, NULL, NULL, 101),
 (27, 1, 5, 2, '건강보험', 'DEDUCTION', 'TAXABLE_RATE', 198520, 0, 0, 198520, NULL, NULL, NULL, 102),
 (28, 1, 5, 3, '장기요양보험', 'DEDUCTION', 'TAXABLE_RATE', 25709, 0, 0, 25709, NULL, NULL, NULL, 103),
 (29, 1, 5, 4, '고용보험', 'DEDUCTION', 'TAXABLE_RATE', 50400, 0, 0, 64400, NULL, NULL, NULL, 104),
 (30, 1, 6, 11, '식대', 'EARNING', 'FIXED', 200000, 0, 200000, NULL, NULL, NULL, NULL, 10),
 (31, 1, 6, 1, '국민연금', 'DEDUCTION', 'TAXABLE_RATE', 202500, 0, 0, 202500, NULL, NULL, NULL, 101),
 (32, 1, 6, 2, '건강보험', 'DEDUCTION', 'TAXABLE_RATE', 159525, 0, 0, 159525, NULL, NULL, NULL, 102),
 (33, 1, 6, 3, '장기요양보험', 'DEDUCTION', 'TAXABLE_RATE', 20659, 0, 0, 20659, NULL, NULL, NULL, 103),
 (34, 1, 6, 4, '고용보험', 'DEDUCTION', 'TAXABLE_RATE', 40500, 0, 0, 51750, NULL, NULL, NULL, 104),
 (35, 1, 7, 11, '식대', 'EARNING', 'FIXED', 200000, 0, 200000, NULL, NULL, NULL, NULL, 10),
 (36, 1, 7, 12, '직책수당', 'EARNING', 'FIXED', 200000, 200000, 0, NULL, NULL, NULL, NULL, 11),
 (37, 1, 7, 1, '국민연금', 'DEDUCTION', 'TAXABLE_RATE', 234000, 0, 0, 234000, NULL, NULL, NULL, 101),
 (38, 1, 7, 2, '건강보험', 'DEDUCTION', 'TAXABLE_RATE', 184340, 0, 0, 184340, NULL, NULL, NULL, 102),
 (39, 1, 7, 3, '장기요양보험', 'DEDUCTION', 'TAXABLE_RATE', 23873, 0, 0, 23873, NULL, NULL, NULL, 103),
 (40, 1, 7, 4, '고용보험', 'DEDUCTION', 'TAXABLE_RATE', 46800, 0, 0, 59800, NULL, NULL, NULL, 104),
 (41, 1, 8, 11, '식대', 'EARNING', 'FIXED', 200000, 0, 200000, NULL, NULL, NULL, NULL, 10),
 (42, 1, 8, 12, '직책수당', 'EARNING', 'FIXED', 200000, 200000, 0, NULL, NULL, NULL, NULL, 11),
 (43, 1, 8, 1, '국민연금', 'DEDUCTION', 'TAXABLE_RATE', 229500, 0, 0, 229500, NULL, NULL, NULL, 101),
 (44, 1, 8, 2, '건강보험', 'DEDUCTION', 'TAXABLE_RATE', 180795, 0, 0, 180795, NULL, NULL, NULL, 102),
 (45, 1, 8, 3, '장기요양보험', 'DEDUCTION', 'TAXABLE_RATE', 23414, 0, 0, 23414, NULL, NULL, NULL, 103),
 (46, 1, 8, 4, '고용보험', 'DEDUCTION', 'TAXABLE_RATE', 45900, 0, 0, 58650, NULL, NULL, NULL, 104),
 (47, 1, 9, 11, '식대', 'EARNING', 'FIXED', 200000, 0, 200000, NULL, NULL, NULL, NULL, 10),
 (48, 1, 9, 1, '국민연금', 'DEDUCTION', 'TAXABLE_RATE', 180000, 0, 0, 180000, NULL, NULL, NULL, 101),
 (49, 1, 9, 2, '건강보험', 'DEDUCTION', 'TAXABLE_RATE', 141800, 0, 0, 141800, NULL, NULL, NULL, 102),
 (50, 1, 9, 3, '장기요양보험', 'DEDUCTION', 'TAXABLE_RATE', 18364, 0, 0, 18364, NULL, NULL, NULL, 103),
 (51, 1, 9, 4, '고용보험', 'DEDUCTION', 'TAXABLE_RATE', 36000, 0, 0, 46000, NULL, NULL, NULL, 104),
 (52, 1, 10, 11, '식대', 'EARNING', 'FIXED', 200000, 0, 200000, NULL, NULL, NULL, NULL, 10),
 (53, 1, 10, 1, '국민연금', 'DEDUCTION', 'TAXABLE_RATE', 157500, 0, 0, 157500, NULL, NULL, NULL, 101),
 (54, 1, 10, 2, '건강보험', 'DEDUCTION', 'TAXABLE_RATE', 124075, 0, 0, 124075, NULL, NULL, NULL, 102),
 (55, 1, 10, 3, '장기요양보험', 'DEDUCTION', 'TAXABLE_RATE', 16068, 0, 0, 16068, NULL, NULL, NULL, 103),
 (56, 1, 10, 4, '고용보험', 'DEDUCTION', 'TAXABLE_RATE', 31500, 0, 0, 40250, NULL, NULL, NULL, 104),
 (57, 1, 11, 11, '식대', 'EARNING', 'FIXED', 200000, 0, 200000, NULL, NULL, NULL, NULL, 10),
 (58, 1, 11, 1, '국민연금', 'DEDUCTION', 'TAXABLE_RATE', 144000, 0, 0, 144000, NULL, NULL, NULL, 101),
 (59, 1, 11, 2, '건강보험', 'DEDUCTION', 'TAXABLE_RATE', 113440, 0, 0, 113440, NULL, NULL, NULL, 102),
 (60, 1, 11, 3, '장기요양보험', 'DEDUCTION', 'TAXABLE_RATE', 14691, 0, 0, 14691, NULL, NULL, NULL, 103),
 (61, 1, 11, 4, '고용보험', 'DEDUCTION', 'TAXABLE_RATE', 28800, 0, 0, 36800, NULL, NULL, NULL, 104),
 (62, 1, 12, 11, '식대', 'EARNING', 'FIXED', 200000, 0, 200000, NULL, NULL, NULL, NULL, 10),
 (63, 1, 12, 1, '국민연금', 'DEDUCTION', 'TAXABLE_RATE', 144000, 0, 0, 144000, NULL, NULL, NULL, 101),
 (64, 1, 12, 2, '건강보험', 'DEDUCTION', 'TAXABLE_RATE', 113440, 0, 0, 113440, NULL, NULL, NULL, 102),
 (65, 1, 12, 3, '장기요양보험', 'DEDUCTION', 'TAXABLE_RATE', 14691, 0, 0, 14691, NULL, NULL, NULL, 103),
 (66, 1, 12, 4, '고용보험', 'DEDUCTION', 'TAXABLE_RATE', 28800, 0, 0, 36800, NULL, NULL, NULL, 104),
 (67, 1, 13, 11, '식대', 'EARNING', 'FIXED', 200000, 0, 200000, NULL, NULL, NULL, NULL, 10),
 (68, 1, 13, 1, '국민연금', 'DEDUCTION', 'TAXABLE_RATE', 126000, 0, 0, 126000, NULL, NULL, NULL, 101),
 (69, 1, 13, 2, '건강보험', 'DEDUCTION', 'TAXABLE_RATE', 99260, 0, 0, 99260, NULL, NULL, NULL, 102),
 (70, 1, 13, 3, '장기요양보험', 'DEDUCTION', 'TAXABLE_RATE', 12854, 0, 0, 12854, NULL, NULL, NULL, 103),
 (71, 1, 13, 4, '고용보험', 'DEDUCTION', 'TAXABLE_RATE', 25200, 0, 0, 32200, NULL, NULL, NULL, 104),
 (72, 1, 14, 11, '식대', 'EARNING', 'FIXED', 200000, 0, 200000, NULL, NULL, NULL, NULL, 10),
 (73, 1, 14, 1, '국민연금', 'DEDUCTION', 'TAXABLE_RATE', 450000, 0, 0, 450000, NULL, NULL, NULL, 101),
 (74, 1, 14, 2, '건강보험', 'DEDUCTION', 'TAXABLE_RATE', 354500, 0, 0, 354500, NULL, NULL, NULL, 102),
 (75, 1, 14, 3, '장기요양보험', 'DEDUCTION', 'TAXABLE_RATE', 45910, 0, 0, 45910, NULL, NULL, NULL, 103),
 (76, 1, 14, 4, '고용보험', 'DEDUCTION', 'TAXABLE_RATE', 90000, 0, 0, 115000, NULL, NULL, NULL, 104),
 (77, 1, 15, 11, '식대', 'EARNING', 'FIXED', 200000, 0, 200000, NULL, NULL, NULL, NULL, 10),
 (78, 1, 15, 12, '직책수당', 'EARNING', 'FIXED', 400000, 400000, 0, NULL, NULL, NULL, NULL, 11),
 (79, 1, 15, 1, '국민연금', 'DEDUCTION', 'TAXABLE_RATE', 355500, 0, 0, 355500, NULL, NULL, NULL, 101),
 (80, 1, 15, 2, '건강보험', 'DEDUCTION', 'TAXABLE_RATE', 280055, 0, 0, 280055, NULL, NULL, NULL, 102),
 (81, 1, 15, 3, '장기요양보험', 'DEDUCTION', 'TAXABLE_RATE', 36268, 0, 0, 36268, NULL, NULL, NULL, 103),
 (82, 1, 15, 4, '고용보험', 'DEDUCTION', 'TAXABLE_RATE', 71100, 0, 0, 90850, NULL, NULL, NULL, 104),
 (83, 1, 16, 11, '식대', 'EARNING', 'FIXED', 200000, 0, 200000, NULL, NULL, NULL, NULL, 10),
 (84, 1, 16, 12, '직책수당', 'EARNING', 'FIXED', 400000, 400000, 0, NULL, NULL, NULL, NULL, 11),
 (85, 1, 16, 10, '출장비 정산', 'EARNING', 'TRIP_EXPENSE', 60000, 0, 60000, NULL, NULL, NULL, NULL, 60),
 (86, 1, 16, 1, '국민연금', 'DEDUCTION', 'TAXABLE_RATE', 378000, 0, 0, 378000, NULL, NULL, NULL, 101),
 (87, 1, 16, 2, '건강보험', 'DEDUCTION', 'TAXABLE_RATE', 297780, 0, 0, 297780, NULL, NULL, NULL, 102),
 (88, 1, 16, 3, '장기요양보험', 'DEDUCTION', 'TAXABLE_RATE', 38564, 0, 0, 38564, NULL, NULL, NULL, 103),
 (89, 1, 16, 4, '고용보험', 'DEDUCTION', 'TAXABLE_RATE', 75600, 0, 0, 96600, NULL, NULL, NULL, 104),
 (90, 1, 17, 11, '식대', 'EARNING', 'FIXED', 200000, 0, 200000, NULL, NULL, NULL, NULL, 10),
 (91, 1, 17, 12, '직책수당', 'EARNING', 'FIXED', 200000, 200000, 0, NULL, NULL, NULL, NULL, 11),
 (92, 1, 17, 1, '국민연금', 'DEDUCTION', 'TAXABLE_RATE', 256500, 0, 0, 256500, NULL, NULL, NULL, 101),
 (93, 1, 17, 2, '건강보험', 'DEDUCTION', 'TAXABLE_RATE', 202065, 0, 0, 202065, NULL, NULL, NULL, 102),
 (94, 1, 17, 3, '장기요양보험', 'DEDUCTION', 'TAXABLE_RATE', 26168, 0, 0, 26168, NULL, NULL, NULL, 103),
 (95, 1, 17, 4, '고용보험', 'DEDUCTION', 'TAXABLE_RATE', 51300, 0, 0, 65550, NULL, NULL, NULL, 104),
 (96, 1, 18, 11, '식대', 'EARNING', 'FIXED', 200000, 0, 200000, NULL, NULL, NULL, NULL, 10),
 (97, 1, 18, 12, '직책수당', 'EARNING', 'FIXED', 200000, 200000, 0, NULL, NULL, NULL, NULL, 11),
 (98, 1, 18, 1, '국민연금', 'DEDUCTION', 'TAXABLE_RATE', 252000, 0, 0, 252000, NULL, NULL, NULL, 101),
 (99, 1, 18, 2, '건강보험', 'DEDUCTION', 'TAXABLE_RATE', 198520, 0, 0, 198520, NULL, NULL, NULL, 102),
 (100, 1, 18, 3, '장기요양보험', 'DEDUCTION', 'TAXABLE_RATE', 25709, 0, 0, 25709, NULL, NULL, NULL, 103),
 (101, 1, 18, 4, '고용보험', 'DEDUCTION', 'TAXABLE_RATE', 50400, 0, 0, 64400, NULL, NULL, NULL, 104),
 (102, 1, 19, 11, '식대', 'EARNING', 'FIXED', 200000, 0, 200000, NULL, NULL, NULL, NULL, 10),
 (103, 1, 19, 1, '국민연금', 'DEDUCTION', 'TAXABLE_RATE', 202500, 0, 0, 202500, NULL, NULL, NULL, 101),
 (104, 1, 19, 2, '건강보험', 'DEDUCTION', 'TAXABLE_RATE', 159525, 0, 0, 159525, NULL, NULL, NULL, 102),
 (105, 1, 19, 3, '장기요양보험', 'DEDUCTION', 'TAXABLE_RATE', 20659, 0, 0, 20659, NULL, NULL, NULL, 103),
 (106, 1, 19, 4, '고용보험', 'DEDUCTION', 'TAXABLE_RATE', 40500, 0, 0, 51750, NULL, NULL, NULL, 104),
 (107, 1, 20, 11, '식대', 'EARNING', 'FIXED', 200000, 0, 200000, NULL, NULL, NULL, NULL, 10),
 (108, 1, 20, 12, '직책수당', 'EARNING', 'FIXED', 200000, 200000, 0, NULL, NULL, NULL, NULL, 11),
 (109, 1, 20, 13, '성과급', 'EARNING', 'MANUAL', 300000, 300000, 0, NULL, NULL, NULL, NULL, 20),
 (110, 1, 20, 1, '국민연금', 'DEDUCTION', 'TAXABLE_RATE', 247500, 0, 0, 247500, NULL, NULL, NULL, 101),
 (111, 1, 20, 2, '건강보험', 'DEDUCTION', 'TAXABLE_RATE', 194975, 0, 0, 194975, NULL, NULL, NULL, 102),
 (112, 1, 20, 3, '장기요양보험', 'DEDUCTION', 'TAXABLE_RATE', 25250, 0, 0, 25250, NULL, NULL, NULL, 103),
 (113, 1, 20, 4, '고용보험', 'DEDUCTION', 'TAXABLE_RATE', 49500, 0, 0, 63250, NULL, NULL, NULL, 104),
 (114, 1, 21, 11, '식대', 'EARNING', 'FIXED', 200000, 0, 200000, NULL, NULL, NULL, NULL, 10),
 (115, 1, 21, 12, '직책수당', 'EARNING', 'FIXED', 200000, 200000, 0, NULL, NULL, NULL, NULL, 11),
 (116, 1, 21, 5, '연장근로수당', 'EARNING', 'ATTENDANCE', 171166, 171166, 0, NULL, 4.50, 38037, '4.5시간 × 25,358원 × 1.5', 51),
 (117, 1, 21, 6, '야간근로수당', 'EARNING', 'ATTENDANCE', 8452, 8452, 0, NULL, 0.67, 12679, '40분 × 25,358원 × 0.5', 52),
 (118, 1, 21, 1, '국민연금', 'DEDUCTION', 'TAXABLE_RATE', 237582, 0, 0, 237582, NULL, NULL, NULL, 101),
 (119, 1, 21, 2, '건강보험', 'DEDUCTION', 'TAXABLE_RATE', 187162, 0, 0, 187162, NULL, NULL, NULL, 102),
 (120, 1, 21, 3, '장기요양보험', 'DEDUCTION', 'TAXABLE_RATE', 24238, 0, 0, 24238, NULL, NULL, NULL, 103),
 (121, 1, 21, 4, '고용보험', 'DEDUCTION', 'TAXABLE_RATE', 47516, 0, 0, 60715, NULL, NULL, NULL, 104),
 (122, 1, 22, 11, '식대', 'EARNING', 'FIXED', 200000, 0, 200000, NULL, NULL, NULL, NULL, 10),
 (123, 1, 22, 13, '성과급', 'EARNING', 'MANUAL', 500000, 500000, 0, NULL, NULL, NULL, NULL, 20),
 (124, 1, 22, 5, '연장근로수당', 'EARNING', 'ATTENDANCE', 90427, 90427, 0, NULL, 3.00, 30142, '3시간 × 20,095원 × 1.5', 51),
 (125, 1, 22, 1, '국민연금', 'DEDUCTION', 'TAXABLE_RATE', 206569, 0, 0, 206569, NULL, NULL, NULL, 101),
 (126, 1, 22, 2, '건강보험', 'DEDUCTION', 'TAXABLE_RATE', 162730, 0, 0, 162730, NULL, NULL, NULL, 102),
 (127, 1, 22, 3, '장기요양보험', 'DEDUCTION', 'TAXABLE_RATE', 21074, 0, 0, 21074, NULL, NULL, NULL, 103),
 (128, 1, 22, 4, '고용보험', 'DEDUCTION', 'TAXABLE_RATE', 41313, 0, 0, 52789, NULL, NULL, NULL, 104),
 (129, 1, 23, 11, '식대', 'EARNING', 'FIXED', 200000, 0, 200000, NULL, NULL, NULL, NULL, 10),
 (130, 1, 23, 9, '결근 공제', 'DEDUCTION', 'ATTENDANCE', 141624, 0, 0, NULL, 8.00, 17703, '1일(8시간) × 17,703원 × 1', 55),
 (131, 1, 23, 1, '국민연금', 'DEDUCTION', 'TAXABLE_RATE', 157500, 0, 0, 157500, NULL, NULL, NULL, 101),
 (132, 1, 23, 2, '건강보험', 'DEDUCTION', 'TAXABLE_RATE', 124075, 0, 0, 124075, NULL, NULL, NULL, 102),
 (133, 1, 23, 3, '장기요양보험', 'DEDUCTION', 'TAXABLE_RATE', 16068, 0, 0, 16068, NULL, NULL, NULL, 103),
 (134, 1, 23, 4, '고용보험', 'DEDUCTION', 'TAXABLE_RATE', 31500, 0, 0, 40250, NULL, NULL, NULL, 104),
 (135, 1, 24, 11, '식대', 'EARNING', 'FIXED', 200000, 0, 200000, NULL, NULL, NULL, NULL, 10),
 (136, 1, 24, 1, '국민연금', 'DEDUCTION', 'TAXABLE_RATE', 144000, 0, 0, 144000, NULL, NULL, NULL, 101),
 (137, 1, 24, 2, '건강보험', 'DEDUCTION', 'TAXABLE_RATE', 113440, 0, 0, 113440, NULL, NULL, NULL, 102),
 (138, 1, 24, 3, '장기요양보험', 'DEDUCTION', 'TAXABLE_RATE', 14691, 0, 0, 14691, NULL, NULL, NULL, 103),
 (139, 1, 24, 4, '고용보험', 'DEDUCTION', 'TAXABLE_RATE', 28800, 0, 0, 36800, NULL, NULL, NULL, 104),
 (140, 1, 25, 11, '식대', 'EARNING', 'FIXED', 200000, 0, 200000, NULL, NULL, NULL, NULL, 10),
 (141, 1, 25, 5, '연장근로수당', 'EARNING', 'ATTENDANCE', 21531, 21531, 0, NULL, 1.00, 21531, '1시간 × 14,354원 × 1.5', 51),
 (142, 1, 25, 1, '국민연금', 'DEDUCTION', 'TAXABLE_RATE', 126968, 0, 0, 126968, NULL, NULL, NULL, 101),
 (143, 1, 25, 2, '건강보험', 'DEDUCTION', 'TAXABLE_RATE', 100023, 0, 0, 100023, NULL, NULL, NULL, 102),
 (144, 1, 25, 3, '장기요양보험', 'DEDUCTION', 'TAXABLE_RATE', 12953, 0, 0, 12953, NULL, NULL, NULL, 103),
 (145, 1, 25, 4, '고용보험', 'DEDUCTION', 'TAXABLE_RATE', 25393, 0, 0, 32447, NULL, NULL, NULL, 104);
-- 출장 경비 청구(9월분은 9월 급여에 반영, 10월분은 미정산)
INSERT INTO expense_claim (id, company_id, business_trip_id, employee_id, status, paystub_id, cancel_reason, created_at, updated_at) VALUES
 (1, 1, 2, 6, 'APPROVED', 16, NULL, '2026-09-10 17:30:00+09', '2026-09-11 10:00:00+09'),
 (2, 1, 3, 10, 'PENDING', NULL, NULL, '2026-10-21 17:30:00+09', '2026-10-22 10:00:00+09'),
 (3, 1, 1, 7, 'APPROVED', NULL, NULL, '2026-10-08 17:30:00+09', '2026-10-09 10:00:00+09');
-- 출장 경비 줄(일비 — 영수증이 필요 없는 종류)
INSERT INTO expense_claim_line (id, company_id, expense_claim_id, expense_type_id, used_date, amount, description, receipt_file_id) VALUES
 (1, 1, 1, 4, '2026-09-08', 30000, '출장 일비', NULL),
 (2, 1, 1, 4, '2026-09-09', 30000, '출장 일비', NULL),
 (3, 1, 2, 4, '2026-10-19', 30000, '출장 일비', NULL),
 (4, 1, 2, 4, '2026-10-20', 30000, '출장 일비', NULL),
 (5, 1, 3, 4, '2026-10-06', 30000, '출장 일비', NULL),
 (6, 1, 3, 4, '2026-10-07', 30000, '출장 일비', NULL);
-- 승인 단계(휴가 · 연장 · 출장 · 경비)
INSERT INTO approval_step (id, company_id, work_type, target_id, approval_line_id, round, step_order, approver_id, status, approved_minutes, comment, acted_at, needs_reassign, created_at) VALUES
 (7, 1, 'LEAVE', 3, 1, 1, 1, 7, 'APPROVED', NULL, '즐거운 시간 보내세요', '2026-09-07 14:00:00+09', false, '2026-09-07 10:00:00+09'),
 (8, 1, 'LEAVE', 3, 1, 1, 2, 6, 'APPROVED', NULL, '즐거운 시간 보내세요', '2026-09-07 18:00:00+09', false, '2026-09-07 10:00:00+09'),
 (9, 1, 'LEAVE', 4, 1, 1, 1, 3, 'APPROVED', NULL, NULL, '2026-08-31 14:00:00+09', false, '2026-08-31 10:00:00+09'),
 (10, 1, 'LEAVE', 4, 1, 1, 2, 3, 'SKIPPED', NULL, NULL, NULL, false, '2026-08-31 10:00:00+09'),
 (11, 1, 'LEAVE', 5, 1, 1, 1, 7, 'APPROVED', NULL, '몸조리 잘 하세요', '2026-08-17 14:00:00+09', false, '2026-08-17 10:00:00+09'),
 (12, 1, 'LEAVE', 5, 1, 1, 2, 6, 'APPROVED', NULL, '몸조리 잘 하세요', '2026-08-17 18:00:00+09', false, '2026-08-17 10:00:00+09'),
 (13, 1, 'LEAVE', 6, 1, 1, 1, 3, 'APPROVED', NULL, NULL, '2026-10-07 14:00:00+09', false, '2026-10-07 10:00:00+09'),
 (14, 1, 'LEAVE', 6, 1, 1, 2, 3, 'SKIPPED', NULL, NULL, NULL, false, '2026-10-07 10:00:00+09'),
 (15, 1, 'LEAVE', 7, 1, 1, 1, 4, 'PENDING', NULL, NULL, NULL, false, '2026-10-16 10:00:00+09'),
 (16, 1, 'LEAVE', 7, 1, 1, 2, 3, 'WAITING', NULL, NULL, NULL, false, '2026-10-16 10:00:00+09'),
 (17, 1, 'LEAVE', 8, 1, 1, 1, 7, 'APPROVED', NULL, '확인했습니다', '2026-10-16 14:00:00+09', false, '2026-10-16 10:00:00+09'),
 (18, 1, 'LEAVE', 8, 1, 1, 2, 6, 'PENDING', NULL, NULL, NULL, false, '2026-10-16 10:00:00+09'),
 (19, 1, 'LEAVE', 9, 1, 1, 1, 10, 'REJECTED', NULL, '릴리스 일정과 겹칩니다. 다음 주로 옮겨 주세요', '2026-09-11 14:00:00+09', false, '2026-09-11 10:00:00+09'),
 (20, 1, 'LEAVE', 9, 1, 1, 2, 6, 'CANCELLED', NULL, NULL, NULL, false, '2026-09-11 10:00:00+09'),
 (21, 1, 'LEAVE', 10, 1, 1, 1, 5, 'CANCELLED', NULL, NULL, NULL, false, '2026-10-16 10:00:00+09'),
 (22, 1, 'LEAVE', 10, 1, 1, 2, 3, 'CANCELLED', NULL, NULL, NULL, false, '2026-10-16 10:00:00+09'),
 (23, 1, 'LEAVE', 11, 1, 1, 1, 6, 'APPROVED', NULL, NULL, '2026-10-16 14:00:00+09', false, '2026-10-16 10:00:00+09'),
 (24, 1, 'LEAVE', 11, 1, 1, 2, 6, 'SKIPPED', NULL, NULL, NULL, false, '2026-10-16 10:00:00+09'),
 (25, 1, 'LEAVE_CANCEL', 11, NULL, 1, 1, 6, 'PENDING', NULL, NULL, NULL, false, '2026-10-21 16:22:17.237728+09'),
 (26, 1, 'OVERTIME', 2, 2, 1, 1, 7, 'APPROVED', 180, NULL, '2026-09-09 18:00:00+09', false, '2026-09-09 14:00:00+09'),
 (27, 1, 'OVERTIME', 3, 2, 1, 1, 7, 'APPROVED', 60, '1시간이면 충분합니다', '2026-09-21 18:00:00+09', false, '2026-09-21 14:00:00+09'),
 (28, 1, 'OVERTIME', 4, 2, 1, 1, 6, 'APPROVED', 270, NULL, '2026-09-29 18:00:00+09', false, '2026-09-29 14:00:00+09'),
 (29, 1, 'OVERTIME', 5, 2, 1, 1, 10, 'REJECTED', NULL, '다음 날 근무 시간에 처리해 주세요', '2026-10-14 18:00:00+09', false, '2026-10-14 14:00:00+09'),
 (30, 1, 'OVERTIME', 6, 2, 1, 1, 5, 'PENDING', NULL, NULL, NULL, false, '2026-10-19 14:00:00+09'),
 (31, 1, 'BUSINESS_TRIP', 2, 3, 1, 1, 1, 'APPROVED', NULL, NULL, '2026-09-03 15:00:00+09', false, '2026-09-03 11:00:00+09'),
 (32, 1, 'TRIP_EXPENSE', 1, 4, 1, 1, 1, 'APPROVED', NULL, NULL, '2026-09-10 21:30:00+09', false, '2026-09-10 17:30:00+09'),
 (33, 1, 'BUSINESS_TRIP', 3, 3, 1, 1, 6, 'APPROVED', NULL, NULL, '2026-10-14 15:00:00+09', false, '2026-10-14 11:00:00+09'),
 (34, 1, 'TRIP_EXPENSE', 2, 4, 1, 1, 6, 'PENDING', NULL, NULL, NULL, false, '2026-10-21 17:30:00+09'),
 (35, 1, 'BUSINESS_TRIP', 4, 3, 1, 1, 7, 'PENDING', NULL, NULL, NULL, false, '2026-10-15 11:00:00+09'),
 (36, 1, 'BUSINESS_TRIP', 5, 3, 1, 1, 3, 'REJECTED', NULL, '화상 참여로 대체해 주세요', '2026-09-24 15:00:00+09', false, '2026-09-24 11:00:00+09'),
 (37, 1, 'TRIP_EXPENSE', 3, 4, 1, 1, 6, 'APPROVED', NULL, NULL, '2026-10-08 21:30:00+09', false, '2026-10-08 17:30:00+09');
-- 평가 기간 — 2026 상반기(마감)
INSERT INTO eval_cycle (id, company_id, name, start_date, end_date, status, started_at, closed_at, created_at, updated_at) VALUES
 (2, 1, '2026 상반기 평가', '2026-07-01', '2026-07-15', 'CLOSED', '2026-07-01 09:00:00+09', '2026-07-16 18:00:00+09', '2026-06-24 10:00:00+09', '2026-07-16 18:00:00+09');
-- 평가 대상 규칙
INSERT INTO eval_cycle_target (id, company_id, eval_cycle_id, priority, cond_is_org_lead, cond_job_title_id, cond_job_grade_id, cond_employment_type_id, eval_template_id) VALUES
 (4, 1, 2, 1, true, NULL, NULL, NULL, 2),
 (5, 1, 2, 2, NULL, NULL, NULL, NULL, 1);
-- 평가(확정 9 · 확정 대기 1 · 미제출 1)
INSERT INTO evaluation (id, company_id, eval_cycle_id, target_employee_id, evaluator_id, eval_template_id, status, overall_comment, submitted_at, confirmed_by, confirmed_at, reopen_reason, created_at, updated_at) VALUES
 (1, 1, 2, 3, 1, 2, 'CONFIRMED', '일정 관리가 꼼꼼하다. 기술 문서화를 더 해 주면 좋겠다.', '2026-07-03 16:00:00+09', 4, '2026-07-14 11:00:00+09', NULL, '2026-07-01 09:00:00+09', '2026-07-14 11:00:00+09'),
 (2, 1, 2, 6, 1, 2, 'CONFIRMED', '목표를 초과 달성했다. 다음 분기에는 후배 육성에도 힘써 주기 바란다.', '2026-07-04 16:00:00+09', 4, '2026-07-14 11:00:00+09', NULL, '2026-07-01 09:00:00+09', '2026-07-14 11:00:00+09'),
 (3, 1, 2, 4, 3, 2, 'CONFIRMED', '새 업무에 빠르게 적응했다. 우선순위 판단을 조금 더 연습하자.', '2026-07-05 16:00:00+09', 4, '2026-07-14 11:00:00+09', NULL, '2026-07-01 09:00:00+09', '2026-07-14 11:00:00+09'),
 (4, 1, 2, 5, 3, 2, 'CONFIRMED', '맡은 업무를 안정적으로 수행했고 동료와의 협업이 원활했다.', '2026-07-06 16:00:00+09', 4, '2026-07-14 11:00:00+09', NULL, '2026-07-01 09:00:00+09', '2026-07-14 11:00:00+09'),
 (5, 1, 2, 2, 4, 1, 'CONFIRMED', '목표를 초과 달성했다. 다음 분기에는 후배 육성에도 힘써 주기 바란다.', '2026-07-07 16:00:00+09', 4, '2026-07-14 11:00:00+09', NULL, '2026-07-01 09:00:00+09', '2026-07-14 11:00:00+09'),
 (6, 1, 2, 7, 6, 2, 'CONFIRMED', '일정 관리가 꼼꼼하다. 기술 문서화를 더 해 주면 좋겠다.', '2026-07-08 16:00:00+09', 4, '2026-07-14 11:00:00+09', NULL, '2026-07-01 09:00:00+09', '2026-07-14 11:00:00+09'),
 (7, 1, 2, 10, 6, 2, 'CONFIRMED', '목표를 초과 달성했다. 다음 분기에는 후배 육성에도 힘써 주기 바란다.', '2026-07-09 16:00:00+09', 4, '2026-07-14 11:00:00+09', NULL, '2026-07-01 09:00:00+09', '2026-07-14 11:00:00+09'),
 (8, 1, 2, 8, 7, 1, 'CONFIRMED', '새 업무에 빠르게 적응했다. 우선순위 판단을 조금 더 연습하자.', '2026-07-02 16:00:00+09', 4, '2026-07-14 11:00:00+09', NULL, '2026-07-01 09:00:00+09', '2026-07-14 11:00:00+09'),
 (9, 1, 2, 12, 5, 1, 'SUBMITTED', '새 업무에 빠르게 적응했다. 우선순위 판단을 조금 더 연습하자.', '2026-07-03 16:00:00+09', NULL, NULL, NULL, '2026-07-01 09:00:00+09', '2026-07-03 16:00:00+09'),
 (10, 1, 2, 11, 10, 1, 'NOT_STARTED', NULL, NULL, NULL, NULL, NULL, '2026-07-01 09:00:00+09', '2026-07-01 09:00:00+09'),
 (11, 1, 2, 9, 7, 1, 'CONFIRMED', '맡은 업무를 안정적으로 수행했고 동료와의 협업이 원활했다.', '2026-07-05 16:00:00+09', 4, '2026-07-14 11:00:00+09', NULL, '2026-07-01 09:00:00+09', '2026-07-14 11:00:00+09');
-- 평가 답(질문별 점수)
INSERT INTO eval_answer (id, company_id, evaluation_id, eval_question_id, score) VALUES
 (1, 1, 5, 1, 4),
 (2, 1, 5, 2, 4),
 (3, 1, 5, 3, 5),
 (4, 1, 5, 4, 3),
 (5, 1, 1, 5, 4),
 (6, 1, 1, 6, 5),
 (7, 1, 1, 7, 3),
 (8, 1, 1, 8, 4),
 (9, 1, 3, 5, 5),
 (10, 1, 3, 6, 3),
 (11, 1, 3, 7, 4),
 (12, 1, 3, 8, 5),
 (13, 1, 4, 5, 3),
 (14, 1, 4, 6, 4),
 (15, 1, 4, 7, 5),
 (16, 1, 4, 8, 4),
 (17, 1, 2, 5, 4),
 (18, 1, 2, 6, 5),
 (19, 1, 2, 7, 4),
 (20, 1, 2, 8, 3),
 (21, 1, 6, 5, 5),
 (22, 1, 6, 6, 4),
 (23, 1, 6, 7, 3),
 (24, 1, 6, 8, 5),
 (25, 1, 8, 1, 4),
 (26, 1, 8, 2, 3),
 (27, 1, 8, 3, 5),
 (28, 1, 8, 4, 4),
 (29, 1, 11, 1, 3),
 (30, 1, 11, 2, 5),
 (31, 1, 11, 3, 4),
 (32, 1, 11, 4, 4),
 (33, 1, 7, 5, 5),
 (34, 1, 7, 6, 4),
 (35, 1, 7, 7, 4),
 (36, 1, 7, 8, 3),
 (37, 1, 9, 1, 4),
 (38, 1, 9, 2, 3),
 (39, 1, 9, 3, 4),
 (40, 1, 9, 4, 4);
-- 휴가 개별 조정(포상 휴가)
INSERT INTO leave_grant (id, company_id, employee_id, leave_type_id, leave_year, grant_type, days, reason, created_by, created_at) VALUES
 (33, 1, 11, 1, 2026, 'ADJUSTMENT', 1, '3분기 우수사원 포상 휴가', 4, '2026-10-10 11:00:00+09');
-- 급여 변경(수습 종료)
INSERT INTO employee_salary (id, company_id, employee_id, salary_type, annual_salary, monthly_base, effective_from, reason, created_by, created_at) VALUES
 (24, 1, 9, 'MONTHLY', NULL, 3000000, '2026-10-01', '수습 종료 급여 조정', 4, '2026-09-28 14:00:00+09');
-- 감사 로그(정산 · 지급 · 휴가 조정 · 급여 변경 · 근태 정정)
INSERT INTO audit_log (id, company_id, actor_id, action, target_type, target_id, before_value, after_value, created_at) VALUES
 (1, 1, 4, 'EXECUTE', 'PAYROLL_RUN', 1, NULL, '{"totals": {"netPay": 53086372, "grossPay": 71700000, "headcount": 13, "totalDeduction": 18613628, "companyBurdenTotal": 6670978}, "payDate": "2026-09-25", "payMonth": "2026-08"}', '2026-09-04 15:00:00+09'),
 (2, 1, 4, 'UPDATE', 'PAYROLL_RUN', 1, '{"status": "CONFIRMED"}', '{"status": "PAID"}', '2026-09-25 10:00:00+09'),
 (3, 1, 4, 'EXECUTE', 'PAYROLL_RUN', 2, NULL, '{"totals": {"netPay": 51037568, "grossPay": 69451576, "headcount": 12, "totalDeduction": 18414008, "companyBurdenTotal": 6467422}, "payDate": "2026-10-23", "payMonth": "2026-09"}', '2026-10-02 15:00:00+09'),
 (4, 1, 4, 'CREATE', 'LEAVE_GRANT', 33, NULL, '{"id": 33, "days": 1, "reason": "3분기 우수사원 포상 휴가", "createdAt": "2026-10-21T16:22:19.146926+09:00", "grantType": "ADJUSTMENT", "leaveYear": 2026, "employeeId": 11, "createdById": 4, "leaveTypeId": 1, "createdByName": "정수아", "leaveTypeName": "연차"}', '2026-10-10 11:00:00+09'),
 (5, 1, 4, 'CREATE', 'EMPLOYEE_SALARY', 24, NULL, '{"reason": "수습 종료 급여 조정", "employeeId": 9, "salaryType": "MONTHLY", "monthlyBase": 3000000, "annualSalary": "", "effectiveFrom": "2026-10-01"}', '2026-09-28 14:00:00+09'),
 (6, 1, 4, 'UPDATE', 'ATTENDANCE', 252, '{"reason": null, "status": "MISSING_CHECKOUT", "workType": "OFFICE", "checkInAt": "2026-09-17T08:53+09:00", "checkOutAt": null, "businessTripId": null, "leaveRequestId": null}', '{"reason": "퇴근 미기록 — 출입 기록 확인 후 정정", "status": "CHECKED_OUT", "workType": "OFFICE", "checkInAt": "2026-09-17T08:53+09:00", "checkOutAt": "2026-09-17T18:20+09:00", "businessTripId": null, "leaveRequestId": null}', '2026-09-18 09:40:00+09');
-- 기존 행 변경: 윤서연 부산 출장 결과 보고, 한도윤 9/17 퇴근 미기록 정정
UPDATE business_trip SET report_text = '고객사 담당자와 도입 일정(12월 오픈) 확정. 데이터 이관 범위를 합의했다.',
       reported_at = '2026-10-08 17:00+09', updated_at = '2026-10-08 17:00+09' WHERE id = 1;
UPDATE attendance SET status = 'CHECKED_OUT', check_out_at = '2026-09-17 18:20+09', check_in_method = 'ADMIN', check_out_method = 'ADMIN',
       correction_reason = '퇴근 미기록 — 출입 기록 확인 후 정정', corrected_by = 4, corrected_at = '2026-09-18 09:40+09', updated_at = '2026-09-18 09:40+09'
 WHERE employee_id = 5 AND work_date = '2026-09-17';

-- 10/21 ~ 시드를 넣는 날 전날까지의 근무일 — 비워 두면 그 사이가 모두 결근이 되므로 같은 규칙으로 채운다(넣는 날에 따라 줄 수가 달라진다)
SELECT setval(pg_get_serial_sequence('attendance', 'id'), (SELECT max(id) FROM attendance));
INSERT INTO attendance (company_id, employee_id, work_date, status, work_type, check_in_at, check_out_at,
                        check_in_method, check_out_method)
SELECT 1, e.id, d::date, 'CHECKED_OUT',
       CASE WHEN EXTRACT(ISODOW FROM d) = 5 AND e.id IN (8, 9, 11, 12) THEN 'REMOTE' ELSE 'OFFICE' END::work_type,
       (d::date + time '08:30' + make_interval(mins => ((e.id * 31 + EXTRACT(DOY FROM d))::int % 28))) AT TIME ZONE 'Asia/Seoul',
       (d::date + time '18:00' + make_interval(mins => ((e.id * 31 + EXTRACT(DOY FROM d))::int % 40))) AT TIME ZONE 'Asia/Seoul',
       'WEB', 'WEB'
FROM employee e CROSS JOIN generate_series(date '2026-10-21', CURRENT_DATE - 1, interval '1 day') d
WHERE e.company_id = 1 AND e.status <> 'RESIGNED' AND EXTRACT(ISODOW FROM d) <= 5
  AND NOT EXISTS (SELECT 1 FROM holiday h WHERE h.company_id = 1 AND h.holiday_date = d::date)
  AND NOT EXISTS (SELECT 1 FROM attendance a WHERE a.employee_id = e.id AND a.work_date = d::date);

-- ---------------------------------------------------------------------
-- 시퀀스를 넣은 ID 다음 값으로 맞춘다
-- ---------------------------------------------------------------------
DO $$
DECLARE r RECORD;
BEGIN
  FOR r IN
    SELECT c.table_name, pg_get_serial_sequence(c.table_name, 'id') AS seq
    FROM information_schema.columns c
    WHERE c.table_schema = 'public' AND c.column_name = 'id'
      AND pg_get_serial_sequence(c.table_name, 'id') IS NOT NULL
  LOOP
    EXECUTE format('SELECT setval(%L, COALESCE((SELECT MAX(id) FROM %I), 0) + 1, false)', r.seq, r.table_name);
  END LOOP;
END $$;

COMMIT;
