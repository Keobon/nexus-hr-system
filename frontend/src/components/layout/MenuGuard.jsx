import { Navigate } from 'react-router-dom';
import { useMe } from '../../auth/useMe';
import { isSetupLocked } from '../../auth/permissions';
import ForbiddenPage from '../../pages/ForbiddenPage';
import { findMenu } from './menu';

/**
 * 메뉴와 같은 규칙으로 주소 접근을 막는다(메뉴 구조 M-1·M-4·M-6).
 * 숨기는 것은 편의일 뿐이고 최종으로 막는 것은 서버다.
 */
export default function MenuGuard({ menuKey, children }) {
  const { me, can, canAny } = useMe();
  const item = findMenu(menuKey);
  if (!item || !item.visible({ me, can, canAny })) return <ForbiddenPage />;
  if (item.lockedDuringSetup && isSetupLocked(me)) return <Navigate to="/setup" replace />;
  return children;
}
