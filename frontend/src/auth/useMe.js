import { useMemo } from 'react';
import { useQuery } from '@tanstack/react-query';
import { getMe, meKey } from '../api/auth';
import { getToken } from './token';
import { makeCan } from './permissions';

/**
 * GET /me 캐시(메뉴 구조 M-3). 화면 이동 때 AppLayout 이 다시 받는다.
 * → { me, can, canAny, isLoading, error, refetch }
 */
export function useMe() {
  const query = useQuery({
    queryKey: meKey,
    queryFn: getMe,
    enabled: !!getToken(),
    staleTime: 30_000,
  });
  const { can, canAny } = useMemo(() => makeCan(query.data), [query.data]);
  return { me: query.data, can, canAny, isLoading: query.isLoading, error: query.error, refetch: query.refetch };
}
