package org.ja.Utils;

public class OperationUtils {

    public static long calcTotalSleepTimeForRetries(int retries){
        long time = 0;
        retries = retries - 4;
        if(retries <=0)
            return 0;

        if(retries <= 5)
            return retries * 1;
        time = 5 * 1;
        retries = retries - 5;

        if(retries <= 90)
            return time + retries * 5;
        time = time + retries * 5L;
        retries = retries - 90;

        time = time + retries * 10L;
        return  time;
    }
}
