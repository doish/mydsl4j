/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.doish.mydsl4j;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import io.undertow.Undertow;
import io.undertow.Handlers;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.RoutingHandler;
import io.undertow.util.Headers;

public class MydslCore {

  private static class LastKeyValue {

    private final Object key;
    private final Object value;
    private final Throwable error;

    private LastKeyValue(Object value) {
      this.key = "";
      this.value = value;
      this.error = null;
    }

    private LastKeyValue(Object key, Object value) {
      this.key = key;
      this.value = value;
      this.error = null;
    }

    private LastKeyValue(Object key, Object parent, Throwable error) {
      this.key = key;
      this.value = parent;
      this.error = error;
    }

    private boolean hasError() {
      return this.error != null;
    }

    private Object value() {
      return this.value;
    }

    @Override
    public String toString() {
      return "key:" + this.key.toString() + " value:" + this.value.toString();
    }
  }
  private static final Map<String, DslFunction> DSL_FUNCTIONS = new LinkedHashMap<>();
  private static final Map<String, Object> DSL_AVAILABLE_FUNCTIONS = new LinkedHashMap<>();
  private static final Pattern firstValuePattern = Pattern.compile("^([^\\[ \\]\\.]+)\\.?(.+)$");
  private static final Pattern nextKeyPattern = Pattern.compile("^(\\[([^\\[\\]]+)\\]|([^\\[\\] \\.]+))\\.?(.*)$");
  private static final Map<Class, Class> classMap = new LinkedHashMap<>();

  static {
    try {
      classMap.put(Class.forName("java.lang.Integer"), new Integer(1).getClass().getConstructors()[0].getParameterTypes()[0]);
      DSL_AVAILABLE_FUNCTIONS.put("Undertow.builder", (Undertow.class).getMethod("builder"));
      DSL_AVAILABLE_FUNCTIONS.put("Handlers.routing", (Handlers.class).getMethod("routing"));
    } catch (Exception e) {
    }
  }

  private static Result evaluateAll(Argument[] args, Container container) {
    Object[] result = new Object[args.length];
    for (int i = 0; i < args.length; i++) {
      Argument arg = args[i];
      Result res = arg.evaluate(container);
      if (res.hasError()) {
        return res;
      } else {
        result[i] = res.value();
      }
    }
    return new Result(result, null);
  }

  private static Result propertyGet(Object parent, Object key) {
    if (key instanceof String) {
      String stringKey = (String) key;
      try {
        int intKey = Integer.parseInt(stringKey);
        return new Result(((List) parent).get(intKey), null);
      } catch (NumberFormatException e) {
        if (parent instanceof Map) {
          return new Result(((Map) parent).get(key), null);
        } else {
          Class<?> parentClass = parent.getClass();
          try {
            Optional<Field> findFirst = Stream.of(parentClass.getDeclaredFields()).filter((Field f) -> f.getName().equals(stringKey) && Modifier.isPublic(f.getModifiers())).findFirst();
            if (findFirst.isPresent()) {
              return new Result(findFirst.get().get(parent), null);
            } else {
              try {
                List<Method> collect = Stream.of(parentClass.getMethods())
                        .filter((Method m) -> {
                          return true;
                        })
                        .filter((Method m) -> m.getName().equals(stringKey)
                        && Modifier.isPublic(m.getModifiers())).collect(Collectors.toList());
                Method[] methods = collect.toArray(new Method[collect.size()]);
                if (!collect.isEmpty()) {
                  return new Result(methods, null);
                }
              } catch (SecurityException ex1) {
                Logger.getLogger(MydslCore.class.getName()).log(Level.SEVERE, null, ex1);
              }
            }

          } catch (IllegalArgumentException | IllegalAccessException ex) {

          }
        }
      }
    } else if (key instanceof Integer) {
      return new Result(((List) parent).get((int) key), null);
    } else {
      return new Result(null, new Exception("propertyGet error: key type is invalid."));
    }
    return new Result(null, null);
  }

