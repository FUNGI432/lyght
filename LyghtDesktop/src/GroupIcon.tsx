/** Same icon glyphs as the Android app's ic_group_*.xml vector drawables, for visual parity. */
export function GroupIcon({ label }: { label: string }) {
  const common = { width: 18, height: 18, viewBox: "0 0 24 24" };
  switch (label) {
    case "Basic":
      return (
        <svg {...common}>
          <path fill="currentColor" d="M12,2 L14,9 L21,9 L15.5,13.2 L17.5,20 L12,15.8 L6.5,20 L8.5,13.2 L3,9 L10,9 Z" />
        </svg>
      );
    case "Curtain":
      return (
        <svg {...common}>
          <path fill="currentColor" d="M3,3 L21,3 L21,5 L3,5 Z" />
          <path fill="currentColor" d="M5,6 C5,6 8,10 6,13 C4.5,15.3 6,19 6,19 L8,19 C8,19 6.3,15.3 7.7,13.2 C9.6,10.2 7,6 7,6 Z" />
          <path fill="currentColor" d="M15,6 C15,6 18,10 16,13 C14.5,15.3 16,19 16,19 L18,19 C18,19 16.3,15.3 17.7,13.2 C19.6,10.2 17,6 17,6 Z" />
        </svg>
      );
    case "Trans":
      return (
        <svg {...common}>
          <circle cx="9" cy="12" r="6" fill="none" stroke="currentColor" strokeWidth="1.6" />
          <circle cx="15" cy="12" r="6" fill="none" stroke="currentColor" strokeWidth="1.6" />
        </svg>
      );
    case "Water":
      return (
        <svg {...common}>
          <path
            fill="none"
            stroke="currentColor"
            strokeWidth="1.8"
            strokeLinecap="round"
            d="M2,9 C4,6.5 6,6.5 8,9 C10,11.5 12,11.5 14,9 C16,6.5 18,6.5 20,9 C21,10.2 22,10.6 22,10.6"
          />
          <path
            fill="none"
            stroke="currentColor"
            strokeWidth="1.8"
            strokeLinecap="round"
            d="M2,15 C4,12.5 6,12.5 8,15 C10,17.5 12,17.5 14,15 C16,12.5 18,12.5 20,15 C21,16.2 22,16.6 22,16.6"
          />
        </svg>
      );
    case "Flow":
      return (
        <svg {...common}>
          <path
            fill="none"
            stroke="currentColor"
            strokeWidth="1.8"
            strokeLinecap="round"
            d="M2,12 C6,12 6,7 10,7 C14,7 14,17 18,17 C19,17 19.5,16.5 20,16"
          />
          <path fill="currentColor" d="M17.5,13.5 L22,16 L17.5,18.5 Z" />
        </svg>
      );
    case "Tail":
      return (
        <svg {...common}>
          <path fill="currentColor" opacity="0.35" d="M3,16 L15,9 L16.5,11.7 L7,17.3 Z" />
          <circle cx="17" cy="14" r="4" fill="currentColor" />
        </svg>
      );
    case "Run":
      return (
        <svg {...common}>
          <path fill="currentColor" d="M3,6 L11,12 L3,18 Z" />
          <path fill="currentColor" d="M12,6 L20,12 L12,18 Z" />
        </svg>
      );
    case "RunBack":
      return (
        <svg {...common}>
          <path fill="currentColor" d="M21,6 L13,12 L21,18 Z" />
          <path fill="currentColor" d="M12,6 L4,12 L12,18 Z" />
        </svg>
      );
    case "Scenes":
      return (
        <svg {...common}>
          <rect x="2.8" y="4.8" width="18.4" height="14.4" rx="1" fill="none" stroke="currentColor" strokeWidth="1.6" />
          <circle cx="8" cy="11" r="1.8" fill="currentColor" />
          <path fill="currentColor" d="M3.5,18 L9,12.5 L12.5,16 L15.5,13 L20.5,18 Z" />
        </svg>
      );
    default:
      return null;
  }
}
