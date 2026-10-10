import { Tag } from 'antd';
import { LABELS } from '../../constants/labels';

/** 코드값 배지 — <StatusTag group="leaveStatus" code="PENDING" /> */
export default function StatusTag({ group, code }) {
  const item = LABELS[group]?.[code];
  if (!item) return null;
  return <Tag color={item.color}>{item.label}</Tag>;
}
