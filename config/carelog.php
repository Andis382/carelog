<?php

return [
    'locales' => [
        'sq' => 'Shqip',
        'en' => 'English',
    ],

    'defaults' => [
        'locale' => env('CARELOG_DEFAULT_LOCALE', 'sq'),
        'timezone' => env('CARELOG_DEFAULT_TIMEZONE', 'Europe/Tirane'),
    ],

    /*
    |---------------------------------------------------------------------------
    | How long you can take back your own entry
    |---------------------------------------------------------------------------
    | A slip is a slip for about half an hour. After that a dose record stands,
    | which is what makes it worth anything to the person who wrote it — a paid
    | carer's log is her account of what she did, and an account that can be
    | quietly revised later is not an account.
    */
    'undo_minutes' => env('CARELOG_UNDO_MINUTES', 30),

    /*
    |---------------------------------------------------------------------------
    | What this software will not do
    |---------------------------------------------------------------------------
    | Listed here because it is a product decision, not an oversight, and the
    | next person to open this file should find it before they add a feature.
    |
    |   It stores no reference ranges of its own. A range is written down by a
    |   named member of the family and every judgement cites them.
    |
    |   It never computes a dose, a schedule change, or an interaction warning.
    |
    |   It sends nothing to anyone outside the circle, and there is no table in
    |   this schema that could join one circle's health data to another's.
    */
];
