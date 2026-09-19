# CareLog

**A shared daily log for everyone looking after one elderly parent — so that "did she get the eight o'clock one?" has an answer two days later.**

When a parent gets old, the care spreads over three to six people: a sister nearby, a husband, a paid carer, a neighbour, a son in Germany. It is coordinated in a WhatsApp group, where facts scroll out of sight in four minutes. Nobody records whether the morning pill was given, Tuesday's blood pressure, what the doctor actually said, when the nappies run out, or who is on duty Friday. Double doses happen. The child abroad hears "she's fine" and then gets a call from a hospital. When the main carer gets ill herself, the substitute knows nothing.

---

## The one rule

**Before a second dose is written, the software asks a person.**

A dose is recorded against a specific slot on a specific date. If that slot already has an answer on it, nothing is saved — you are told *who* answered and *at what minute*, and only a human can decide whether what you are holding is genuinely a second dose. If it is, it is written with a reason and kept beside the first, permanently and visibly.

That question is the whole product. A WhatsApp group cannot ask it; a checkbox cannot ask it. This asks it at the only moment it is worth asking: while your thumb is over the button and a box of pills is in your other hand.

Two more rules follow from who actually has to fill this in:

**Nobody can change anybody else's entry.** What a paid carer writes is her account of what she did, with her name on it. That is what makes it worth writing, and what makes it worth anything if she is ever accused of something. She can take back her own slip for half an hour; after that it stands.

**It never blames.** A blank slot is reported as "not recorded", never as "missed". The software cannot tell a missed dose from a tired daughter forgetting to tick a box, and guessing wrong turns the one person doing the work into a suspect.

---

## What it refuses to do

**It gives no medical advice, and it never will.**

It ships no reference ranges. It computes no dose, no schedule change, and no interaction warning. It can say a number is outside the range *somebody in the family wrote down*, quoting the range and naming who wrote it and when. That is a fact about your own notes. Anything more is a phone practising medicine.

`app/Models/ReadingRange.php` is where that lives, and `tests/Unit/DigestToneTest.php` fails the build if any shipped wording, in any language, starts sounding clinical.

Health data stays inside the circle. There is no table in this schema that could join one family's data to another's, and nothing is sent anywhere.

---

## What it does

| | |
|---|---|
| **Today** | Who is on duty, what is still to do, the last few readings, what to buy, and everything written down so far. |
| **Medicines** | The day's doses grouped by morning, midday, evening, night. Three buttons each: given, skipped, refused. Afterwards, a name and a minute. |
| **Log** | Meals, fluids, mood, sleep, appointments, incidents, a photo of a prescription. One table with a kind, because families always need to record something nobody anticipated. |
| **Readings** | Blood pressure, sugar, temperature, pulse, oxygen, weight — each compared only against the range a named person wrote down. |
| **Rota** | Two weeks, a day, a person, a window. Anybody can change it; a rota is a plan made together. |
| **Supplies** | Fine / running low / out. Three levels, no quantity — nobody counts nappies, and a field asking for a number stays empty. |
| **The week** | A plain summary for whoever is far away and paying: doses recorded, readings, appointments, who did the work, what to buy. Kept verbatim once written. |
| **Languages** | Albanian and English, per person. The sister in Tirana and the son in Munich read the same circle in their own language, and the summary follows whoever receives it. |

---

## Running it

Needs PHP 8.2+ and Composer. No Node, no build step, no `npm install`: the CSS is hand-written.

```bash
git clone https://github.com/Andis382/carelog.git
cd carelog
composer install
cp .env.example .env
php artisan key:generate
php artisan migrate --seed
php artisan storage:link
php artisan serve
```

Open http://localhost:8000 and log in as `demo@carelog.test` / `password` — that is Besa, the daughter who lives nearby. The seed also has `ana@carelog.test` (the paid carer, who records most of it) and `luan@carelog.test` (the son in Munich, who reads the summary and records nothing).

Three weeks of history is seeded with the situations this is for: two blank slots, one genuine double dose with a reason attached, a fall in the bathroom, and a blood pressure outside the range Besa wrote down after the appointment.

**Database.** SQLite by default, which needs no setup but does need `pdo_sqlite`. For Postgres set `DB_CONNECTION=pgsql` and the usual credentials.

**Tests.**

```bash
php artisan test
```

35 tests. If your PHP has no `pdo_sqlite`: `DB_CONNECTION=pgsql DB_DATABASE=carelog_test php artisan test`.

---

## Layout

```
app/
  Services/
    MedicationBoard.php    the day's doses, and the guard that asks before a second one
    WeeklyDigest.php       the week, written for the person who is not there
  Models/
    Circle · CircleMember · Medication · MedicationSlot · DoseEvent
    Reading · ReadingRange · LogEntry · Shift · Supply · Digest
  Http/Controllers/Concerns/InCircle.php   a circle you are not in does not exist (404, not 403)
resources/views/           Blade, mobile first
resources/icons.php        the Phosphor glyphs used, as path data
design-system/carelog/     tokens, type scale, the rules and why
public/css/app.css         hand written, no build step
lang/{sq,en}/              the interface, and every word of the weekly summary
tests/                     35: the dose guard, privacy, digest tone, interface discipline
```

---

## What it looks like, and why

A deep teal header over a calm, slightly cool surface, generous spacing, large gentle controls. It is opened at seven in the morning by somebody who has been up since five, and at midnight by somebody worrying from another country. It has to read as a kept notebook rather than a hospital chart — which is also why red, in this app, means "somebody already answered this" and never "emergency".

The whole system is written down in [`design-system/carelog/MASTER.md`](design-system/carelog/MASTER.md). Three rules are enforced by `tests/Unit/InterfaceDisciplineTest.php`: no emoji as icons, no status that means something only by colour, and a 48px tap-target floor with visible focus and honoured reduced motion.

---

## Honest notes

- **The hard part is not the software.** The person who has to log is the tired sister or the carer, and WhatsApp wins on friction every time. The bet is that a paid carer fills it in because it is her proof of work, and once she does, everyone else reads it. If that bet is wrong the product does not work, and no amount of features fixes it.
- **A long list of dead competitors says this market is hard to charge for.** CareZone, Amazon Care Hub and Alexa Together all shut down. Jointly, ianacare and Caring Village are alive and mostly free. The narrow claim here: none of them is an operational shift log between a hired carer and a family, in the family's language, with the remote child as the paying member.
- **Nothing is offline yet.** A phone in a flat with bad signal will lose a tap, and that is the one thing that must never happen quietly. An outbox is real work and is not pretended at.
- **No invitations by email, no roles beyond three.** Joining is a link. That is deliberately crude, and it is how five relatives and a carer actually get in without anybody administering anything — but anybody with the link can join, and the app says so on the people page rather than hiding it.

## Roadmap

- A real offline outbox for the dose buttons
- Sending the weekly summary automatically, rather than handing you a link
- A printable sheet for the substitute carer who arrives and knows nothing
- Photo compression before upload

## Licence

MIT. See [LICENSE](LICENSE).
