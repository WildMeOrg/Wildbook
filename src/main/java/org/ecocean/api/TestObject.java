package org.ecocean.api;

import java.util.List;

public class TestObject extends org.ecocean.api.ApiBase {

    private int testField = 0;
    private String description = "TEST OBJECT";
    private List<TestObject2> twos = null;


    public String description() {
        return description;
    }

    public int getTestField() {
        return testField;
    }

    public List<TestObject2> getTwos() {
        return twos;
    }
    public void setTwos(List<TestObject2> t) {
        twos = t;
    }
}


