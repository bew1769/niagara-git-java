package com.bew1769.niagaragit;

/** How a station path should be treated. Secret wins over noisy. */
public enum Kind {
    SHOW("show"),
    NOISY("noisy"),
    SECRET("secret"),
    UNSAFE("unsafe");

    private final String label;

    Kind(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}
