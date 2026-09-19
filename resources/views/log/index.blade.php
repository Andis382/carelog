@extends('layouts.circle')
@section('title', __('ui.log.title'))

@section('content')
<div class="page-head">
    <span class="micro">{{ $date->locale(app()->getLocale())->translatedFormat('l j F') }}</span>
    <h1>{{ __('ui.log.title') }}</h1>
</div>

<div class="filters">
    @for ($back = 3; $back >= 0; $back--)
        @php($day = $circle->today()->copy()->subDays($back))
        <a class="filter" href="{{ route('log.index', [$circle, $day->toDateString()]) }}"
           @if ($day->isSameDay($date)) aria-current="true" @endif>
            {{ $back === 0 ? __('ui.common.today') : $day->locale(app()->getLocale())->translatedFormat('D j/n') }}
        </a>
    @endfor
</div>

<h2>{{ __('ui.log.add') }}</h2>
<form method="post" action="{{ route('log.store', $circle) }}" enctype="multipart/form-data" class="panel">
    @csrf
    <div class="cols2">
        <x-field name="kind" control="select" :label="__('ui.log.kind')" selected="note"
                 :options="collect(\App\Models\LogEntry::KINDS)->mapWithKeys(fn ($k) => [$k => __('entry.kind.'.$k)])->all()" />
        <x-field name="value" :label="__('ui.log.value')" optional maxlength="40" />
    </div>
    <x-field name="body" control="textarea" :label="__('ui.log.body')" maxlength="2000" />
    <x-field name="photo" type="file" :label="__('ui.log.photo')" :hint="__('ui.log.photo_hint')"
             accept="image/*" capture="environment" />
    <button class="btn block"><x-icon name="check" size="18" />{{ __('ui.log.save') }}</button>
</form>

<div class="panel flush">
    <div class="rows">
        @forelse ($entries as $entry)
            <div class="entry">
                <span class="glyph {{ $entry->tone() === 'warn' ? 'warn' : '' }}"><x-icon :name="$entry->icon()" size="19" /></span>
                <span class="entry-body">
                    <span class="entry-title">
                        {{ $entry->kindLabel() }}@if ($entry->valueLabel()) · {{ $entry->valueLabel() }}@endif
                    </span>
                    <span class="entry-sub">
                        {{ $entry->recorder?->shortName() }} · <span class="num">{{ $entry->at->timezone($circle->timezone)->format('H:i') }}</span>
                        @if ($entry->body)<br>{{ $entry->body }}@endif
                    </span>
                    @if ($entry->photo_path)
                        <img class="shot small" style="margin-top:var(--s2)"
                             src="{{ \Illuminate\Support\Facades\Storage::disk('public')->url($entry->photo_path) }}" alt="">
                    @endif
                </span>
                <span class="entry-side">
                    {{-- Only the person who wrote it may take it back. See InCircle. --}}
                    @if ((int) $entry->recorded_by === (int) auth()->id())
                        <form method="post" action="{{ route('log.destroy', [$circle, $entry]) }}">
                            @csrf @method('DELETE')
                            <button class="linkbtn danger">{{ __('ui.common.remove') }}</button>
                        </form>
                    @endif
                </span>
            </div>
        @empty
            <div class="empty">
                <x-icon name="notepad" size="36" />
                <p>{{ __('ui.log.none') }}</p>
            </div>
        @endforelse
    </div>
</div>

<h2>{{ __('ui.log.readings') }}</h2>
<div class="panel flush">
    <div class="rows">
        @foreach ($readings as $reading)
            @php($range = $ranges->get($reading->kind))
            @php($verdict = $range?->verdictFor($reading->value))
            <div class="entry">
                <span class="glyph {{ $verdict ? 'warn' : '' }}"><x-icon :name="$reading->icon()" size="19" /></span>
                <span class="entry-body">
                    <span class="entry-title">{{ $reading->kindLabel() }} <span class="num">{{ $reading->display() }}</span></span>
                    <span class="entry-sub">
                        {{ $reading->recorder?->shortName() }} · <span class="num">{{ $reading->at->timezone($circle->timezone)->format('H:i') }}</span>
                        @if ($verdict && $range)
                            <br>{{ __('ui.log.outside', ['who' => $range->setter?->shortName() ?? '—', 'range' => $range->describe()]) }}
                        @endif
                    </span>
                </span>
            </div>
        @endforeach
    </div>

    <details class="more" style="margin:0;border:0;border-top:1px solid var(--line);border-radius:0;box-shadow:none">
        <summary>{{ __('ui.log.add_reading') }}</summary>
        <div class="inner">
            <form method="post" action="{{ route('readings.store', $circle) }}">
                @csrf
                <x-field name="kind" control="select" :label="__('ui.log.reading_kind')" selected="bp"
                         :options="collect(\App\Models\Reading::KINDS)->mapWithKeys(fn ($k) => [$k => __('reading.kind.'.$k)])->all()" />
                <div class="cols2">
                    <x-field name="value" type="number" :label="__('ui.log.value_1')" step="0.1" min="0" max="999" inputmode="decimal" required />
                    <x-field name="value_2" type="number" :label="__('ui.log.value_2')" step="0.1" min="0" max="999" inputmode="decimal" optional />
                </div>
                <x-field name="note" :label="__('ui.log.body')" optional maxlength="300" />
                <button class="btn block small"><x-icon name="check" size="15" />{{ __('ui.common.save') }}</button>
            </form>
        </div>
    </details>
</div>

{{--
    The range comes from a person, not from this software. Every judgement
    anywhere in the app cites whoever wrote it down.
--}}
<details class="more">
    <summary><x-icon name="shield" size="18" />{{ __('ui.log.range') }}</summary>
    <div class="inner">
        <p class="hint">{{ __('ui.log.range_hint') }}</p>
        <form method="post" action="{{ route('ranges.store', $circle) }}">
            @csrf
            <x-field name="kind" control="select" :label="__('ui.log.reading_kind')" selected="bp"
                     :options="collect(\App\Models\Reading::KINDS)->mapWithKeys(fn ($k) => [$k => __('reading.kind.'.$k)])->all()" />
            <div class="cols2">
                <x-field name="low" type="number" :label="__('ui.log.range_low')" step="0.1" min="0" max="999" inputmode="decimal" optional />
                <x-field name="high" type="number" :label="__('ui.log.range_high')" step="0.1" min="0" max="999" inputmode="decimal" optional />
            </div>
            <x-field name="source" :label="__('ui.log.range_source')" :hint="__('ui.log.range_source_hint')" maxlength="120" optional />
            <button class="btn block small"><x-icon name="check" size="15" />{{ __('ui.log.set_range') }}</button>
        </form>

        @if ($ranges->isNotEmpty())
            <dl class="spec" style="margin-top:var(--s4)">
                @foreach ($ranges as $range)
                    <div>
                        <dt>{{ __('reading.kind.'.$range->kind) }}</dt>
                        <dd>
                            <span class="num">{{ $range->describe() }}</span>
                            <span class="note">{{ $range->setter?->shortName() }}@if ($range->source) · {{ $range->source }}@endif</span>
                        </dd>
                    </div>
                @endforeach
            </dl>
        @endif
    </div>
</details>
@endsection
