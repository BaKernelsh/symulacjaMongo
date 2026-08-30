package org.ja.Utils;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class CombinatoricsUtilsTest {


    @Test
    public void getNumberOfCombinationsTest(){
        Assertions.assertEquals(435, CombinatoricsUtils.numberOfCombinations(30, 2));
    }
}
