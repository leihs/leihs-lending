# Theme Comparison: lending vs inventory

## Core Difference: Tailwind v4 vs v3

The structural differences all stem from this:

- **lending**: Tailwind v4.1 — no `tailwind.config.js`, everything lives in CSS
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

Both use the same Shadcn "new-york" / slate palette, expressed differently:

| Token | lending (OKLCH) | inventory (HSL channels) |
|---|---|---|
| `--background` | `oklch(1 0 0)` | `0 0% 100%` |
| `--foreground` | `oklch(0.137 0.036 258.526)` | `222.2 84% 4.9%` |
| `--primary` | `oklch(0.208 0.04 265.727)` | `222.2 47.4% 11.2%` |
| `--primary-foreground` | `oklch(0.984 0.003 247.858)` | `210 40% 98%` |
| `--secondary` | `oklch(0.968 0.007 247.895)` | `210 40% 96.1%` |
| `--secondary-foreground` | `oklch(0.208 0.04 265.727)` | `222.2 47.4% 11.2%` |
| `--muted` | `oklch(0.968 0.007 247.895)` | `210 40% 96.1%` |
| `--muted-foreground` | `oklch(0.555 0.041 257.44)` | `215.4 16.3% 46.9%` |
| `--accent` | `oklch(0.968 0.007 247.895)` | `210 40% 96.1%` |
| `--accent-foreground` | `oklch(0.208 0.04 265.727)` | `222.2 47.4% 11.2%` |
| `--destructive` | `oklch(0.637 0.208 25.326)` | `0 84.2% 60.2%` |
| `--destructive-foreground` | `oklch(0.984 0.003 247.858)` | `210 40% 98%` |
| `--border` | `oklch(0.929 0.013 255.532)` | `214.3 31.8% 91.4%` |
| `--input` | `oklch(0.929 0.013 255.532)` | `214.3 31.8% 91.4%` |
| `--ring` | `oklch(0.137 0.036 258.526)` | `222.2 84% 4.9%` |
| `--radius` | `0.5rem` | `0.5rem` |
| `--app-background` | `oklch(0.926 0.014 255.03)` | `214 33% 91%` |
| `--shadow` | `oklch(0 0 0 / 0.1)` | `0 0% 0% / 0.1` |

Visually equivalent — OKLCH is the Tailwind v4 convention and is perceptually uniform.

### Dark Mode

| Token | lending (OKLCH) | inventory (HSL channels) |
|---|---|---|
| `--background` | `oklch(0.137 0.036 258.526)` | `222.2 84% 4.9%` |
| `--foreground` | `oklch(0.984 0.003 247.858)` | `210 40% 98%` |
| `--primary` | `oklch(0.984 0.003 247.858)` | `210 40% 98%` |
| `--primary-foreground` | `oklch(0.208 0.04 265.727)` | `222.2 47.4% 11.2%` |
| `--secondary` | `oklch(0.28 0.037 259.974)` | `217.2 32.6% 17.5%` |
| `--muted` | `oklch(0.28 0.037 259.974)` | `217.2 32.6% 17.5%` |
| `--muted-foreground` | `oklch(0.711 0.035 256.788)` | `215 20.2% 65.1%` |
| `--accent` | `oklch(0.28 0.037 259.974)` | `217.2 32.6% 17.5%` |
| `--destructive` | `oklch(0.396 0.133 25.721)` | `0 62.8% 30.6%` |
| `--border` | `oklch(0.28 0.037 259.974)` | `217.2 32.6% 17.5%` |
| `--input` | `oklch(0.28 0.037 259.974)` | `217.2 32.6% 17.5%` |
| `--ring` | `oklch(0.869 0.02 252.847)` | `212.7 26.8% 83.9%` |
| `--app-background` | `oklch(0.36 0.03 256.828)` | `215 20.2% 25.1%` |
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

---

## inventory-Specific Issues

- `components.json` has stale `"css": "global.css"` — doesn't match the actual file path
- Has a legacy `additional.css` with hardcoded hex colors and old-style table/button rules predating the Shadcn setup
