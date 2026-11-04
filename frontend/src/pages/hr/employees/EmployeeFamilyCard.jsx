// 직원 상세 › 가족 정보(F-EMP-09) — 인사팀(EMPLOYEE_MANAGE)이 가족을 등록 · 수정 · 삭제한다. 직원 본인은 내정보에서 조회만
// 부양가족 수 = 공제 대상인 가족 수, 자녀 수 = 공제 대상인 자녀 수 — 서버가 세고 다음 급여 정산의 소득세(과세표준 공제)에 쓴다
import { useState } from 'react';
import { Checkbox, DatePicker, Form, Input, Modal, Select, Switch } from 'antd';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import dayjs from 'dayjs';
import { api } from '../../../api/client';
import { employeeKey } from '../../../api/employees';
import { applyFieldErrors } from '../../../components/common/formErrors';
import { useNotify } from '../../../components/common/useNotify';
import { labelOf, optionsOf } from '../../../constants/labels';
import { ConfirmModal, ModalFoot } from '../parts';

/** GET /employees/{id}/family → { dependentsCount, childrenCount, members[{ id, name, relation, birthDate, isTaxDependent, isDisabled, isCohabiting }] } */
const familyKey = (id) => ['employees', 'family', String(id)];
const getFamily = (id) => api.get(`/employees/${id}/family`);
/** POST { name, relation, birthDate?, isTaxDependent?, isDisabled?, isCohabiting? } · PATCH 은 보낸 필드만 · DELETE */
const createFamily = (id, body) => api.post(`/employees/${id}/family`, body);
const updateFamily = (id, familyId, patch) => api.patch(`/employees/${id}/family/${familyId}`, patch);
const deleteFamily = (id, familyId) => api.delete(`/employees/${id}/family/${familyId}`);

export default function EmployeeFamilyCard({ employee: e, canEdit }) {
  const notify = useNotify();
  const queryClient = useQueryClient();
  const family = useQuery({ queryKey: familyKey(e.id), queryFn: () => getFamily(e.id) });
  const [editing, setEditing] = useState(null); // null · 'new' · 가족 한 명
  const [removing, setRemoving] = useState(null);
  const refresh = () => {
    queryClient.invalidateQueries({ queryKey: familyKey(e.id) });
    queryClient.invalidateQueries({ queryKey: employeeKey(e.id) });
  };
  const toggle = useMutation({
    mutationFn: (m) => updateFamily(e.id, m.id, { isTaxDependent: !m.isTaxDependent }),
    onSuccess: (_, m) => {
      refresh();
      notify.success(`${m.name}을(를) ${m.isTaxDependent ? '공제 대상에서 뺐습니다' : '공제 대상으로 바꿨습니다'}`);
    },
    onError: (err) => notify.error(err),
  });
  if (family.error) return null;
  const data = family.data;
  const members = data?.members ?? [];

  return (
    <div className="hr-card hr-info" style={{ gap: '0.75rem' }}>
      <div className="hr-card-head">
        <p className="hr-card-title">가족 정보</p>
        {canEdit && (
          <button type="button" className="hr-link" onClick={() => setEditing('new')}>
            가족 추가
          </button>
        )}
      </div>
      {data && (
        <p className="hr-note">{`공제 대상 부양가족 ${data.dependentsCount}명 · 자녀 ${data.childrenCount}명 — 다음 급여 정산부터 소득세 과세표준에서 1인당 공제합니다(회사 설정 › 계산 변수).`}</p>
      )}
      <div className="hr-table is-sm">
        <div className="hr-tr is-head">
          <span style={{ width: '8rem' }}>이름</span>
          <span style={{ width: '6rem' }}>관계</span>
          <span style={{ width: '8rem' }}>생년월일</span>
          <span style={{ width: '7rem' }}>공제 대상</span>
          <span style={{ width: '5rem' }}>동거</span>
          {canEdit && <span>관리</span>}
        </div>
        {members.length === 0 && <div className="hr-tr is-empty">등록된 가족이 없습니다</div>}
        {members.map((m) => (
          <div key={m.id} className="hr-tr">
            <span className="hr-strong" style={{ width: '8rem' }}>{m.name}</span>
            <span style={{ width: '6rem' }}>{labelOf('familyRelation', m.relation)}</span>
            <span style={{ width: '8rem' }}>{m.birthDate ?? '-'}</span>
            <span style={{ width: '7rem' }}>
              {canEdit ? (
                <Switch size="small" checked={m.isTaxDependent} loading={toggle.isPending && toggle.variables?.id === m.id} onChange={() => toggle.mutate(m)} />
              ) : m.isTaxDependent ? (
                '공제'
              ) : (
                '-'
              )}
            </span>
            <span style={{ width: '5rem' }}>{m.isCohabiting ? '동거' : '-'}</span>
            {canEdit && (
              <span style={{ display: 'flex', gap: '0.75rem' }}>
                <button type="button" className="hr-link" onClick={() => setEditing(m)}>
                  수정
                </button>
                <button type="button" className="hr-link is-red" onClick={() => setRemoving(m)}>
                  삭제
                </button>
              </span>
            )}
          </div>
        ))}
      </div>
      {editing && <FamilyModal employee={e} member={editing === 'new' ? null : editing} onDone={refresh} onClose={() => setEditing(null)} />}
      <ConfirmModal
        open={!!removing}
        danger
        okText="삭제"
        title="가족을 삭제할까요?"
        desc={removing ? `${removing.name}(${labelOf('familyRelation', removing.relation)})을(를) 삭제합니다. 이미 확정된 급여명세서는 바뀌지 않습니다.` : undefined}
        onClose={() => setRemoving(null)}
        onConfirm={async () => {
          await deleteFamily(e.id, removing.id);
          refresh();
          notify.success(`${removing.name}을(를) 삭제했습니다`);
        }}
      />
    </div>
  );
}

