<?php

namespace App\Http\Controllers;

use App\Http\Controllers\Concerns\InCircle;
use App\Models\Circle;
use App\Models\Shift;
use Illuminate\Http\Request;
use Symfony\Component\HttpKernel\Exception\NotFoundHttpException;

class RotaController extends Controller
{
    use InCircle;

    public function index(Request $request, Circle $circle)
    {
        $this->member($circle);
        $from = $circle->today();

        return view('rota', [
            'circle' => $circle,
            'from' => $from,
            'days' => collect(range(0, 13))->map(fn (int $n) => $from->copy()->addDays($n)),
            'shifts' => $circle->shifts()->with('user')
                ->whereBetween('on_date', [$from->toDateString(), $from->copy()->addDays(13)->toDateString()])
                ->get()->groupBy(fn (Shift $shift) => $shift->on_date->toDateString()),
            'people' => $circle->members()->with('user')->get(),
        ]);
    }

    public function store(Request $request, Circle $circle)
    {
        $this->member($circle);

        $data = $request->validate([
            'user_id' => ['required', 'integer'],
            'on_date' => ['required', 'date'],
            'from_time' => ['nullable', 'date_format:H:i'],
            'to_time' => ['nullable', 'date_format:H:i', 'after:from_time'],
            'note' => ['nullable', 'string', 'max:200'],
        ]);

        // Only somebody already in the circle can be put on the rota.
        if (! $circle->members()->where('user_id', $data['user_id'])->exists()) {
            throw new NotFoundHttpException();
        }

        $circle->shifts()->create($data);

        return back()->with('status', __('flash.shift_added'));
    }

    /**
     * Anyone in the circle can take a shift off the rota.
     *
     * Deliberately not owner-only, unlike the log. A rota is a plan people
     * make together and change together; an entry in the log is a claim about
     * what somebody did, and that belongs to whoever made it.
     */
    public function destroy(Request $request, Circle $circle, Shift $shift)
    {
        $this->member($circle);

        if ((int) $shift->circle_id !== (int) $circle->id) {
            throw new NotFoundHttpException();
        }

        $shift->delete();

        return back()->with('status', __('flash.shift_removed'));
    }
}
