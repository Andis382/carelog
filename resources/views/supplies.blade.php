@extends('layouts.circle')
@section('title', __('ui.supplies.title'))

@section('content')
<div class="page-head">
    <span class="micro">{{ $circle->name }}</span>
    <h1>{{ __('ui.supplies.title') }}</h1>
    <p>{{ __('ui.supplies.subtitle') }}</p>
</div>

<div class="panel flush">
    <div class="rows">
        @forelse ($supplies as $supply)
            <div class="entry">
                <span class="glyph {{ $supply->needsBuying() ? 'warn' : '' }}">
                    <x-icon :name="$supply->icon()" size="19" />
                </span>
                <span class="entry-body">
                    <span class="entry-title">{{ $supply->name }}</span>
                    <span class="entry-sub">
                        @if ($supply->level_set_at)
                            {{ __('ui.supplies.set_by', [
                                'who' => $supply->updater?->shortName() ?? '—',
                                'when' => $supply->level_set_at->timezone($circle->timezone)->diffForHumans(),
                            ]) }}
                        @endif
                    </span>
                    <span class="actions" style="margin-top:var(--s2)">
                        @foreach (\App\Models\Supply::LEVELS as $level)
                            <form method="post" action="{{ route('supplies.level', [$circle, $supply]) }}">
                                @csrf
                                <input type="hidden" name="level" value="{{ $level }}">
                                <button class="btn {{ $supply->level === $level ? '' : 'ghost' }} small">
                                    {{ __('supply.level.'.$level) }}
                                </button>
                            </form>
                        @endforeach
                    </span>
                </span>
                <span class="entry-side">
                    <form method="post" action="{{ route('supplies.destroy', [$circle, $supply]) }}">
                        @csrf @method('DELETE')
                        <button class="linkbtn danger">{{ __('ui.common.remove') }}</button>
                    </form>
                </span>
            </div>
        @empty
            <div class="empty">
                <x-icon name="supplies" size="36" />
                <p>{{ __('ui.supplies.none') }}</p>
            </div>
        @endforelse
    </div>
</div>

<form method="post" action="{{ route('supplies.store', $circle) }}" class="panel">
    @csrf
    <x-field name="name" :label="__('ui.supplies.name')" required maxlength="60" />
    <button class="btn block"><x-icon name="plus" size="18" />{{ __('ui.supplies.add') }}</button>
</form>
@endsection
