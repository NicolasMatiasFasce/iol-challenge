package iolchallenge.config.compression.httpfilters.request;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;

import java.io.IOException;
import java.util.zip.GZIPInputStream;

public class GZIPRequestStream extends ServletInputStream {

    private static final String CLOSED_INPUT_STREAM = "Cannot read a closed input stream";
    private final GZIPInputStream gzipStream;
    private boolean closed;

    public GZIPRequestStream(HttpServletRequest request) throws IOException {
        this.closed = false;
        this.gzipStream = new GZIPInputStream(request.getInputStream());
    }

    public int read() throws IOException {
        if (this.closed) {
            throw new IOException(CLOSED_INPUT_STREAM);
        } else {
            return this.gzipStream.read();
        }
    }

    @Override
    public int read(byte[] b) throws IOException {
        if (this.closed) {
            throw new IOException(CLOSED_INPUT_STREAM);
        } else {
            return this.gzipStream.read(b);
        }
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (this.closed) {
            throw new IOException(CLOSED_INPUT_STREAM);
        } else {
            return this.gzipStream.read(b, off, len);
        }
    }

    @Override
    public void close() throws IOException {
        if (this.closed) {
            throw new IOException("This input stream has already been closed");
        } else {
            this.gzipStream.close();
            this.closed = true;
        }
    }

    @Override
    public boolean isFinished() {
        return this.closed;
    }

    @Override
    public boolean isReady() {
        return !this.closed;
    }

    @Override
    public void setReadListener(ReadListener readListener) {
        // No implementation
    }
}
