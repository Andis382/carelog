<?php

namespace App\Http\Controllers;

use App\Http\Controllers\Concerns\InCircle;
use App\Models\Circle;
use App\Services\WeeklyDigest;
use Carbon\CarbonInterface;
use Illuminate\Http\Request;
use Illuminate\Support\Carbon;
use Symfony\Component\HttpKernel\Exception\NotFoundHttpException;

class DigestController extends Controller
{
    use InCircle;

    public function __construct(private readonly WeeklyDigest $digests)
    {
    }

    public function show(Request $request, Circle $circle, ?string $week = null)
    {
        $this->member($circle);
        $start = $this->week($circle, $week);

        $digest = $circle->digests()->firstWhere('week_start', $start->toDateString())
            ?? $this->digests->build($circle, $start);

        return view('digest', [
            'circle' => $circle,
            'start' => $start,
            'end' => $start->copy()->addDays(6),
            'digest' => $digest,
            'recipients' => $circle->members()->with('user')->where('gets_digest', true)->get(),
        ]);
    }

    public function rebuild(Request $request, Circle $circle, string $week)
    {
        $this->member($circle);
        $start = $this->week($circle, $week);

        $this->digests->build($circle, $start);

        return redirect()->route('digest.show', [$circle, $start->toDateString()])
            ->with('status', __('flash.digest_rebuilt'));
    }

    private function week(Circle $circle, ?string $value): CarbonInterface
    {
        $today = $circle->today();

        if ($value === null) {
            return $today->copy()->startOfWeek();
        }

        try {
            $start = Carbon::parse($value, $today->getTimezone())->startOfWeek();
        } catch (\Throwable) {
            throw new NotFoundHttpException();
        }

        if ($start->toDateString() > $today->toDateString()) {
            throw new NotFoundHttpException();
        }

        return $start;
    }
}
