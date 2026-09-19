<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

/**
 * One circle per person being cared for, and everything hangs off it.
 *
 * The shape worth explaining is `dose_events`. A dose is not a boolean on a
 * medication; it is an event on one scheduled slot on one date, carrying who
 * recorded it and when. That is what makes the double-dose question askable:
 * "Ana marked this given at 08:10 — is this a different dose?" A checkbox
 * cannot ask that, and a missed or doubled dose is the thing this whole
 * product exists to prevent.
 *
 * Nothing here ever computes a dose, a schedule change or a warning. It
 * records what people did.
 */
return new class extends Migration
{
    public function up(): void
    {
        Schema::table('users', function (Blueprint $table) {
            $table->string('phone')->nullable()->after('email');
            $table->string('locale', 5)->default('sq');
            $table->string('timezone')->default('Europe/Tirane');
            $table->timestamp('last_seen_at')->nullable();
        });

        // The person being cared for. Named after them, because that is how
        // every family already refers to the whole arrangement.
        Schema::create('circles', function (Blueprint $table) {
            $table->id();
            $table->string('name');
            $table->date('born_on')->nullable();
            $table->string('city')->nullable();
            $table->string('timezone')->default('Europe/Tirane');
            $table->text('about')->nullable();          // allergies, the doctor's name, the door code
            $table->string('join_code', 32)->unique();
            $table->timestamps();
        });

        Schema::create('circle_members', function (Blueprint $table) {
            $table->id();
            $table->foreignId('circle_id')->constrained()->cascadeOnDelete();
            $table->foreignId('user_id')->constrained()->cascadeOnDelete();
            // family  : a relative, near or far
            // carer   : paid help. The log is her proof of work.
            // payer   : whoever the weekly summary is written for
            $table->string('role')->default('family');
            $table->boolean('gets_digest')->default(false);
            $table->timestamp('joined_at')->nullable();
            $table->timestamps();

            $table->unique(['circle_id', 'user_id']);
        });

        Schema::create('medications', function (Blueprint $table) {
            $table->id();
            $table->foreignId('circle_id')->constrained()->cascadeOnDelete();
            $table->string('name');
            $table->string('strength')->nullable();     // "5 mg", written by the family, never derived
            $table->string('form')->default('tablet');  // tablet | drops | injection | patch | other
            $table->text('note')->nullable();           // "with food", copied off the box
            $table->boolean('active')->default(true);
            $table->unsignedSmallInteger('sort')->default(0);
            $table->timestamps();
        });

        // A time of day a medication is due, and on which weekdays.
        Schema::create('medication_slots', function (Blueprint $table) {
            $table->id();
            $table->foreignId('medication_id')->constrained()->cascadeOnDelete();
            $table->time('at_time');
            $table->string('weekdays', 7)->default('1234567');   // iso weekday digits
            $table->timestamps();

            $table->index(['medication_id', 'at_time']);
        });

        Schema::create('dose_events', function (Blueprint $table) {
            $table->id();
            $table->foreignId('medication_slot_id')->constrained()->cascadeOnDelete();
            $table->foreignId('recorded_by')->constrained('users');
            $table->date('on_date');
            $table->string('status');                   // given | skipped | refused
            $table->timestamp('at');                    // when it actually happened
            $table->text('note')->nullable();

            // Set only when somebody knowingly recorded a second dose for a
            // slot that already had one. It is never silent and never deleted:
            // it is the audit trail for exactly the event that matters most.
            $table->text('extra_reason')->nullable();
            $table->foreignId('duplicates_id')->nullable()->constrained('dose_events')->nullOnDelete();

            $table->timestamps();
            $table->index(['medication_slot_id', 'on_date']);
        });

        // Blood pressure, sugar, temperature, weight. A number and who took it.
        Schema::create('readings', function (Blueprint $table) {
            $table->id();
            $table->foreignId('circle_id')->constrained()->cascadeOnDelete();
            $table->foreignId('recorded_by')->constrained('users');
            $table->string('kind');                     // bp | glucose | temperature | weight | pulse | oxygen
            $table->decimal('value', 6, 1);
            $table->decimal('value_2', 6, 1)->nullable(); // diastolic
            $table->timestamp('at');
            $table->text('note')->nullable();
            $table->timestamps();

            $table->index(['circle_id', 'kind', 'at']);
        });

        /**
         * The range a reading is compared against — written down by a member of
         * the family, with their name on it. The app never supplies one and
         * never judges a number on its own; it can only ever say "outside the
         * range Besa wrote down", which is a fact rather than medical advice.
         */
        Schema::create('reading_ranges', function (Blueprint $table) {
            $table->id();
            $table->foreignId('circle_id')->constrained()->cascadeOnDelete();
            $table->foreignId('set_by')->constrained('users');
            $table->string('kind');
            $table->decimal('low', 6, 1)->nullable();
            $table->decimal('high', 6, 1)->nullable();
            $table->string('source')->nullable();        // "Dr Hoxha, 12/03"
            $table->timestamps();

            $table->unique(['circle_id', 'kind']);
        });

        // Meals, fluids, mood, sleep, a visit, a note, a photo of a prescription.
        Schema::create('log_entries', function (Blueprint $table) {
            $table->id();
            $table->foreignId('circle_id')->constrained()->cascadeOnDelete();
            $table->foreignId('recorded_by')->constrained('users');
            $table->string('kind');                      // meal | drink | mood | sleep | visit | note | incident
            $table->string('value')->nullable();         // "ate half", "good", "3 hours"
            $table->text('body')->nullable();
            $table->string('photo_path')->nullable();
            $table->timestamp('at');
            $table->timestamps();

            $table->index(['circle_id', 'at']);
        });

        // Who is on duty. Simple on purpose: a day, a person, a window.
        Schema::create('shifts', function (Blueprint $table) {
            $table->id();
            $table->foreignId('circle_id')->constrained()->cascadeOnDelete();
            $table->foreignId('user_id')->constrained()->cascadeOnDelete();
            $table->date('on_date');
            $table->time('from_time')->nullable();
            $table->time('to_time')->nullable();
            $table->text('note')->nullable();
            $table->timestamps();

            $table->index(['circle_id', 'on_date']);
        });

        // Nappies, gloves, the blue pills. Running out is its own emergency.
        Schema::create('supplies', function (Blueprint $table) {
            $table->id();
            $table->foreignId('circle_id')->constrained()->cascadeOnDelete();
            $table->foreignId('updated_by')->nullable()->constrained('users')->nullOnDelete();
            $table->string('name');
            $table->string('level')->default('ok');      // ok | low | out
            $table->timestamp('level_set_at')->nullable();
            $table->unsignedSmallInteger('sort')->default(0);
            $table->timestamps();
        });

        // What was sent to the member who is far away and paying for this.
        Schema::create('digests', function (Blueprint $table) {
            $table->id();
            $table->foreignId('circle_id')->constrained()->cascadeOnDelete();
            $table->date('week_start');
            $table->text('body');
            $table->timestamp('sent_at')->nullable();
            $table->timestamps();

            $table->unique(['circle_id', 'week_start']);
        });
    }

    public function down(): void
    {
        foreach (['digests', 'supplies', 'shifts', 'log_entries', 'reading_ranges',
            'readings', 'dose_events', 'medication_slots', 'medications',
            'circle_members', 'circles'] as $table) {
            Schema::dropIfExists($table);
        }

        Schema::table('users', function (Blueprint $table) {
            $table->dropColumn(['phone', 'locale', 'timezone', 'last_seen_at']);
        });
    }
};
