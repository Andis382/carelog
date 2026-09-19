@extends('layouts.plain')
@section('title', __('ui.circle.mine'))
@section('pageclass', 'narrow')

@section('topnav')
    <form method="post" action="{{ route('logout') }}">
        @csrf <button class="btn ghost small">{{ __('ui.nav.logout') }}</button>
    </form>
@endsection

@section('content')
<div class="page-head">
    <span class="micro">CareLog</span>
    <h1>{{ __('ui.circle.mine') }}</h1>
</div>

<div class="panel flush">
    <div class="rows">
        @forelse ($circles as $circle)
            <a class="entry" href="{{ route('circle.today', $circle) }}">
                <span class="glyph"><x-icon name="hand-heart" size="19" /></span>
                <span class="entry-body">
                    <span class="entry-title">{{ $circle->name }}</span>
                    <span class="entry-sub">
                        {{ __('role.'.$circle->pivot->role) }}
                        @if ($circle->city) · {{ $circle->city }} @endif
                    </span>
                </span>
                <span class="entry-side"><x-icon name="chevron" size="18" /></span>
            </a>
        @empty
            <div class="empty">
                <x-icon name="hand-heart" size="40" />
                <p>{{ __('ui.circle.none') }}</p>
            </div>
        @endforelse
    </div>
</div>

<a class="btn block big" href="{{ route('circles.create') }}">
    <x-icon name="plus" size="20" />{{ __('ui.circle.new') }}
</a>
@endsection
