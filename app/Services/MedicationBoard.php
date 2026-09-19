<?php

namespace App\Services;

use App\Models\Circle;
use App\Models\DoseEvent;
use App\Models\MedicationSlot;
use App\Models\User;
use Carbon\CarbonInterface;
use Illuminate\Support\Collection;
use Illuminate\Support\Facades\DB;

/**
 * The day's medicines, and the one rule that makes this worth installing.
 *
 * A dose is recorded against a specific slot on a specific date. Before it is
 * written, this asks whether that slot already has an event on it. If it does,
 * nothing is saved: the caller gets the existing event back and has to decide,
 * out loud, whether what they are holding is genuinely a second dose.
 *
 * That is the whole safety feature, and it is the one thing a WhatsApp group
 * cannot do. In a group, "did you give mum her pill?" scrolls out of sight in
 * four minutes; here the question is asked by the software at the moment
 * somebody's thumb is over the button, with a name and a time attached.
 *
 * When a second dose really was given, it is written with a reason and a link
 * back to the first, and it is never quietly folded into the first one. The
 * permanent, visible record of a double dose is more use to a family and a
 * doctor than a tidy screen.
 *
 * Nothing here decides anything medical. It does not compute a schedule, warn
 * about an interaction, or suggest skipping. It records what people did.
 */
class MedicationBoard
{
    /**
     * Every dose due on a date, grouped by part of day, each with whatever has
     * already been recorded against it.
     *
     * @return Collection<string, Collection<int, array{slot: MedicationSlot, events: Collection<int, DoseEvent>}>>
     */
    public function board(Circle $circle, CarbonInterface $date): Collection
    {
        $slots = MedicationSlot::query()
            ->with(['medication', 'events' => fn ($query) => $query
                ->where('on_date', $date->toDateString())
                ->with('recorder')
                ->orderBy('at')])
            ->whereHas('medication', fn ($query) => $query
                ->where('circle_id', $circle->id)
                ->where('active', true))
            ->orderBy('at_time')
            ->get()
            ->filter(fn (MedicationSlot $slot) => $slot->fallsOn($date));

        return $slots
            ->map(fn (MedicationSlot $slot) => ['slot' => $slot, 'events' => $slot->events])
            ->groupBy(fn (array $row) => $row['slot']->partOfDay());
    }

    /** What has already been written against one slot on one date. */
    public function eventsFor(MedicationSlot $slot, CarbonInterface $date): Collection
    {
        return $slot->events()
            ->with('recorder')
            ->where('on_date', $date->toDateString())
            ->orderBy('at')
            ->get();
    }

    /**
     * The event that would collide with a new one, or null.
     *
     * A skipped or refused dose counts: "Ana marked this skipped at 08:10" is
     * exactly as worth stopping for as "Ana gave it".
     */
    public function existingFor(MedicationSlot $slot, CarbonInterface $date): ?DoseEvent
    {
        return $slot->events()
            ->with('recorder')
            ->where('on_date', $date->toDateString())
            ->whereNull('duplicates_id')
            ->orderBy('at')
            ->first();
    }

    /**
     * Write a dose.
     *
     * @return array{event: ?DoseEvent, conflict: ?DoseEvent}
     *         conflict set and event null means nothing was saved and the
     *         caller must ask the human before trying again with $extraReason.
     */
    public function record(
        MedicationSlot $slot,
        CarbonInterface $date,
        User $by,
        string $status,
        ?string $note = null,
        ?string $extraReason = null,
        ?CarbonInterface $at = null,
    ): array {
        $existing = $this->existingFor($slot, $date);

        if ($existing && $extraReason === null) {
            // Somebody already dealt with this slot. Do not write, do not
            // overwrite, do not guess. Hand the fact back and let a person
            // decide what it means.
            return ['event' => null, 'conflict' => $existing];
        }

        $event = DB::transaction(fn () => $slot->events()->create([
            'recorded_by' => $by->id,
            'on_date' => $date->toDateString(),
            'status' => in_array($status, DoseEvent::STATUSES, true) ? $status : DoseEvent::GIVEN,
            'at' => $at ?? now(),
            'note' => $note,
            'extra_reason' => $existing ? $extraReason : null,
            'duplicates_id' => $existing?->id,
        ]));

        return ['event' => $event->load('recorder'), 'conflict' => null];
    }

    /**
     * Undo — but only your own, and only the last thing you wrote.
     *
     * A paid carer's record is her defence if she is ever accused of anything,
     * so nobody else can reach in and change it. She can correct her own slip
     * for as long as it is plausibly a slip; after that it stands, which is
     * what makes it worth anything as evidence.
     */
    public function canUndo(DoseEvent $event, User $user, int $withinMinutes = 30): bool
    {
        return (int) $event->recorded_by === (int) $user->id
            && $event->created_at?->diffInMinutes(now()) < $withinMinutes;
    }

    /**
     * How much of a period's medicine is accounted for.
     *
     * Reported as "recorded", never as "taken" and never as a score. A blank
     * slot may mean a dose was missed or may mean a tired daughter forgot to
     * tick a box, and this software cannot tell the difference — so it says
     * what it knows and nothing more.
     *
     * @return array{due: int, recorded: int, given: int, missed_record: int, extra: int}
     */
    public function coverage(Circle $circle, CarbonInterface $from, CarbonInterface $to): array
    {
        $slots = MedicationSlot::query()
            ->whereHas('medication', fn ($query) => $query->where('circle_id', $circle->id)->where('active', true))
            ->get();

        $events = DoseEvent::query()
            ->whereIn('medication_slot_id', $slots->pluck('id'))
            ->whereBetween('on_date', [$from->toDateString(), $to->toDateString()])
            ->get();

        $due = 0;
        for ($day = $from->copy(); $day->lte($to); $day->addDay()) {
            $due += $slots->filter(fn (MedicationSlot $slot) => $slot->fallsOn($day))->count();
        }

        $primary = $events->whereNull('duplicates_id');

        return [
            'due' => $due,
            'recorded' => $primary->count(),
            'given' => $primary->where('status', DoseEvent::GIVEN)->count(),
            'missed_record' => max(0, $due - $primary->count()),
            'extra' => $events->whereNotNull('duplicates_id')->count(),
        ];
    }
}
