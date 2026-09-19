<?php

namespace App\Http\Controllers;

use App\Http\Controllers\Concerns\InCircle;
use App\Models\Circle;
use App\Models\DoseEvent;
use App\Models\Medication;
use App\Models\MedicationSlot;
use App\Services\MedicationBoard;
use Carbon\CarbonInterface;
use Illuminate\Http\Request;
use Illuminate\Support\Carbon;
use Illuminate\Validation\Rule;
use Symfony\Component\HttpKernel\Exception\NotFoundHttpException;

class MedsController extends Controller
{
    use InCircle;

    public function __construct(private readonly MedicationBoard $board)
    {
    }

    public function index(Request $request, Circle $circle, ?string $date = null)
    {
        $this->member($circle);
        $on = $this->date($circle, $date);

        return view('meds.index', [
            'circle' => $circle,
            'date' => $on,
            'board' => $this->board->board($circle, $on),
            'coverage' => $this->board->coverage($circle, $on, $on),
            'isToday' => $on->isSameDay($circle->today()),
        ]);
    }

    /**
     * Record one dose.
     *
     * If the slot already has something on it, nothing is written: the screen
     * comes back with the existing event and asks whether this really is a
     * second dose. Only a person can answer that, and their answer is stored
     * with their name on it.
     */
    public function record(Request $request, Circle $circle, MedicationSlot $slot, string $date)
    {
        $this->member($circle);
        $this->slotBelongs($circle, $slot);
        $on = $this->date($circle, $date);

        $data = $request->validate([
            'status' => ['required', Rule::in(DoseEvent::STATUSES)],
            'note' => ['nullable', 'string', 'max:300'],
            'extra_reason' => ['nullable', 'string', 'max:300'],
        ]);

        $result = $this->board->record(
            slot: $slot,
            date: $on,
            by: $request->user(),
            status: $data['status'],
            note: $data['note'] ?? null,
            extraReason: $data['extra_reason'] ?? null,
        );

        if ($result['conflict']) {
            return redirect()
                ->route('meds.index', [$circle, $on->toDateString()])
                ->with('conflict', [
                    'slot' => $slot->id,
                    'status' => $data['status'],
                    'who' => $result['conflict']->recorder?->shortName(),
                    'at' => $result['conflict']->at->timezone($circle->timezone)->format('H:i'),
                    'what' => $result['conflict']->statusLabel(),
                ]);
        }

        return redirect()
            ->route('meds.index', [$circle, $on->toDateString()])
            ->with('status', __('flash.dose_recorded'));
    }

    public function undo(Request $request, Circle $circle, DoseEvent $event)
    {
        $this->member($circle);
        $this->slotBelongs($circle, $event->slot);

        // Only your own, and only while it is plausibly a slip. After that it
        // stands — which is exactly what makes the log worth anything to the
        // person who wrote it.
        if (! $this->board->canUndo($event, $request->user())) {
            throw new NotFoundHttpException();
        }

        $date = $event->on_date->toDateString();
        $event->delete();

        return redirect()->route('meds.index', [$circle, $date])->with('status', __('flash.dose_undone'));
    }

    public function manage(Request $request, Circle $circle)
    {
        $this->member($circle);

        return view('meds.manage', [
            'circle' => $circle,
            'medications' => $circle->medications()->with('slots')->get(),
        ]);
    }

    public function store(Request $request, Circle $circle)
    {
        $this->member($circle);
        $data = $this->rules($request);

        $medication = $circle->medications()->create([
            'name' => $data['name'],
            'strength' => $data['strength'] ?? null,
            'form' => $data['form'],
            'note' => $data['note'] ?? null,
            'active' => true,
            'sort' => (int) ($circle->medications()->max('sort') ?? 0) + 10,
        ]);

        $this->syncSlots($medication, $data['times'] ?? [], $data['weekdays'] ?? '1234567');

        return redirect()->route('meds.manage', $circle)->with('status', __('flash.med_added', ['name' => $medication->name]));
    }

    public function update(Request $request, Circle $circle, Medication $medication)
    {
        $this->member($circle);
        $this->medBelongs($circle, $medication);
        $data = $this->rules($request);

        $medication->update([
            'name' => $data['name'],
            'strength' => $data['strength'] ?? null,
            'form' => $data['form'],
            'note' => $data['note'] ?? null,
            'active' => $request->boolean('active'),
        ]);

        $this->syncSlots($medication, $data['times'] ?? [], $data['weekdays'] ?? '1234567');

        return redirect()->route('meds.manage', $circle)->with('status', __('flash.saved'));
    }

    /**
     * A medicine that has ever been given is retired, never deleted.
     *
     * Deleting it would take the history of every dose with it, and that
     * history is the only answer anyone will ever have to "when did she stop
     * taking the white ones?".
     */
    public function destroy(Request $request, Circle $circle, Medication $medication)
    {
        $this->member($circle);
        $this->medBelongs($circle, $medication);

        $hasHistory = DoseEvent::whereIn('medication_slot_id', $medication->slots()->pluck('id'))->exists();

        if ($hasHistory) {
            $medication->update(['active' => false]);

            return redirect()->route('meds.manage', $circle)
                ->with('status', __('flash.med_retired', ['name' => $medication->name]));
        }

        $medication->delete();

        return redirect()->route('meds.manage', $circle)
            ->with('status', __('flash.med_removed', ['name' => $medication->name]));
    }

    private function rules(Request $request): array
    {
        return $request->validate([
            'name' => ['required', 'string', 'max:80'],
            'strength' => ['nullable', 'string', 'max:40'],
            'form' => ['required', Rule::in(Medication::FORMS)],
            'note' => ['nullable', 'string', 'max:300'],
            'times' => ['array', 'max:8'],
            'times.*' => ['nullable', 'date_format:H:i'],
            'weekdays' => ['nullable', 'string', 'regex:/^[1-7]{1,7}$/'],
        ]);
    }

    private function syncSlots(Medication $medication, array $times, string $weekdays): void
    {
        $times = collect($times)->filter()->unique()->values();

        // A slot with history is never removed: the doses recorded against it
        // still have to mean something a year from now.
        foreach ($medication->slots as $slot) {
            if (! $times->contains($slot->time()) && ! $slot->events()->exists()) {
                $slot->delete();
            }
        }

        foreach ($times as $time) {
            $medication->slots()->updateOrCreate(['at_time' => $time], ['weekdays' => $weekdays]);
        }
    }

    private function slotBelongs(Circle $circle, ?MedicationSlot $slot): void
    {
        if (! $slot || (int) $slot->medication?->circle_id !== (int) $circle->id) {
            throw new NotFoundHttpException();
        }
    }

    private function medBelongs(Circle $circle, Medication $medication): void
    {
        if ((int) $medication->circle_id !== (int) $circle->id) {
            throw new NotFoundHttpException();
        }
    }

    private function date(Circle $circle, ?string $value): CarbonInterface
    {
        $today = $circle->today();

        if ($value === null) {
            return $today->copy();
        }

        try {
            $on = Carbon::parse($value, $today->getTimezone())->startOfDay();
        } catch (\Throwable) {
            throw new NotFoundHttpException();
        }

        // Yesterday's pill can still be recorded this morning; next Tuesday's
        // cannot be recorded at all.
        if ($on->toDateString() > $today->toDateString()
            || $on->toDateString() < $today->copy()->subYear()->toDateString()) {
            throw new NotFoundHttpException();
        }

        return $on;
    }
}
