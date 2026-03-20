package iolchallenge.config.connector.model;

import java.util.Map;

public abstract class ConnectorProperties {
    private final RestConnectorProperties restConnector;
    private final Map<String, ClientResourceProperties> resources;

    protected ConnectorProperties(RestConnectorProperties connector, Map<String, ClientResourceProperties> resources) {
        this.restConnector = connector;
        this.resources = resources;
    }

    public RestConnectorProperties restConnector() {
        return restConnector;
    }

    public Map<String, ClientResourceProperties> resources() {
        return resources;
    }
}
