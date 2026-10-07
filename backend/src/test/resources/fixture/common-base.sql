-- 공통 기반 테스트 데이터. 테스트마다 트랜잭션으로 넣고 롤백한다. ID는 데모 데이터와 겹치지 않게 9xxxx.
-- 회사 A(9001)
--   회사(91000, 최상위) ┬ 본부(91001, 조직장 E1) ─ 팀(91002, 조직장 E2) ─ 파트(91003, 조직장 없음)
--                       └ 별도 조직(91004)
--   E1 91101 본부 · E2 91102 팀 · E3 91103 파트 · E4 91104 별도 조직
--   E5 91105 비밀번호 변경 필요 · E6 91106 계정 비활성 · E7 91107 퇴직
-- 회사 B(9002): 조직 92001, 직원 92101
INSERT INTO company (id, name, business_reg_no, ceo_name, address, phone, email) VALUES
  (9001, '테스트A', '900-00-00001', '대표A', '서울', '02-000-0001', 'a@test.example'),
  (9002, '테스트B', '900-00-00002', '대표B', '서울', '02-000-0002', 'b@test.example');

INSERT INTO org_unit (id, company_id, parent_id, name) VALUES
  (91000, 9001, NULL,  '회사A'),
  (91001, 9001, 91000, '본부'),
  (91002, 9001, 91001, '팀'),
  (91003, 9001, 91002, '파트'),
  (91004, 9001, 91000, '별도 조직'),
  (92001, 9002, NULL,  'B 조직');

INSERT INTO employment_type (id, company_id, name, sort_order) VALUES
  (91301, 9001, '정규직', 1),
  (92301, 9002, '정규직', 1);

INSERT INTO employee (id, company_id, employee_no, name, email, hire_date, org_unit_id, employment_type_id, status) VALUES
  (91101, 9001, 'T-1', '직원1', 'e1@test.example', '2024-01-01', 91001, 91301, 'ACTIVE'),
  (91102, 9001, 'T-2', '직원2', 'e2@test.example', '2024-01-01', 91002, 91301, 'ACTIVE'),
  (91103, 9001, 'T-3', '직원3', 'e3@test.example', '2024-01-01', 91003, 91301, 'ACTIVE'),
  (91104, 9001, 'T-4', '직원4', 'e4@test.example', '2024-01-01', 91004, 91301, 'ACTIVE'),
  (91105, 9001, 'T-5', '직원5', 'e5@test.example', '2024-01-01', 91004, 91301, 'ACTIVE'),
  (91106, 9001, 'T-6', '직원6', 'e6@test.example', '2024-01-01', 91004, 91301, 'ACTIVE'),
  (91107, 9001, 'T-7', '직원7', 'e7@test.example', '2024-01-01', 91003, 91301, 'RESIGNED'),
  (92101, 9002, 'B-1', 'B직원', 'b1@test.example', '2024-01-01', 92001, 92301, 'ACTIVE');

UPDATE org_unit SET lead_employee_id = 91101 WHERE id = 91001;
UPDATE org_unit SET lead_employee_id = 91102 WHERE id = 91002;

INSERT INTO role (id, company_id, name) VALUES
  (91201, 9001, '팀 조회'),
  (91202, 9001, '관리자'),
  (91203, 9001, '일반'),
  (92201, 9002, '관리자');

INSERT INTO role_permission (company_id, role_id, permission_code, scope) VALUES
  (9001, 91201, 'EMPLOYEE_READ', 'TEAM'),
  (9001, 91202, 'EMPLOYEE_READ', 'ALL'),
  (9001, 91202, 'COMPANY_MANAGE', 'ALL'),
  (9002, 92201, 'EMPLOYEE_READ', 'ALL'),
  (9002, 92201, 'COMPANY_MANAGE', 'ALL');

-- password_hash 는 로그인을 하지 않으므로 아무 값
INSERT INTO account (company_id, employee_id, password_hash, role_id, is_active, must_change_password) VALUES
  (9001, 91101, 'x', 91201, TRUE,  FALSE),
  (9001, 91102, 'x', 91201, TRUE,  FALSE),
  (9001, 91103, 'x', 91201, TRUE,  FALSE),
  (9001, 91104, 'x', 91202, TRUE,  FALSE),
  (9001, 91105, 'x', 91202, TRUE,  TRUE),
  (9001, 91106, 'x', 91202, FALSE, FALSE),
  (9001, 91107, 'x', 91203, TRUE,  FALSE),
  (9002, 92101, 'x', 92201, TRUE,  FALSE);
