package org.ja;

import java.util.LinkedList;

public class Transaction {

    private String transactionId;
    private LinkedList<Operation> operations;
    private int currentOpIndex = 0;

}
