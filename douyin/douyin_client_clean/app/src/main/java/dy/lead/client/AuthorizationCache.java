package dy.lead.client;

import android.content.SharedPreferences;

final class AuthorizationCache {
    static final String CHECKED_AT="auth_checked_at",TOKEN_KEY="auth_checked_token_key";
    static long remaining(SharedPreferences prefs,String token){
        return AuthorizationPolicy.remaining(prefs.getLong(CHECKED_AT,0),System.currentTimeMillis(),prefs.getString(TOKEN_KEY,""),token);
    }
    static void save(SharedPreferences prefs,String token){
        prefs.edit().putLong(CHECKED_AT,System.currentTimeMillis()).putString(TOKEN_KEY,AuthorizationPolicy.tokenKey(token)).apply();
    }
    static void clear(SharedPreferences.Editor edit){edit.remove(CHECKED_AT).remove(TOKEN_KEY);}
}
