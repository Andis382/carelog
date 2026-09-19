<?php

namespace App\Http\Controllers;

use App\Http\Controllers\Concerns\InCircle;
use App\Models\Circle;
use App\Models\LogEntry;
use App\Models\Reading;
use Carbon\CarbonInterface;
use Illuminate\Http\Request;
use Illuminate\Support\Carbon;
use Illuminate\Validation\Rule;
use Symfony\Component\HttpKernel\Exception\NotFoundHttpException;

class LogController extends Controller
{
    use InCircle;

    public function index(Request $request, Circle $circle, ?string $date = null)
    {
        $this->member($circle);
        $on = $this->date($circle, $date);

        return view('log.index', [
            'circle' => $circle,
            'date' => $on,
            'entries' => $circle->entries()->with('recorder')
                ->whereBetween('at', [$on, $on->copy()->endOfDay()])
                ->orderByDesc('at')->get(),
            'readings' => $circle->readings()->with('recorder')
                ->whereBetween('at', [$on, $on->copy()->endOfDay()])
                ->orderByDesc('at')->get(),
            'ranges' => $circle->ranges()->with('setter')->get()->keyBy('kind'),
            'isToday' => $on->isSameDay($circle->today()),
        ]);
    }

    public function store(Request $request, Circle $circle)
    {
        $this->member($circle);

        $data = $request->validate([
            'kind' => ['required', Rule::in(LogEntry::KINDS)],
            'value' => ['nullable', 'string', 'max:40'],
            'body' => ['nullable', 'string', 'max:2000'],
            'at' => ['nullable', 'date'],
            'photo' => ['nullable', 'image', 'max:8192'],
        ]);

        $entry = $circle->entries()->make([
            'recorded_by' => $request->user()->id,
            'kind' => $data['kind'],
            'value' => $data['value'] ?? null,
            'body' => $data['body'] ?? null,
            'at' => isset($data['at']) ? Carbon::parse($data['at']) : now(),
        ]);

        if ($request->hasFile('photo')) {
            $entry->photo_path = $request->file('photo')->store('entries', 'public');
        }

        $entry->save();

        return back()->with('status', __('flash.logged'));
    }

    /** Your own, and only your own. See the note in InCircle. */
    public function destroy(Request $request, Circle $circle, LogEntry $entry)
    {
        $this->member($circle);

        if ((int) $entry->circle_id !== (int) $circle->id) {
            throw new NotFoundHttpException();
        }

        $this->mine($entry);
        $entry->delete();

        return back()->with('status', __('flash.removed'));
    }

    public function storeReading(Request $request, Circle $circle)
    {
        $this->member($circle);

        $data = $request->validate([
            'kind' => ['required', Rule::in(Reading::KINDS)],
            'value' => ['required', 'numeric', 'min:0', 'max:999'],
            'value_2' => ['nullable', 'numeric', 'min:0', 'max:999'],
            'note' => ['nullable', 'string', 'max:300'],
            'at' => ['nullable', 'date'],
        ]);

        $circle->readings()->create([
            'recorded_by' => $request->user()->id,
            'kind' => $data['kind'],
            'value' => $data['value'],
            'value_2' => $data['kind'] === 'bp' ? ($data['value_2'] ?? null) : null,
            'note' => $data['note'] ?? null,
            'at' => isset($data['at']) ? Carbon::parse($data['at']) : now(),
        ]);

        return back()->with('status', __('flash.reading_saved'));
    }

    /**
     * Somebody in the family writes down the range, with their name on it.
     *
     * The app has none of its own and never will. It can say a number is
     * outside the range Besa wrote down after the appointment on the twelfth;
     * it must never say a number is high.
     */
    public function storeRange(Request $request, Circle $circle)
    {
        $this->member($circle);

        $data = $request->validate([
            'kind' => ['required', Rule::in(Reading::KINDS)],
            'low' => ['nullable', 'numeric', 'min:0', 'max:999'],
            'high' => ['nullable', 'numeric', 'min:0', 'max:999', 'gt:low'],
            'source' => ['nullable', 'string', 'max:120'],
        ]);

        $circle->ranges()->updateOrCreate(
            ['kind' => $data['kind']],
            [
                'set_by' => $request->user()->id,
                'low' => $data['low'] ?? null,
                'high' => $data['high'] ?? null,
                'source' => $data['source'] ?? null,
            ],
        );

        return back()->with('status', __('flash.range_saved'));
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

        if ($on->toDateString() > $today->toDateString()) {
            throw new NotFoundHttpException();
        }

        return $on;
    }
}
