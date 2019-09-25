package at.rodrigo.api.gateway.routes;


import at.rodrigo.api.gateway.entity.Api;
import at.rodrigo.api.gateway.entity.Path;
import at.rodrigo.api.gateway.parser.SwaggerParser;
import at.rodrigo.api.gateway.utils.CamelUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.InterceptSendToEndpointDefinition;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.rest.RestOperationParamDefinition;
import org.apache.camel.model.rest.RestParamType;
import org.apache.camel.spi.CamelInternalProcessorAdvice;
import org.apache.camel.spi.InterceptSendToEndpoint;
import org.apache.http.conn.HttpHostConnectException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Component
@Slf4j
public class SwaggerRestRouter extends RouteBuilder {

    @Autowired
    SwaggerParser swaggerParser;

    @Autowired
    RestTemplate restTemplate;

    @Autowired
    CamelUtils camelUtils;

    @Value("${api.gateway.swagger.rest.endpoint}")
    private String apiGatewaySwaggerRestEndpoint;

    private Api[] apiList;

    @Override
    public void configure() {

        log.info("Starting configuration of Swagger Routes");

        if(apiList == null) {
            apiList = restTemplate.getForObject(apiGatewaySwaggerRestEndpoint, Api[].class);
        }

        for(Api api : apiList) {
            try {
                addRoutes(api);
            } catch(Exception e) {
                log.error(e.getMessage(), e);
            }
        }
    }

    public void addRoutes(Api api) throws Exception {

        List<Path> pathList = swaggerParser.parse(api.getSwaggerEndpoint());

        for(Path path : pathList) {
            if(!path.getPath().equals("/error")) {
                RestOperationParamDefinition restParamDefinition = new RestOperationParamDefinition();
                List<String> paramList = camelUtils.evaluatePath(path.getPath());

                String routeID = camelUtils.normalizeRouteId(api, path);
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

            }
        }
    }
}