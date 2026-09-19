<?php

namespace App\Http\Controllers;

use App\Http\Controllers\Concerns\InCircle;
use App\Models\Circle;
use App\Models\Supply;
use Illuminate\Http\Request;
use Illuminate\Validation\Rule;
use Symfony\Component\HttpKernel\Exception\NotFoundHttpException;

class SuppliesController extends Controller
{
    use InCircle;

    public function index(Request $request, Circle $circle)
    {
        $this->member($circle);

        return view('supplies', [
            'circle' => $circle,
            'supplies' => $circle->supplies()->with('updater')->get(),
        ]);
    }

    public function store(Request $request, Circle $circle)
    {
        $this->member($circle);

        $data = $request->validate(['name' => ['required', 'string', 'max:60']]);

        $circle->supplies()->create($data + [
            'level' => Supply::OK,
            'updated_by' => $request->user()->id,
            'level_set_at' => now(),
            'sort' => (int) ($circle->supplies()->max('sort') ?? 0) + 10,
        ]);

        return back()->with('status', __('flash.supply_added', ['name' => $data['name']]));
    }

    /** One tap. Whoever notices, marks it — that is the entire feature. */
    public function level(Request $request, Circle $circle, Supply $supply)
    {
        $this->member($circle);
        $this->belongs($circle, $supply);

        $data = $request->validate(['level' => ['required', Rule::in(Supply::LEVELS)]]);

        $supply->update([
            'level' => $data['level'],
            'updated_by' => $request->user()->id,
            'level_set_at' => now(),
        ]);

        return back()->with('status', __('flash.saved'));
    }

    public function destroy(Request $request, Circle $circle, Supply $supply)
    {
        $this->member($circle);
        $this->belongs($circle, $supply);
        $supply->delete();

        return back()->with('status', __('flash.removed'));
    }

    private function belongs(Circle $circle, Supply $supply): void
    {
        if ((int) $supply->circle_id !== (int) $circle->id) {
            throw new NotFoundHttpException();
        }
    }
}
