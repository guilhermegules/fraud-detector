package com.rinha.frauddetector.adapter.engine;

@FunctionalInterface
public interface Distance<T> {
  float calculate(T a, T b);

}
