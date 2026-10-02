package com.gojolimitless.ability;

/** The five technique slots, in keybind order. */
public enum MoveType {
    BLUE("blue"),
    RED("red"),
    PURPLE("purple"),
    NUKE("nuke"),
    DOMAIN("domain");

    public final String key;

    MoveType(String key) { this.key = key; }

    public static MoveType byId(int id) {
        MoveType[] v = values();
        return id >= 0 && id < v.length ? v[id] : null;
    }
}
