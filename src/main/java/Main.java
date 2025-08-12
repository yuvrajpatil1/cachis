import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import Components.Server.RedisConfig;
import Components.Server.SlaveTcpServer;
import Components.Server.MasterTcpServer;
import Config.AppConfig;

public class Main {
  public static void main(String[] args) {
    AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(AppConfig.class);
    MasterTcpServer master = context.getBean(MasterTcpServer.class);
    SlaveTcpServer slave = context.getBean(SlaveTcpServer.class);
    RedisConfig redisConfig = context.getBean(RedisConfig.class);

    int port = 6379;
    redisConfig.setPort(port);
    redisConfig.setRole("master");

    for (int i = 0; i < args.length; i++) {

      switch (args[i]) {
        case "--port":
          port = Integer.parseInt(args[i + 1]);
          redisConfig.setPort(port);
          break;
        case "--replicaof":
          redisConfig.setRole("slave");
          // <MASTER_HOST> <MASTER_PORT>
          String masterHost = args[i + 1].split(" ")[0];
          int masterPort = Integer.parseInt(args[i + 1].split(" ")[1]);
          redisConfig.setMasterHost((masterHost));
          redisConfig.setMasterPort(masterPort);
          break;
      }
    }

    if (redisConfig.getRole().equals("slave"))
      slave.startServer();
    else
      master.startServer();
  }
}
