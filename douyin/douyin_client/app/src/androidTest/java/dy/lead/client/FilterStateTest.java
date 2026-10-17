package dy.lead.client;

import android.test.InstrumentationTestCase;

/** Isolated transition test: no screenshots, navigation or network requests. */
public class FilterStateTest extends InstrumentationTestCase {
    public void testApplyTransitionAfterOcrUsesPreviouslyReadableNativePanel() throws Exception {
        getInstrumentation().runOnMainSync(()->{
            final int[] requests={0};
            AutomationService service=new AutomationService(){
                @Override void requestScreen(){requests[0]++;}
            };
            try {
                service.filterPhase="WAIT_RESULTS";
                service.visualFilters=true;
                service.nativeFilterObserved=true;
                FilterPage page=service.filterPage();
                assertNotNull("Native panel was readable before apply: disappearance must not need another OCR",page);
                assertFalse(page.isPanel());
                assertEquals(0,requests[0]);
                service.nativeFilterObserved=false;
                assertNull("If native panel was never readable, absence alone is not proof of closure",service.filterPage());
                assertEquals(1,requests[0]);
            } finally {service.network.shutdownNow();service.ai.shutdownNow();}
        });
    }
}
