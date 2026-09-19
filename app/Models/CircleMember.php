<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;

class CircleMember extends Model
{
    /** A relative, near or far. */
    public const FAMILY = 'family';

    /** Paid help. For her the log is proof of work, which is the whole reason it gets filled in. */
    public const CARER = 'carer';

    /** Whoever the weekly summary is written for — usually the child abroad. */
    public const PAYER = 'payer';

    public const ROLES = [self::FAMILY, self::CARER, self::PAYER];

    protected $fillable = ['user_id', 'role', 'gets_digest', 'joined_at'];

    protected function casts(): array
    {
        return ['gets_digest' => 'bool', 'joined_at' => 'datetime'];
    }

    public function circle(): BelongsTo
    {
        return $this->belongsTo(Circle::class);
    }

    public function user(): BelongsTo
    {
        return $this->belongsTo(User::class);
    }

    public function roleLabel(): string
    {
        return __('role.'.$this->role);
    }

    public function icon(): string
    {
        return match ($this->role) {
            self::CARER => 'hand-heart',
            self::PAYER => 'star',
            default => 'user',
        };
    }
}
