<?php

namespace Database\Seeders;

use App\Models\Circle;
use App\Models\CircleMember;
use App\Models\DoseEvent;
use App\Models\LogEntry;
use App\Models\Reading;
use App\Models\Supply;
use App\Models\User;
use App\Services\WeeklyDigest;
use Illuminate\Database\Seeder;
use Illuminate\Support\Carbon;

/**
 * One family, three weeks in.
 *
 * Seeded with the situations the product is actually for: a carer who records
 * nearly everything, a daughter who records some of it, a son abroad who
 * records none of it and reads the summary, two blank slots, and one genuine
 * double dose with a reason attached. A tidy seed would hide every one of them.
 */
class DatabaseSeeder extends Seeder
{
    public function run(): void
    {
        $ana = $this->user('Ana Berisha', 'ana@carelog.test', 'sq');      // the paid carer
        $besa = $this->user('Besa Krasniqi', 'demo@carelog.test', 'sq');  // the daughter nearby
        $luan = $this->user('Luan Krasniqi', 'luan@carelog.test', 'en');  // the son in Munich

        Circle::where('name', 'Nexhmije')->delete();

        $circle = Circle::create([
            'name' => 'Nexhmije',
            'born_on' => '1941-03-02',
            'city' => 'Tiranë',
            'timezone' => 'Europe/Tirane',
            'about' => 'Alergji ndaj penicilinës. Dr. Hoxha, 069 22 33 444. Kodi i derës 4417. Fqinja Drita ka çelës.',
            'join_code' => 'nexhmije01',
        ]);

        foreach ([[$besa, CircleMember::FAMILY, false], [$ana, CircleMember::CARER, false], [$luan, CircleMember::PAYER, true]] as [$user, $role, $digest]) {
            $circle->members()->create([
                'user_id' => $user->id,
                'role' => $role,
                'gets_digest' => $digest,
                'joined_at' => now()->subWeeks(3),
            ]);
        }

        // name, strength, form, note, times
        $catalogue = [
            ['Concor', '5 mg', 'tablet', 'Me ushqim, në mëngjes.', ['08:00']],
            ['Metformin', '850 mg', 'tablet', 'Pas vaktit.', ['08:00', '20:00']],
            ['Atorvastatin', '20 mg', 'tablet', null, ['20:00']],
            ['Pika për sytë', null, 'drops', 'Një pikë në secilin sy.', ['09:00', '21:00']],
            ['Paracetamol', '500 mg', 'tablet', 'Vetëm nëse ka dhimbje.', ['14:00']],
        ];

        $slots = collect();
        foreach ($catalogue as $i => [$name, $strength, $form, $note, $times]) {
            $medication = $circle->medications()->create([
                'name' => $name,
                'strength' => $strength,
                'form' => $form,
                'note' => $note,
                'active' => true,
                'sort' => ($i + 1) * 10,
            ]);

            foreach ($times as $time) {
                $slots->push($medication->slots()->create(['at_time' => $time, 'weekdays' => '1234567']));
            }
        }

        mt_srand(20260920);
        $today = Carbon::now('Europe/Tirane')->startOfDay();

        for ($back = 20; $back >= 0; $back--) {
            $date = $today->copy()->subDays($back);

            foreach ($slots as $slot) {
                // Today is deliberately left half done, so the demo opens on
                // the screen the whole product exists for.
                if ($back === 0 && (int) substr($slot->time(), 0, 2) >= 14) {
                    continue;
                }

                // Two slots in three weeks have nothing written against them.
                if (in_array($back, [11, 6], true) && $slot->time() === '20:00') {
                    continue;
                }

                $who = mt_rand(0, 100) < 70 ? $ana : $besa;
                $status = mt_rand(0, 100) < 96 ? DoseEvent::GIVEN : (mt_rand(0, 1) ? DoseEvent::SKIPPED : DoseEvent::REFUSED);
                [$h, $m] = array_map('intval', explode(':', $slot->time()));

                $event = $slot->events()->create([
                    'recorded_by' => $who->id,
                    'on_date' => $date->toDateString(),
                    'status' => $status,
                    'at' => $date->copy()->setTime($h, $m + mt_rand(-10, 25)),
                ]);

                // Four days ago, two people reached for the same box. This is
                // the event the product exists to make visible.
                if ($back === 4 && $slot->time() === '08:00' && $slot->medication->name === 'Concor') {
                    $slot->events()->create([
                        'recorded_by' => $besa->id,
                        'on_date' => $date->toDateString(),
                        'status' => DoseEvent::GIVEN,
                        'at' => $date->copy()->setTime(8, 40),
                        'extra_reason' => 'Ana ishte te dyqani, nuk e dinte. E pyeta pas dhe ia thashë mjekut.',
                        'duplicates_id' => $event->id,
                    ]);
                }
            }

            // Readings a few times a week.
            if ($back % 3 === 0) {
                $circle->readings()->create([
                    'recorded_by' => $ana->id,
                    'kind' => 'bp',
                    'value' => mt_rand(128, 158),
                    'value_2' => mt_rand(76, 96),
                    'at' => $date->copy()->setTime(9, mt_rand(0, 50)),
                ]);
            }
            if ($back % 4 === 0) {
                $circle->readings()->create([
                    'recorded_by' => $ana->id,
                    'kind' => 'glucose',
                    'value' => mt_rand(55, 95) / 10,
                    'at' => $date->copy()->setTime(7, mt_rand(0, 45)),
                ]);
            }

            // The ordinary texture of a day.
            foreach ([['meal', 'half', 13], ['mood', 'ok', 17]] as [$kind, $value, $hour]) {
                if (mt_rand(0, 100) < 70) {
                    $circle->entries()->create([
                        'recorded_by' => $ana->id,
                        'kind' => $kind,
                        'value' => $value,
                        'at' => $date->copy()->setTime($hour, mt_rand(0, 55)),
                    ]);
                }
            }
        }

        $circle->entries()->create([
            'recorded_by' => $besa->id,
            'kind' => 'visit',
            'body' => 'Dr. Hoxha. Ndryshoi tabletën e mbrëmjes, tha ta masim tensionin çdo ditë për dy javë.',
            'at' => $today->copy()->subDays(3)->setTime(11, 20),
        ]);

        $circle->entries()->create([
            'recorded_by' => $ana->id,
            'kind' => 'incident',
            'body' => 'U rrëzua lehtë në banjë, pa lëndim. I thashë Besës menjëherë.',
            'at' => $today->copy()->subDays(8)->setTime(19, 5),
        ]);

        // The range comes from a person, with a name and a date.
        $circle->ranges()->create([
            'set_by' => $besa->id,
            'kind' => 'bp',
            'low' => 110,
            'high' => 140,
            'source' => 'Dr. Hoxha, 12 mars',
        ]);

        foreach ([['Pelena', Supply::LOW], ['Doreza', Supply::OK], ['Concor', Supply::OK], ['Shirita për sheqerin', Supply::OUT]] as $i => [$name, $level]) {
            $circle->supplies()->create([
                'name' => $name,
                'level' => $level,
                'updated_by' => $ana->id,
                'level_set_at' => now()->subDays($i),
                'sort' => ($i + 1) * 10,
            ]);
        }

        foreach (range(0, 6) as $ahead) {
            $day = $today->copy()->addDays($ahead);
            $circle->shifts()->create([
                'user_id' => $day->isWeekend() ? $besa->id : $ana->id,
                'on_date' => $day->toDateString(),
                'from_time' => $day->isWeekend() ? null : '08:00',
                'to_time' => $day->isWeekend() ? null : '16:00',
            ]);
        }

        app(WeeklyDigest::class)->build($circle, $today->copy()->startOfWeek());
        app(WeeklyDigest::class)->build($circle, $today->copy()->startOfWeek()->subWeek());

        $this->command?->info('Seeded Nexhmije: demo@carelog.test / password (Besa, the daughter)');
        $this->command?->info('Also ana@carelog.test (the carer) and luan@carelog.test (the son abroad).');
    }

    private function user(string $name, string $email, string $locale): User
    {
        return User::updateOrCreate(['email' => $email], [
            'name' => $name,
            'password' => 'password',
            'locale' => $locale,
            'timezone' => $locale === 'en' ? 'Europe/Berlin' : 'Europe/Tirane',
            'last_seen_at' => now(),
        ]);
    }
}
