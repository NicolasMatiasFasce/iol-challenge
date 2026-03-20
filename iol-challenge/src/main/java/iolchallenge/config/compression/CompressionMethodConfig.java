package iolchallenge.config.compression;


import iolchallenge.config.compression.httpfilters.request.GZIPRequestStream;
import iolchallenge.config.compression.httpfilters.request.SnappyRequestStream;
import iolchallenge.config.compression.httpfilters.response.GZIPResponseStream;
import iolchallenge.config.compression.httpfilters.response.SnappyResponseStream;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


public enum CompressionMethodConfig {
    GZIP("gzip", GZIPResponseStream.class, GZIPRequestStream.class),
    SNAPPY("x-snappy", SnappyResponseStream.class, SnappyRequestStream.class);

    private final String compressionMethod;
    private final Class<?> responseStreamClass;
    private final Class<?> requestStreamClass;

    CompressionMethodConfig(String compressionMethod, Class<?> responseStream, Class<?> requestStream) {
        this.compressionMethod = compressionMethod;
        this.responseStreamClass = responseStream;
        this.requestStreamClass = requestStream;
    }

    public String getCompressionMethod() {
        return this.compressionMethod;
    }

    public Class<?> getResponseStreamClass() {
        return this.responseStreamClass;
    }

    public static CompressionMethodConfig resolve(String header) {
        if (header == null || header.isEmpty()) {
            return null;
        }

        String[] splits = header.split(",");
        for (String headerItem : splits) {
            for (CompressionMethodConfig config : values()) {
                if (config.getCompressionMethod().equals(headerItem.trim())) {
                    return config;
                }
            }
        }
        return null;
    }

    public ServletOutputStream createServletOutputStream(HttpServletResponse response) {
        try {
            return (ServletOutputStream)this.responseStreamClass.getConstructor(HttpServletResponse.class).newInstance(response);
        } catch (Exception var3) {
            throw new RuntimeException(var3);
        }
    }

    public ServletInputStream createServletInputStream(HttpServletRequest request) {
        try {
            return (ServletInputStream)this.requestStreamClass.getConstructor(HttpServletRequest.class).newInstance(request);
        } catch (Exception var3) {
            throw new RuntimeException(var3);
        }
    }
}
