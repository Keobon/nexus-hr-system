// 회사 서류 탭 — GET · POST /company/documents. 종류별 현재본 + 이전 버전, 만료 표시, 삭제 없음(Figma Admin_cpdoc)
// 기본 종류 3개(사업자등록증 · 법인등기부등본 · 통장 사본)는 아직 없어도 카드를 보여 주고 [등록]으로 바로 올린다
import { useState } from 'react';
import { DatePicker, Form, Input, Modal, Select, Spin } from 'antd';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { companyDocsKey, createCompanyDoc, getCompanyDocs } from '../../../../api/company';
import { downloadFile } from '../../../../api/files';
import FileUpload from '../../../../components/common/FileUpload';
import { applyFieldErrors } from '../../../../components/common/formErrors';
import { useNotify } from '../../../../components/common/useNotify';
import { errorMessage } from '../../../../constants/errors';
import { labelOf, optionsOf } from '../../../../constants/labels';
import { ModalFoot } from '../../../hr/parts';
import { ymd } from '../parts';

const BASE_TYPES = ['BUSINESS_REGISTRATION', 'CORP_REGISTRATION', 'BANK_ACCOUNT_COPY'];

const titleOf = (g) => (g.docType === 'OTHER' ? `기타 · ${g.docName}` : labelOf('companyDocumentType', g.docType));
const lineOf = (v) => `${v.fileName} · 발급 ${v.issuedDate ?? '-'} · 만료 ${v.expiresAt ?? '-'}`;

export default function DocsTab() {
  const notify = useNotify();
  const list = useQuery({ queryKey: companyDocsKey, queryFn: getCompanyDocs });
  const [adding, setAdding] = useState(null); // { docType?, docName? }
  const [open, setOpen] = useState({});

  const groups = list.data ?? [];
  const missing = BASE_TYPES.filter((t) => !groups.some((g) => g.docType === t)).map((t) => ({ docType: t, docName: null, current: null, versions: [] }));
  const cards = [...groups, ...missing].sort((a, b) => order(a) - order(b));

  const download = async (fileId) => {
    try { await downloadFile(fileId); } catch (err) { notify.error(err); }
  };

  return (
    <>
      <div className="ad-bar">
        <span className="hr-note">종류마다 최신 서류가 현재본입니다. 새로 올리면 이전 것은 이전 버전으로 남고, 삭제할 수 없습니다.</span>
        <button type="button" className="ad-btn is-lg" onClick={() => setAdding({})}>+ 서류 추가</button>
      </div>
      {list.isLoading ? (
        <Spin />
      ) : list.error ? (
        <p className="hr-notice is-error">{errorMessage(list.error)}</p>
      ) : (
        <div className="ad-docs">
          {cards.map((g) => {
            const key = `${g.docType}:${g.docName ?? ''}`;
            const v = g.current;
            return (
              <div key={key} className="ad-doc">
                <div className="ad-doc-main">
                  <div className="ad-doc-title">
                    <strong className={v?.expired ? 'is-expired' : ''}>{titleOf(g)}{v?.expired ? ' (만료)' : ''}</strong>
                    <span>{v ? lineOf(v) : '등록된 서류가 없습니다'}</span>
                  </div>
                  {v && (
                    <button type="button" className="ad-doc-toggle" disabled={g.versions.length === 0}
                      onClick={() => setOpen((o) => ({ ...o, [key]: !o[key] }))}>
                      {`이전 버전 ${g.versions.length}건 ${open[key] ? '▲' : '▼'}`}
                    </button>
                  )}
                  <button type="button" className="ad-doc-btn" onClick={() => setAdding({ docType: g.docType, docName: g.docName, hasCurrent: !!v })}>
                    {v ? '새 버전' : '등록'}
                  </button>
                  {v && <button type="button" className="ad-doc-btn" onClick={() => download(v.fileId)}>다운로드</button>}
                </div>
                {open[key] && g.versions.length > 0 && (
                  <div className="ad-doc-old">
                    {g.versions.map((o) => (
                      <div key={o.id}>
                        <span>{`v${o.versionNo} · ${lineOf(o)}${o.memo ? ` · ${o.memo}` : ''} · ${o.uploadedByName}`}</span>
                        <button type="button" className="ad-doc-btn" onClick={() => download(o.fileId)}>다운로드</button>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            );
          })}
        </div>
      )}
      {adding && <DocModal initial={adding} onClose={() => setAdding(null)} />}
    </>
  );
}

function order(g) {
  const i = BASE_TYPES.indexOf(g.docType);
  return i === -1 ? 10 : i;
}

function DocModal({ initial, onClose }) {
  const notify = useNotify();
  const queryClient = useQueryClient();
  const [form] = Form.useForm();
  const docType = Form.useWatch('docType', form) ?? initial.docType;
  const fixed = !!initial.docType;
  const save = useMutation({
    mutationFn: createCompanyDoc,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: companyDocsKey });
      notify.success('서류를 올렸습니다');
      onClose();
    },
    onError: (err) => { if (!applyFieldErrors(form, err)) notify.error(err); },
  });
  const onFinish = (v) => save.mutate({
    docType: v.docType,
    docName: v.docType === 'OTHER' ? v.docName.trim() : null,
    fileId: v.fileId,
    issuedDate: ymd(v.issuedDate),
    expiresAt: ymd(v.expiresAt),
    memo: v.memo?.trim() || null,
  });
  return (
    <Modal open title={fixed ? `${initial.docType === 'OTHER' ? initial.docName : labelOf('companyDocumentType', initial.docType)} ${initial.hasCurrent ? '새 버전' : '등록'}` : '회사 서류 추가'}
      footer={null} onCancel={onClose} width={520} className="hr-modal" destroyOnHidden centered>
      <p className="hr-modal-sub">pdf · jpg · png, 10MB까지. 올리면 이 종류의 현재본이 됩니다.</p>
      <Form form={form} layout="vertical" requiredMark onFinish={onFinish} initialValues={{ docType: initial.docType, docName: initial.docName }}>
        <Form.Item name="docType" label="종류" rules={[{ required: true, message: '서류 종류를 고르세요' }]}>
          <Select options={optionsOf('companyDocumentType')} disabled={fixed} placeholder="선택하세요" />
        </Form.Item>
        {docType === 'OTHER' && (
          <Form.Item name="docName" label="서류 이름" rules={[{ required: true, whitespace: true, message: '서류 이름을 입력하세요' }]}>
            <Input maxLength={100} disabled={fixed} placeholder="예: 중소기업확인서" />
          </Form.Item>
        )}
        <Form.Item name="fileId" label="파일" rules={[{ required: true, message: '파일을 올리세요' }]}>
          <FileUpload purpose="COMPANY_DOCUMENT" />
        </Form.Item>
        <div className="hr-form-row" style={{ gap: '1rem' }}>
          <Form.Item name="issuedDate" label="발급일"><DatePicker className="full-width" /></Form.Item>
          <Form.Item name="expiresAt" label="만료일" dependencies={['issuedDate']}
            rules={[({ getFieldValue }) => ({
              validator: (_, val) => (!val || !getFieldValue('issuedDate') || !val.isBefore(getFieldValue('issuedDate'), 'day')
                ? Promise.resolve() : Promise.reject(new Error('만료일은 발급일 이후여야 합니다'))),
            })]}>
            <DatePicker className="full-width" />
          </Form.Item>
        </div>
        <Form.Item name="memo" label="메모"><Input maxLength={255} /></Form.Item>
        <ModalFoot onCancel={onClose} okText="올리기" busy={save.isPending} />
      </Form>
    </Modal>
  );
}
