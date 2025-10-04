package io.scriptor.machine;

public interface IODevice extends Device {

    long begin();

    long end();

    long read(int offset, int size);

    void write(int offset, int size, long value);
}
