<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;

/**
 * Anything worth writing down that is not a dose or a number.
 *
 * Meals, fluids, mood, sleep, a doctor's visit, an incident, a photo of a
 * prescription. Deliberately one table with a kind: a family will want to
 * record something nobody anticipated, and a note is always better than a
 * missing feature.
 */
class LogEntry extends Model
{
    public const KINDS = ['meal', 'drink', 'mood', 'sleep', 'visit', 'note', 'incident'];

    protected $fillable = ['recorded_by', 'kind', 'value', 'body', 'photo_path', 'at'];

    protected function casts(): array
    {
        return ['at' => 'datetime'];
    }

    public function circle(): BelongsTo
    {
        return $this->belongsTo(Circle::class);
    }

    public function recorder(): BelongsTo
    {
        return $this->belongsTo(User::class, 'recorded_by');
    }

    public function kindLabel(): string
    {
        return __('entry.kind.'.$this->kind);
    }

    public function icon(): string
    {
        return match ($this->kind) {
            'meal' => 'meal',
            'drink' => 'drink',
            'mood' => match ($this->value) {
                'good' => 'mood-good',
                'low', 'bad' => 'mood-low',
                default => 'mood-mid',
            },
            'sleep' => 'bed',
            'visit' => 'stethoscope',
            'incident' => 'warning',
            default => 'notepad',
        };
    }

    public function tone(): string
    {
        return $this->kind === 'incident' ? 'warn' : 'quiet';
    }

    public function valueLabel(): ?string
    {
        if (! $this->value) {
            return null;
        }

        $key = 'entry.value.'.$this->kind.'.'.$this->value;
        $translated = __($key);

        return $translated === $key ? $this->value : $translated;
    }
}