  private static LastKeyValue getLastKeyValue(Container container, Argument arg, Container root) {
    Object rawArg = arg.rawArg();
    boolean rootIsNull = root == null;
    if (rootIsNull) {
      root = container;
    }
    if (rawArg instanceof String) {
      String stringArg = (String) rawArg;
      if (rawArg.equals("$")) {
        return new LastKeyValue(root);
      } else if (DSL_AVAILABLE_FUNCTIONS.containsKey(stringArg)) {
        return new LastKeyValue(DSL_AVAILABLE_FUNCTIONS.get(stringArg));
      } else if (!stringArg.contains(".") && !stringArg.contains(("["))) {
        return new LastKeyValue(stringArg);
      } else {
        Object cursor = container;
        String remainStr = stringArg;
        if (rootIsNull) {
          Matcher matcher = firstValuePattern.matcher(remainStr);
          if (matcher.find()) {
            LastKeyValue lastKeyValue = getLastKeyValue(container, new Argument(matcher.group(1)), null);
            if (lastKeyValue.hasError()) {
              return lastKeyValue;
            }
            Object firstValue = lastKeyValue.value();
            if (firstValue != null) {
              cursor = firstValue;
              remainStr = matcher.group(2);
            } else {
              return new LastKeyValue(null, stringArg);
            }
          } else {
            //TBD
          }
        }
        while (true) {
          Matcher nextKeyMatch = nextKeyPattern.matcher(remainStr);
          if (nextKeyMatch.find()) {
            String arrayKeyStr = nextKeyMatch.group(2);
            String periodKeyStr = nextKeyMatch.group(3);
            String remain = nextKeyMatch.group(4);
            Object nextKey;
            if (periodKeyStr != null) {
              LastKeyValue nextKeyResult;
              if (arrayKeyStr != null) {
                nextKeyResult = getLastKeyValue(root, new Argument(arrayKeyStr), null);
                if (nextKeyResult.hasError()) {
                  return nextKeyResult;
                }
              } else {
                nextKeyResult = getLastKeyValue(root, new Argument(periodKeyStr), null);
                if (nextKeyResult.hasError()) {
                  return nextKeyResult;
                }
              }
              if (nextKeyResult.key.equals("")) {
                nextKey = nextKeyResult.value;
              } else if (nextKeyResult.key == null) {
                nextKey = null;
              } else {
                nextKey = propertyGet(nextKeyResult.value, nextKeyResult.key).value();
              }
            } else {
              Result res = new Argument(arrayKeyStr).evaluate(container);
              if (res.hasError()) {
                return new LastKeyValue(null, null, res.error());
              } else {
                nextKey = res.value();
              }
            }
            if (remain.equals("")) {
              return new LastKeyValue(nextKey, cursor, null);
            } else {
              Result res = propertyGet(cursor, nextKey);
              if (res.hasError()) {
                return new LastKeyValue(null, null, res.error());
              } else {
                cursor = res.value();
              }
              remainStr = remain;
            }
          } else {
            return new LastKeyValue(null, null, null);
          }
        }
      }
    } else {
      Result res = arg.evaluate(container);
      if (res.hasError()) {
        return new LastKeyValue(null, null, res.error());
      } else {
        return new LastKeyValue(res.value());
      }
    }
  }

