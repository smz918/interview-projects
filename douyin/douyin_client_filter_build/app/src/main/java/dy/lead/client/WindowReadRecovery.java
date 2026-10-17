package dy.lead.client;

/** Three bounded recovery rounds; each gesture gets a full interval to be observed. */
final class WindowReadRecovery {
    static final long WAIT_MS=40000;
    long began=-1,roundAt=-1,loggedAt=-1;int rounds;
    void reset(){began=roundAt=loggedAt=-1;rounds=0;}
    void begin(long now){if(began<0){began=roundAt=now;}}
    boolean due(long now){return roundAt>=0&&now-roundAt>=WAIT_MS;}
    boolean exhausted(){return rounds>=3;}
    void retried(long now){rounds++;roundAt=now;}
    long remaining(long now){return Math.max(0,WAIT_MS-(now-roundAt));}
    long duration(long now){return began<0?0:now-began;}
}
