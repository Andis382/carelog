@extends('layouts.plain')
@section('title', __('ui.tagline'))
@section('description', 'A shared daily log for everyone looking after one elderly parent: medicines with a name and a time on every dose, readings, a rota, supplies, and a weekly summary for whoever is far away.')

@section('topnav')
    <a class="btn ghost small" href="{{ route('login') }}">{{ __('ui.nav.login') }}</a>
    <a class="btn small" href="{{ route('register') }}">{{ __('ui.nav.register') }}</a>
@endsection

@section('content')
<div class="hero">
    <span class="micro">CareLog</span>
    <h1>{{ __('ui.tagline') }}</h1>
    <p class="lead">
        When a parent gets old, the care spreads over three to six people: a sister nearby, a husband,
        a paid carer, a neighbour, a son in Germany. It is coordinated in a WhatsApp group, where
        "did she get the eight o'clock one?" scrolls out of sight in four minutes and nobody can
        answer it two days later.
    </p>
</div>

<div class="flow">
    <div>
        <span class="n">01</span>
        <h3>Every dose has a name on it</h3>
        <p>Three buttons: given, skipped, refused. Afterwards the row shows who answered and at what minute — which is the one thing a chat cannot hold.</p>
    </div>
    <div>
        <span class="n">02</span>
        <h3>It asks before a double dose</h3>
        <p>If somebody already answered, nothing is saved. You are told who and when, and only a person can decide whether what you are holding is genuinely a second one.</p>
    </div>
    <div>
        <span class="n">03</span>
        <h3>A week's worth for whoever is away</h3>
        <p>On Sunday, a plain summary: doses recorded, readings, appointments, who did the work, what to buy. No alarms, no diagnosis, no padding.</p>
    </div>
</div>

<div class="example">
    <span class="micro">Sunday evening</span>
    <div class="draft" style="margin-top:var(--s2)">Nexhmije — 14/09 to 20/09

Medicines: 40 of 42 doses recorded.
  2 doses have nothing written against them.
  1 second dose was recorded, with a reason.

Readings:
  Blood pressure: 148/92 mmHg (5 readings) — outside the range Besa wrote down (110–140)

Appointments:
  17/09 — Dr Hoxha, changed the evening tablet

Written down by: Ana (31), Besa (12), Luan (3)
To buy: gloves (running low)

Written from what the people there recorded. It is not a medical record and gives no medical advice.</div>
</div>

<h2>What it refuses to do</h2>
<ul class="lead">
    <li><strong>It gives no medical advice.</strong> It ships no reference ranges, computes no dose, and warns about no interaction. It can say a number is outside the range somebody in the family wrote down, with their name and the date they wrote it. That is a fact about your own notes. Anything more would be a phone practising medicine.</li>
    <li><strong>It never blames anybody.</strong> A blank slot is reported as "not recorded", never as "missed" — the software cannot tell the difference, and guessing wrong turns the person doing the work into a suspect.</li>
    <li><strong>Nobody can edit anybody else's entry.</strong> What a paid carer writes is her account of what she did, with her name on it. That is what makes it worth filling in, and what makes it worth anything if she is ever accused of something.</li>
</ul>

<h2>Why this and not a medication app</h2>
<p class="lead">
    Medisafe and MyTherapy remind the patient — the one person in this situation who cannot use them.
    Jointly and Caring Village coordinate a circle but were not built for daily vitals or for a paid
    carer. Agency software costs what agencies pay. What is missing is an operational shift log shared
    between a hired carer and a family, in the family's language, with the person paying for it living
    in another country.
</p>

<a class="btn big block" href="{{ route('register') }}">{{ __('ui.nav.register') }}</a>
<p class="center small faint">Open source. Health data stays in the circle and is never sold, shared or joined to anything.</p>
@endsection
