<?php

namespace App\Services;

use App\Models\Circle;
use App\Models\Digest;
use App\Models\LogEntry;
use App\Models\Reading;
use App\Models\Supply;
use Carbon\CarbonInterface;

/**
 * The week, written for the person who is not there.
 *
 * This is the part somebody pays for. A child working in Germany sends money
 * home every month and hears "she's fine" on the phone, then gets a call from
 * a hospital. What they are actually buying is not surveillance of their
 * sister — it is the difference between "fine" and a page of specifics.
 *
 * Three rules hold the wording, and tests/Unit/DigestToneTest.php enforces
 * them against every shipped language:
 *
 *   It never blames. A blank slot is reported as "not recorded", never as
 *   "missed" — this software cannot tell a missed dose from a tired daughter
 *   forgetting to tick a box, and guessing wrong turns the one person doing
 *   the work into a suspect.
 *
 *   It never diagnoses. A number outside a range is reported as outside the
 *   range somebody in the family wrote down, with their name on it.
 *
 *   A quiet week is reported as quiet. Nothing is padded to look like value
 *   for money, because the weeks when nothing happens are the good ones.
 */
class WeeklyDigest
{
    public function __construct(private readonly MedicationBoard $board)
    {
    }

    public function build(Circle $circle, CarbonInterface $weekStart, bool $persist = true): Digest
    {
        $start = $weekStart->copy()->startOfDay();
        $end = $start->copy()->addDays(6);

        $body = $this->compose($circle, $start, $end);

        $digest = $persist
            ? $circle->digests()->updateOrCreate(['week_start' => $start->toDateString()], ['body' => $body])
            : new Digest(['week_start' => $start->toDateString(), 'body' => $body]);

        return $digest;
    }

    public function compose(Circle $circle, CarbonInterface $start, CarbonInterface $end): string
    {
        $was = app()->getLocale();
        app()->setLocale($this->digestLocale($circle) ?: $was);

        try {
            return $this->lines($circle, $start, $end);
        } finally {
            app()->setLocale($was);
        }
    }

    private function lines(Circle $circle, CarbonInterface $start, CarbonInterface $end): string
    {
        $out = [];
        $out[] = __('digest.heading', [
            'name' => $circle->name,
            'from' => $start->format('d/m'),
            'to' => $end->format('d/m'),
        ]);
        $out[] = '';

        // Medicine, as coverage and never as a grade.
        $coverage = $this->board->coverage($circle, $start, $end);
        if ($coverage['due'] > 0) {
            $out[] = __('digest.meds', [
                'recorded' => $coverage['recorded'],
                'due' => $coverage['due'],
            ]);

            if ($coverage['missed_record'] > 0) {
                $out[] = '  '.trans_choice('digest.meds_blank', $coverage['missed_record'], ['n' => $coverage['missed_record']]);
            }

            if ($coverage['extra'] > 0) {
                $out[] = '  '.trans_choice('digest.meds_extra', $coverage['extra'], ['n' => $coverage['extra']]);
            }
        }

        // Numbers, with the family's own range cited by name.
        $readings = Reading::query()
            ->where('circle_id', $circle->id)
            ->whereBetween('at', [$start, $end->copy()->endOfDay()])
            ->orderBy('at')
            ->get();

        if ($readings->isNotEmpty()) {
            $out[] = '';
            $out[] = __('digest.readings');
            $ranges = $circle->ranges()->with('setter')->get()->keyBy('kind');

            foreach ($readings->groupBy('kind') as $kind => $group) {
                $last = $group->last();
                $line = '  '.$last->kindLabel().': '.$last->display()
                    .' ('.trans_choice('digest.times', $group->count(), ['n' => $group->count()]).')';

                $range = $ranges->get($kind);
                $verdict = $range?->verdictFor($last->value);
                if ($range && $verdict) {
                    $line .= ' — '.__('digest.outside_range', [
                        'who' => $range->setter?->shortName() ?? '—',
                        'range' => $range->describe(),
                    ]);
                }

                $out[] = $line;
            }
        }

        // Anything anyone thought worth writing down.
        $entries = LogEntry::query()
            ->with('recorder')
            ->where('circle_id', $circle->id)
            ->whereBetween('at', [$start, $end->copy()->endOfDay()])
            ->orderBy('at')
            ->get();

        $incidents = $entries->where('kind', 'incident');
        $visits = $entries->where('kind', 'visit');

        if ($visits->isNotEmpty()) {
            $out[] = '';
            $out[] = __('digest.visits');
            foreach ($visits as $visit) {
                $out[] = '  '.$visit->at->format('d/m').' — '.($visit->body ?: $visit->kindLabel());
            }
        }

        if ($incidents->isNotEmpty()) {
            $out[] = '';
            $out[] = __('digest.incidents');
            foreach ($incidents as $incident) {
                $out[] = '  '.$incident->at->format('d/m').' — '.($incident->body ?: $incident->kindLabel());
            }
        }

        // Who actually did the work. The remote payer rarely knows.
        $byPerson = $entries->groupBy('recorded_by')
            ->map(fn ($group) => ['who' => $group->first()->recorder?->shortName() ?? '—', 'n' => $group->count()])
            ->sortByDesc('n')
            ->take(4);

        if ($byPerson->isNotEmpty()) {
            $out[] = '';
            $out[] = __('digest.who').' '.$byPerson
                ->map(fn ($row) => $row['who'].' ('.$row['n'].')')
                ->implode(', ');
        }

        // Things to buy before somebody drives across town for one packet.
        $short = $circle->supplies()->get()->filter(fn (Supply $supply) => $supply->needsBuying());
        if ($short->isNotEmpty()) {
            $out[] = '';
            $out[] = __('digest.supplies').' '.$short->map(fn (Supply $s) => $s->name.' ('.$s->levelLabel().')')->implode(', ');
        }

        // A quiet week is a good week, and it is allowed to say so.
        if ($entries->isEmpty() && $readings->isEmpty() && $coverage['recorded'] === 0) {
            $out[] = '';
            $out[] = __('digest.quiet');
        }

        $out[] = '';
        $out[] = __('digest.footer');

        return implode("\n", $out);
    }

    /**
     * The digest is written for whoever receives it, in their language.
     *
     * Before anybody has been marked as the recipient it falls back to the
     * first person in the circle, which is nearly always the one who set it
     * up. A summary in the wrong language is still readable; one generated in
     * whatever locale a cron job happened to boot in is not.
     */
    private function digestLocale(Circle $circle): ?string
    {
        $members = $circle->members()->with('user')->orderBy('id')->get();

        return $members->firstWhere('gets_digest', true)?->user?->locale
            ?? $members->first()?->user?->locale;
    }
}
