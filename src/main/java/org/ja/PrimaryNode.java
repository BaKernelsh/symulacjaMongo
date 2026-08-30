package org.ja;

import java.util.HashMap;

public class PrimaryNode {

    private HashMap<String, Collection> collectionsByName;

    public Collection getCollectionByName(String name){
        return collectionsByName.get(name);
    }

    public int getNumberOfDocsInCollection(String collectionName){
        return getCollectionByName(collectionName).getNumberOfDocuments();
    }

}
