package at.rodrigo.api.gateway.cache;

import at.rodrigo.api.gateway.entity.Api;
import at.rodrigo.api.gateway.entity.Path;
import at.rodrigo.api.gateway.entity.ThrottlingPolicy;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
@Slf4j
public class ThrottlingManager {

    @Autowired
    private HazelcastInstance hazelcastInstance;

    public void applyThrottling(Api api) {
        if(api.getThrottlingPolicy() != null) {
            ThrottlingPolicy memoryThrottlingPolicy = cloneThrottling(api.getThrottlingPolicy());
            List<Path> pathList = api.getPaths();
            List<String> apiRouteList = new ArrayList<>();
            for(Path path : pathList) {
                if(api.getThrottlingPolicy().isApplyPerPath()) {
                    List<String> routeList = new ArrayList<>();
                    routeList.add(path.getRouteID());
                    memoryThrottlingPolicy.setRouteID(routeList);
                    getThrottlingPolicies().put(UUID.randomUUID().toString(), memoryThrottlingPolicy);
                } else {
                    apiRouteList.add(path.getRouteID());
                }
            }
            if(!api.getThrottlingPolicy().isApplyPerPath()) {
                memoryThrottlingPolicy.setRouteID(apiRouteList);
                getThrottlingPolicies().put(UUID.randomUUID().toString(), memoryThrottlingPolicy);
            }
        }
    }

    public IMap<String, ThrottlingPolicy> getThrottlingPolicies() {
        return hazelcastInstance.getMap(CacheConstants.THROTTLING_POLICIES_IMAP_NAME);
    }

    public void saveThrottlingPolicy(String id, ThrottlingPolicy throttlingPolicy) {
        this.getThrottlingPolicies().put(id, throttlingPolicy);
    }

    ThrottlingPolicy cloneThrottling(ThrottlingPolicy throttlingPolicy) {
        ThrottlingPolicy localThrottlingPolicy = new ThrottlingPolicy();
        localThrottlingPolicy.setStartCountingAt(throttlingPolicy.getStartCountingAt());
        localThrottlingPolicy.setTotalCalls(throttlingPolicy.getTotalCalls());
        localThrottlingPolicy.setThrottlingExpiration(null);
        localThrottlingPolicy.setMaxCallsAllowed(throttlingPolicy.getMaxCallsAllowed());
        localThrottlingPolicy.setApplyPerPath(throttlingPolicy.isApplyPerPath());
        localThrottlingPolicy.setPeriodForMaxCalls(throttlingPolicy.getPeriodForMaxCalls());
        return localThrottlingPolicy;
    }

}
