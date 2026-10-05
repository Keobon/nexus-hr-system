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
 (13, 1, '성과급',   'EARNING', TRUE,  NULL,   'MANUAL','SELECTED', NULL,   FALSE, 20),
 (14, 1, '소급 조정','EARNING', TRUE,  NULL,   'MANUAL','SELECTED', NULL,   FALSE, 21),
 (31, 2, '식대',     'EARNING', FALSE, 200000, 'FIXED', 'ALL',      250000, TRUE,  10),  -- 한도 초과 5만원은 과세
 (32, 2, '생산수당', 'EARNING', TRUE,  NULL,   'FIXED', 'SELECTED', NULL,   TRUE,  11),
 (33, 2, '소급 조정','EARNING', TRUE,  NULL,   'MANUAL','SELECTED', NULL,   FALSE, 21);

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
