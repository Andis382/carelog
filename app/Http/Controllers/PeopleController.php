<?php

namespace App\Http\Controllers;

use App\Http\Controllers\Concerns\InCircle;
use App\Models\Circle;
use App\Models\CircleMember;
use Illuminate\Http\Request;
use Illuminate\Validation\Rule;
use Symfony\Component\HttpKernel\Exception\NotFoundHttpException;

class PeopleController extends Controller
{
    use InCircle;

    public function index(Request $request, Circle $circle)
    {
        $this->member($circle);

        return view('people', [
            'circle' => $circle,
            'me' => $this->member($circle),
            'members' => $circle->members()->with('user')->get(),
            'link' => route('circles.join', $circle->join_code),
        ]);
    }

    public function update(Request $request, Circle $circle, CircleMember $member)
    {
        $this->member($circle);

        if ((int) $member->circle_id !== (int) $circle->id) {
            throw new NotFoundHttpException();
        }

        $data = $request->validate([
            'role' => ['required', Rule::in(CircleMember::ROLES)],
            'gets_digest' => ['nullable', 'boolean'],
        ]);

        $member->update([
            'role' => $data['role'],
            'gets_digest' => $request->boolean('gets_digest'),
        ]);

        return back()->with('status', __('flash.saved'));
    }

    /**
     * Leaving takes you out; it never takes your entries with you.
     *
     * What a carer recorded stays in the log after she has gone, with her name
     * on it. That is the point of it: six months later, somebody has to be
     * able to see what was done and who did it.
     */
    public function leave(Request $request, Circle $circle)
    {
        $member = $this->member($circle);

        if ($circle->members()->count() === 1) {
            return back()->with('status', __('flash.last_member'));
        }

        $member->delete();

        return redirect()->route('circles.index')->with('status', __('flash.left', ['name' => $circle->name]));
    }
}
