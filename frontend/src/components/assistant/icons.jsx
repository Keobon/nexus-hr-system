// AI 비서 아이콘 — 확정 디자인의 선 아이콘(인라인 SVG, currentColor). 장식이라 모두 aria-hidden, 이름은 버튼의 aria-label 이 맡는다
const Svg = ({ size = 16, children, fill = 'none', ...rest }) => (
  <svg width={size} height={size} viewBox="0 0 24 24" fill={fill} stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" focusable="false" {...rest}>
    {children}
  </svg>
);

export const CloseIcon = (p) => (
  <Svg size={14} strokeWidth="2.4" {...p}>
    <path d="M6 6l12 12M18 6L6 18" />
  </Svg>
);

export const PlusIcon = (p) => (
  <Svg size={18} {...p}>
    <path d="M12 5v14M5 12h14" />
  </Svg>
);

/** 종이비행기(보내기) */
export const SendIcon = (p) => (
  <Svg size={18} {...p}>
    <path d="M22 2L11 13" />
    <path d="M22 2l-7 20-4-9-9-4 20-7z" />
  </Svg>
);

/** 처음으로(되돌리기) */
export const HomeIcon = (p) => (
  <Svg size={14} {...p}>
    <path d="M3 12a9 9 0 1 0 3-6.7" />
    <path d="M3 4v5h5" />
  </Svg>
);

export const StarIcon = (p) => (
  <Svg size={14} fill="currentColor" strokeWidth="1.5" {...p}>
    <path d="M12 2.8l2.8 5.7 6.3.9-4.6 4.4 1.1 6.2L12 17l-5.6 3 1.1-6.2L2.9 9.4l6.3-.9L12 2.8z" />
  </Svg>
);

export const ArrowIcon = (p) => (
  <Svg size={12} {...p}>
    <path d="M5 12h14M13 6l6 6-6 6" />
  </Svg>
);

export const CalendarIcon = (p) => (
  <Svg size={17} {...p}>
    <rect x="3" y="5" width="18" height="16" rx="2.5" />
    <path d="M3 10h18M8 3v4M16 3v4" />
  </Svg>
);

export const ClockIcon = (p) => (
  <Svg size={17} {...p}>
    <circle cx="12" cy="12" r="9" />
    <path d="M12 7v5l3 2" />
  </Svg>
);

export const PlaneIcon = (p) => (
  <Svg size={17} {...p}>
    <path d="M17.8 19.2L16 11l3.5-3.5C21 6 21.5 4 21 3c-1-.5-3 0-4.5 1.5L13 8 4.8 6.2c-.5-.1-.9.1-1.1.5l-.3.5c-.2.5-.1 1 .3 1.3L9 12l-2 3H4l-1 1 3 2 2 3 1-1v-3l3-2 3.5 5.3c.3.4.8.5 1.3.3l.5-.2c.4-.3.6-.7.5-1.2z" />
  </Svg>
);
