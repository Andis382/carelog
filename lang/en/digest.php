<?php

/*
 * Every word of the weekly summary.
 *
 * This is what the person paying for the app actually reads, and it is the
 * only thing they see all week. Three rules, held by
 * tests/Unit/DigestToneTest.php against every shipped language:
 *
 *   1. It never blames. A blank slot is "not recorded", never "missed". This
 *      software cannot tell a missed dose from a tired daughter forgetting to
 *      tick a box, and guessing wrong turns the one person doing the work into
 *      a suspect — which is how the log stops being filled in.
 *   2. It never diagnoses. A number is only ever "outside the range <name>
 *      wrote down", with the range quoted and a person's name on it.
 *   3. A quiet week is reported as quiet. Nothing is padded out to justify a
 *      subscription, because the weeks when nothing happens are the good ones.
 */

return [
    'heading' => ':name — :from to :to',

    'meds' => 'Medicines: :recorded of :due doses recorded.',
    'meds_blank' => ':n dose has nothing written against it.|:n doses have nothing written against them.',
    'meds_extra' => ':n second dose was recorded, with a reason.|:n second doses were recorded, each with a reason.',

    'readings' => 'Readings:',
    'times' => ':n reading|:n readings',
    'outside_range' => 'outside the range :who wrote down (:range)',

    'visits' => 'Appointments:',
    'incidents' => 'Worth knowing about:',

    'who' => 'Written down by:',
    'supplies' => 'To buy:',

    'quiet' => 'A quiet week. Nothing was written down, which usually means there was nothing to write down.',

    'footer' => 'Written from what the people there recorded. It is not a medical record and gives no medical advice.',
];
