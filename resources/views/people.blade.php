@extends('layouts.circle')
@section('title', __('ui.people.title'))

@section('content')
<div class="page-head">
    <span class="micro">{{ $circle->name }}</span>
    <h1>{{ __('ui.people.title') }}</h1>
    <p>{{ __('ui.people.subtitle') }}</p>
</div>

<div class="panel lead-primary">
    <span class="micro">{{ __('ui.people.invite') }}</span>
    <p class="mono small breakable" id="invite-link">{{ $link }}</p>
    <div class="actions">
        <button type="button" class="btn small" data-copy-target="invite-link">
            <x-icon name="copy" size="15" /><span>{{ __('ui.people.copy') }}</span>
        </button>
    </div>
    <p class="hint">{{ __('ui.people.invite_hint') }}</p>
</div>

<div class="panel flush">
    <div class="rows">
        @foreach ($members as $member)
            <div class="entry">
                <span class="avatar big {{ $member->role }}">{{ $member->user->initials() }}</span>
                <span class="entry-body">
                    <span class="entry-title">
                        {{ $member->user->name }}
                        @if ((int) $member->user_id === (int) auth()->id())
                            <span class="faint small">({{ __('ui.people.you') }})</span>
                        @endif
                    </span>
                    <span class="entry-sub">
                        {{ $member->roleLabel() }}
                        @if ($member->joined_at) · {{ __('ui.people.joined', ['when' => $member->joined_at->diffForHumans()]) }} @endif
                    </span>
                    <form method="post" action="{{ route('people.update', [$circle, $member]) }}" class="row" style="margin-top:var(--s3)">
                        @csrf
                        <label class="sr-only" for="role-{{ $member->id }}">{{ __('ui.people.role') }}</label>
                        <select id="role-{{ $member->id }}" name="role" style="max-width:14rem">
                            @foreach (\App\Models\CircleMember::ROLES as $role)
                                <option value="{{ $role }}" @selected($member->role === $role)>{{ __('role.'.$role) }}</option>
                            @endforeach
                        </select>
                        <label class="check" style="margin:0;flex:1 1 12rem">
                            <input type="checkbox" name="gets_digest" value="1" @checked($member->gets_digest)>
                            <span class="what">{{ __('ui.people.digest') }}</span>
                        </label>
                        <button class="btn small">{{ __('ui.people.save') }}</button>
                    </form>
                </span>
                <span class="entry-side">
                    @if ($member->gets_digest)
                        <x-tag tone="warn" icon="star">{{ __('ui.nav.digest') }}</x-tag>
                    @endif
                </span>
            </div>
        @endforeach
    </div>
</div>

<details class="more">
    <summary style="color:var(--bad)"><x-icon name="prohibit" size="18" />{{ __('ui.people.leave') }}</summary>
    <div class="inner">
        <p class="small">{{ __('ui.people.leave_note') }}</p>
        <form method="post" action="{{ route('people.leave', $circle) }}">
            @csrf @method('DELETE')
            <button class="btn danger block">{{ __('ui.people.leave') }}</button>
        </form>
    </div>
</details>
@endsection

@push('scripts')
<script>
document.querySelectorAll('[data-copy-target]').forEach(function (btn) {
  btn.addEventListener('click', async function () {
    var text = document.getElementById(btn.dataset.copyTarget).textContent.trim();
    try { await navigator.clipboard.writeText(text); }
    catch (e) {
      var ta = document.createElement('textarea');
      ta.value = text; document.body.appendChild(ta); ta.select();
      document.execCommand('copy'); ta.remove();
    }
    var label = btn.querySelector('span');
    var original = label.textContent;
    label.textContent = @json(__('ui.people.copied'));
    setTimeout(function () { label.textContent = original; }, 1500);
  });
});
</script>
@endpush
