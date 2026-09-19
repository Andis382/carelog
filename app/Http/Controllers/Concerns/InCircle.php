<?php

namespace App\Http\Controllers\Concerns;

use App\Models\Circle;
use App\Models\CircleMember;
use Symfony\Component\HttpKernel\Exception\NotFoundHttpException;

/**
 * A circle you are not in does not exist.
 *
 * 404 rather than 403, deliberately. This is somebody's mother's medication
 * list; "you may not see this" still confirms that a circle with that id is
 * there and that somebody you can name is in it.
 */
trait InCircle
{
    protected function member(Circle $circle): CircleMember
    {
        $member = auth()->user()?->membershipOf($circle);

        if (! $member) {
            throw new NotFoundHttpException();
        }

        return $member;
    }

    /**
     * Only the person who wrote something may change it.
     *
     * This is the carer's shield. A paid carer fills the log in because it is
     * her proof of what she did; the moment a family member can quietly edit
     * her entries, it stops being proof and she stops filling it in.
     */
    protected function mine(\Illuminate\Database\Eloquent\Model $record, string $column = 'recorded_by'): void
    {
        if ((int) $record->{$column} !== (int) auth()->id()) {
            throw new NotFoundHttpException();
        }
    }
}
