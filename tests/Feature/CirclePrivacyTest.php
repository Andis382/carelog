<?php

namespace Tests\Feature;

use App\Models\Circle;
use App\Models\CircleMember;
use App\Models\DoseEvent;
use App\Models\User;
use App\Services\WeeklyDigest;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Tests\TestCase;

/**
 * Who can see what, and who can change what.
 *
 * This is health data about somebody's mother, held by a group of people who
 * do not all get on. The boundaries are the product.
 */
class CirclePrivacyTest extends TestCase
{
    use RefreshDatabase;

    private function person(string $name, string $email, string $locale = 'en'): User
    {
        return User::create([
            'name' => $name, 'email' => $email, 'password' => 'secret-secret',
            'locale' => $locale, 'timezone' => 'Europe/Tirane',
        ]);
    }

    private function circle(User $owner, string $name = 'Nexhmije'): Circle
    {
        $circle = Circle::create(['name' => $name, 'timezone' => 'Europe/Tirane']);
        $circle->members()->create(['user_id' => $owner->id, 'role' => CircleMember::FAMILY, 'joined_at' => now()]);

        return $circle;
    }

    public function test_a_circle_you_are_not_in_does_not_exist(): void
    {
        $mine = $this->person('Besa', 'besa@example.test');
        $stranger = $this->person('Someone Else', 'nope@example.test');
        $circle = $this->circle($mine);

        // 404 and not 403: "you may not see this" still confirms the circle is
        // there and that somebody you could name is in it.
        $this->actingAs($stranger)->get(route('circle.today', $circle))->assertNotFound();
        $this->actingAs($stranger)->get(route('meds.index', $circle))->assertNotFound();
        $this->actingAs($stranger)->get(route('digest.show', $circle))->assertNotFound();
        $this->actingAs($stranger)->get(route('people.index', $circle))->assertNotFound();
    }

    public function test_a_guest_sees_nothing_at_all(): void
    {
        $besa = $this->person('Besa', 'besa2@example.test');
        $circle = $this->circle($besa);

        $this->get(route('circle.today', $circle))->assertRedirect(route('login'));
        $this->get(route('meds.index', $circle))->assertRedirect(route('login'));
    }

    /**
     * The carer's shield. What she writes is her account of what she did, and
     * the moment somebody else can quietly edit it, it stops being worth
     * writing.
     */
    public function test_nobody_can_remove_anybody_elses_entry(): void
    {
        $ana = $this->person('Ana', 'ana2@example.test');
        $besa = $this->person('Besa', 'besa3@example.test');
        $circle = $this->circle($besa);
        $circle->members()->create(['user_id' => $ana->id, 'role' => CircleMember::CARER, 'joined_at' => now()]);

        $entry = $circle->entries()->create([
            'recorded_by' => $ana->id,
            'kind' => 'note',
            'body' => 'Mbajti këmbët lart gjithë pasdite.',
            'at' => now(),
        ]);

        $this->actingAs($besa)->delete(route('log.destroy', [$circle, $entry]))->assertNotFound();
        $this->assertDatabaseHas('log_entries', ['id' => $entry->id]);

        $this->actingAs($ana)->delete(route('log.destroy', [$circle, $entry]))->assertRedirect();
        $this->assertDatabaseMissing('log_entries', ['id' => $entry->id]);
    }

    public function test_leaving_a_circle_leaves_your_entries_behind(): void
    {
        $ana = $this->person('Ana', 'ana3@example.test');
        $besa = $this->person('Besa', 'besa4@example.test');
        $circle = $this->circle($besa);
        $circle->members()->create(['user_id' => $ana->id, 'role' => CircleMember::CARER, 'joined_at' => now()]);

        $circle->entries()->create(['recorded_by' => $ana->id, 'kind' => 'note', 'body' => 'x', 'at' => now()]);

        $this->actingAs($ana)->delete(route('people.leave', $circle))->assertRedirect();

        $this->assertSame(1, $circle->entries()->count(), 'six months later somebody has to be able to see what was done');
        $this->assertSame(1, $circle->members()->count());
    }

