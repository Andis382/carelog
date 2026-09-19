<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;

/** Who is on duty. A day, a person, a window. Nothing else. */
class Shift extends Model
{
    protected $fillable = ['user_id', 'on_date', 'from_time', 'to_time', 'note'];

    protected function casts(): array
    {
        return ['on_date' => 'date'];
    }

    public function circle(): BelongsTo
    {
        return $this->belongsTo(Circle::class);
    }

    public function user(): BelongsTo
    {
        return $this->belongsTo(User::class);
    }

    public function window(): string
    {
        if (! $this->from_time && ! $this->to_time) {
            return __('rota.all_day');
        }

        return substr((string) $this->from_time, 0, 5).'–'.substr((string) $this->to_time, 0, 5);
    }
}
