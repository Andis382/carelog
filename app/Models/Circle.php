<?php

namespace App\Models;

use Carbon\CarbonInterface;
use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsToMany;
use Illuminate\Database\Eloquent\Relations\HasMany;
use Illuminate\Support\Str;

/**
 * One person being cared for, and everyone around them.
 *
 * Named after the elder, because that is how every family already refers to
 * the whole arrangement: not "the care plan", but "mum".
 */
class Circle extends Model
{
    use HasFactory;

    protected $fillable = ['name', 'born_on', 'city', 'timezone', 'about', 'join_code'];

    protected function casts(): array
    {
        return ['born_on' => 'date'];
    }

    protected static function booted(): void
    {
        static::creating(function (Circle $circle) {
            $circle->join_code ??= Str::lower(Str::random(10));
        });
    }

    public function members(): HasMany
    {
        return $this->hasMany(CircleMember::class);
    }

    public function users(): BelongsToMany
    {
        return $this->belongsToMany(User::class, 'circle_members')->withPivot('role', 'gets_digest');
    }

    public function medications(): HasMany
    {
        return $this->hasMany(Medication::class)->orderBy('sort')->orderBy('name');
    }

    public function readings(): HasMany
    {
        return $this->hasMany(Reading::class);
    }

    public function ranges(): HasMany
    {
        return $this->hasMany(ReadingRange::class);
    }

    public function entries(): HasMany
    {
        return $this->hasMany(LogEntry::class);
    }

    public function shifts(): HasMany
    {
        return $this->hasMany(Shift::class);
    }

    public function supplies(): HasMany
    {
        return $this->hasMany(Supply::class)->orderBy('sort')->orderBy('name');
    }

    public function digests(): HasMany
    {
        return $this->hasMany(Digest::class);
    }

    public function today(): CarbonInterface
    {
        return now($this->timezone ?: config('app.timezone'))->startOfDay();
    }

    public function age(): ?int
    {
        return $this->born_on?->age;
    }
}
