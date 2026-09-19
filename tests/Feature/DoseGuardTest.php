<?php

namespace Tests\Feature;

use App\Models\Circle;
use App\Models\CircleMember;
use App\Models\DoseEvent;
use App\Models\MedicationSlot;
use App\Models\User;
use App\Services\MedicationBoard;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Carbon;
use Tests\TestCase;

/**
 * The one rule that makes this product worth installing, pinned.
 *
 * A double dose is the failure mode this whole app exists to prevent, and it
 * is exactly the failure mode that leaves no trace: nobody writes down the
 * pill they gave, so nobody can tell it was given twice. Every branch of the
 * guard is held here.
 */
class DoseGuardTest extends TestCase
{
    use RefreshDatabase;

    private Circle $circle;
    private User $ana;
    private User $besa;
    private MedicationSlot $slot;
    private MedicationBoard $board;

    protected function setUp(): void
    {
        parent::setUp();

        $this->board = app(MedicationBoard::class);
        $this->ana = $this->person('Ana Berisha', 'ana@example.test');
        $this->besa = $this->person('Besa Krasniqi', 'besa@example.test');

        $this->circle = Circle::create(['name' => 'Nexhmije', 'timezone' => 'Europe/Tirane']);
        $this->circle->members()->create(['user_id' => $this->ana->id, 'role' => CircleMember::CARER, 'joined_at' => now()]);
        $this->circle->members()->create(['user_id' => $this->besa->id, 'role' => CircleMember::FAMILY, 'joined_at' => now()]);

        $medication = $this->circle->medications()->create(['name' => 'Concor', 'strength' => '5 mg', 'form' => 'tablet', 'active' => true]);
        $this->slot = $medication->slots()->create(['at_time' => '08:00', 'weekdays' => '1234567']);
    }

    private function person(string $name, string $email): User
    {
        return User::create([
            'name' => $name, 'email' => $email, 'password' => 'secret-secret',
            'locale' => 'en', 'timezone' => 'Europe/Tirane',
        ]);
    }

    private function today(): Carbon
    {
        return $this->circle->today();
    }

    public function test_a_first_dose_is_simply_written(): void
    {
        $result = $this->board->record($this->slot, $this->today(), $this->ana, DoseEvent::GIVEN);

        $this->assertNotNull($result['event']);
        $this->assertNull($result['conflict']);
        $this->assertSame((int) $this->ana->id, (int) $result['event']->recorded_by);
    }

    /** The heart of it: the second person is stopped and told who and when. */
    public function test_a_second_person_is_stopped_and_told_who_answered(): void
    {
        $this->board->record($this->slot, $this->today(), $this->ana, DoseEvent::GIVEN);

        $second = $this->board->record($this->slot, $this->today(), $this->besa, DoseEvent::GIVEN);

        $this->assertNull($second['event'], 'nothing may be written while a human has not been asked');
        $this->assertNotNull($second['conflict']);
        $this->assertSame('Ana Berisha', $second['conflict']->recorder->name);
        $this->assertSame(1, DoseEvent::count(), 'the conflicting attempt must not leave a row behind');
    }

    public function test_a_second_dose_is_kept_when_somebody_says_so_and_says_why(): void
    {
        $first = $this->board->record($this->slot, $this->today(), $this->ana, DoseEvent::GIVEN)['event'];

        $second = $this->board->record(
            slot: $this->slot,
            date: $this->today(),
            by: $this->besa,
            status: DoseEvent::GIVEN,
            extraReason: 'Ana was at the shop and did not know.',
        );

        $this->assertNotNull($second['event']);
        $this->assertTrue($second['event']->isExtra());
        $this->assertSame((int) $first->id, (int) $second['event']->duplicates_id);
        $this->assertSame('Ana was at the shop and did not know.', $second['event']->extra_reason);

        // Both stay. A tidy screen is worth less than a true one.
        $this->assertSame(2, DoseEvent::count());
    }

    /**
     * A skip is an answer too. "Ana marked this skipped" is exactly as worth
     * stopping for as "Ana gave it" — arguably more.
     */
    public function test_a_skip_also_stops_the_next_person(): void
    {
        $this->board->record($this->slot, $this->today(), $this->ana, DoseEvent::SKIPPED);

        $second = $this->board->record($this->slot, $this->today(), $this->besa, DoseEvent::GIVEN);

        $this->assertNull($second['event']);
        $this->assertSame(DoseEvent::SKIPPED, $second['conflict']->status);
    }

