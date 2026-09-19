<?php

namespace App\Models;

use Carbon\CarbonInterface;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;
use Illuminate\Database\Eloquent\Relations\HasMany;

/**
 * A time of day a medication is due, on given weekdays.
 *
 * Weekdays are the digits themselves — "135" is Monday, Wednesday, Friday —
 * rather than a bitmask, because somebody will read this column in a database
 * client one day and it should tell them what it means without a lookup table.
 */
class MedicationSlot extends Model
{
    protected $fillable = ['at_time', 'weekdays'];

    public function medication(): BelongsTo
    {
        return $this->belongsTo(Medication::class);
    }

    public function events(): HasMany
    {
        return $this->hasMany(DoseEvent::class);
    }

    public function time(): string
    {
        return substr((string) $this->at_time, 0, 5);
    }

    public function fallsOn(CarbonInterface $date): bool
    {
        return str_contains((string) $this->weekdays, (string) $date->isoWeekday());
    }

    /** Morning, afternoon, evening, night — for grouping the day's board. */
    public function partOfDay(): string
    {
        $hour = (int) substr($this->time(), 0, 2);

        return match (true) {
            $hour < 11 => 'morning',
            $hour < 16 => 'midday',
            $hour < 21 => 'evening',
            default => 'night',
        };
    }
}
