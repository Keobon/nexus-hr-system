import { useState } from 'react';
import { Button, Image, Space, Typography, Upload } from 'antd';
import { CloseOutlined, EyeOutlined, PaperClipOutlined, UploadOutlined } from '@ant-design/icons';
import { FILE_PURPOSES, MAX_FILE_BYTES, openFile, uploadFile } from '../../api/files';
import { useFileUrl } from './useFileUrl';
import { useNotify } from './useNotify';

const IMAGE_PURPOSES = ['PROFILE', 'LOGO'];

function formatBytes(bytes) {
  if (bytes === null || bytes === undefined) return '';
  if (bytes < 1024 * 1024) return `${Math.max(1, Math.round(bytes / 1024))}KB`;
  return `${(bytes / 1024 / 1024).toFixed(1)}MB`;
}

/**
 * 파일 올리기(프론트 가이드 2.5) — 고르면 바로 POST /files 하고 fileId 를 폼 값으로 넘긴다.
 * Form.Item 안에 그대로 넣는다: <Form.Item name="receiptFileId"><FileUpload purpose="RECEIPT" /></Form.Item>
 *
 * - purpose: RECEIPT · COMPANY_DOCUMENT · EMPLOYEE_DOCUMENT · PROFILE · LOGO (API 14장)
 * - value / onChange: fileId(숫자) 또는 null — Form.Item 이 넣어 준다
 * - fileName: 이미 연결된 파일을 수정 화면에서 보여 줄 때의 이름(없으면 "첨부 파일")
 * - 형식(jpg · png · pdf, 프로필 · 로고는 jpg · png)과 10MB 는 올리기 전에 막는다
 */
export default function FileUpload({ purpose, value, onChange, fileName, disabled = false, buttonText = '파일 선택' }) {
  const notify = useNotify();
  const [uploading, setUploading] = useState(false);
  const [uploaded, setUploaded] = useState(null); // 방금 올린 파일의 { id, originalName, sizeBytes }
  const isImage = IMAGE_PURPOSES.includes(purpose);
  const allowed = FILE_PURPOSES[purpose];
  const previewUrl = useFileUrl(isImage ? value : null);

  const current = value ? (uploaded?.id === value ? uploaded : { id: value, originalName: fileName ?? '첨부 파일' }) : null;

  const beforeUpload = (file) => {
    if (!allowed.includes(file.type)) {
      // 프로필 · 로고는 pdf 가 안 되므로 5장 문구("jpg, png, pdf만") 대신 따로
      notify.error(isImage ? { message: 'jpg, png만 올릴 수 있습니다' } : { code: 'FILE_TYPE_NOT_ALLOWED' });
      return Upload.LIST_IGNORE;
    }
    if (file.size > MAX_FILE_BYTES) {
      notify.error({ code: 'FILE_TOO_LARGE' });
      return Upload.LIST_IGNORE;
    }
    return true;
  };

  const customRequest = async ({ file, onSuccess, onError }) => {
    setUploading(true);
    try {
      const result = await uploadFile(file, purpose);
      setUploaded(result);
      onChange?.(result.id);
      onSuccess(result);
    } catch (err) {
      notify.error(err);
      onError(err);
    } finally {
      setUploading(false);
    }
  };

  const view = async () => {
    try {
      await openFile(current.id);
    } catch (err) {
      notify.error(err);
    }
  };

  const picker = (
    <Upload
      accept={allowed.join(',')}
      showUploadList={false}
      beforeUpload={beforeUpload}
      customRequest={customRequest}
      disabled={disabled || uploading}
      maxCount={1}
    >
      <Button icon={<UploadOutlined />} loading={uploading} disabled={disabled}>
        {current ? '바꾸기' : buttonText}
      </Button>
    </Upload>
  );

  if (!current) return picker;

  return (
    <Space wrap align="center">
      {isImage && previewUrl ? (
        <Image src={previewUrl} width={64} height={64} className="file-thumb" alt={current.originalName} />
      ) : (
        <Typography.Text className="file-chip">
          <PaperClipOutlined /> {current.originalName}
          {current.sizeBytes ? <Typography.Text type="secondary"> · {formatBytes(current.sizeBytes)}</Typography.Text> : null}
        </Typography.Text>
      )}
      {!isImage && <Button size="small" type="link" icon={<EyeOutlined />} onClick={view}>보기</Button>}
      {!disabled && picker}
      {!disabled && (
        <Button size="small" type="text" icon={<CloseOutlined />} onClick={() => onChange?.(null)} aria-label="첨부 지우기" />
      )}
    </Space>
  );
}
