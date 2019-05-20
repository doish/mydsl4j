/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.doish.mydsl4j;

@FunctionalInterface
public interface VarArgFunction<R, T> {

  R apply(T... args);
}
