// 회사 정보 · 초기 설정(API 3장) — COMPANY_MANAGE
import { api } from './client';

/**
 * GET /company — COMPANY_MANAGE 면 전체 필드:
 * { id, name, nameEn, businessRegNo, corpRegNo, ceoName, address, phone, fax, email, website, businessType, businessItem,
 *   foundedDate, logoFileId, payDay, employeeNoPrefix, fiscalYearStartMonth, setupCompleted }
 */
export const companyKey = ['company'];
export const getCompany = () => api.get('/company');

/**
 * PATCH /company — 바꾼 필드만. 회사명 · 대표자명 · 사업자등록번호 · 주소를 바꾸면
 * change { effectiveDate, reason, documentId? } 를 같이 보낸다(변경 이력에 남는다)
 */
export const updateCompany = (patch) => api.patch('/company', patch);

/** 이력이 남는 중요 정보 4개(CompanyField) */
export const TRACKED_FIELDS = { name: 'NAME', ceoName: 'CEO_NAME', businessRegNo: 'BUSINESS_REG_NO', address: 'ADDRESS' };

/** GET /company/change-history → [{ id, field, oldValue, newValue, effectiveDate, reason, document{id,docType,docName}?, changedBy{id,name}, createdAt }] 최신순 */
export const companyHistoryKey = ['company', 'change-history'];
export const getCompanyHistory = () => api.get('/company/change-history');

/**
 * GET /company/documents → [{ docType, docName, current: Version|null, versions: Version[] }] — versions 는 이전 버전(최신순)
 * Version = { id, versionNo, fileId, fileName, issuedDate, expiresAt, expired, memo, uploadedByName, createdAt }
 */
export const companyDocsKey = ['company', 'documents'];
export const getCompanyDocs = () => api.get('/company/documents');
/** POST /company/documents { docType, docName?(기타만), fileId, issuedDate?, expiresAt?, memo? } — 새 버전이 현재본이 된다. 삭제는 없다 */
export const createCompanyDoc = (body) => api.post('/company/documents', body);

/**
 * GET /company/setup → { setupCompleted, steps: [{ step, counts, done }] }
 * step: ORG_UNITS · JOB_GRADES_TITLES · EMPLOYMENT_TYPES · WORK_SCHEDULE_HOLIDAYS · LEAVE_TYPES · PAY_ITEMS · EMPLOYEES
 */
export const setupKey = ['company', 'setup'];
export const getSetup = () => api.get('/company/setup');
/** POST /company/setup/complete — 체크가 없어도 끝낼 수 있다 */
export const completeSetup = () => api.post('/company/setup/complete');
