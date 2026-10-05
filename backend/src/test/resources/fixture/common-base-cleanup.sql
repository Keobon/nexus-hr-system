-- common-base.sql 로 넣은 데이터를 지운다(트랜잭션 없이 도는 테스트용)
DELETE FROM account         WHERE company_id IN (9001, 9002);
DELETE FROM role_permission WHERE company_id IN (9001, 9002);
DELETE FROM role            WHERE company_id IN (9001, 9002);
UPDATE org_unit SET lead_employee_id = NULL WHERE company_id IN (9001, 9002);
DELETE FROM employee        WHERE company_id IN (9001, 9002);
DELETE FROM org_unit        WHERE company_id IN (9001, 9002);
DELETE FROM employment_type WHERE company_id IN (9001, 9002);
DELETE FROM company         WHERE id IN (9001, 9002);
