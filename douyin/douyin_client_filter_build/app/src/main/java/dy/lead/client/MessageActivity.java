package dy.lead.client;

/** Compatibility route: collection and messaging share one scrollable workspace. */
public class MessageActivity extends android.app.Activity {
    public void onCreate(android.os.Bundle state){super.onCreate(state);startActivity(new android.content.Intent(this,AutomationActivity.class).addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP));finish();}
}
