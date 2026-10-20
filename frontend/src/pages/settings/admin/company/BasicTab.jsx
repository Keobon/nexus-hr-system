// 기본 정보 탭 — GET · PATCH /company(바꾼 필드만). 중요 정보 4개가 바뀌면 저장 전에 "중요 정보 변경" 창(Figma 326:6882)
import { useEffect, useState } from 'react';
import { DatePicker, Form, Input, InputNumber, Modal, Select, Spin, Upload } from 'antd';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { meKey } from '../../../../api/auth';
import { TRACKED_FIELDS, companyDocsKey, companyHistoryKey, companyKey, getCompany, getCompanyDocs, updateCompany } from '../../../../api/company';
import { FILE_PURPOSES, MAX_FILE_BYTES, uploadFile } from '../../../../api/files';
import { applyFieldErrors } from '../../../../components/common/formErrors';
import { useFileUrl } from '../../../../components/common/useFileUrl';
import { useNotify } from '../../../../components/common/useNotify';
import { errorMessage } from '../../../../constants/errors';
import { labelOf } from '../../../../constants/labels';
import { ModalFoot } from '../../../hr/parts';
import { toDay, today, ymd } from '../parts';

const FIELD_LABEL = { name: '회사명', ceoName: '대표자명', businessRegNo: '사업자등록번호', address: '사업장 주소' };
const TEXT_FIELDS = ['name', 'businessRegNo', 'ceoName', 'address', 'phone', 'email', 'nameEn', 'corpRegNo', 'businessType',
  'businessItem', 'fax', 'website', 'employeeNoPrefix'];
const MONTHS = Array.from({ length: 12 }, (_, i) => ({ value: i + 1, label: `${i + 1}월` }));

/** 숫자만 받아 000-00-00000 */
export function formatBizNo(v) {
  const d = String(v ?? '').replace(/\D/g, '').slice(0, 10);
  if (d.length <= 3) return d;
  if (d.length <= 5) return `${d.slice(0, 3)}-${d.slice(3)}`;
  return `${d.slice(0, 3)}-${d.slice(3, 5)}-${d.slice(5)}`;
}

/** 숫자만 받아 000000-0000000 */
function formatCorpNo(v) {
  const d = String(v ?? '').replace(/\D/g, '').slice(0, 13);
  return d.length <= 6 ? d : `${d.slice(0, 6)}-${d.slice(6)}`;
}

const toForm = (c) => ({ ...c, foundedDate: toDay(c.foundedDate) });

/** 폼 값 → 서버 값(빈 문자열은 null) */
function fromForm(v) {
  const out = { ...v, foundedDate: ymd(v.foundedDate) };
  TEXT_FIELDS.forEach((k) => {
    const s = typeof out[k] === 'string' ? out[k].trim() : out[k];
    out[k] = s === '' || s === undefined ? null : s;
  });
  return out;
}

const KEYS = [...TEXT_FIELDS, 'foundedDate', 'logoFileId', 'payDay', 'fiscalYearStartMonth'];

