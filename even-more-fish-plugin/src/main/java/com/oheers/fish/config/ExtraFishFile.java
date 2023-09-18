package com.oheers.fish.config;

import com.oheers.fish.EvenMoreFish;
import com.oheers.fish.fishing.items.Fish;
import com.oheers.fish.fishing.items.Names;
import com.oheers.fish.fishing.items.Rarity;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ExtraFishFile {

    private final EvenMoreFish plugin;

    private Map<String,FileConfiguration> extraFishConfig=new HashMap<>();

    public ExtraFishFile(EvenMoreFish plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload(){
        File dir=new File(plugin.getDataFolder(),"extrafish");
        if(!dir.exists()){
            dir.mkdirs();
        }
        for(File file:dir.listFiles()){
            loadConfig(file);
        }
        EvenMoreFish.extraFishFile=this;
    }

    private void loadConfig(File file){
        FileConfiguration configuration=new YamlConfiguration();
        try {
            configuration.load(file);
        } catch (IOException | InvalidConfigurationException e) {
            throw new RuntimeException(e);
        }
        String name = configuration.getRoot().getString("name");
        if(name==null||name.length()==0||EvenMoreFish.extraFishCollection.containsKey(name)){
            throw new RuntimeException(file.getPath()+" 中的唯一标识名称:"+name+"已经存在");
        }
        extraFishConfig.put(name,configuration);
        Names names=new Names();
        Map<Rarity,List<Fish>> fishs=new HashMap<>();
        names.loadRarities(configuration,EvenMoreFish.raritiesFile.getConfig(),fishs);
        EvenMoreFish.extraFishCollection.put(name,fishs);
    }

    public Map<String, FileConfiguration> getExtraFishConfig() {
        return extraFishConfig;
    }
}
