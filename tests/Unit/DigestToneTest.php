<?php

namespace Tests\Unit;

use PHPUnit\Framework\TestCase;

/**
 * What the weekly summary is allowed to say, enforced rather than described.
 *
 * The person who reads this is anxious, four countries away, and cannot check
 * anything. The person it is about is somebody's mother. Getting the tone
 * wrong here does real damage in both directions: a summary that sounds like a
 * diagnosis frightens a son in Munich at midnight, and one that sounds like a
 * report card turns the sister doing all the work into a suspect.
 */
class DigestToneTest extends TestCase
{
    /** Anything that reads as a medical judgement from a phone. */
    private const CLINICAL = [
        'diagnos', 'abnormal', 'dangerous', 'critical', 'urgent', 'emergency',
        'too high', 'too low', 'should see a doctor', 'symptom of',
        'diagnoz', 'anormal', 'rrezikshëm', 'kritik', 'urgjent', 'shumë i lartë',
    ];

    /** Anything that turns a blank box into an accusation. */
    private const BLAMING = [
        'missed', 'failed', 'neglect', 'forgot', 'nobody bothered', 'should have',
        'e humbur', 'dështoi', 'neglizhoi', 'harroi', 'duhej',
    ];

    /** Nothing here is for sale. */
    private const PROMOTIONAL = [
        'upgrade', 'premium', 'unlock', 'trial', 'subscribe',
        'abonohu', 'provë falas', 'ofertë',
    ];

    private function locales(): array
    {
        return array_map('basename', glob(dirname(__DIR__, 2).'/lang/*', GLOB_ONLYDIR));
    }

    private function strings(string $locale): array
    {
        $file = dirname(__DIR__, 2)."/lang/{$locale}/digest.php";
        $this->assertFileExists($file, "Every shipped language needs a digest.php: {$locale}");

        $loaded = require $file;
        $flat = [];
        array_walk_recursive($loaded, function ($value, $key) use (&$flat) {
            $flat[$key] = $value;
        });

        return $flat;
    }

    public function test_the_summary_never_sounds_like_a_diagnosis(): void
    {
        foreach ($this->locales() as $locale) {
            foreach ($this->strings($locale) as $key => $text) {
                foreach (self::CLINICAL as $word) {
                    $this->assertStringNotContainsStringIgnoringCase(
                        $word, $text,
                        "digest.{$key} in {$locale} reads as a medical judgement: \"{$text}\""
                    );
                }
            }
        }
    }

    public function test_the_summary_never_blames_the_person_doing_the_work(): void
    {
        foreach ($this->locales() as $locale) {
            foreach ($this->strings($locale) as $key => $text) {
                foreach (self::BLAMING as $word) {
                    $this->assertStringNotContainsStringIgnoringCase(
                        $word, $text,
                        "digest.{$key} in {$locale} blames somebody: \"{$text}\""
                    );
                }
            }
        }
    }

    public function test_the_summary_never_sells_anything(): void
    {
        foreach ($this->locales() as $locale) {
            foreach ($this->strings($locale) as $key => $text) {
                foreach (self::PROMOTIONAL as $word) {
                    $this->assertStringNotContainsStringIgnoringCase(
                        $word, $text,
                        "digest.{$key} in {$locale} is selling something: \"{$text}\""
                    );
                }
            }
        }
    }

    /**
     * A number is only ever outside a range a named person wrote down. The
     * sentence has to carry both the name and the range, or it becomes the app
     * having an opinion about somebody's blood pressure.
     */
    public function test_a_number_outside_a_range_always_cites_who_set_it(): void
    {
        foreach ($this->locales() as $locale) {
            $strings = $this->strings($locale);
            $this->assertArrayHasKey('outside_range', $strings);
            $this->assertStringContainsString(':who', $strings['outside_range'], "digest.outside_range in {$locale} must name who wrote the range");
            $this->assertStringContainsString(':range', $strings['outside_range'], "digest.outside_range in {$locale} must quote the range");
        }
    }

    /** It has to be able to say a week was quiet, without padding it out. */
    public function test_a_quiet_week_can_be_reported_as_quiet(): void
    {
        foreach ($this->locales() as $locale) {
            $this->assertNotSame('', trim($this->strings($locale)['quiet'] ?? ''));
        }
    }

    /** Every footer has to disclaim, in every language. */
    public function test_every_language_says_it_is_not_a_medical_record(): void
    {
        foreach ($this->locales() as $locale) {
            $footer = $this->strings($locale)['footer'] ?? '';
            $this->assertNotSame('', trim($footer));
            $this->assertMatchesRegularExpression(
                '/medical|mjek/i',
                $footer,
                "digest.footer in {$locale} must say it is not a medical record and gives no medical advice"
            );
        }
    }

    public function test_the_languages_have_not_drifted_apart(): void
    {
        $locales = $this->locales();
        $this->assertGreaterThan(1, count($locales));

        $reference = array_keys($this->strings($locales[0]));
        sort($reference);

        foreach (array_slice($locales, 1) as $locale) {
            $keys = array_keys($this->strings($locale));
            sort($keys);
            $this->assertSame($reference, $keys, "lang/{$locale}/digest.php does not match lang/{$locales[0]}/digest.php");
        }
    }
}
