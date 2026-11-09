/** Simple VU-style level meter: a row of segments that light up with the
 * current input level, green -> yellow -> red like a classic audio meter. */
export function AudioMeter({ level }: { level: number }) {
  const segments = 24;
  const lit = Math.round(level * segments);
  return (
    <div className="audio-meter">
      {Array.from({ length: segments }).map((_, i) => {
        const on = i < lit;
        const zone = i / segments;
        const color = zone > 0.85 ? "#ff5252" : zone > 0.6 ? "#ffd740" : "#4caf50";
        return (
          <div
            key={i}
            className="audio-meter-seg"
            style={{ background: on ? color : "var(--surface-alt)" }}
          />
        );
      })}
    </div>
  );
}
