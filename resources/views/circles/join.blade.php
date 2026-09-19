@extends('layouts.plain')
@section('title', __('ui.circle.join_title', ['name' => $circle->name]))
@section('pageclass', 'narrow')

@section('content')
<div class="page-head">
    <span class="micro">CareLog</span>
    <h1>{{ __('ui.circle.join_title', ['name' => $circle->name]) }}</h1>
    <p>{{ __('ui.circle.join_body', ['name' => $circle->name]) }}</p>
</div>

<form method="post" action="{{ route('circles.join.store', $circle->join_code) }}" class="panel">
    @csrf
    <x-field name="role" control="select" :label="__('ui.circle.join_as')" selected="family"
             :options="collect(\App\Models\CircleMember::ROLES)->mapWithKeys(fn ($r) => [$r => __('role.'.$r)])->all()" />
    <button class="btn block big"><x-icon name="check" size="20" />{{ __('ui.circle.join') }}</button>
</form>
@endsection
