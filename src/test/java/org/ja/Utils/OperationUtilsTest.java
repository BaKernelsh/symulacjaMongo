package org.ja.Utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;

public class OperationUtilsTest {


    @Test
    public void calcSleepTimeForRetriesTest(){

        Assertions.assertEquals(0, OperationUtils.calcTotalSleepTimeForRetries(1));
        Assertions.assertEquals(0, OperationUtils.calcTotalSleepTimeForRetries(4));

        Assertions.assertEquals(4, OperationUtils.calcTotalSleepTimeForRetries(1));


    }
}
