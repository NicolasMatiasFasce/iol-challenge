package iolchallenge.config.compression.httpfilters.response;


import iolchallenge.config.compression.CompressionMethodConfig;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletResponse;
import org.xerial.snappy.SnappyOutputStream;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class SnappyResponseStream extends ServletOutputStream {
    protected ByteArrayOutputStream baos;
    protected SnappyOutputStream snappyOutputStream;
    protected boolean closed;
    protected HttpServletResponse response;
    protected ServletOutputStream output;

    public SnappyResponseStream(HttpServletResponse response) throws IOException {
        this.closed = false;
        this.response = response;
        this.output = response.getOutputStream();
        this.baos = new ByteArrayOutputStream();
        this.snappyOutputStream = new SnappyOutputStream(this.baos);
    }

    @Override
    public void close() throws IOException {
        if (this.closed) {
            throw new IOException("This output stream has already been closed");
        } else {
            this.snappyOutputStream.close();
            byte[] bytes = this.baos.toByteArray();
            this.response.setHeader("Content-Length", Integer.toString(bytes.length));
            this.response.setHeader("Content-Encoding", CompressionMethodConfig.SNAPPY.getCompressionMethod());
            this.output.write(bytes);
            this.output.flush();
            this.output.close();
            this.closed = true;
        }
    }

    @Override
    public void flush() throws IOException {
        if (this.closed) {
            throw new IOException("Cannot flush a closed output stream");
        } else {
            this.snappyOutputStream.flush();
        }
    }

    public void write(int b) throws IOException {
        if (this.closed) {
            throw new IOException("Cannot write to a closed output stream");
        } else {
            this.snappyOutputStream.write((byte)b);
        }
    }

    @Override
    public void write(byte[] b) throws IOException {
        this.write(b, 0, b.length);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        if (this.closed) {
            throw new IOException("Cannot write to a closed output stream");
        } else {
            this.snappyOutputStream.write(b, off, len);
        }
    }

    @Override
    public boolean isReady() {
        return !closed;
    }

    @Override
    public void setWriteListener(WriteListener writeListener) {
        // Empty implementation
    }
}
