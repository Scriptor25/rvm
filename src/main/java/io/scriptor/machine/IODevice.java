package io.scriptor.machine;

public interface IODevice extends Device {

    long read(long offset, int size);

    void write(long offset, int size, long value);
}
