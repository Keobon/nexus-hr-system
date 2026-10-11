// AntD 테마. Figma 글꼴 Inter(한글은 Pretendard)와 기본색 #38bdf8.
// UI_SCALE 은 styles.css 의 --ui-scale 과 같은 값으로 둔다(AntD 는 px 토큰이라 따로 맞춘다).
export const UI_SCALE = 0.875;

export const theme = {
  token: {
    colorPrimary: '#38bdf8',
    borderRadius: 8,
    fontSize: Math.round(14 * UI_SCALE),
    controlHeight: Math.round(32 * UI_SCALE),
    fontFamily:
      "Inter, 'Pretendard Variable', Pretendard, -apple-system, BlinkMacSystemFont, 'Segoe UI', 'Apple SD Gothic Neo', 'Malgun Gothic', sans-serif",
  },
};