export default function BasicTab() {
  const notify = useNotify();
  const queryClient = useQueryClient();
  const [form] = Form.useForm();
  const company = useQuery({ queryKey: companyKey, queryFn: getCompany });
  const [pending, setPending] = useState(null); // { patch, changes: [{ key, before, after }] }

  useEffect(() => {
    if (company.data) form.setFieldsValue(toForm(company.data));
  }, [company.data, form]);

  const save = useMutation({
    mutationFn: updateCompany,
    onSuccess: (data) => {
      queryClient.setQueryData(companyKey, data);
      queryClient.invalidateQueries({ queryKey: companyHistoryKey });
      queryClient.invalidateQueries({ queryKey: meKey }); // 사이드바 회사명 · 로고
      setPending(null);
      notify.success('회사 정보를 저장했습니다');
    },
  });

  const onFinish = (values) => {
    const next = fromForm(values);
    const orig = company.data;
    const patch = {};
    KEYS.forEach((k) => {
      if ((next[k] ?? null) !== (orig[k] ?? null)) patch[k] = next[k] ?? null;
    });
    if (Object.keys(patch).length === 0) {
      notify.success('바뀐 내용이 없습니다');
      return;
    }
    const changes = Object.keys(TRACKED_FIELDS).filter((k) => k in patch).map((k) => ({ key: k, before: orig[k], after: patch[k] }));
    if (changes.length > 0) {
      setPending({ patch, changes });
      return;
    }
    save.mutate(patch, { onError: (err) => { if (!applyFieldErrors(form, err)) notify.error(err); } });
  };

  if (company.isLoading) return <Spin />;
  if (company.error) return <p className="hr-notice is-error">{errorMessage(company.error)}</p>;

  return (
    <>
      <Form form={form} layout="vertical" onFinish={onFinish} requiredMark className="hr" style={{ gap: '1rem' }}>
        <section className="ad-section">
          <div className="ad-section-head"><h2>기본</h2></div>
          <div className="ad-grid">
            <Form.Item name="name" label="회사명" rules={[{ required: true, whitespace: true, message: '회사명을 입력하세요' }]}>
              <Input maxLength={100} />
            </Form.Item>
            <Form.Item name="businessRegNo" label="사업자번호" normalize={formatBizNo}
              rules={[{ required: true, message: '사업자등록번호를 입력하세요' }, { pattern: /^\d{3}-\d{2}-\d{5}$/, message: '숫자 10자리를 입력하세요' }]}>
              <Input inputMode="numeric" placeholder="000-00-00000" />
            </Form.Item>
            <Form.Item name="ceoName" label="대표자명" rules={[{ required: true, whitespace: true, message: '대표자명을 입력하세요' }]}>
              <Input maxLength={50} />
            </Form.Item>
            <Form.Item name="address" label="사업장 주소" rules={[{ required: true, whitespace: true, message: '사업장 주소를 입력하세요' }]}>
              <Input maxLength={255} />
            </Form.Item>
            <Form.Item name="phone" label="대표 전화번호" rules={[{ required: true, whitespace: true, message: '대표 전화번호를 입력하세요' }]}>
              <Input maxLength={20} placeholder="02-1234-5678" />
            </Form.Item>
            <Form.Item name="email" label="대표 이메일"
              rules={[{ required: true, message: '대표 이메일을 입력하세요' }, { type: 'email', message: '이메일 형식이 아닙니다' }]}>
              <Input maxLength={100} />
            </Form.Item>
          </div>
        </section>

        <section className="ad-section">
          <div className="ad-with-logo">
            <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
              <div className="ad-section-head"><h2>부가 정보</h2></div>
              <Form.Item name="logoFileId" noStyle><LogoBox /></Form.Item>
            </div>
            <div className="ad-grid" style={{ '--cols': 4 }}>
              <Form.Item name="nameEn" label="영문 상호"><Input maxLength={100} /></Form.Item>
              <Form.Item name="corpRegNo" label="법인등록번호" normalize={formatCorpNo}
                rules={[{ pattern: /^(\d{6}-\d{7})?$/, message: '숫자 13자리를 입력하세요' }]}>
                <Input inputMode="numeric" placeholder="000000-0000000" />
              </Form.Item>
              <Form.Item name="businessType" label="업태"><Input maxLength={100} /></Form.Item>
              <Form.Item name="businessItem" label="종목"><Input maxLength={100} /></Form.Item>
              <Form.Item name="foundedDate" label="설립일"><DatePicker placeholder="YYYY-MM-DD" /></Form.Item>
              <Form.Item name="fax" label="팩스"><Input maxLength={20} /></Form.Item>
              <Form.Item name="website" label="홈페이지" className="ad-span-2"><Input maxLength={255} placeholder="https://" /></Form.Item>
            </div>
          </div>
        </section>

        <section className="ad-section">
          <div className="ad-section-head"><h2>운영 설정</h2></div>
          <div className="ad-grid">
            <Form.Item name="payDay" label="기본 급여 지급일" rules={[{ required: true, message: '지급일을 입력하세요' }]}>
              <InputNumber min={1} max={31} precision={0} suffix="일" />
            </Form.Item>
            <Form.Item name="employeeNoPrefix" label="사원번호 접두어"
              rules={[{ pattern: /^[A-Za-z0-9-]{0,10}$/, message: '영문 · 숫자 · 하이픈 10자까지' }]}>
              <Input maxLength={10} placeholder="예: NX-" />
            </Form.Item>
            <Form.Item name="fiscalYearStartMonth" label="회계연도 시작월" rules={[{ required: true, message: '시작월을 고르세요' }]}
              extra={<span className="hr-hint">휴가가 부여된 뒤에는 바꿀 수 없습니다</span>}>
              <Select options={MONTHS} />
            </Form.Item>
          </div>
        </section>

        <div className="ad-save-row">
          <button type="button" className="ad-btn is-gray" onClick={() => form.setFieldsValue(toForm(company.data))} disabled={save.isPending}>
            되돌리기
          </button>
          <button type="submit" className="ad-btn" disabled={save.isPending}>{save.isPending ? '저장 중…' : '변경'}</button>
        </div>
      </Form>

      {pending && (
        <ChangeModal pending={pending} busy={save.isPending} onClose={() => setPending(null)}
          onSubmit={(change, modalForm) => save.mutate({ ...pending.patch, change }, {
            onError: (err) => {
              // change.* 는 창 안, 나머지 필드는 본문 폼에
              if (err?.code === 'VALIDATION_ERROR' && err.fields) {
                const inModal = Object.keys(err.fields).filter((k) => k.startsWith('change'));
                if (inModal.length) applyFieldErrors(modalForm, { ...err, fields: Object.fromEntries(inModal.map((k) => [k.replace('change.', ''), err.fields[k]])) });
                const rest = Object.fromEntries(Object.entries(err.fields).filter(([k]) => !k.startsWith('change')));
                if (Object.keys(rest).length) { setPending(null); applyFieldErrors(form, { ...err, fields: rest }); }
                return;
              }
              notify.error(err);
            },
          })} />
      )}
    </>
  );
}

