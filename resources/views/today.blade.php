@extends('layouts.circle')
@section('title', __('ui.today.title'))

@section('content')
<div class="page-head">
    <span class="micro">{{ $today->locale(app()->getLocale())->translatedFormat('l j F Y') }}</span>
    <h1>{{ $circle->name }}</h1>
    @if ($circle->about)<p>{{ $circle->about }}</p>@endif
</div>

{{-- Who is around. The first thing anybody wants to know. --}}
<div class="panel lead-primary">
    <div class="row between">
        <span class="micro">{{ __('ui.today.on_duty') }}</span>
        <a class="linkbtn" href="{{ route('rota.index', $circle) }}">{{ __('ui.nav.rota') }}</a>
    </div>
    @if ($shifts->isNotEmpty())
        <div class="row" style="margin-top:var(--s3)">
            @foreach ($shifts as $shift)
                <span class="row">
                    <span class="avatar big">{{ $shift->user->initials() }}</span>
                    <span>
                        <strong>{{ $shift->user->shortName() }}</strong>
                        <span class="small faint">{{ $shift->window() }}</span>
                    </span>
                </span>
            @endforeach
        </div>
    @else
        <p class="small muted">{{ __('ui.today.nobody_on_duty') }}</p>
    @endif
</div>

@if ($coverage['due'] > 0)
    <div class="counters">
        <a class="counter {{ $coverage['missed_record'] ? 'is-warn' : 'is-ok' }}" href="{{ route('meds.index', $circle) }}">
            <span class="micro">{{ __('ui.today.doses_left') }}</span>
            <span class="v">{{ $coverage['missed_record'] }}</span>
        </a>
        <a class="counter is-ok" href="{{ route('meds.index', $circle) }}">
            <span class="micro">{{ __('ui.today.doses_done') }}</span>
            <span class="v">{{ $coverage['recorded'] }}<span class="unit"> / {{ $coverage['due'] }}</span></span>
        </a>
    </div>
@endif

@php($pending = $board->flatten(1)->filter(fn ($row) => $row['events']->whereNull('duplicates_id')->isEmpty()))
@if ($pending->isNotEmpty())
    <h2>{{ __('ui.today.doses_left') }}</h2>
    <div class="panel flush">
        @foreach ($pending->take(4) as $row)
            @include('partials.dose', ['row' => $row, 'circle' => $circle, 'date' => $today])
        @endforeach
    </div>
    <a class="btn ghost block" href="{{ route('meds.index', $circle) }}">
        <x-icon name="pill" size="18" />{{ __('ui.today.all_meds') }}
    </a>
@endif

@if ($readings->isNotEmpty())
    <h2>{{ __('ui.today.latest') }}</h2>
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
                            {{ $reading->recorder?->shortName() }} · <span class="num">{{ $reading->at->timezone($circle->timezone)->format('d/m H:i') }}</span>
                            @if ($verdict && $range)
                                <br>{{ __('ui.log.outside', ['who' => $range->setter?->shortName() ?? '—', 'range' => $range->describe()]) }}
                            @endif
                        </span>
                    </span>
                </div>
            @endforeach
        </div>
        <div class="panel-foot">
            <a class="btn ghost small" href="{{ route('log.index', $circle) }}">
                <x-icon name="heartbeat" size="15" />{{ __('ui.log.add_reading') }}
            </a>
        </div>
    </div>
@endif

@if ($short->isNotEmpty())
    <h2>{{ __('ui.today.to_buy') }}</h2>
    <div class="panel">
        <div class="actions">
            @foreach ($short as $supply)
                <x-tag :tone="$supply->tone() === 'bad' ? 'bad' : 'warn'" :icon="$supply->icon()">
                    {{ $supply->name }} · {{ $supply->levelLabel() }}
                </x-tag>
            @endforeach
        </div>
    </div>
@endif

<h2>{{ __('ui.today.recent') }}</h2>
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
                </span>
            </div>
        @empty
            <div class="empty">
                <x-icon name="notepad" size="36" />
                <p>{{ __('ui.today.nothing_yet') }}</p>
            </div>
        @endforelse
    </div>
    <div class="panel-foot">
        <a class="btn small" href="{{ route('log.index', $circle) }}">
            <x-icon name="plus" size="15" />{{ __('ui.log.add') }}
        </a>
        <span class="grow"></span>
        <a class="btn ghost small" href="{{ route('digest.show', $circle) }}">
            <x-icon name="file" size="15" />{{ __('ui.nav.digest') }}
        </a>
        <a class="btn ghost small" href="{{ route('people.index', $circle) }}">
            <x-icon name="users" size="15" />{{ __('ui.nav.people') }}
        </a>
    </div>
</div>
@endsection
