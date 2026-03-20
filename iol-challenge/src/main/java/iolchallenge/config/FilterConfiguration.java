package iolchallenge.config;

import iolchallenge.config.compression.CompressionFilter;
import jakarta.servlet.Filter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FilterConfiguration {

    private static final String ALL_URL = "/*";
    private static final int COMPRESSION_FILTER_ORDER = 1;

    @Bean
    public FilterRegistrationBean<Filter> compressionFilter() {
        return initializeFilterRegistrationBean(new CompressionFilter(), COMPRESSION_FILTER_ORDER, ALL_URL);
    }

    private <T extends Filter> FilterRegistrationBean<Filter> initializeFilterRegistrationBean(T filter, int order, String... urlPatterns) {
        final var registrationBean = new FilterRegistrationBean<>();
        registrationBean.setOrder(order);
        registrationBean.setFilter(filter);
        registrationBean.addUrlPatterns(urlPatterns);
        return registrationBean;
    }
}
