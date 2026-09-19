@extends('layouts.circle')
@section('title', __('ui.meds.title'))

@section('content')
<div class="page-head">
    <span class="micro">{{ $date->locale(app()->getLocale())->translatedFormat('l j F') }}</span>
    <h1>{{ __('ui.meds.title') }}</h1>
</div>

<div class="filters">
    @for ($back = 2; $back >= 0; $back--)
        @php($day = $circle->today()->copy()->subDays($back))
        <a class="filter" href="{{ route('meds.index', [$circle, $day->toDateString()]) }}"
           @if ($day->isSameDay($date)) aria-current="true" @endif>
            {{ $back === 0 ? __('ui.common.today') : ($back === 1 ? __('ui.common.yesterday') : $day->locale(app()->getLocale())->translatedFormat('D j/n')) }}
        </a>
    @endfor
    <a class="filter" href="{{ route('meds.manage', $circle) }}">
        <x-icon name="edit" size="15" />{{ __('ui.meds.manage') }}
    </a>
</div>

{{--
    The question this product exists to ask, asked at the only moment it is
    worth asking: while a thumb is over the button and a box of pills is in the
    other hand.
--}}
@if (session('conflict'))
    @php($c = session('conflict'))
    <div class="conflict" role="alert" tabindex="-1" id="conflict">
        <h2>{{ __('ui.conflict.title') }}</h2>
        <p>{{ __('ui.conflict.body', ['who' => $c['who'], 'what' => mb_strtolower($c['what']), 'time' => $c['at']]) }}</p>
        <form method="post" action="{{ route('meds.record', [$circle, $c['slot'], $date->toDateString()]) }}">
            @csrf
            <input type="hidden" name="status" value="{{ $c['status'] }}">
            <x-field name="extra_reason" :label="__('ui.conflict.reason_label')" :hint="__('ui.conflict.reason_hint')"
                     required maxlength="300" autofocus />
            <div class="actions">
                <button class="btn"><x-icon name="check" size="18" />{{ __('ui.conflict.confirm') }}</button>
                <a class="btn ghost" href="{{ route('meds.index', [$circle, $date->toDateString()]) }}">{{ __('ui.conflict.cancel') }}</a>
            </div>
        </form>
    </div>
    @push('scripts')
    <script>document.getElementById('conflict')?.focus();</script>
    @endpush
@endif

@if ($coverage['due'] > 0)
    <div class="counters">
        <div class="counter is-ok">
            <span class="micro">{{ __('ui.today.doses_done') }}</span>
            <span class="v">{{ $coverage['recorded'] }}<span class="unit"> / {{ $coverage['due'] }}</span></span>
        </div>
        <div class="counter {{ $coverage['missed_record'] ? 'is-warn' : '' }}">
            <span class="micro">{{ __('ui.today.doses_left') }}</span>
            <span class="v">{{ $coverage['missed_record'] }}</span>
        </div>
    </div>
@endif

@forelse ($board as $part => $rows)
    <div class="dose-group">
        <span class="micro">{{ __('ui.meds.part.'.$part) }}</span>
        <div class="panel flush">
            @foreach ($rows as $row)
                @include('partials.dose', ['row' => $row, 'circle' => $circle, 'date' => $date])
            @endforeach
        </div>
    </div>
@empty
    <div class="panel">
        <div class="empty">
            <x-icon name="pill" size="40" />
            <p>{{ $circle->medications()->exists() ? __('ui.meds.none_today') : __('ui.meds.none_at_all') }}</p>
        </div>
        <a class="btn block" href="{{ route('meds.manage', $circle) }}">
            <x-icon name="plus" size="18" />{{ __('ui.meds.add') }}
        </a>
    </div>
@endforelse
@endsection
