package com.oheers.fish.config;

import com.oheers.fish.EvenMoreFish;
import com.oheers.fish.rods.Rod;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class RodFile {

    private final EvenMoreFish plugin;
    private FileConfiguration rodConfig;

    public RodFile(EvenMoreFish plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload(){
        File rodsFile = new File(this.plugin.getDataFolder(), "rods.yml");

        if (!rodsFile.exists()) {
            rodsFile.getParentFile().mkdirs();
            this.plugin.saveResource("rods.yml", false);
        }

        this.rodConfig = new YamlConfiguration();

        try {
            this.rodConfig.load(rodsFile);
        } catch (IOException | org.bukkit.configuration.InvalidConfigurationException e) {
            e.printStackTrace();
        }
        loadRods();
        EvenMoreFish.rodFile=this;
    }


    private void loadRods(){
        Map<String,Rod> rodMap=new HashMap<>();
        Set<String> rods = this.rodConfig.getRoot().getKeys(false);
        for (String rod : rods) {
            Rod rodEntity = new Rod();
            if (this.rodConfig.getConfigurationSection(rod).contains("display")) {
                rodEntity.setDisplay(this.rodConfig.getConfigurationSection(rod).getString("display"));
            }
            if(this.rodConfig.getConfigurationSection(rod).contains("lore")){
                rodEntity.setLore((List<String>) this.rodConfig.getConfigurationSection(rod).getList("lore"));
            }
            if(this.rodConfig.getConfigurationSection(rod).contains("nbt")){
                Map<String,String> nbtMap=new HashMap<>();
                Set<String> keys = this.rodConfig.getConfigurationSection(rod+".nbt").getKeys(false);
                for(String nbt:keys){
                    nbtMap.put(nbt,this.rodConfig.getConfigurationSection(rod+".nbt").getString(nbt));
                }
                rodEntity.setNbt(nbtMap);
            }
            EvenMoreFish.rodMap=rodMap;
        }
    }
}
