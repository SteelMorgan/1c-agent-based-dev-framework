package io.github.onec.xmlgen.model;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Генератор ID для элементов формы.
 * 
 * ID элементов формы — это auto-increment счётчик, начинающийся с 1.
 * ID уникальны в пределах секции (элементы, реквизиты, команды нумеруются НЕЗАВИСИМО).
 * 
 * Специальные ID:
 * - AutoCommandBar всегда имеет id="-1"
 */
public class IdGenerator {
    
    private final AtomicInteger counter;
    
    /**
     * Создать генератор с начальным значением 1.
     */
    public IdGenerator() {
        this(1);
    }
    
    /**
     * Создать генератор с заданным начальным значением.
     */
    public IdGenerator(int start) {
        this.counter = new AtomicInteger(start);
    }
    
    /**
     * Получить следующий ID.
     */
    public int next() {
        return counter.getAndIncrement();
    }
    
    /**
     * Сбросить счётчик на начальное значение.
     */
    public void reset() {
        counter.set(1);
    }
    
    /**
     * Получить текущее значение счётчика (без инкремента).
     */
    public int current() {
        return counter.get();
    }
}
