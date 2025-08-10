package Components.Server;

import org.springframework.stereotype.Component;

// import lombok.Getter;
// import lombok.Setter;

// @Getter
// @Setter
@Component
public class RedisConfig {

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String role;
    public int port;
}
