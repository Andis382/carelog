<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;

/**
 * Somebody gave, skipped or was refused one dose.
 *
 * An event rather than a checkbox, because a checkbox cannot say who ticked it
 * or when, and cannot be asked "somebody already ticked this — is yours a
 * different dose?". A missed or doubled dose is the thing this product exists
 * to prevent, so it gets the richest row in the schema.
 */
class DoseEvent extends Model
{
    public const GIVEN = 'given';
    public const SKIPPED = 'skipped';
    public const REFUSED = 'refused';

    public const STATUSES = [self::GIVEN, self::SKIPPED, self::REFUSED];

    protected $fillable = ['recorded_by', 'on_date', 'status', 'at', 'note', 'extra_reason', 'duplicates_id'];

    protected function casts(): array
    {
        return ['on_date' => 'date', 'at' => 'datetime'];
    }

    public function slot(): BelongsTo
    {
        return $this->belongsTo(MedicationSlot::class, 'medication_slot_id');
    }

    public function recorder(): BelongsTo
    {
        return $this->belongsTo(User::class, 'recorded_by');
    }

    public function duplicates(): BelongsTo
    {
        return $this->belongsTo(self::class, 'duplicates_id');
    }

    /** A second dose somebody knowingly recorded against an already-filled slot. */
    public function isExtra(): bool
    {
        return $this->duplicates_id !== null;
    }

    public function statusLabel(): string
    {
        return __('dose.status.'.$this->status);
    }

    public function tone(): string
    {
        return match ($this->status) {
            self::GIVEN => $this->isExtra() ? 'warn' : 'ok',
            self::REFUSED => 'warn',
            default => 'quiet',
        };
    }

    public function icon(): string
    {
        return match ($this->status) {
            self::GIVEN => $this->isExtra() ? 'warning' : 'check',
            self::REFUSED => 'prohibit',
            default => 'minus',
        };
    }
}