/** 로고 칸(점선 98×78) — 누르면 jpg · png 올리기(purpose LOGO) */
function LogoBox({ value, onChange }) {
  const notify = useNotify();
  const [busy, setBusy] = useState(false);
  const url = useFileUrl(value);
  const before = (file) => {
    if (!FILE_PURPOSES.LOGO.includes(file.type)) { notify.error({ message: 'jpg, png만 올릴 수 있습니다' }); return Upload.LIST_IGNORE; }
    if (file.size > MAX_FILE_BYTES) { notify.error({ code: 'FILE_TOO_LARGE' }); return Upload.LIST_IGNORE; }
    return true;
  };
  const upload = async ({ file, onSuccess, onError }) => {
    setBusy(true);
    try {
      const res = await uploadFile(file, 'LOGO');
      onChange?.(res.id);
      onSuccess(res);
    } catch (err) {
      notify.error(err);
      onError(err);
    } finally {
      setBusy(false);
    }
  };
  return (
    <div className="ad-logo">
      <span>로고</span>
      <Upload accept={FILE_PURPOSES.LOGO.join(',')} showUploadList={false} beforeUpload={before} customRequest={upload} disabled={busy}>
        <div className={`ad-logo-box${busy ? ' is-busy' : ''}`} role="button" aria-label="로고 올리기">
          {value && url ? <img src={url} alt="회사 로고" /> : '*jpg, png'}
        </div>
      </Upload>
      {value ? <button type="button" className="hr-link is-red" onClick={() => onChange?.(null)}>로고 지우기</button> : null}
    </div>
  );
}

/** 중요 정보 변경 — 효력일 · 사유 · 근거 서류(회사 서류에서 선택) → PATCH 의 change */
function ChangeModal({ pending, busy, onClose, onSubmit }) {
  const [form] = Form.useForm();
  const docs = useQuery({ queryKey: companyDocsKey, queryFn: getCompanyDocs });
  const docOptions = (docs.data ?? []).filter((g) => g.current).map((g) => ({
    value: g.current.id,
    label: `${g.docType === 'OTHER' ? g.docName : labelOf('companyDocumentType', g.docType)}${g.current.issuedDate ? ` (${g.current.issuedDate} 발급)` : ''}`,
  }));
  const names = pending.changes.map((c) => FIELD_LABEL[c.key]).join(' · ');
  return (
    <Modal open title="중요 정보 변경" footer={null} onCancel={busy ? undefined : onClose} width={640} className="hr-modal" destroyOnHidden centered>
      <p className="hr-modal-sub">{`${names}을(를) 변경합니다. 효력일과 사유가 변경 이력에 기록됩니다.`}</p>
      <div className="ad-change">
        {pending.changes.map((c) => (
          <div key={c.key}>
            <span className="k">{FIELD_LABEL[c.key]}</span>
            <span>{c.before || '-'} <span className="ad-arrow">→</span> {c.after || '-'}</span>
          </div>
        ))}
      </div>
      <Form form={form} layout="vertical" requiredMark initialValues={{ effectiveDate: toDay(today()) }}
        onFinish={(v) => onSubmit({ effectiveDate: ymd(v.effectiveDate), reason: v.reason.trim(), documentId: v.documentId ?? null }, form)}>
        <Form.Item name="effectiveDate" label="효력일" rules={[{ required: true, message: '효력일을 고르세요' }]}>
          <DatePicker className="full-width" />
        </Form.Item>
        <Form.Item name="reason" label="사유" rules={[{ required: true, whitespace: true, message: '변경 사유를 입력하세요' }]}>
          <Input maxLength={255} placeholder="예: 상호 변경 등기 완료" />
        </Form.Item>
        <Form.Item name="documentId" label="근거 서류(회사 서류에서 선택)"
          extra={docOptions.length === 0 && !docs.isLoading ? <span className="hr-hint">등록된 회사 서류가 없습니다. 회사 서류 탭에서 먼저 올릴 수 있습니다.</span> : null}>
          <Select allowClear placeholder="선택 안 함" options={docOptions} loading={docs.isLoading} />
        </Form.Item>
        <ModalFoot onCancel={onClose} okText="변경" busy={busy} />
      </Form>
    </Modal>
  );
}
