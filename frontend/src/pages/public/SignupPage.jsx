import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';
import { meKey, registerCompany } from '../../api/auth';
import { setToken } from '../../auth/token';
import { nextPath } from '../../auth/nextPath';
import BrandLogo from '../../components/brand/BrandLogo';
import { errorMessage } from '../../constants/errors';
import iconRadioCircle from '../../assets/figma/icon-radio-circle.svg';
import './auth.css';

// 길이 제한은 서버 CompanyRegisterRequest 와 같다
// 1단계 회사 정보(first_page) · 2단계 관리자(second_page) — POST /companies 의 company · admin
const COMPANY_FIELDS = [
  { name: 'name', label: '회사 이름', autoComplete: 'organization', maxLength: 100 },
  { name: 'ceoName', label: '대표자명', maxLength: 50 },
  { name: 'phone', label: '전화번호', type: 'tel', autoComplete: 'tel', maxLength: 20 },
  { name: 'businessRegNo', label: '사업자 번호', placeholder: '000-00-00000', inputMode: 'numeric' },
  { name: 'address', label: '주소명', autoComplete: 'street-address', maxLength: 255 },
  { name: 'email', label: '이메일', type: 'email', autoComplete: 'email', maxLength: 100 },
];

// 아이디는 로그인 이메일이다(F-AUTH-01)
const ADMIN_FIELDS = [
  { name: 'name', label: '관리자 이름', autoComplete: 'name', maxLength: 50 },
  { name: 'email', label: 'ID(아이디)', type: 'email', placeholder: '로그인에 쓸 이메일', autoComplete: 'username', maxLength: 100 },
  { name: 'password', label: 'PASSWORD(비밀번호)', type: 'password', autoComplete: 'new-password' },
  { name: 'passwordConfirm', label: 'PASSWORD 확인', type: 'password', autoComplete: 'new-password' },
];

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

/** 사업자등록번호 자동 하이픈 000-00-00000 (프론트 가이드 3.1) */
function formatBusinessRegNo(value) {
  const d = value.replace(/\D/g, '').slice(0, 10);
  if (d.length <= 3) return d;
  if (d.length <= 5) return `${d.slice(0, 3)}-${d.slice(3)}`;
  return `${d.slice(0, 3)}-${d.slice(3, 5)}-${d.slice(5)}`;
}

function validateCompany(v) {
  const e = {};
  if (!v.name.trim()) e.name = '회사 이름을 입력하세요';
  if (!v.ceoName.trim()) e.ceoName = '대표자명을 입력하세요';
  if (!v.phone.trim()) e.phone = '전화번호를 입력하세요';
  if (!/^\d{3}-\d{2}-\d{5}$/.test(v.businessRegNo)) e.businessRegNo = '사업자 번호 10자리를 입력하세요';
  if (!v.address.trim()) e.address = '주소를 입력하세요';
  if (!EMAIL_RE.test(v.email.trim())) e.email = '이메일 형식이 아닙니다';
  return e;
}

// 8자 이상, 영문 · 숫자 포함(F-AUTH-03)
function validateAdmin(v) {
  const e = {};
  if (!v.name.trim()) e.name = '관리자 이름을 입력하세요';
  if (!EMAIL_RE.test(v.email.trim())) e.email = '이메일 형식으로 입력하세요';
  if (!(v.password.length >= 8 && /[A-Za-z]/.test(v.password) && /\d/.test(v.password))) {
    e.password = '8자 이상, 영문과 숫자를 포함하세요';
  }
  if (v.passwordConfirm !== v.password) e.passwordConfirm = '비밀번호가 다릅니다';
  return e;
}

// 서버 오류 → 어느 단계 · 어느 칸인지. VALIDATION_ERROR 의 fields 키는 "company.name" · "admin.email"
function mapServerError(err) {
  if (err.code === 'BUSINESS_REG_NO_DUPLICATE') {
    return { step: 'company', fields: { businessRegNo: errorMessage(err) } };
  }
  if (err.code === 'EMAIL_DUPLICATE') {
    return { step: 'admin', fields: { email: errorMessage(err) } };
  }
  if (err.code === 'VALIDATION_ERROR' && err.fields) {
    const company = {};
    const admin = {};
    Object.entries(err.fields).forEach(([key, msg]) => {
      const [group, field] = key.split('.');
      if (group === 'company') company[field] = msg;
      else if (group === 'admin') admin[field] = msg;
    });
    if (Object.keys(company).length) return { step: 'company', fields: company };
    if (Object.keys(admin).length) return { step: 'admin', fields: admin };
  }
  return null;
}

