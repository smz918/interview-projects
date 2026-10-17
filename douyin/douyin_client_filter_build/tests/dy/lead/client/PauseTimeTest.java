package dy.lead.client;

public final class PauseTimeTest {
    static void check(boolean value,String reason){if(!value)throw new AssertionError(reason);}
    public static void main(String[] args){
        long start=10000,pause=15000,resume=615000;
        long phase=PauseTime.shift(start,pause,resume);
        check(resume-phase==5000,"Ten-minute pause must not consume phase timeout");
        long sendDelay=PauseTime.deadline(45000,resume-pause);
        check(sendDelay-resume==30000,"Keep remaining queue rest, never skip recipient");
        long callbackAt=30000;
        check(PauseTime.shift(callbackAt,pause,resume)==resume,"Callback during pause must not create a negative phase age");
        phase=PauseTime.shift(phase,resume+1000,resume+61000);
        check(resume+61000-phase==6000,"Repeated pause counts only actual active time");
        check(PauseTime.shift(-1,pause,resume)==-1,"Window recovery unset sentinel");
        check(PauseTime.shift(0,pause,resume)==0,"Unused phase timer");
        check(PauseTime.deadline(Long.MAX_VALUE,resume-pause)==Long.MAX_VALUE,"Do not overflow infinite deadline");
        System.out.println("PASS: long/repeated pauses, queue remainder, pending callbacks and timer sentinels");
    }
}
