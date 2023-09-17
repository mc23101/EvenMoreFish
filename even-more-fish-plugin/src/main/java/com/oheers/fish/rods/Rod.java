package com.oheers.fish.rods;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class Rod implements Serializable {

    String display;

    List<String> lore=new ArrayList<>();

    Map<String, String> nbt=new HashMap<>();

    public String getDisplay() {
        return display;
    }

    public void setDisplay(String display) {
        this.display = display;
    }

    public List<String> getLore() {
        return lore;
    }

    public void setLore(List<String> lore) {
        this.lore = lore;
    }

    public Map<String, String> getNbt() {
        return nbt;
    }

    public void setNbt(Map<String, String> nbt) {
        this.nbt = nbt;
    }
}
