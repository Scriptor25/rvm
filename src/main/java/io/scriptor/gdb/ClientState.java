package io.scriptor.gdb;

import java.nio.ByteBuffer;

public final class ClientState {

    public final ByteBuffer buffer = ByteBuffer.allocateDirect(0x2000);
    public final StringBuilder packet = new StringBuilder();
    public final StringBuilder packetChecksum = new StringBuilder();

    public boolean append = false;
    public boolean end = false;

    public int checksum;

    public boolean noack = false;
}
