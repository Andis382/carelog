@extends('layouts.circle')
@section('title', __('ui.digest.title'))

@section('content')
<div class="page-head">
    <span class="micro">{{ __('ui.digest.week_of', ['from' => $start->format('d/m'), 'to' => $end->format('d/m')]) }}</span>
    <h1>{{ __('ui.digest.title') }}</h1>
    <p>{{ __('ui.digest.subtitle') }}</p>
</div>

<div class="filters">
    @foreach ([0, 1, 2, 3] as $back)
        @php($week = $circle->today()->copy()->startOfWeek()->subWeeks($back))
        <a class="filter" href="{{ route('digest.show', [$circle, $week->toDateString()]) }}"
           @if ($week->isSameDay($start)) aria-current="true" @endif>
            {{ $back === 0 ? __('ui.digest.this_week') : ($back === 1 ? __('ui.digest.last_week') : $week->format('d/m')) }}
        </a>
    @endforeach
</div>

<div class="panel">
    <span class="micro">{{ __('ui.digest.recipients') }}</span>
    @if ($recipients->isNotEmpty())
        <div class="row" style="margin-top:var(--s2)">
            @foreach ($recipients as $member)
                <span class="row">
                    <span class="avatar payer">{{ $member->user->initials() }}</span>
                    <span>{{ $member->user->shortName() }}</span>
                </span>
            @endforeach
        </div>
    @else
        <p class="small muted">{{ __('ui.digest.nobody') }}
            <a href="{{ route('people.index', $circle) }}">{{ __('ui.nav.people') }}</a>
        </p>
    @endif
</div>

{{--
    Shown verbatim. The page is a rendering of the wording, not the other way
    round: the only way anybody can tell whether what lands in Germany on a
    Sunday night is worth reading is to read exactly it.
--}}
<div class="panel">
    <div class="draft" id="digest-body">{{ $digest->body }}</div>
    <div class="actions">
        <button type="button" class="btn ghost small" data-copy-target="digest-body">
            <x-icon name="copy" size="15" /><span>{{ __('ui.digest.copy') }}</span>
        </button>
        <a class="btn ghost small" href="https://wa.me/?text={{ rawurlencode($digest->body) }}" target="_blank" rel="noopener">
            <x-icon name="chat" size="15" />{{ __('ui.digest.send') }}
        </a>
        <form method="post" action="{{ route('digest.rebuild', [$circle, $start->toDateString()]) }}">
            @csrf
            <button class="btn small"><x-icon name="retry" size="15" />{{ __('ui.digest.rebuild') }}</button>
        </form>
    </div>
</div>
@endsection

@push('scripts')
<script>
document.querySelectorAll('[data-copy-target]').forEach(function (btn) {
  btn.addEventListener('click', async function () {
    var text = document.getElementById(btn.dataset.copyTarget).textContent;
    try { await navigator.clipboard.writeText(text); }
    catch (e) {
      var ta = document.createElement('textarea');
      ta.value = text; document.body.appendChild(ta); ta.select();
      document.execCommand('copy'); ta.remove();
    }
    var label = btn.querySelector('span');
    var original = label.textContent;
    label.textContent = @json(__('ui.digest.copied'));
    setTimeout(function () { label.textContent = original; }, 1500);
  });
});
</script>
@endpush
