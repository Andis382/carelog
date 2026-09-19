<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;

class Reading extends Model
{
    /** Blood pressure is the one with two numbers; everything else has one. */
    public const KINDS = ['bp', 'glucose', 'temperature', 'pulse', 'oxygen', 'weight'];

    protected $fillable = ['recorded_by', 'kind', 'value', 'value_2', 'at', 'note'];

    protected function casts(): array
    {
        return ['at' => 'datetime', 'value' => 'float', 'value_2' => 'float'];
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
        return __('reading.kind.'.$this->kind);
    }

    public function unit(): string
    {
        return __('reading.unit.'.$this->kind);
    }

    public function display(): string
    {
        $number = $this->kind === 'bp'
            ? $this->trim($this->value).'/'.$this->trim($this->value_2)
            : $this->trim($this->value);

        return $number.' '.$this->unit();
    }

    public function icon(): string
    {
        return match ($this->kind) {
            'bp', 'pulse' => 'heartbeat',
            'temperature' => 'thermometer',
            'glucose' => 'drink',
            'oxygen' => 'pulse',
            default => 'notepad',
        };
    }

    private function trim(?float $value): string
    {
        if ($value === null) {
            return '—';
        }

        return rtrim(rtrim(number_format($value, 1, '.', ''), '0'), '.');
    }
}
