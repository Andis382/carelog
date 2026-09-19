@php($slot = $row['slot'])
@php($events = $row['events'])
@php($primary = $events->firstWhere('duplicates_id', null))
@php($extras = $events->filter->isExtra())

{{--
    One dose due. Three buttons until somebody answers, and after that a name
    and a minute — which is the whole thing a WhatsApp group loses four minutes
    after it is said, and the reason anybody installs this.
--}}
<div class="dose {{ $primary ? 'is-done' : '' }}">
    <span class="dose-time">
        <span class="hh">{{ $slot->time() }}</span>
    </span>

    <div class="dose-body">
        <div class="row between">
            <span class="dose-name grow">{{ $slot->medication->label() }}</span>
            @if ($primary)
                <x-tag :tone="$primary->tone()" :icon="$primary->icon()">{{ $primary->statusLabel() }}</x-tag>
            @endif
        </div>

        @if ($slot->medication->note)
            <span class="dose-note">{{ $slot->medication->note }}</span>
        @endif

        @if ($primary)
            <div class="dose-by">
                <span class="avatar">{{ $primary->recorder?->initials() }}</span>
                <span>{{ __('ui.meds.by_at', [
                    'who' => $primary->recorder?->shortName() ?? '—',
                    'time' => $primary->at->timezone($circle->timezone)->format('H:i'),
                ]) }}</span>
                @if ($primary->note)<span class="faint">· {{ $primary->note }}</span>@endif

                @if ((int) $primary->recorded_by === (int) auth()->id() && $primary->created_at->diffInMinutes(now()) < config('carelog.undo_minutes'))
                    <form method="post" action="{{ route('meds.undo', [$circle, $primary]) }}">
                        @csrf @method('DELETE')
                        <button class="linkbtn">{{ __('ui.meds.undo') }}</button>
                    </form>
                @endif
            </div>

            @foreach ($extras as $extra)
                <div class="dose-by">
                    <x-tag tone="warn" icon="warning">{{ __('ui.meds.extra') }}</x-tag>
                    <span class="avatar carer">{{ $extra->recorder?->initials() }}</span>
                    <span>{{ __('ui.meds.by_at', [
                        'who' => $extra->recorder?->shortName() ?? '—',
                        'time' => $extra->at->timezone($circle->timezone)->format('H:i'),
                    ]) }}</span>
                    @if ($extra->extra_reason)<span class="faint">· {{ $extra->extra_reason }}</span>@endif
                </div>
            @endforeach
        @else
            <div class="dose-buttons">
                @foreach ([['given', 'give', 'check'], ['skipped', 'skip', 'minus'], ['refused', 'refuse', 'prohibit']] as [$status, $class, $icon])
                    <form method="post" action="{{ route('meds.record', [$circle, $slot, $date->toDateString()]) }}">
                        @csrf
                        <input type="hidden" name="status" value="{{ $status }}">
                        <button class="dose-btn {{ $class }}">
                            <x-icon :name="$icon" size="16" />
                            {{ __('ui.meds.'.($status === 'given' ? 'give' : ($status === 'skipped' ? 'skip' : 'refuse'))) }}
                        </button>
                    </form>
                @endforeach
            </div>
        @endif
    </div>
</div>
