package iolchallenge.config.compression;

import iolchallenge.config.compression.httpfilters.request.CompressionRequestWrapper;
import iolchallenge.config.compression.httpfilters.response.CompressionResponseWrapper;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

public class CompressionFilter implements Filter {

    public static final String ACCEPT_ENCODING = "Accept-Encoding";
    public static final String CONTENT_ENCODING = "Content-Encoding";

    @Override
    public void init(FilterConfig filterConfig) {
        // No initialization needed
    }

    public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest)req;
        HttpServletResponse response = (HttpServletResponse)resp;
        CompressionMethodConfig compressionResponseMethodConfig = CompressionMethodConfig.resolve(request.getHeader(ACCEPT_ENCODING));
        CompressionMethodConfig compressionRequestMethodConfig = CompressionMethodConfig.resolve(request.getHeader(CONTENT_ENCODING));
        if (compressionRequestMethodConfig != null) {
            CompressionRequestWrapper wrappedRequest = new CompressionRequestWrapper(request, compressionRequestMethodConfig);
            if (compressionResponseMethodConfig != null) {
                CompressionResponseWrapper wrappedResponse = new CompressionResponseWrapper(response, compressionResponseMethodConfig);
                chain.doFilter(wrappedRequest, wrappedResponse);
                wrappedResponse.finishResponse();
            } else {
                chain.doFilter(wrappedRequest, resp);
            }
            wrappedRequest.finishRequest();
        } else if (compressionResponseMethodConfig != null) {
            CompressionResponseWrapper wrappedResponse = new CompressionResponseWrapper(response, compressionResponseMethodConfig);
            chain.doFilter(req, wrappedResponse);
            wrappedResponse.finishResponse();
        } else {
            chain.doFilter(req, resp);
        }
    }

    @Override
    public void destroy() {
        // No cleanup needed
    }
}
