import logo from '../../assets/figma/logo.png';

/**
 * NEXUS labs 로고(Figma "logo" 400 × 76).
 * 원본 PNG 는 500 × 500 정사각형이고 가운데 띠만 보이도록 Figma 와 같은 비율로 잘라 쓴다.
 */
export default function BrandLogo({ className = '' }) {
  return (
    <div className={`brand-logo ${className}`}>
      <img src={logo} alt="NEXUS labs" />
    </div>
  );
}
