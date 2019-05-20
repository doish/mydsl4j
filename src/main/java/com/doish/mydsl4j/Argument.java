package com.doish.mydsl4j;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class Argument {

  private final Object rawArg;
  private static final Pattern DOLLER_REPLACE_PATTERN = Pattern.compile("^(\\$\\.?)");

  public Argument(Object rawArg) {
    if (rawArg instanceof String) {
      if (rawArg.equals("$")) {
        this.rawArg = "$";
      } else {
        Matcher matcher = DOLLER_REPLACE_PATTERN.matcher((String) rawArg);
        if (matcher.find()) {
          this.rawArg = DOLLER_REPLACE_PATTERN.matcher((String) rawArg).replaceFirst("\\$.");
        } else {
          this.rawArg = rawArg;
        }
      }
    } else {
      this.rawArg = rawArg;
    }
  }

  public Object rawArg() {
    return rawArg;
  }

  public Result evaluate(Container container) {
    if (this.rawArg instanceof String) {
      String stringArg = (String) this.rawArg();
      if (stringArg.equals("$")) {
        return new Result(container, null);
      } else if (stringArg.startsWith("$")) {
        return MydslCore.dslFunctions("get").apply(container, new Argument(stringArg));
      }
    } else if (this.rawArg instanceof List) {
      List<Object> listArgs = (List) this.rawArg;
      List<Object> evaluated = new ArrayList<>();
      for (Object arg : listArgs) {
        Result result = new Argument(arg).evaluate(container);
        if (result.hasError()) {
          return result;
        } else {
          evaluated.add(result.value());
        }
      }
      return new Result(evaluated, null);
    } else if (this.rawArg instanceof Map) {
      Map mapArg = (Map) this.rawArg;
      if (mapArg.isEmpty()) {
        return new Result(new LinkedHashMap<String, Object>(), null);
      } else if (mapArg.size() == 1) {
        String key = (String) mapArg.keySet().iterator().next();
        if (MydslCore.hasFunction(key)) {
          Object value = ((Map) this.rawArg).get(key);
          if (value instanceof List) {
            List<Argument> collect = ((List<Argument>) ((List) value).stream().map((Object t) -> {
              return new Argument(t);
            }).collect(Collectors.toList()));
            return MydslCore.dslFunctions(key).apply(container, collect.toArray(new Argument[collect.size()]));
          } else {
            return MydslCore.dslFunctions(key).apply(container, new Argument(value));
          }
        } else if (key.startsWith("$")) {
          return MydslCore.dslFunctions("set").apply(container, new Argument[]{
            new Argument(key), new Argument(((Map) this.rawArg).get(key))});
        }
      }
    }
    return new Result(this.rawArg, null);
  }
}
