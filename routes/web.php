<?php

use App\Http\Controllers\Auth\AuthController;
use App\Http\Controllers\CircleController;
use App\Http\Controllers\DigestController;
use App\Http\Controllers\LogController;
use App\Http\Controllers\MedsController;
use App\Http\Controllers\PeopleController;
use App\Http\Controllers\RotaController;
use App\Http\Controllers\SuppliesController;
use Illuminate\Support\Facades\Route;

Route::get('/', [CircleController::class, 'landing'])->name('home');

Route::middleware('guest')->group(function () {
    Route::get('login', [AuthController::class, 'showLogin'])->name('login');
    Route::post('login', [AuthController::class, 'login']);
    Route::get('register', [AuthController::class, 'showRegister'])->name('register');
    Route::post('register', [AuthController::class, 'register']);
});

Route::post('logout', [AuthController::class, 'logout'])->name('logout');

Route::middleware('auth')->group(function () {
    Route::get('circles', [CircleController::class, 'index'])->name('circles.index');
    Route::get('circles/new', [CircleController::class, 'create'])->name('circles.create');
    Route::post('circles', [CircleController::class, 'store'])->name('circles.store');

    // Joining by link, which is how a carer and four relatives get in without
    // anybody administering anything.
    Route::get('join/{code}', [CircleController::class, 'joinForm'])->name('circles.join');
    Route::post('join/{code}', [CircleController::class, 'join'])->name('circles.join.store');

    Route::prefix('c/{circle}')->group(function () {
        Route::get('/', [CircleController::class, 'show'])->name('circle.today');
        Route::get('settings', [CircleController::class, 'edit'])->name('circle.settings');
        Route::put('settings', [CircleController::class, 'update'])->name('circle.update');

        Route::get('meds/{date?}', [MedsController::class, 'index'])->name('meds.index');
        Route::post('meds/{slot}/{date}', [MedsController::class, 'record'])->name('meds.record');
        Route::delete('doses/{event}', [MedsController::class, 'undo'])->name('meds.undo');
        Route::get('medicines', [MedsController::class, 'manage'])->name('meds.manage');
        Route::post('medicines', [MedsController::class, 'store'])->name('meds.store');
        Route::put('medicines/{medication}', [MedsController::class, 'update'])->name('meds.update');
        Route::delete('medicines/{medication}', [MedsController::class, 'destroy'])->name('meds.destroy');

        Route::get('log/{date?}', [LogController::class, 'index'])->name('log.index');
        Route::post('log', [LogController::class, 'store'])->name('log.store');
        Route::delete('log/{entry}', [LogController::class, 'destroy'])->name('log.destroy');
        Route::post('readings', [LogController::class, 'storeReading'])->name('readings.store');
        Route::post('ranges', [LogController::class, 'storeRange'])->name('ranges.store');

        Route::get('rota', [RotaController::class, 'index'])->name('rota.index');
        Route::post('rota', [RotaController::class, 'store'])->name('rota.store');
        Route::delete('rota/{shift}', [RotaController::class, 'destroy'])->name('rota.destroy');

        Route::get('supplies', [SuppliesController::class, 'index'])->name('supplies.index');
        Route::post('supplies', [SuppliesController::class, 'store'])->name('supplies.store');
        Route::post('supplies/{supply}/level', [SuppliesController::class, 'level'])->name('supplies.level');
        Route::delete('supplies/{supply}', [SuppliesController::class, 'destroy'])->name('supplies.destroy');

        Route::get('people', [PeopleController::class, 'index'])->name('people.index');
        Route::post('people/{member}', [PeopleController::class, 'update'])->name('people.update');
        Route::delete('people/leave', [PeopleController::class, 'leave'])->name('people.leave');

        Route::get('digest/{week?}', [DigestController::class, 'show'])->name('digest.show');
        Route::post('digest/{week}', [DigestController::class, 'rebuild'])->name('digest.rebuild');
    });
});
