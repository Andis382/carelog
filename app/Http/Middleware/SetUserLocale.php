<?php

namespace App\Http\Middleware;

use Closure;
use Illuminate\Http\Request;

/**
 * Each person reads the app in their own language.
 *
 * This matters more here than in most products: the sister in Tirana and the
 * son in Munich are looking at the same circle, and one of them has been away
 * for fifteen years. The weekly summary follows whoever receives it, not
 * whoever generated it.
 */
class SetUserLocale
{
    public function handle(Request $request, Closure $next)
    {
        $locale = $request->user()?->locale ?: config('carelog.defaults.locale');

        if (array_key_exists($locale, config('carelog.locales'))) {
            app()->setLocale($locale);
        }

        return $next($request);
    }
}
