package org.ja.Utils;

import org.apache.commons.numbers.combinatorics.Combinations;

public class CombinatoricsUtils {

    public static int numberOfCombinations(int n, int k){
        Combinations c =
                Combinations.of(n, k);
        int cNumber = 0;
        for (int[] ints : c) {
            cNumber++;
        }
        return cNumber;
    }

}
