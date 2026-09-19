<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;

/**
 * What was sent to the member who is far away.
 *
 * Kept verbatim rather than regenerated, so that what somebody read on a
 * Sunday night in Germany stays exactly what they read, even after the week's
 * entries are corrected.
 */
class Digest extends Model
{
    protected $fillable = ['week_start', 'body', 'sent_at'];

    protected function casts(): array
    {
        return ['week_start' => 'date', 'sent_at' => 'datetime'];
    }

    public function circle(): BelongsTo
    {
        return $this->belongsTo(Circle::class);
    }
}
