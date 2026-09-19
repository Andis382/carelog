<?php

namespace App\Http\Controllers\Auth;

use App\Http\Controllers\Controller;
use App\Models\User;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Auth;
use Illuminate\Validation\Rule;
use Illuminate\Validation\ValidationException;

class AuthController extends Controller
{
    public function showLogin()
    {
        return view('auth.login');
    }

    public function login(Request $request)
    {
        $data = $request->validate([
            'email' => ['required', 'email'],
            'password' => ['required', 'string'],
        ]);

        if (! Auth::attempt($data, true)) {
            throw ValidationException::withMessages(['email' => __('auth.failed')]);
        }

        $request->session()->regenerate();
        $request->user()->forceFill(['last_seen_at' => now()])->save();

        return redirect()->intended(route('circles.index'));
    }

    public function showRegister(Request $request)
    {
        return view('auth.register', ['invite' => $request->query('join')]);
    }

    public function register(Request $request)
    {
        $data = $request->validate([
            'name' => ['required', 'string', 'max:80'],
            'email' => ['required', 'email', 'max:120', 'unique:users,email'],
            'phone' => ['nullable', 'string', 'max:30'],
            'password' => ['required', 'string', 'min:8', 'confirmed'],
            'locale' => ['nullable', Rule::in(array_keys(config('carelog.locales')))],
            'join' => ['nullable', 'string', 'max:40'],
        ]);

        $user = User::create([
            'name' => $data['name'],
            'email' => $data['email'],
            'phone' => $data['phone'] ?? null,
            'password' => $data['password'],
            'locale' => $data['locale'] ?? config('carelog.defaults.locale'),
            'timezone' => config('carelog.defaults.timezone'),
            'last_seen_at' => now(),
        ]);

        Auth::login($user, true);
        $request->session()->regenerate();

        // Somebody who arrived through an invite link should land in the
        // circle they were invited to, not on an empty list.
        if (! empty($data['join'])) {
            return redirect()->route('circles.join', $data['join']);
        }

        return redirect()->route('circles.create')->with('status', __('flash.welcome'));
    }

    public function logout(Request $request)
    {
        Auth::logout();
        $request->session()->invalidate();
        $request->session()->regenerateToken();

        return redirect()->route('home');
    }
}