    public function test_the_last_person_cannot_leave_the_circle_empty(): void
    {
        $besa = $this->person('Besa', 'besa5@example.test');
        $circle = $this->circle($besa);

        $this->actingAs($besa)->delete(route('people.leave', $circle));

        $this->assertSame(1, $circle->members()->count());
    }

    public function test_joining_by_link_puts_you_in_and_says_so_in_the_log(): void
    {
        $besa = $this->person('Besa', 'besa6@example.test');
        $luan = $this->person('Luan', 'luan@example.test');
        $circle = $this->circle($besa);

        $this->actingAs($luan)->post(route('circles.join.store', $circle->join_code), ['role' => 'payer'])
            ->assertRedirect(route('circle.today', $circle));

        $this->assertSame(2, $circle->members()->count());
        $this->assertTrue($circle->members()->where('user_id', $luan->id)->first()->gets_digest);

        // Everyone should be able to see who else can read this, and when they arrived.
        $this->assertSame(1, $circle->entries()->count());
    }

    public function test_a_wrong_join_code_is_not_a_hint(): void
    {
        $luan = $this->person('Luan', 'luan2@example.test');

        $this->actingAs($luan)->get(route('circles.join', 'not-a-real-code'))->assertNotFound();
    }

    public function test_a_medicine_from_another_circle_cannot_be_recorded(): void
    {
        $besa = $this->person('Besa', 'besa7@example.test');
        $other = $this->person('Other', 'other@example.test');

        $mine = $this->circle($besa, 'Nexhmije');
        $theirs = $this->circle($other, 'Someone else');

        $medication = $theirs->medications()->create(['name' => 'Their pill', 'form' => 'tablet', 'active' => true]);
        $slot = $medication->slots()->create(['at_time' => '08:00', 'weekdays' => '1234567']);

        $this->actingAs($besa)->post(
            route('meds.record', [$mine, $slot, $mine->today()->toDateString()]),
            ['status' => 'given'],
        )->assertNotFound();

        $this->assertSame(0, DoseEvent::count());
    }

    /** The summary follows whoever receives it, not whoever generated it. */
    public function test_the_weekly_summary_is_written_in_the_recipients_language(): void
    {
        $besa = $this->person('Besa', 'besa8@example.test', 'sq');
        $luan = $this->person('Luan', 'luan3@example.test', 'en');
        $circle = $this->circle($besa);
        $circle->members()->create([
            'user_id' => $luan->id, 'role' => CircleMember::PAYER,
            'gets_digest' => true, 'joined_at' => now(),
        ]);

        app()->setLocale('sq');
        $digest = app(WeeklyDigest::class)->build($circle, $circle->today()->startOfWeek());

        $this->assertStringContainsString('quiet week', strtolower($digest->body));
        $this->assertSame('sq', app()->getLocale(), 'the locale must be put back where it was found');
    }

    public function test_a_quiet_week_is_reported_as_quiet(): void
    {
        $besa = $this->person('Besa', 'besa9@example.test', 'en');
        $circle = $this->circle($besa);

        $digest = app(WeeklyDigest::class)->build($circle, $circle->today()->startOfWeek());

        $this->assertStringContainsString('quiet', strtolower($digest->body));
        $this->assertStringNotContainsStringIgnoringCase('missed', $digest->body);
    }

    public function test_the_summary_cites_whoever_wrote_the_range(): void
    {
        $besa = $this->person('Besa Krasniqi', 'besa10@example.test', 'en');
        $circle = $this->circle($besa);

        $circle->ranges()->create(['set_by' => $besa->id, 'kind' => 'bp', 'low' => 110, 'high' => 140, 'source' => 'Dr Hoxha']);
        $circle->readings()->create([
            'recorded_by' => $besa->id, 'kind' => 'bp',
            'value' => 165, 'value_2' => 98, 'at' => $circle->today()->startOfWeek()->addHours(9),
        ]);

        $body = app(WeeklyDigest::class)->build($circle, $circle->today()->startOfWeek())->body;

        $this->assertStringContainsString('Besa', $body);
        $this->assertStringContainsString('110–140', $body);
        // Never the app's own opinion.
        $this->assertStringNotContainsStringIgnoringCase('too high', $body);
        $this->assertStringNotContainsStringIgnoringCase('dangerous', $body);
    }
}
