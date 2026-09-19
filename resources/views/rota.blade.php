@extends('layouts.circle')
@section('title', __('ui.rota.title'))

@section('content')
<div class="page-head">
    <span class="micro">{{ $circle->name }}</span>
    <h1>{{ __('ui.rota.title') }}</h1>
    <p>{{ __('ui.rota.subtitle') }}</p>
</div>

<div class="panel flush">
    <div class="rows">
        @foreach ($days as $day)
            @php($onDay = $shifts->get($day->toDateString(), collect()))
            <div class="entry">
                <span class="glyph {{ $day->isSameDay($from) ? '' : '' }}">
                    <span class="num" style="font-size:var(--t-xs);font-weight:700">{{ $day->format('j') }}</span>
                </span>
                <span class="entry-body">
                    <span class="entry-title">
                        {{ $day->locale(app()->getLocale())->translatedFormat('l') }}
                        @if ($day->isSameDay($from))
                            <x-tag tone="info" icon="check">{{ __('ui.rota.today') }}</x-tag>
                        @endif
                    </span>
                    <span class="entry-sub">
                        @forelse ($onDay as $shift)
                            {{ $shift->user->shortName() }} · {{ $shift->window() }}@if ($shift->note) — {{ $shift->note }}@endif
                            @if (! $loop->last)<br>@endif
                        @empty
                            {{ __('ui.rota.nobody') }}
                        @endforelse
                    </span>
                </span>
                <span class="entry-side">
                    @if ($onDay->isNotEmpty())
                        <span class="faces">
                            @foreach ($onDay->take(3) as $shift)
                                <span class="avatar">{{ $shift->user->initials() }}</span>
                            @endforeach
                        </span>
                        @foreach ($onDay as $shift)
                            <form method="post" action="{{ route('rota.destroy', [$circle, $shift]) }}">
                                @csrf @method('DELETE')
                                <button class="linkbtn danger">{{ $shift->user->shortName() }} · {{ __('ui.rota.remove') }}</button>
                            </form>
                        @endforeach
                    @endif
                </span>
            </div>
        @endforeach
    </div>
</div>

<h2>{{ __('ui.rota.add') }}</h2>
<form method="post" action="{{ route('rota.store', $circle) }}" class="panel">
    @csrf
    <div class="cols2">
        <x-field name="user_id" control="select" :label="__('ui.rota.who')"
                 :options="$people->mapWithKeys(fn ($m) => [$m->user_id => $m->user->name])->all()" />
        <x-field name="on_date" type="date" :label="__('ui.rota.day')" :value="$from->toDateString()" required />
    </div>
    <div class="cols2">
        <x-field name="from_time" type="time" :label="__('ui.rota.from')" optional />
        <x-field name="to_time" type="time" :label="__('ui.rota.to')" optional />
    </div>
    <x-field name="note" :label="__('ui.rota.note')" optional maxlength="200" />
    <button class="btn block"><x-icon name="plus" size="18" />{{ __('ui.rota.save') }}</button>
</form>
@endsection
