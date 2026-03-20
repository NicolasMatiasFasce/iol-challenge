package iolchallenge.config.compression.httpfilters.response;


import iolchallenge.config.compression.CompressionMethodConfig;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

public class CompressionResponseWrapper extends HttpServletResponseWrapper {
    protected HttpServletResponse origResponse;
    protected ServletOutputStream stream = null;
    protected CompressionMethodConfig compressionMethodConfig;
    protected PrintWriter writer = null;

    public CompressionResponseWrapper(HttpServletResponse response, CompressionMethodConfig compressionMethodConfig) {
        super(response);
        this.origResponse = response;
        this.compressionMethodConfig = compressionMethodConfig;
    }

    private ServletOutputStream createOutputStream() {
        return this.compressionMethodConfig.createServletOutputStream(this.origResponse);
    }

    public void finishResponse() {
        try {
            if (this.writer != null) {
                this.writer.close();
            } else if (this.stream != null) {
                this.stream.close();
            }
        } catch (IOException ignored) {
        }

    }

    @Override
    public void flushBuffer() throws IOException {
        this.stream.flush();
    }

    @Override
    public ServletOutputStream getOutputStream() {
        if (this.writer != null) {
            throw new IllegalStateException("getWriter() has already been called!");
        } else {
            if (this.stream == null) {
                this.stream = this.createOutputStream();
            }

            return this.stream;
        }
    }

    @Override
    public PrintWriter getWriter() {
        if (this.writer != null) {
            return this.writer;
        } else if (this.stream != null) {
            throw new IllegalStateException("getOutputStream() has already been called!");
        } else {
            this.stream = this.createOutputStream();
            this.writer = new PrintWriter(new OutputStreamWriter(this.stream, StandardCharsets.UTF_8));
            return this.writer;
        }
    }

    @Override
    public void setContentLength(int length) {
        // Empty implementation
    }
}
