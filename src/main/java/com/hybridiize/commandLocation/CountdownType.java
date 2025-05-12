package com.hybridiize.commandLocation;

public enum CountdownType {
    EXP_BAR,
    PLAIN_TEXT,
    BOSS_BAR,
    SMALL_TITLE,
    LARGE_TITLE;

    public static CountdownType fromString(String s) {
        try {
            return CountdownType.valueOf(s.toUpperCase());
        } catch (IllegalArgumentException e) {
            return SMALL_TITLE; // Default if invalid string
        }
    }
}