/** 가족 추가 · 수정 — 수정은 바뀐 칸만 PATCH */
function FamilyModal({ employee: e, member, onDone, onClose }) {
  const notify = useNotify();
  const [form] = Form.useForm();
  const save = useMutation({
    mutationFn: (v) => {
      const body = {
        name: v.name.trim(),
        relation: v.relation,
        birthDate: v.birthDate ? v.birthDate.format('YYYY-MM-DD') : null,
        isTaxDependent: !!v.isTaxDependent,
        isDisabled: !!v.isDisabled,
        isCohabiting: !!v.isCohabiting,
      };
      if (!member) return createFamily(e.id, body);
      const before = { name: member.name, relation: member.relation, birthDate: member.birthDate ?? null, isTaxDependent: member.isTaxDependent, isDisabled: member.isDisabled, isCohabiting: member.isCohabiting };
      const patch = Object.fromEntries(Object.entries(body).filter(([k, val]) => before[k] !== val));
      return Object.keys(patch).length ? updateFamily(e.id, member.id, patch) : Promise.resolve();
    },
    onSuccess: (_, v) => {
      onDone();
      notify.success(member ? `${v.name.trim()}의 정보를 저장했습니다` : `${v.name.trim()}을(를) 가족으로 추가했습니다`);
      onClose();
    },
    onError: (err) => {
      if (!applyFieldErrors(form, err)) notify.error(err);
    },
  });

  return (
    <Modal open title={member ? '가족 정보 수정' : '가족 추가'} footer={null} onCancel={onClose} width={480} className="hr-modal" destroyOnHidden centered>
      <p className="hr-modal-sub">{`${e.name} · 공제 대상으로 체크한 가족만 부양가족 수에 들어갑니다.`}</p>
      <Form
        form={form}
        layout="vertical"
        requiredMark={false}
        onFinish={(v) => save.mutate(v)}
        initialValues={
          member
            ? { ...member, birthDate: member.birthDate ? dayjs(member.birthDate) : null }
            : { relation: 'CHILD', isTaxDependent: true, isDisabled: false, isCohabiting: true }
        }
      >
        <div className="hr-form-row">
          <Form.Item name="name" label="이름" rules={[{ required: true, whitespace: true, message: '이름을 입력하세요' }]}>
            <Input maxLength={50} />
          </Form.Item>
          <Form.Item name="relation" label="관계" rules={[{ required: true, message: '관계를 고르세요' }]}>
            <Select options={optionsOf('familyRelation')} />
          </Form.Item>
        </div>
        <Form.Item name="birthDate" label="생년월일">
          <DatePicker className="full-width" format="YYYY-MM-DD" disabledDate={(d) => d.isAfter(dayjs(), 'day')} />
        </Form.Item>
        <Form.Item name="isTaxDependent" valuePropName="checked" extra="소득세 계산 때 1인당 공제합니다. 자녀면 다자녀 추가 공제에도 셉니다.">
          <Checkbox>공제 대상(부양가족)</Checkbox>
        </Form.Item>
        <div className="hr-form-row">
          <Form.Item name="isCohabiting" valuePropName="checked">
            <Checkbox>동거</Checkbox>
          </Form.Item>
          <Form.Item name="isDisabled" valuePropName="checked">
            <Checkbox>장애인</Checkbox>
          </Form.Item>
        </div>
        <ModalFoot onCancel={onClose} busy={save.isPending} />
      </Form>
    </Modal>
  );
}
