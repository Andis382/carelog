@extends('layouts.circle')
@section('title', __('ui.meds.manage'))

@section('content')
<div class="page-head">
    <span class="micro">{{ $circle->name }}</span>
    <h1>{{ __('ui.meds.manage') }}</h1>
</div>

<div class="panel flush">
    <div class="rows">
        @forelse ($medications as $medication)
            <div class="entry">
                <span class="glyph"><x-icon :name="$medication->icon()" size="19" /></span>
                <span class="entry-body">
                    <span class="entry-title">{{ $medication->label() }}</span>
                    <span class="entry-sub">
                        {{ $medication->formLabel() }}
                        @if ($medication->slots->isNotEmpty())
                            · <span class="num">{{ $medication->slots->map->time()->implode(', ') }}</span>
                        @endif
                        @if ($medication->note)<br>{{ $medication->note }}@endif
                    </span>
                </span>
                <span class="entry-side">
                    @unless ($medication->active)
                        <x-tag icon="archive">{{ __('ui.meds.retired') }}</x-tag>
                    @endunless
                    <details class="more" style="margin:0;border:0;box-shadow:none;background:none">
                        <summary style="min-height:32px;padding:0;font-size:var(--t-xs)">{{ __('ui.meds.save') }}</summary>
                        <div class="inner" style="padding:var(--s3) 0 0">
                            <form method="post" action="{{ route('meds.update', [$circle, $medication]) }}">
                                @csrf @method('PUT')
                                <input type="hidden" name="name" value="{{ $medication->name }}">
                                <input type="hidden" name="strength" value="{{ $medication->strength }}">
                                <input type="hidden" name="form" value="{{ $medication->form }}">
                                <input type="hidden" name="note" value="{{ $medication->note }}">
                                @foreach ($medication->slots as $slot)
                                    <input type="hidden" name="times[]" value="{{ $slot->time() }}">
                                @endforeach
                                <label class="check">
                                    <input type="checkbox" name="active" value="1" @checked($medication->active)>
                                    <span class="what">{{ __('ui.meds.active') }}</span>
                                </label>
                                <button class="btn small block">{{ __('ui.meds.save') }}</button>
                            </form>
                            <form method="post" action="{{ route('meds.destroy', [$circle, $medication]) }}" style="margin-top:var(--s2)">
                                @csrf @method('DELETE')
                                <button class="linkbtn danger">{{ __('ui.meds.remove') }}</button>
                            </form>
                        </div>
                    </details>
                </span>
            </div>
        @empty
            <div class="empty">
                <x-icon name="pill" size="36" />
                <p>{{ __('ui.meds.none_at_all') }}</p>
            </div>
        @endforelse
    </div>
</div>

<h2>{{ __('ui.meds.add') }}</h2>
<form method="post" action="{{ route('meds.store', $circle) }}" class="panel">
    @csrf
    <div class="cols2">
        <x-field name="name" :label="__('ui.meds.name')" required autocomplete="off" />
        <x-field name="strength" :label="__('ui.meds.strength')" :hint="__('ui.meds.strength_hint')" optional maxlength="40" />
    </div>

    <x-field name="form" control="select" :label="__('ui.meds.form')" selected="tablet"
             :options="collect(\App\Models\Medication::FORMS)->mapWithKeys(fn ($f) => [$f => __('med.form.'.$f)])->all()" />

    <x-field name="note" :label="__('ui.meds.note')" :hint="__('ui.meds.note_hint')" optional maxlength="300" />

    <div class="field">
        <span class="name">{{ __('ui.meds.times') }}</span>
        <div class="cols2">
            @foreach ([0, 1, 2, 3] as $i)
                <input type="time" name="times[]" aria-label="{{ __('ui.meds.times') }} {{ $i + 1 }}">
            @endforeach
        </div>
        <p class="hint">{{ __('ui.meds.times_hint') }}</p>
    </div>

    <x-field name="weekdays" :label="__('ui.meds.weekdays')" value="1234567"
             :hint="__('ui.meds.every_day')" pattern="[1-7]{1,7}" class="mono" />

    <button class="btn block"><x-icon name="plus" size="18" />{{ __('ui.meds.add') }}</button>
</form>

<p class="small faint">{{ __('ui.meds.retire_note') }}</p>
@endsection
