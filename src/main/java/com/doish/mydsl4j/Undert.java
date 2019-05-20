/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.doish.mydsl4j;

import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.RoutingHandler;
import io.undertow.util.Headers;
import java.lang.reflect.Method;
import java.util.stream.Stream;

/**
 *
 * @author uotan
 */
public class Undert {

  public static void main(final String[] args) throws NoSuchMethodException {
    RoutingHandler get = Handlers.routing().get("/", (final HttpServerExchange exchange) -> {
      exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/plain");
      exchange.getResponseSender().send("Hello World");
    });
    Undertow server = Undertow.builder()
            .addHttpListener(8080, "localhost")
            .setHandler(get)
            .build();
    server.start();

    System.out.println("Started server at http://127.1:8080/  Hit ^C to stop");
  }
}