function FieldGrid({ fields, values, errors, onChange }) {
  return (
    <div className="signup-grid">
      {fields.map((f) => (
        <FieldRow key={f.name} field={f} value={values[f.name]} error={errors[f.name]} onChange={onChange} />
      ))}
    </div>
  );
}

function FieldRow({ field, value, error, onChange }) {
  const id = `signup-${field.name}`;
  return (
    <>
      <label className="signup-label" htmlFor={id}>
        {field.label}
      </label>
      <div className="signup-field">
        <input
          id={id}
          className="signup-input"
          type={field.type ?? 'text'}
          inputMode={field.inputMode}
          placeholder={field.placeholder}
          maxLength={field.maxLength}
          autoComplete={field.autoComplete ?? 'off'}
          value={value}
          aria-invalid={!!error}
          onChange={(e) => onChange(field.name, e.target.value)}
        />
        {error && <p className="signup-field-error">{error}</p>}
      </div>
    </>
  );
}

/**
 * 회사 등록(F-COMP-01) — Figma first_page(회사 등록) → second_page(관리자 등록).
 * 로그인 화면에 링크가 없고 /signup 주소로만 들어온다(일반 직원은 회사를 등록하지 않는다).
 * 등록하면 그 관리자로 바로 로그인되고 초기 설정 마법사로 이어진다(API 2장 next = SETUP_WIZARD).
 * 마법사를 끝내지 못했으면 관리자 계정으로 로그인할 때 다시 마법사로 간다.
 */
export default function SignupPage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [step, setStep] = useState('company');
  const [company, setCompany] = useState({ name: '', ceoName: '', phone: '', businessRegNo: '', address: '', email: '' });
  const [admin, setAdmin] = useState({ name: '', email: '', password: '', passwordConfirm: '' });
  const [errors, setErrors] = useState({});
  const [alert, setAlert] = useState(null);
  const [loading, setLoading] = useState(false);

  const changeCompany = (name, value) => {
    setCompany((v) => ({ ...v, [name]: name === 'businessRegNo' ? formatBusinessRegNo(value) : value }));
    setErrors((e) => ({ ...e, [name]: undefined }));
  };
  const changeAdmin = (name, value) => {
    setAdmin((v) => ({ ...v, [name]: value }));
    setErrors((e) => ({ ...e, [name]: undefined }));
  };

  const goAdmin = (e) => {
    e.preventDefault();
    const found = validateCompany(company);
    setErrors(found);
    if (Object.keys(found).length === 0) {
      setAlert(null);
      setStep('admin');
    }
  };

  const submit = async (e) => {
    e.preventDefault();
    const found = validateAdmin(admin);
    setErrors(found);
    if (Object.keys(found).length) return;

    setLoading(true);
    setAlert(null);
    try {
      const trim = (o) => Object.fromEntries(Object.entries(o).map(([k, v]) => [k, v.trim()]));
      const { token, next } = await registerCompany({
        company: trim(company),
        admin: { name: admin.name.trim(), email: admin.email.trim(), password: admin.password },
      });
      setToken(token);
      queryClient.removeQueries({ queryKey: meKey });
      navigate(nextPath(next), { replace: true });
    } catch (err) {
      const mapped = mapServerError(err);
      if (mapped) {
        setStep(mapped.step);
        setErrors(mapped.fields);
      } else {
        setAlert(errorMessage(err));
      }
    } finally {
      setLoading(false);
    }
  };

  const isCompany = step === 'company';

  return (
    <div className="signup-page auth-font">
      <BrandLogo />
      <div className="signup-head">● 기본정보</div>
      <form className={`signup-card ${isCompany ? 'is-company' : 'is-admin'}`} onSubmit={isCompany ? goAdmin : submit} noValidate>
        <h1 className="signup-step-title">
          <img src={iconRadioCircle} alt="" />
          <span>{isCompany ? '회사 등록' : '관리자 등록'}</span>
        </h1>
        {isCompany ? (
          <FieldGrid fields={COMPANY_FIELDS} values={company} errors={errors} onChange={changeCompany} />
        ) : (
          <FieldGrid fields={ADMIN_FIELDS} values={admin} errors={errors} onChange={changeAdmin} />
        )}
        {alert && <p className="signup-alert">{alert}</p>}
        <button type="submit" className="signup-submit" disabled={loading}>
          {loading ? '등록 중…' : '확인'}
        </button>
      </form>
      <p className="auth-copyright">© 2026 Team Oishi</p>
    </div>
  );
}
