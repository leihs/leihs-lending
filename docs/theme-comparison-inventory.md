# Theme Comparison: lending vs inventory

## Core Difference: Tailwind v4 vs v3

The structural differences all stem from this:

- **lending**: Tailwind v4.3 — no `tailwind.config.js`, everything lives in CSS
- **inventory**: Tailwind v3.3 — uses `tailwind.config.js` for all theme tokens

---

## File Inventory

| File type | lending | inventory |
|---|---|---|
| Main CSS (theme variables) | `src/leihs/lending/client/main.css` | `src/leihs/inventory/client/main.css` |
| Tailwind config | none (v4 — config lives in CSS) | `tailwind.config.js` |
| Shadcn config | `components.json` | `components.json` |
| Extra CSS | — | `resources/public/inventory/assets/css/additional.css` (legacy) |

---

## Color Format

Both use the same Shadcn "new-york" / slate palette, expressed differently. lending references Tailwind's palette variables (`--color-slate-*`, `--color-red-*`); the only literal values are the OKLCH shadow colors:

| Token | lending (Tailwind palette) | inventory (HSL channels) |
|---|---|---|
| `--background` | `var(--color-white)` | `0 0% 100%` |
| `--foreground` | `var(--color-slate-950)` | `222.2 84% 4.9%` |
| `--primary` | `var(--color-slate-900)` | `222.2 47.4% 11.2%` |
| `--primary-foreground` | `var(--color-slate-50)` | `210 40% 98%` |
| `--secondary` | `var(--color-slate-100)` | `210 40% 96.1%` |
| `--secondary-foreground` | `var(--color-slate-900)` | `222.2 47.4% 11.2%` |
| `--muted` | `var(--color-slate-100)` | `210 40% 96.1%` |
| `--muted-foreground` | `var(--color-slate-500)` | `215.4 16.3% 46.9%` |
| `--accent` | `var(--color-slate-100)` | `210 40% 96.1%` |
| `--accent-foreground` | `var(--color-slate-900)` | `222.2 47.4% 11.2%` |
| `--destructive` | `var(--color-red-600)` | `0 84.2% 60.2%` |
| `--destructive-foreground` | `var(--color-slate-50)` | `210 40% 98%` |
| `--success` | `var(--color-lime-600)` | — |
| `--success-foreground` | `var(--color-white)` | — |
| `--error` | `var(--color-red-600)` | — |
| `--error-foreground` | `var(--color-white)` | — |
| `--info` | `var(--color-blue-600)` | — |
| `--info-foreground` | `var(--color-white)` | — |
| `--border` | `var(--color-slate-200)` | `214.3 31.8% 91.4%` |
| `--input` | `var(--color-slate-200)` | `214.3 31.8% 91.4%` |
| `--ring` | `var(--color-slate-950)` | `222.2 84% 4.9%` |
| `--radius` | `0.5rem` | `0.5rem` |
| `--app-background` | `var(--color-slate-200)` | `214 33% 91%` |
| `--shadow` | `oklch(0 0 0 / 0.1)` | `0 0% 0% / 0.1` |

Mostly equivalent. Slight deviations from inventory, since the Tailwind v4 palette doesn't exactly match the old Shadcn HSL values: `slate-950` (foreground, ring, dark background) is a bit darker and bluer, `red-600` (destructive) is darker, and dark `--app-background` (`slate-700`) is a bit lighter.

### Dark Mode

| Token | lending (Tailwind palette) | inventory (HSL channels) |
|---|---|---|
| `--background` | `var(--color-slate-950)` | `222.2 84% 4.9%` |
| `--foreground` | `var(--color-slate-50)` | `210 40% 98%` |
| `--primary` | `var(--color-slate-50)` | `210 40% 98%` |
| `--primary-foreground` | `var(--color-slate-900)` | `222.2 47.4% 11.2%` |
| `--secondary` | `var(--color-slate-800)` | `217.2 32.6% 17.5%` |
| `--muted` | `var(--color-slate-800)` | `217.2 32.6% 17.5%` |
| `--muted-foreground` | `var(--color-slate-400)` | `215 20.2% 65.1%` |
| `--accent` | `var(--color-slate-800)` | `217.2 32.6% 17.5%` |
| `--destructive` | `var(--color-red-400)` | `0 62.8% 30.6%` |
| `--success` | `var(--color-lime-500)` | — |
| `--success-foreground` | `var(--color-lime-950)` | — |
| `--error` | `var(--color-red-400)` | — |
| `--error-foreground` | `var(--color-red-950)` | — |
| `--info` | `var(--color-blue-400)` | — |
| `--info-foreground` | `var(--color-blue-950)` | — |
| `--border` | `var(--color-slate-800)` | `217.2 32.6% 17.5%` |
| `--input` | `var(--color-slate-800)` | `217.2 32.6% 17.5%` |
| `--ring` | `var(--color-slate-300)` | `212.7 26.8% 83.9%` |
| `--app-background` | `var(--color-slate-700)` | `215 20.2% 25.1%` |
| `--shadow` | `oklch(0 0 0 / 0.3)` | `0 0% 0% / 0.3` |

---

## Structural Differences

| Aspect | lending | inventory |
|---|---|---|
| Dark mode | `@custom-variant dark (&:is(.dark *))` in CSS | `darkMode: ["class"]` in JS config |
| Token wiring | `@theme inline { --color-primary: var(--primary) }` | `primary: "hsl(var(--primary))"` in JS config |
| Container | `@utility container { ... }` in CSS | `theme.container` in JS config |
| Animations | `@import "tw-animate-css"` | `require("tailwindcss-animate")` plugin + explicit keyframes |
| Extra radius tokens | `--radius-xl` present | Missing |
| `outline-ring/50` reset | Yes (`@apply border-border outline-ring/50`) | No (only `@apply border-border`) |
| `--color-shadow` mapped | Yes (via `@theme inline`) | No |
| Status tokens (`--success`, `--error`, `--info`) | Yes | No |

---

## inventory-Specific Issues

- `components.json` has stale `"css": "global.css"` — doesn't match the actual file path
- Has a legacy `additional.css` with hardcoded hex colors and old-style table/button rules predating the Shadcn setup
