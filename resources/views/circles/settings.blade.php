@extends('layouts.circle')
@section('title', __('ui.circle.settings'))
@section('pageclass', 'narrow')

@section('content')
<div class="page-head">
    <span class="micro">{{ $circle->name }}</span>
    <h1>{{ __('ui.circle.settings') }}</h1>
</div>

<form method="post" action="{{ route('circle.update', $circle) }}" class="panel">
    @csrf @method('PUT')
    <x-field name="name" :label="__('ui.circle.name')" :value="$circle->name" required maxlength="80" />
    <div class="cols2">
        <x-field name="born_on" type="date" :label="__('ui.circle.born_on')" :value="$circle->born_on?->toDateString()" optional :max="now()->toDateString()" />
        <x-field name="city" :label="__('ui.circle.city')" :value="$circle->city" optional maxlength="80" />
    </div>
    <x-field name="about" control="textarea" :label="__('ui.circle.about')" :hint="__('ui.circle.about_hint')" :value="$circle->about" maxlength="2000" />
    <x-field name="timezone" :label="__('ui.circle.timezone')" :value="$circle->timezone" required class="mono" />
    <button class="btn block"><x-icon name="check" size="18" />{{ __('ui.circle.save') }}</button>
</form>

<div class="panel">
    <div class="actions">
        <a class="btn ghost small" href="{{ route('people.index', $circle) }}">
            <x-icon name="users" size="15" />{{ __('ui.nav.people') }}
        </a>
        <a class="btn ghost small" href="{{ route('circles.index') }}">
            <x-icon name="hand-heart" size="15" />{{ __('ui.circle.mine') }}
        </a>
        <span class="grow"></span>
        <form method="post" action="{{ route('logout') }}">
            @csrf <button class="btn ghost small"><x-icon name="logout" size="15" />{{ __('ui.nav.logout') }}</button>
        </form>
    </div>
</div>
@endsection
