// 화면 크기 단위 — Figma 의 px 값을 rem 으로 바꿔 쓴다(1rem = Figma 16px).
// 전체 크기는 styles.css 의 --ui-scale 하나로 조절한다(html 글자 크기 = 16px × --ui-scale).
export const rem = (px) => `${px / 16}rem`;
