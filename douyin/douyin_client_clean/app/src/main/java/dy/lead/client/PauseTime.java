package dy.lead.client;

/** Preserve elapsed phase time and remaining queue delays across a user pause. */
final class PauseTime {
    static long shift(long timestamp,long pausedAt,long now){
        if(timestamp<=0||timestamp==Long.MAX_VALUE)return timestamp;
        return timestamp+Math.max(0,now-Math.max(timestamp,pausedAt));
    }
    static long deadline(long timestamp,long duration){
        if(timestamp<=0||timestamp==Long.MAX_VALUE)return timestamp;
        return timestamp+Math.max(0,duration);
    }
}
