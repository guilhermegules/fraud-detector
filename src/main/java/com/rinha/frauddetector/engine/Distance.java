package com.rinha.frauddetector.engine;

@FunctionalInterface
public interface Distance<T> {
  double calculate(T a, T b);
}
