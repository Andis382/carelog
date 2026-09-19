<?php

/*
 * Çdo fjalë e përmbledhjes javore.
 *
 * Kjo është ajo që lexon vërtet personi që paguan, dhe është e vetmja gjë që
 * sheh gjithë javën. Tri rregulla, të mbajtura nga
 * tests/Unit/DigestToneTest.php në çdo gjuhë të dërguar:
 *
 *   1. Nuk fajëson kurrë. Një rubrikë bosh është "pa shënim", kurrë "e humbur".
 *   2. Nuk diagnostikon kurrë. Një numër është vetëm "jashtë intervalit që
 *      shkroi <emri>", me intervalin të cituar dhe një emër mbi të.
 *   3. Një javë e qetë raportohet si e qetë. Asgjë nuk zgjatet për të
 *      justifikuar një abonim.
 */

return [
    'heading' => ':name — :from deri :to',

    'meds' => 'Barnat: :recorded nga :due doza me shënim.',
    'meds_blank' => ':n dozë nuk ka asgjë të shkruar.|:n doza nuk kanë asgjë të shkruar.',
    'meds_extra' => ':n dozë e dytë u shënua, me arsye.|:n doza të dyta u shënuan, secila me arsye.',

    'readings' => 'Matjet:',
    'times' => ':n matje|:n matje',
    'outside_range' => 'jashtë intervalit që shkroi :who (:range)',

    'visits' => 'Takimet:',
    'incidents' => 'Për t\'u ditur:',

    'who' => 'Shkruar nga:',
    'supplies' => 'Për të blerë:',

    'quiet' => 'Javë e qetë. Nuk u shkrua asgjë, që zakonisht do të thotë se nuk kishte çfarë të shkruhej.',

    'footer' => 'Shkruar nga ato që shënuan njerëzit atje. Nuk është kartelë mjekësore dhe nuk jep këshilla mjekësore.',
];
