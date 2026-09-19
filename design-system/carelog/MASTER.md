# CareLog — Design System (Master)

Source of truth for every screen. Built with the **UI/UX Pro Max** skill and
reconciled by hand; each row records the search it came from.

```bash
python3 .claude/skills/ui-ux-pro-max/scripts/search.py "healthcare care coordination family calm trust" --domain color
python3 .claude/skills/ui-ux-pro-max/scripts/search.py "calm trustworthy humane health readable" --domain typography
python3 .claude/skills/ui-ux-pro-max/scripts/search.py "keyboard focus modal" --domain ux
python3 .claude/skills/ui-ux-pro-max/scripts/search.py "blade layout accessibility form validation" --stack laravel
```

---

## 1. Product

| | |
|---|---|
| **Type** | A shared operational log for an informal care team. |
| **Users** | A paid carer (records most of it), a daughter nearby (records some), a son abroad (records none, reads the summary). |
| **Context** | Seven in the morning after five hours' sleep. Midnight, in another country, worrying. |
| **Stack** | Laravel 12 + Blade, hand-written CSS, no Node, no build step (`--stack laravel`). |

The design fact everything follows from: **this is a kept notebook, not a
hospital chart.** Nothing may look like an alarm, because the app has no
business raising one — it holds no medical knowledge and gives no advice.

## 2. Colour

`--domain color` → the **Healthcare App** palette, "Calm cyan + health green":
primary `#0891B2`, accent `#059669`. Deepened for text contrast and given a
teal header.

| Role | Light | Dark |
|---|---|---|
| `--brand` | `#0e7490` | `#56c4e0` |
| `--header` | `#0a2b34` → `#113d49` | `#061216` → `#0b2029` |
| `--canvas` / `--surface` | `#edf3f5` / `#ffffff` | `#07151a` / `#102128` |
| `--ink` / `--ink-2` / `--ink-3` | `#0d2229` / `#46606a` / `#63808b` | `#e6f2f5` / `#a2bcc5` / `#80a0aa` |
| `--ok` | `#0a7048` on `#e3f5ec` | `#4fd39d` on `#0d2b22` |
| `--warn` | `#8f5a08` on `#fdf1dc` | `#ecb75e` on `#2a2112` |
| `--bad` | `#b8332a` on `#fceceb` | `#ff8f85` on `#2e1715` |

**What the colours mean here is unusual and deliberate.** Green is "recorded".
Amber is "still to do". Red is "two people did the same thing". **None of them
ever means anything about anybody's health** — the app is not entitled to that
opinion, and a product that cries wolf about a blood pressure is one a family
learns to ignore in a fortnight.

Every foreground and background pair was checked against 4.5:1 in both themes.
Dark is a full first-class theme: this is genuinely read at midnight.

## 3. Typography — Medical Clean

`--domain typography` → **Medical Clean** (Figtree + Noto Sans), listed for
"Healthcare, medical clinics, pharma, health apps, accessibility". Simplified
to **Figtree alone** across the interface — it covers 400–800 with open
apertures and unmistakable digits, and one family is one request fewer on a bad
connection. **JetBrains Mono** carries times and readings so a column of blood
pressures lines up.

Scale: 11 · 13 · 14 · 16 · 18 · 22 · 28 · 36 · 48. Body never below 16px, and
`line-height: 1.6` throughout — slightly looser than the other products in this
family, because a lot of what is read here is read by people over sixty.

## 4. Shape, spacing, depth

4px rhythm. Radius 10 / 14 / 18 / 24 — the softest in the family, on purpose.
`--tap: 48px`, enforced by test; the three dose buttons are a full-width
three-column grid so they cannot be mis-tapped.

Depth is a tight contact shadow plus a soft ambient one, with a hairline of
light along the top edge. No borders drawn around everything.

## 5. Icons — Phosphor, regular weight

71 glyphs copied from `@phosphor-icons/core` (MIT) into `resources/icons.php`
as raw path data: no package, no font, no build step. Regenerate with
`scripts/build-icons.py`. Emoji are banned and the ban is tested.

## 6. The components that carry the product

**The dose row** is the product. A time chip, the medicine, and three equal
buttons until somebody answers — after which the buttons are replaced by an
avatar, a name and a minute. The replacement is the point: a tick would have
told you nothing.

**The conflict panel** is the only thing in the app allowed to interrupt. Amber
rather than red, because two people both trying to look after somebody is not
an emergency. It names who answered, quotes the time, and offers exactly two
ways out, one of which requires a sentence that stays on the record for good.

**The avatar** is initials on a tinted pill, coloured by role: family in brand
teal, carer in green, the remote payer in amber. Somebody's name appears beside
nearly every fact in this app, and it needs to be cheap to render.

## 7. Motion

Colour and shadow at 120–190ms, a one-pixel lift on press. Nothing animates on
arrival. `prefers-reduced-motion: reduce` removes all of it, and that is tested.

## 8. Rules this interface keeps

- Skip link; focus ring never removed; `aria-current` on the active tab.
- Every field has a visible label; the error sits under its own field, tied
  with `aria-describedby` and `aria-invalid`; a failed form gets a focusable
  summary at the top linking to each bad field.
- Colour is the third signal — every status tag carries an icon and a word.
- A count is announced as a sentence, never as a bare number.
- `noindex` on every page: this is somebody's mother's medication list.
- Mobile first, `min-height: 100dvh`, no horizontal scroll at 375px, safe-area
  padding under the tab bar.
- Five bottom destinations, each with an icon *and* a word.

## 9. Anti-patterns

- Emoji as icons *(tested against)*
- A raw hex in a screen *(tested against)*
- A status tag without an icon *(tested against)*
- Red meaning anything about somebody's health
- Clinical, blaming or promotional wording in the weekly summary
  *(tested against, in every shipped language)*
- Small text — nothing below 11px, and 11px only for uppercase labels
