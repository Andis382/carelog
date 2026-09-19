<!DOCTYPE html>
<html lang="{{ str_replace('_', '-', app()->getLocale()) }}">
<head>
@include('partials.head')
<title>@yield('title') · {{ $circle->name }}</title>
</head>
<body>
<a class="skip" href="#main">{{ __('ui.a11y.skip') }}</a>

<header class="topbar">
    <a class="brand" href="{{ route('circle.today', $circle) }}">
        <span class="mark" aria-hidden="true"><x-icon name="hand-heart" size="16" /></span>
        {{ $circle->name }}
    </a>
    <div class="topbar-right">
        <span class="who">
            <strong>{{ auth()->user()->shortName() }}</strong>
            {{ auth()->user()->membershipOf($circle)?->roleLabel() }}
        </span>
        <a class="iconbtn" href="{{ route('circle.settings', $circle) }}" aria-label="{{ __('ui.nav.settings') }}"
           @if (request()->routeIs('circle.settings')) aria-current="page" @endif>
            <x-icon name="settings" size="22" />
        </a>
    </div>
</header>

<main class="page @yield('pageclass')" id="main" tabindex="-1">
    @include('partials.flash')
    @yield('content')
</main>

@php($short = $circle->supplies()->where('level', '!=', \App\Models\Supply::OK)->count())

<nav class="tabbar" aria-label="{{ __('ui.a11y.main_nav') }}">
    <a href="{{ route('circle.today', $circle) }}" @if (request()->routeIs('circle.today')) aria-current="page" @endif>
        <x-icon name="home" size="22" />
        <span class="label">{{ __('ui.nav.today') }}</span>
    </a>
    <a href="{{ route('meds.index', $circle) }}" @if (request()->routeIs('meds.*')) aria-current="page" @endif>
        <x-icon name="pill" size="22" />
        <span class="label">{{ __('ui.nav.meds') }}</span>
    </a>
    <a href="{{ route('log.index', $circle) }}" @if (request()->routeIs('log.*')) aria-current="page" @endif>
        <x-icon name="notepad" size="22" />
        <span class="label">{{ __('ui.nav.log') }}</span>
    </a>
    <a href="{{ route('rota.index', $circle) }}" @if (request()->routeIs('rota.*')) aria-current="page" @endif>
        <x-icon name="rota" size="22" />
        <span class="label">{{ __('ui.nav.rota') }}</span>
    </a>
    <a href="{{ route('supplies.index', $circle) }}" @if (request()->routeIs('supplies.*')) aria-current="page" @endif>
        <x-icon name="supplies" size="22" />
        <span class="label">{{ __('ui.nav.supplies') }}</span>
        @if ($short)
            <span class="count" aria-hidden="true">{{ $short }}</span>
            <span class="sr-only">, {{ trans_choice('ui.a11y.supplies_short', $short, ['count' => $short]) }}</span>
        @endif
    </a>
</nav>

@stack('scripts')
</body>
</html>
