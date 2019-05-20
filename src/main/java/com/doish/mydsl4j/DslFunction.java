package com.doish.mydsl4j;

public interface DslFunction {
  public Result apply(Container container, Argument... args);
}