    public function test_the_same_slot_on_a_different_day_is_a_different_dose(): void
    {
        $this->board->record($this->slot, $this->today()->copy()->subDay(), $this->ana, DoseEvent::GIVEN);

        $today = $this->board->record($this->slot, $this->today(), $this->besa, DoseEvent::GIVEN);

        $this->assertNotNull($today['event']);
        $this->assertNull($today['conflict']);
    }

    public function test_an_extra_dose_does_not_itself_block_the_next_one(): void
    {
        // The guard looks at the primary event, so a chain of extras cannot
        // accidentally make the second one authoritative.
        $this->board->record($this->slot, $this->today(), $this->ana, DoseEvent::GIVEN);
        $this->board->record($this->slot, $this->today(), $this->besa, DoseEvent::GIVEN, extraReason: 'second');

        $third = $this->board->record($this->slot, $this->today(), $this->besa, DoseEvent::GIVEN);

        $this->assertNull($third['event']);
        $this->assertSame((int) $this->ana->id, (int) $third['conflict']->recorded_by);
    }

    /** Your own slip, for half an hour. Never anybody else's, and never later. */
    public function test_only_your_own_and_only_while_it_is_plausibly_a_slip(): void
    {
        $event = $this->board->record($this->slot, $this->today(), $this->ana, DoseEvent::GIVEN)['event'];

        $this->assertTrue($this->board->canUndo($event, $this->ana));
        $this->assertFalse($this->board->canUndo($event, $this->besa), "another member must not be able to erase the carer's record");

        $event->created_at = now()->subHours(3);
        $this->assertFalse($this->board->canUndo($event, $this->ana));
    }

    public function test_the_board_only_shows_doses_due_on_that_weekday(): void
    {
        $monday = Carbon::parse('2026-09-21', 'Europe/Tirane');   // a Monday
        $tuesday = $monday->copy()->addDay();

        $this->slot->update(['weekdays' => '1']);   // Mondays only

        $this->assertCount(1, $this->board->board($this->circle, $monday)->flatten(1));
        $this->assertCount(0, $this->board->board($this->circle, $tuesday)->flatten(1));
    }

    public function test_a_stopped_medicine_leaves_the_board_but_keeps_its_history(): void
    {
        $this->board->record($this->slot, $this->today(), $this->ana, DoseEvent::GIVEN);
        $this->slot->medication->update(['active' => false]);

        $this->assertCount(0, $this->board->board($this->circle, $this->today())->flatten(1));
        $this->assertSame(1, DoseEvent::count());
    }

    /**
     * Coverage is reported, never scored. A blank slot may be a missed dose or
     * a tired daughter forgetting to tick a box, and the software cannot tell.
     */
    public function test_coverage_counts_what_is_recorded_and_never_guesses_the_rest(): void
    {
        $this->board->record($this->slot, $this->today()->copy()->subDays(2), $this->ana, DoseEvent::GIVEN);
        $this->board->record($this->slot, $this->today()->copy()->subDays(1), $this->ana, DoseEvent::SKIPPED);
        // Nothing at all for today.

        $coverage = $this->board->coverage($this->circle, $this->today()->copy()->subDays(2), $this->today());

        $this->assertSame(3, $coverage['due']);
        $this->assertSame(2, $coverage['recorded']);
        $this->assertSame(1, $coverage['given']);
        $this->assertSame(1, $coverage['missed_record']);
    }

    public function test_the_screen_asks_rather_than_writing_when_two_people_collide(): void
    {
        $this->actingAs($this->ana)->post(
            route('meds.record', [$this->circle, $this->slot, $this->today()->toDateString()]),
            ['status' => 'given'],
        )->assertRedirect();

        $response = $this->actingAs($this->besa)->post(
            route('meds.record', [$this->circle, $this->slot, $this->today()->toDateString()]),
            ['status' => 'given'],
        );

        $response->assertSessionHas('conflict');
        $this->assertSame(1, DoseEvent::count());
    }
}
