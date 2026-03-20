package iolchallenge.config.compression.httpfilters.request;


import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import org.xerial.snappy.SnappyInputStream;

import java.io.IOException;

public class SnappyRequestStream extends ServletInputStream {

    private static final String CLOSED_INPUT_STREAM = "Cannot read a closed input stream";
    private final SnappyInputStream snappystream;
    private boolean closed;

    public SnappyRequestStream(HttpServletRequest request) throws IOException {
        this.closed = false;
        this.snappystream = new SnappyInputStream(request.getInputStream());
    }

    public int read() throws IOException {
        if (this.closed) {
            throw new IOException(CLOSED_INPUT_STREAM);
        } else {
            return this.snappystream.read();
        }
    }

    @Override
    public int read(byte[] b) throws IOException {
        if (this.closed) {
            throw new IOException(CLOSED_INPUT_STREAM);
        } else {
            return this.snappystream.read(b);
        }
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (this.closed) {
            throw new IOException(CLOSED_INPUT_STREAM);
        } else {
            return this.snappystream.read(b, off, len);
        }
    }

    @Override
    public void close() throws IOException {
        if (this.closed) {
            throw new IOException("This input stream has already been closed");
        } else {
            this.snappystream.close();
            this.closed = true;
        }
    }

    @Override
    public boolean isFinished() {
        return closed;
    }

    @Override
    public boolean isReady() {
        return !closed;
    }

    @Override
    public void setReadListener(ReadListener readListener) {
        // No implementation
    }
}
