package com.rinha.frauddetector.adapter.engine;

@FunctionalInterface
public interface Distance<T> {
  double calculate(T a, T b);
}
