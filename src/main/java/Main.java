import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import Components.TcpServer;

public class Main {
  public static void main(String[] args) {
    // You can use print statements as follows for debugging, they'll be visible
    // when running tests.
    System.out.println("Logs from your program will appear here!");

    AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(AppConfig.class);

    TcpServer app = context.getBean(TcpServer.class);

    int port = 6379;
    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("--port")) {
        port = Integer.parseInt(args[i + 1]);
        i++;
      }
    }
    app.startServer(port);
  }

  // public static String encodingRespString(String s) {
  // String resp = "$";
  // resp += s.length();
  // resp += "\r\n";
  // resp += s;
  // resp += "\r\n";
  // return resp;
  // }
}
