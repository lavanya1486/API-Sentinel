package dev.apisentinel.service;

import java.net.UnknownHostException;

public class BlockedDnsException extends UnknownHostException {
    public BlockedDnsException() { super("Outbound destination is blocked by policy"); }
}
