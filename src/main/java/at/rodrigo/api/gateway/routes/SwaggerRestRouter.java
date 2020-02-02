package at.rodrigo.api.gateway.routes;


import at.rodrigo.api.gateway.cache.ThrottlingManager;
import at.rodrigo.api.gateway.entity.Api;
import at.rodrigo.api.gateway.entity.Path;
import at.rodrigo.api.gateway.parser.SwaggerParser;
import at.rodrigo.api.gateway.repository.ApiRepository;
import at.rodrigo.api.gateway.utils.CamelUtils;
import at.rodrigo.api.gateway.utils.GrafanaUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.rest.RestOperationParamDefinition;
import org.apache.camel.model.rest.RestParamType;
import org.apache.http.conn.HttpHostConnectException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import javax.net.ssl.SSLHandshakeException;
import java.net.UnknownHostException;
import java.util.List;

@Component
@Slf4j
public class SwaggerRestRouter extends RouteBuilder {

    @Autowired
    private ApiRepository apiRepository;

    @Autowired
    private SwaggerParser swaggerParser;

    @Autowired
    private CamelUtils camelUtils;

    @Autowired
    private GrafanaUtils grafanaUtils;

    @Autowired
    private ThrottlingManager throttlingManager;

    @Override
    public void configure() {

        log.info("Starting configuration of Swagger Routes");

        List<Api> apiList = apiRepository.findAllBySwagger(true);
        for(Api api : apiList) {
            try {
                addRoutes(api);
            } catch(Exception e) {
                log.error(e.getMessage(), e);
            }
        }
    }

    void addRoutes(Api api) throws Exception {

        List<Path> pathList = swaggerParser.parse(api.getSwaggerEndpoint());
        api.setPaths(pathList);

        for(Path path : pathList) {
            //if(!path.getPath().equals("/error")) {
                RestOperationParamDefinition restParamDefinition = new RestOperationParamDefinition();
                List<String> paramList = camelUtils.evaluatePath(path.getPath());

                String routeID = camelUtils.normalizeRouteId(api, path);
                path.setRouteID(routeID);
                RouteDefinition routeDefinition;

                switch(path.getVerb()) {
                    case GET:
                        routeDefinition = rest().get("/" + api.getContext() + path.getPath()).route();
                        break;
                    case POST:
                        routeDefinition = rest().post("/" + api.getContext() + path.getPath()).route();
                        break;
                    case PUT:
                        routeDefinition = rest().put("/" + api.getContext() + path.getPath()).route();
                        break;
                    case DELETE:
                        routeDefinition = rest().delete("/" + api.getContext() + path.getPath()).route();
                        break;
                    default:
                        throw new Exception("No verb available");
                }

                camelUtils.buildOnExceptionDefinition(routeDefinition, HttpHostConnectException.class, true, HttpStatus.SERVICE_UNAVAILABLE, "API NOT AVAILABLE", routeID);
                camelUtils.buildOnExceptionDefinition(routeDefinition, UnknownHostException.class, true, HttpStatus.SERVICE_UNAVAILABLE, "API ENDPOINT WITH WRONG HOST", routeID);
                camelUtils.buildOnExceptionDefinition(routeDefinition, SSLHandshakeException.class, true, HttpStatus.SERVICE_UNAVAILABLE, "UNKOWN API", routeID);
                if(paramList.isEmpty()) {
                    camelUtils.buildRoute(routeDefinition, routeID, api, path, false);
                } else {
                    for(String param : paramList) {
                        restParamDefinition.name(param)
                                .type(RestParamType.path)
                                .dataType("String");
                    }
                    camelUtils.buildRoute(routeDefinition, routeID, api, path, true);
                }
            //}
        }
        throttlingManager.applyThrottling(api);
        grafanaUtils.addToGrafana(api);
    }
}