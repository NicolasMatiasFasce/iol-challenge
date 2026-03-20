package iolchallenge.model.metric;

public enum BusinessCounter {

    SOME_COUNTER("someCounter");

    private final String name;

    BusinessCounter(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

}
