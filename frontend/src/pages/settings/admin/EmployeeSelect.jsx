// 직원 검색 선택 — 승인선 단계(특정 직원) · 승인자 재지정. 재직 중인 직원만 이름 · 사원번호로 찾는다
import { useState } from 'react';
import { Select } from 'antd';
import { useQuery } from '@tanstack/react-query';
import { employeesKey, getEmployees } from '../../../api/employees';

/** value: employeeId · initialLabel: 이미 지정된 직원 이름(목록에 없을 때 보여 줄 것) · excludeId: 고를 수 없는 직원(신청자 본인) */
export default function EmployeeSelect({ value, onChange, initialLabel, excludeId, placeholder = '이름 · 사원번호 검색', ...rest }) {
  const [keyword, setKeyword] = useState('');
  const params = { keyword: keyword || undefined, status: ['ACTIVE'], page: 0, size: 20 };
  const list = useQuery({ queryKey: employeesKey(params), queryFn: () => getEmployees(params), staleTime: 30_000 });
  const options = (list.data?.content ?? [])
    .filter((e) => e.id !== excludeId)
    .map((e) => ({ value: e.id, label: `${e.name} (${e.employeeNo}) · ${e.orgUnitName ?? ''}` }));
  if (value && !options.some((o) => o.value === value)) options.unshift({ value, label: initialLabel ?? `직원 #${value}` });
  return (
    <Select showSearch filterOption={false} onSearch={setKeyword} value={value} onChange={onChange} options={options}
      loading={list.isFetching} placeholder={placeholder} notFoundContent={list.isFetching ? '찾는 중…' : '검색 결과가 없습니다'} {...rest} />
  );
}
