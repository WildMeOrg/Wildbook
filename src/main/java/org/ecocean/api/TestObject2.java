package org.ecocean.api;

public class TestObject2 extends org.ecocean.api.ApiBase {

    private String foo = "this is the value of .foo";

    public String description() {
        return "yes, this is my description";
    }

    public String getFoo() {
        return foo;
    }

}


