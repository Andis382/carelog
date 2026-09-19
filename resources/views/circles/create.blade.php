@extends('layouts.plain')
@section('title', __('ui.circle.new'))
@section('pageclass', 'narrow')

@section('content')
<div class="page-head">
    <span class="micro">CareLog</span>
    <h1>{{ __('ui.circle.new') }}</h1>
    <p>{{ __('ui.circle.new_hint') }}</p>
</div>

<form method="post" action="{{ route('circles.store') }}" class="panel">
    @csrf
    <x-field name="name" :label="__('ui.circle.name')" required autofocus maxlength="80" />
    <div class="cols2">
        <x-field name="born_on" type="date" :label="__('ui.circle.born_on')" optional :max="now()->toDateString()" />
        <x-field name="city" :label="__('ui.circle.city')" optional maxlength="80" />
    </div>
    <x-field name="about" control="textarea" :label="__('ui.circle.about')" :hint="__('ui.circle.about_hint')" maxlength="2000" />
    <button class="btn block big"><x-icon name="check" size="20" />{{ __('ui.circle.create') }}</button>
</form>
@endsection
