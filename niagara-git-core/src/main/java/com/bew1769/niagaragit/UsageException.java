package com.bew1769.niagaragit;

/** Bad command line. The CLI exits 2. */
public class UsageException extends Exception {
    public UsageException() {
        super("usage");
    }
}
