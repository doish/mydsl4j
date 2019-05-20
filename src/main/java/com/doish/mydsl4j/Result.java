/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.doish.mydsl4j;

/**
 *
 * @author uotan
 */
public class Result {

  public Result(Object value, Throwable error) {
    this.value = value;
    this.error = error;
  }
  private final Object value;
  private final Throwable error;

  public boolean hasError() {
    return this.error != null;
  }

  public Object value() {
    return this.value;
  }

  public Throwable error() {
    return this.error;
  }
}
