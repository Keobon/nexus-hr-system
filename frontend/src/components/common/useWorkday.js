// 오늘이 회사 근무일인지 — GET /me/attendance/today 의 isWorkday(근무 요일 · 휴일 설정 반영). 휴일에는 "미출근"을 결근처럼 보이지 않게
// (2026-11-15 역할별 점검). 불러오는 중이거나 못 불러오면 근무일로 본다(지금까지와 같은 표시)
import { useQuery } from '@tanstack/react-query';
import { getToday, todayKey } from '../../api/attendance';

export function useWorkday() {
  const q = useQuery({ queryKey: todayKey, queryFn: getToday, staleTime: 60_000 });
  return q.data?.isWorkday ?? true;
}
