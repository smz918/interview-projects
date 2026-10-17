package dy.lead.client;

import android.content.*;
import android.os.SystemClock;
import android.test.InstrumentationTestCase;

/** Isolated preferences and service only; cached paths must never use network. */
public class DailyAuthorizationTest extends InstrumentationTestCase {
    public void testDailyCacheSurvivesServiceRecreationWithoutRenewing() throws Exception {
        getInstrumentation().runOnMainSync(()->{
            SharedPreferences prefs=getInstrumentation().getContext().getSharedPreferences("daily_authorization_test",0);
            prefs.edit().clear().commit();String token="fixture-only-token";
            try {
                prefs.edit().putString("token",token).apply();AuthorizationCache.save(prefs,token);
                long checked=System.currentTimeMillis()-3600000;
                prefs.edit().putLong(AuthorizationCache.CHECKED_AT,checked).apply();
                for(int i=0;i<2;i++){
                    AutomationService service=new AutomationService();service.prefs=prefs;
                    // A cache hit must not submit any work to the shut-down executor.
                    service.network.shutdownNow();service.ai.shutdownNow();
                    service.checkAuth();long remaining=service.lease-SystemClock.elapsedRealtime();
                    assertFalse(service.authBusy);assertEquals(service.lease,service.nextAuth);
                    assertTrue(remaining>22*3600000L&&remaining<=23*3600000L);
                    long deadline=service.lease;service.checkAuth();assertEquals(deadline,service.lease);
                    assertEquals(checked,prefs.getLong(AuthorizationCache.CHECKED_AT,0));
                }
                assertEquals(0L,AuthorizationCache.remaining(prefs,"another-token"));
                prefs.edit().putLong(AuthorizationCache.CHECKED_AT,System.currentTimeMillis()-AuthorizationPolicy.INTERVAL_MS).apply();
                assertEquals(0L,AuthorizationCache.remaining(prefs,token));
                SharedPreferences.Editor edit=prefs.edit();AuthorizationCache.clear(edit);edit.commit();
                assertEquals(0L,AuthorizationCache.remaining(prefs,token));
            }finally{prefs.edit().clear().commit();}
        });
    }
}
