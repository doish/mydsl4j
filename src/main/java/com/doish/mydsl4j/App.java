package com.doish.mydsl4j;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jetty.servlet.Invoker;
import org.yaml.snakeyaml.Yaml;

public class App {

  public static Invoker httpServlet = new Invoker() {
    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
      response.getWriter().write("Hello Servlet!!");
    }
  };

  public static void main(String[] args) throws FileNotFoundException, InterruptedException, Exception {
    InputStream input = new FileInputStream(new File("src/main/resources/dev.yml"));
    Yaml yaml = new Yaml();
    Container container = new Container();
    Result evaluate = new Argument(yaml.load(input)).evaluate(container);

    System.out.println(container);
    System.out.println(evaluate.value());

  }
}
