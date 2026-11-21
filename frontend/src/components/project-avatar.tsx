import * as botttsNeutral from "@dicebear/bottts-neutral";
import { createAvatar } from "@dicebear/core";
import { useMemo } from "react";

/** A small robot face derived from the seed, generated locally (DiceBear), so no network call. */
export function ProjectAvatar({ seed, className = "size-5" }: { seed: string; className?: string }) {
  const src = useMemo(() => createAvatar(botttsNeutral, { seed, radius: 22 }).toDataUri(), [seed]);
  return (
    // eslint-disable-next-line @next/next/no-img-element -- an inline data URI needs no optimisation
    <img src={src} alt="" className={`shrink-0 rounded-[22%] ${className}`} />
  );
}
