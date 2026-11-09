const CODE_COLORS: Record<string, string> = {
  R: "#FF5252",
  G: "#4CAF50",
  B: "#448AFF",
  Y: "#FFD740",
  C: "#26C6DA",
  P: "#E040FB",
};

const WORD_COLORS: Record<string, string> = {
  Red: CODE_COLORS.R,
  Green: CODE_COLORS.G,
  Blue: CODE_COLORS.B,
  Yellow: CODE_COLORS.Y,
  Cyan: CODE_COLORS.C,
  Purple: CODE_COLORS.P,
};

/** Colors the R/G/B/Y/C/P letters (and full color words) inside effect names
 * like "R-G-B Jump" or "Red Marquee", ported from EffectNameColorizer.kt. */
export function EffectName({ name }: { name: string }) {
  const parts = name.split(" ");
  return (
    <>
      {parts.map((word, wi) => {
        const spacer = wi > 0 ? " " : "";
        if (word.includes("-")) {
          const segments = word.split("-");
          return (
            <span key={wi}>
              {spacer}
              {segments.map((seg, si) => {
                const sep = si > 0 ? "-" : "";
                const color = CODE_COLORS[seg];
                return (
                  <span key={si}>
                    {sep}
                    <span style={color ? { color, fontWeight: 700 } : undefined}>{seg}</span>
                  </span>
                );
              })}
            </span>
          );
        }
        const color = WORD_COLORS[word] ?? CODE_COLORS[word];
        return (
          <span key={wi} style={color ? { color, fontWeight: 700 } : undefined}>
            {spacer}
            {word}
          </span>
        );
      })}
    </>
  );
}
