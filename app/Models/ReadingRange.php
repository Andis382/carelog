<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;

/**
 * The range a number is compared against — written down by a person, with
 * their name on it.
 *
 * This app ships no reference values and never will. It cannot say a blood
 * pressure is high; it can only say a number is outside the range Besa wrote
 * down on the twelfth of March after the doctor's appointment. One of those is
 * a fact about the family's own notes. The other is medical advice from a
 * phone, and this is not the software to be giving it.
 */
class ReadingRange extends Model
{
    protected $fillable = ['set_by', 'kind', 'low', 'high', 'source'];

    protected function casts(): array
    {
        return ['low' => 'float', 'high' => 'float'];
    }

    public function circle(): BelongsTo
    {
        return $this->belongsTo(Circle::class);
    }

    public function setter(): BelongsTo
    {
        return $this->belongsTo(User::class, 'set_by');
    }

    /** Null when there is nothing to say, which is most of the time. */
    public function verdictFor(?float $value): ?string
    {
        if ($value === null) {
            return null;
        }

        if ($this->low !== null && $value < $this->low) {
            return 'below';
        }

        if ($this->high !== null && $value > $this->high) {
            return 'above';
        }

        return null;
    }

    public function describe(): string
    {
        return match (true) {
            $this->low !== null && $this->high !== null => $this->trim($this->low).'–'.$this->trim($this->high),
            $this->high !== null => '≤ '.$this->trim($this->high),
            $this->low !== null => '≥ '.$this->trim($this->low),
            default => '—',
        };
    }

    private function trim(float $value): string
    {
        return rtrim(rtrim(number_format($value, 1, '.', ''), '0'), '.');
    }
}
