package iolchallenge.config.server;

import iolchallenge.config.server.model.JettyProperties;
import org.eclipse.jetty.ee10.servlet.security.ConstraintMapping;
import org.eclipse.jetty.ee10.servlet.security.ConstraintSecurityHandler;
import org.eclipse.jetty.security.Constraint;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.util.thread.ThreadPool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.web.embedded.jetty.JettyServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.stereotype.Component;

import static java.util.Optional.ofNullable;

@Component
public class JettyServerFactoryCustomizer implements WebServerFactoryCustomizer<JettyServletWebServerFactory> {
    private static final int DEFAULT_SERVLET_PORT = 9290;
    private final JettyProperties serverProperties;
    private final ThreadPool jettyThreadPool;

    @Autowired
    public JettyServerFactoryCustomizer(
        JettyProperties serverProperties,
        @Qualifier("jettyThreadPool") ThreadPool jettyThreadPool)
    {
        this.serverProperties = serverProperties;
        this.jettyThreadPool = jettyThreadPool;
    }

    @Override
    public void customize(JettyServletWebServerFactory factory) {
        final var port = ofNullable(serverProperties.port()).orElse(DEFAULT_SERVLET_PORT);
        factory.setPort(port);
        factory.setThreadPool(jettyThreadPool);
        factory.addServerCustomizers(server -> {
            Handler handler = server.getHandler();
            server.setHandler(disableTraceHttpMethodFromHandler(handler));
        });
    }

    private Handler disableTraceHttpMethodFromHandler(Handler h) {
        final Constraint disableTraceConstraint = Constraint.from("Disable TRACE", Constraint.Authorization.ANY_USER);

        final ConstraintMapping mapping = new ConstraintMapping();
        mapping.setConstraint(disableTraceConstraint);
        mapping.setMethod("TRACE");
        mapping.setPathSpec("/");

        final Constraint omissionConstraint = Constraint.ALLOWED;
        final ConstraintMapping omissionMapping = new ConstraintMapping();
        omissionMapping.setConstraint(omissionConstraint);
        omissionMapping.setMethod("*");
        omissionMapping.setPathSpec("/");

        final ConstraintSecurityHandler handler = new ConstraintSecurityHandler();
        handler.addConstraintMapping(mapping);
        handler.addConstraintMapping(omissionMapping);
        handler.setHandler(h);
        return handler;
    }
}
