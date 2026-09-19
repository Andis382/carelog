<?php

namespace App\Http\Controllers;

use App\Http\Controllers\Concerns\InCircle;
use App\Models\Circle;
use App\Models\CircleMember;
use App\Models\LogEntry;
use App\Models\Supply;
use App\Services\MedicationBoard;
use Illuminate\Http\Request;
use Symfony\Component\HttpKernel\Exception\NotFoundHttpException;

class CircleController extends Controller
{
    use InCircle;

    public function landing()
    {
        return auth()->check() ? redirect()->route('circles.index') : view('landing');
    }

    public function index(Request $request)
    {
        $circles = $request->user()->circles()->get();

        // One circle is the common case, and making somebody choose from a
        // list of one is a tax on every single visit.
        if ($circles->count() === 1) {
            return redirect()->route('circle.today', $circles->first());
        }

        return view('circles.index', ['circles' => $circles]);
    }

    public function create()
    {
        return view('circles.create');
    }

    public function store(Request $request)
    {
        $data = $request->validate([
            'name' => ['required', 'string', 'max:80'],
            'born_on' => ['nullable', 'date', 'before:today'],
            'city' => ['nullable', 'string', 'max:80'],
            'about' => ['nullable', 'string', 'max:2000'],
        ]);

        $circle = Circle::create($data + ['timezone' => $request->user()->timezone]);
        $circle->members()->create([
            'user_id' => $request->user()->id,
            'role' => CircleMember::FAMILY,
            'joined_at' => now(),
        ]);

        return redirect()->route('people.index', $circle)->with('status', __('flash.circle_created', ['name' => $circle->name]));
    }

    public function show(Request $request, Circle $circle, MedicationBoard $board)
    {
        $this->member($circle);
        $today = $circle->today();

        $shifts = $circle->shifts()->with('user')->where('on_date', $today->toDateString())->get();

        return view('today', [
            'circle' => $circle,
            'me' => $this->member($circle),
            'today' => $today,
            'board' => $board->board($circle, $today),
            'coverage' => $board->coverage($circle, $today, $today),
            'entries' => $circle->entries()->with('recorder')
                ->whereBetween('at', [$today, $today->copy()->endOfDay()])
                ->orderByDesc('at')->limit(8)->get(),
            'readings' => $circle->readings()->with('recorder')
                ->orderByDesc('at')->limit(3)->get(),
            'ranges' => $circle->ranges()->with('setter')->get()->keyBy('kind'),
            'shifts' => $shifts,
            'short' => $circle->supplies()->get()->filter(fn (Supply $s) => $s->needsBuying()),
            'people' => $circle->members()->with('user')->get(),
        ]);
    }

    public function edit(Request $request, Circle $circle)
    {
        $this->member($circle);

        return view('circles.settings', ['circle' => $circle, 'me' => $this->member($circle)]);
    }

    public function update(Request $request, Circle $circle)
    {
        $this->member($circle);

        $circle->update($request->validate([
            'name' => ['required', 'string', 'max:80'],
            'born_on' => ['nullable', 'date', 'before:today'],
            'city' => ['nullable', 'string', 'max:80'],
            'about' => ['nullable', 'string', 'max:2000'],
            'timezone' => ['required', 'timezone'],
        ]));

        return redirect()->route('circle.settings', $circle)->with('status', __('flash.saved'));
    }

    public function joinForm(string $code)
    {
        return view('circles.join', ['circle' => $this->byCode($code)]);
    }

    public function join(Request $request, string $code)
    {
        $circle = $this->byCode($code);

        $data = $request->validate(['role' => ['required', 'in:family,carer,payer']]);

        $circle->members()->firstOrCreate(
            ['user_id' => $request->user()->id],
            ['role' => $data['role'], 'joined_at' => now(), 'gets_digest' => $data['role'] === CircleMember::PAYER],
        );

        // Joining is itself worth logging: everybody in the circle should be
        // able to see who else can read this, and when they arrived.
        $circle->entries()->create([
            'recorded_by' => $request->user()->id,
            'kind' => LogEntry::KINDS[5],
            'body' => __('flash.joined_log', ['who' => $request->user()->name]),
            'at' => now(),
        ]);

        return redirect()->route('circle.today', $circle)->with('status', __('flash.joined', ['name' => $circle->name]));
    }

    private function byCode(string $code): Circle
    {
        return Circle::where('join_code', $code)->first() ?? throw new NotFoundHttpException();
    }
}
