import { type ClassValue, clsx } from "clsx";
import { extendTailwindMerge } from "tailwind-merge";

const twMerge = extendTailwindMerge({
  extend: {
    classGroups: {
      "font-size": [{ text: ["h1", "h2", "h3", "lead", "body", "klein"] }],
      "text-color": [
        {
          text: [
            "blau",
            "blau-link",
            "blau-hell",
            "gruen",
            "grau",
            "schwarz",
            "weiss",
            "success",
            "success-soft",
            "warning",
            "warning-soft",
            "danger",
            "danger-soft",
            "info",
            "info-soft",
          ],
        },
      ],
    },
  },
});

/**
 * Merge class names: clsx handles conditionals, tailwind-merge resolves
 * conflicting Tailwind utilities (last wins).
 */
export function cn(...inputs: ClassValue[]): string {
  return twMerge(clsx(inputs));
}
