package dev.apisentinel.service;

import java.io.IOException;

public class BlockedTargetException extends IOException {
    public BlockedTargetException() { super("Target is blocked by outbound network policy"); }
    public BlockedTargetException(Throwable cause) { super("Target is blocked by outbound network policy", cause); }
}
