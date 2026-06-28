package com.gamersblended.junes.constant;

public enum SignalTypeEnums {
    BROWSE("BROWSE", 1),
    PURCHASE("PURCHASE", 3),
    CART_ADD("CART_ADD", 2);

    private final String name;
    private final int weight;

    SignalTypeEnums(String name, int weight) {
        this.name = name;
        this.weight = weight;
    }

    public String getName() {
        return name;
    }

    public int getWeight() {
        return weight;
    }
}