  static {
    DSL_FUNCTIONS.put("print", (Container container, Argument... args) -> {
      System.out.println(args[0].evaluate(container).value());
      return new Result(null, null);
    });
    DSL_FUNCTIONS.put("get", (Container container, Argument... args) -> {
      Argument firstArg = args[0];
      if (args.length > 1) {
        args = Arrays.copyOfRange(args, 1, args.length);
      } else {
        args = new Argument[]{};
      }
      LastKeyValue lastKeyValue = getLastKeyValue(container, firstArg, null);
      if (lastKeyValue.hasError()) {
        return new Result(null, lastKeyValue.error);
      }
      Object key = lastKeyValue.key;
      Object parentValue = lastKeyValue.value;
      Object defaultValue = null;
      if (args.length > 0) {
        Argument lastArg = args[args.length - 1];
        if (!(lastArg.rawArg() instanceof String)) {
          args = Arrays.copyOf(args, args.length - 1);
          Result res = lastArg.evaluate(container);
          if (res.hasError()) {
            return res;
          }
          defaultValue = res.value();
        }
      }
      if (parentValue != null) {
        if (key == null) {
          return new Result(parentValue, null);
        } else {
          Object cursor = null;
          if (key == "") {
            cursor = parentValue;
          } else {
            if (key instanceof String) {
              String stringKey = (String) key;
              try {
                int intKey = Integer.parseInt(stringKey);
                cursor = ((List) parentValue).get(intKey);
              } catch (NumberFormatException e) {
                if (parentValue instanceof Map) {
                  cursor = ((Map) parentValue).get(key);
                }
              }
            } else if (key instanceof Integer) {
              cursor = ((List) parentValue).get((int) key);
            }
          }
          while (args.length > 0) {
            Argument shiftArg = args[0];
            if (args.length > 1) {
              args = Arrays.copyOfRange(args, 1, args.length);
            } else {
              args = new Argument[]{};
            }
            Result res = shiftArg.evaluate(container);
            if (res.hasError()) {
              return res;
            }
            if (cursor instanceof Map) {
              cursor = ((Map) cursor).get(key);
            } else if (cursor instanceof List) {
              cursor = ((List) cursor).get((int) key);
            }
          }
          if (cursor == null && args.length == 0) {
            return new Result(defaultValue, null);
          }
          return new Result(cursor, null);
        }
      }
      return new Result(null, null);
    });
    DSL_FUNCTIONS.put("set", (Container container, Argument... args) -> {
      Result res = args[1].evaluate(container);
      if (res.hasError()) {
        return res;
      }
      Object evaluated = res.value();
      LastKeyValue lastKeyValue = getLastKeyValue(container, args[0], null);
      if (lastKeyValue.hasError()) {
        return new Result(null, lastKeyValue.error);
      }
      Object key = lastKeyValue.key;
      Object parentValue = lastKeyValue.value;
      if (parentValue != null && key != null && !key.equals("")) {
        if (key instanceof String) {
          String stringKey = (String) key;
          try {
            int intKey = Integer.parseInt(stringKey);
            ((List) parentValue).set(intKey, evaluated);
          } catch (NumberFormatException e) {
            ((Map) parentValue).put(key, evaluated);
          }
        } else if (key instanceof Integer) {
          ((List) parentValue).set((int) key, evaluated);
        }
      }
      return new Result(null, null);
    });

    DSL_FUNCTIONS.put("function", (Container container, Argument... args) -> {
      List argumentNames = (List) args[0].rawArg();
      Argument process = args[1];
      if (args.length > 2) {
        // TBD
      }
      return new Result((VarArgFunction) (Object... newArgs) -> {
        Container newContainer = new Container();
        for (int i = 0; i < Math.min(newArgs.length, argumentNames.size()); i++) {
          newContainer.put((String) argumentNames.get(i), newArgs[i]);
        }
        Result res = process.evaluate(newContainer);
        if (res.hasError()) {
          return res.error();
        } else {
          return res.value();
        }
      }, null);
    });

    DSL_FUNCTIONS.put("do", (Container container, Argument... args) -> {
      Argument firstArg = args[0];
      if (args.length > 1) {
        args = Arrays.copyOfRange(args, 1, args.length);
      } else {
        args = new Argument[]{};
      }
      LastKeyValue lastKeyValue = getLastKeyValue(container, firstArg, null);
      if (lastKeyValue.hasError()) {
        return new Result(null, lastKeyValue.error);
      }
      Object key = lastKeyValue.key;
      Object parentValue = lastKeyValue.value;
      if (parentValue == null || key == null) {
        return new Result(null, null);
      }
      Object cursor;
      Object previous = null;
      if (key.equals("")) {
        cursor = parentValue;
      } else {
        previous = parentValue;
        cursor = propertyGet(parentValue, key).value();
      }
      while (!(cursor instanceof VarArgFunction) && !(cursor instanceof Method[]) && !(cursor instanceof Method) && args.length > 0) {
        Argument nextArg = args[0];
        if (args.length > 1) {
          args = Arrays.copyOfRange(args, 1, args.length);
        } else {
          args = new Argument[]{};
        }
        Result res = nextArg.evaluate(container);
        if (res.hasError()) {
          return res;
        }
        previous = cursor;
        cursor = propertyGet(cursor, res.value()).value();
        if (cursor == null) {
          break;
        }
      }
      if (cursor instanceof VarArgFunction || cursor instanceof Method[] || cursor instanceof Method) {
        Object[] invokeArgs = new Object[]{};
        if (args.length > 0) {
          Result res = evaluateAll(args, container);
          if (res.hasError()) {
            return res;
          } else {
            invokeArgs = (Object[]) res.value();
          }
        }
        if (cursor instanceof VarArgFunction) {
          // TBD
          return new Result(((VarArgFunction) cursor).apply(invokeArgs), null);
        } else if (cursor instanceof Method[] && previous != null) {
          try {
            int expectParameterCount = invokeArgs.length;
            Optional<Method> matchMethod = Stream.of((Method[]) cursor).filter((method) -> {
              return method.getParameterCount() == expectParameterCount;
            }).findFirst();
            if (matchMethod.isPresent()) {
              return new Result(matchMethod.get().invoke(previous, invokeArgs), null);
            } else {
              return new Result(null, new Throwable("no method found."));
            }

          } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
            Logger.getLogger(MydslCore.class.getName()).log(Level.SEVERE, null, ex);
          }
        } else if (cursor instanceof Method) {
          try {
            return new Result(((Method) cursor).invoke(previous, invokeArgs), null);
          } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
            Logger.getLogger(MydslCore.class.getName()).log(Level.SEVERE, null, ex);
          }
        }
      }
      return new Result(null, null);
    });
    DSL_FUNCTIONS.put("new", (Container container, Argument... args) -> {
      try {
        Result res = args[0].evaluate(container);
        if (res.hasError()) {
          return res;
        }
        Class clazz = Class.forName((String) res.value());
        if (args.length > 1) {
          args = Arrays.copyOfRange(args, 1, args.length);
        } else {
          args = new Argument[]{};
        }
        Result evaluateAll = evaluateAll(args, container);
        if (evaluateAll.hasError()) {
          return evaluateAll;
        }
        Object[] values = (Object[]) evaluateAll.value();
        List<? extends Class<?>> classes = Stream.of(values).map((Object o) -> o.getClass()).collect(Collectors.toList());
        List<Constructor> collect = Stream.of(clazz.getConstructors()).filter((constructor) -> {
          Class[] parameterTypes = constructor.getParameterTypes();
          if (parameterTypes.length == classes.size()) {
            for (int i = 0; i < parameterTypes.length; i++) {
              Class<?> get = classes.get(i);
              Class parameterType = parameterTypes[i];
              if (get == parameterType) {
              } else if (parameterType == classMap.get(get)) {
              } else {
                return false;
              }
            }
            return true;
          } else {
            return false;
          }
        }).collect(Collectors.toList());
        if (collect.isEmpty()) {
          return new Result(null, new Exception("no constructor found."));
        } else if (collect.size() > 1) {
          return new Result(null, new Exception("multiple constructor found."));
        } else {
          try {
            return new Result(collect.get(0).newInstance(values), null);
          } catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
            return new Result(null, ex);
          }
        }
      } catch (ClassNotFoundException ex) {
        return new Result(null, ex);
      }
    });
    DSL_FUNCTIONS.put("handler", (Container container, Argument... args) -> {
      String path = (String) args[0].rawArg();
      String method = (String) args[1].rawArg();
      RoutingHandler router = (RoutingHandler) container.get("router");
      if (method.equals("get")) {
        router.get(path, (final HttpServerExchange exchange) -> {
          exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/plain");
          exchange.getResponseSender().send("Hello World");
        });
      }
      return new Result(null, null);
    });
  }

  public static boolean hasFunction(String name) {
    return DSL_FUNCTIONS.containsKey(name);
  }

  public static DslFunction dslFunctions(String name) {
    return DSL_FUNCTIONS.get(name);
  }
}
