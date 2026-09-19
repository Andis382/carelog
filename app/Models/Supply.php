<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;

/**
 * Nappies, gloves, the blue pills.
 *
 * Three levels and no quantity, on purpose. Nobody counts nappies, and a field
 * that asks for a number is a field that stays empty. "Running low" said a day
 * early is worth more than an exact count said never.
 */
class Supply extends Model
{
    public const OK = 'ok';
    public const LOW = 'low';
    public const OUT = 'out';

    public const LEVELS = [self::OK, self::LOW, self::OUT];

    protected $fillable = ['name', 'level', 'updated_by', 'level_set_at', 'sort'];

    protected function casts(): array
    {
        return ['level_set_at' => 'datetime'];
    }

    public function circle(): BelongsTo
    {
        return $this->belongsTo(Circle::class);
    }

    public function updater(): BelongsTo
    {
        return $this->belongsTo(User::class, 'updated_by');
    }

    public function levelLabel(): string
    {
        return __('supply.level.'.$this->level);
    }

    public function tone(): string
    {
        return match ($this->level) {
            self::OUT => 'bad',
            self::LOW => 'warn',
            default => 'ok',
        };
    }

    public function icon(): string
    {
        return match ($this->level) {
            self::OUT => 'warning',
            self::LOW => 'hourglass',
            default => 'check',
        };
    }

    public function needsBuying(): bool
    {
        return $this->level !== self::OK;
    }
}
