package org.ja;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
public class Collection {
    @Getter
    private String name;
    @Getter
    private int numberOfDocuments;

}
