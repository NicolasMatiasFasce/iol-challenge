package iolchallenge.config.connector.model;

public enum ContentEncoding {
    GZIP("gzip"),
    DEFLATE("deflate"),
    SNAPPY("snappy");

    private final String headerValue;

    ContentEncoding(String headerValue) {
        this.headerValue = headerValue;
    }

    public String headerValue() {
        return headerValue;
    }
}

