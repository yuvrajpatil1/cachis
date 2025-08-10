import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import Components.Server.RedisConfig;
import Components.Server.TcpServer;
import Config.AppConfig;

public class Main {
  public static void main(String[] args) {
    AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(AppConfig.class);
    TcpServer app = context.getBean(TcpServer.class);
    RedisConfig redisConfig = context.getBean(RedisConfig.class);

    int port = 6379;
    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("--port")) {
        port = Integer.parseInt(args[i + 1]);
        i++;
      }
    }

    redisConfig.setPort(port);
    redisConfig.setRole("master");
    app.startServer(port);
  }
}
