package iolchallenge.config.compression.httpfilters.request;

import iolchallenge.config.compression.CompressionMethodConfig;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;


public class CompressionRequestWrapper extends HttpServletRequestWrapper {
    private final CompressionMethodConfig compressionMethodConfig;
    private final HttpServletRequest origRequest;
    protected ServletInputStream stream;
    protected BufferedReader reader;

    public CompressionRequestWrapper(HttpServletRequest request, CompressionMethodConfig compressionMethodConfig) {
        super(request);
        this.origRequest = request;
        this.compressionMethodConfig = compressionMethodConfig;
    }

    public void finishRequest() {
        try {
            if (this.reader != null) {
                this.reader.close();
            } else if (this.stream != null) {
                this.stream.close();
            }
        } catch (IOException ignored) {
        }

    }

    @Override
    public ServletInputStream getInputStream(){
        if (this.reader != null) {
            throw new IllegalStateException("getReader() has already been called!");
        } else {
            if (this.stream == null) {
                this.stream = this.createInputStream();
            }

            return this.stream;
        }
    }

    @Override
    public BufferedReader getReader() {
        if (this.reader != null) {
            return this.reader;
        } else if (this.stream != null) {
            throw new IllegalStateException("getInputStream() has already been called!");
        } else {
            this.stream = this.createInputStream();
            this.reader = new BufferedReader(new InputStreamReader(this.stream, StandardCharsets.UTF_8));
            return this.reader;
        }
    }

    private ServletInputStream createInputStream() {
        return this.compressionMethodConfig.createServletInputStream(this.origRequest);
    }
}
