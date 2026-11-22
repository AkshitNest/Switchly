package live.switchly.api.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "switchly.rate-limit")
public class RateLimitProperties {

    private boolean enabled = true;
    private int loginLimit = 5;
    private int loginWindowSeconds = 60;
    private int registerLimit = 3;
    private int registerWindowSeconds = 60;
    private int refreshLimit = 10;
    private int evaluationLimit = 600;

    public int getEvaluationLimit() { return evaluationLimit; }
    public void setEvaluationLimit(int evaluationLimit) {
        if (evaluationLimit < 1) throw new IllegalArgumentException("evaluation-limit must be positive");
        this.evaluationLimit = evaluationLimit;
    }
    private int refreshWindowSeconds = 60;
    private List<String> trustedProxies = new ArrayList<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getLoginLimit() {
        return loginLimit;
    }

    public void setLoginLimit(int loginLimit) {
        this.loginLimit = loginLimit;
    }

    public int getLoginWindowSeconds() {
        return loginWindowSeconds;
    }

    public void setLoginWindowSeconds(int loginWindowSeconds) {
        this.loginWindowSeconds = loginWindowSeconds;
    }

    public int getRegisterLimit() {
        return registerLimit;
    }

    public void setRegisterLimit(int registerLimit) {
        this.registerLimit = registerLimit;
    }

    public int getRegisterWindowSeconds() {
        return registerWindowSeconds;
    }

    public void setRegisterWindowSeconds(int registerWindowSeconds) {
        this.registerWindowSeconds = registerWindowSeconds;
    }

    public int getRefreshLimit() {
        return refreshLimit;
    }

    public void setRefreshLimit(int refreshLimit) {
        this.refreshLimit = refreshLimit;
    }

    public int getRefreshWindowSeconds() {
        return refreshWindowSeconds;
    }

    public void setRefreshWindowSeconds(int refreshWindowSeconds) {
        this.refreshWindowSeconds = refreshWindowSeconds;
    }

    public List<String> getTrustedProxies() {
        return trustedProxies;
    }

    public void setTrustedProxies(List<String> trustedProxies) {
        this.trustedProxies = trustedProxies != null ? trustedProxies : new ArrayList<>();
    }
}

