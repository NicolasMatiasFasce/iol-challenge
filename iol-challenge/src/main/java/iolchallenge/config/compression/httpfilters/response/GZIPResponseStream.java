package iolchallenge.config.compression.httpfilters.response;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletResponse;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPOutputStream;

public class GZIPResponseStream extends ServletOutputStream {
    protected ByteArrayOutputStream baos;
    protected GZIPOutputStream gzipStream;
    protected boolean closed;
    protected HttpServletResponse response;
    protected ServletOutputStream output;

    public GZIPResponseStream(HttpServletResponse response) throws IOException {
        this.closed = false;
        this.response = response;
        this.output = response.getOutputStream();
        this.baos = new ByteArrayOutputStream();
        this.gzipStream = new GZIPOutputStream(this.baos);
    }

    @Override
    public void close() throws IOException {
        if (this.closed) {
            throw new IOException("This output stream has already been closed");
        } else {
            this.gzipStream.finish();
            byte[] bytes = this.baos.toByteArray();
            this.response.setHeader("Content-Length", Integer.toString(bytes.length));
            this.response.setHeader("Content-Encoding", "gzip");
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
            this.gzipStream.flush();
        }
    }

    public void write(int b) throws IOException {
        if (this.closed) {
            throw new IOException("Cannot write to a closed output stream");
        } else {
            this.gzipStream.write((byte)b);
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
            this.gzipStream.write(b, off, len);
        }
    }

    public void reset() {
        // Empty implementation
    }

    @Override
    public boolean isReady() {
        return !this.closed;
    }

    @Override
    public void setWriteListener(WriteListener writeListener) {
        // Empty implementation
    }
}
