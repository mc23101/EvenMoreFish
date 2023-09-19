package com.oheers.fish.gui;

import com.oheers.fish.EvenMoreFish;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class BatisGUI {

    public BatisGUI() {
    }


    public void display(Player player){
       Inventory inventory= Bukkit.createInventory(null,54);
       for(String key: EvenMoreFish.baits.keySet()){
           ItemStack itemStack = EvenMoreFish.baits.get(key).create(null);
           inventory.addItem(itemStack);
       }
       player.openInventory(inventory);
    }

}
