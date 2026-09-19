<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;
use Illuminate\Database\Eloquent\Relations\HasMany;

/**
 * A medication as the family wrote it down.
 *
 * Strength and note are free text copied off the box on purpose. The moment
 * this app starts holding a drug database it starts implying it knows whether
 * a dose is right, and it does not and must not.
 */
class Medication extends Model
{
    use HasFactory;

    public const FORMS = ['tablet', 'capsule', 'drops', 'liquid', 'injection', 'patch', 'inhaler', 'other'];

    protected $fillable = ['name', 'strength', 'form', 'note', 'active', 'sort'];

    protected function casts(): array
    {
        return ['active' => 'bool'];
    }

    public function circle(): BelongsTo
    {
        return $this->belongsTo(Circle::class);
    }

    public function slots(): HasMany
    {
        return $this->hasMany(MedicationSlot::class)->orderBy('at_time');
    }

    public function label(): string
    {
        return trim($this->name.' '.$this->strength);
    }

    public function icon(): string
    {
        return match ($this->form) {
            'injection' => 'syringe',
            'drops', 'liquid' => 'drink',
            'patch' => 'bandage',
            default => 'pill',
        };
    }

    public function formLabel(): string
    {
        return __('med.form.'.$this->form);
    }
}
