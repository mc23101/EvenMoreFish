package com.oheers.fish.fishing;

import com.gmail.nossr50.config.experience.ExperienceConfig;
import com.gmail.nossr50.util.player.UserManager;
import com.oheers.fish.EvenMoreFish;
import com.oheers.fish.FishUtils;
import com.oheers.fish.NbtUtils;
import com.oheers.fish.api.EMFFishEvent;
import com.oheers.fish.baits.Bait;
import com.oheers.fish.baits.BaitNBTManager;
import com.oheers.fish.competition.Competition;
import com.oheers.fish.competition.reward.Reward;
import com.oheers.fish.config.messages.ConfigMessage;
import com.oheers.fish.config.messages.Message;
import com.oheers.fish.exceptions.MaxBaitReachedException;
import com.oheers.fish.exceptions.MaxBaitsReachedException;
import com.oheers.fish.fishing.items.Fish;
import com.oheers.fish.fishing.items.Rarity;
import com.oheers.fish.requirements.Requirement;
import com.oheers.fish.requirements.RequirementContext;
import de.tr7zw.changeme.nbtapi.NBTItem;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.logging.Level;

public class FishingProcessor implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST)
    public static void process(PlayerFishEvent event) {
        if (!isCustomFishAllowed(event.getPlayer().getUniqueId())) {
            return;
        }

        ItemStack rodInHand = event.getPlayer().getInventory().getItemInMainHand();
        NBTItem nbtItem = new NBTItem(rodInHand);


        //是否开启检查 require-nbt-rod 的值,如果开启检查，且不符合条件，那么取消钓鱼
        if (EvenMoreFish.mainConfig.requireNBTRod()) {
            //check if player is using the fishing rod with correct nbt value.
            //检查玩家是否使用正确的NBT值的鱼竿。
            if (rodInHand.getType() != Material.AIR) {
                if (Boolean.FALSE.equals(NbtUtils.hasKey(nbtItem, NbtUtils.Keys.EMF_ROD_NBT))) {
                    //tag is null or tag is false
                    return;
                }
            }
        }

        // 是否开启钓鱼权限检查
        if (EvenMoreFish.mainConfig.requireFishingPermission()) {
            //check if player have permssion to fish emf fishes
            //检查玩家是否有钓鱼的权限
            if (!EvenMoreFish.permission.has(event.getPlayer(), "emf.use_rod")) {
                if (event.getState() == PlayerFishEvent.State.FISHING) {//send msg only when throw the lure
                    new Message(ConfigMessage.NO_PERMISSION_FISHING).broadcast(event.getPlayer(), true, false);
                }
                return;
            }
        }


        // 魔改的代码逻辑

        // 判断鱼竿有没有时间加成
        if(NbtUtils.hasKey(nbtItem,NbtUtils.Keys.FISHING_SPEED)){
            String value=NbtUtils.getString(nbtItem,NbtUtils.Keys.FISHING_SPEED);
            if(value!=null){
                int maxTime= (int)Math.floor(EvenMoreFish.mainConfig.getFishingMaxWaitTime()*(1-Float.parseFloat(value)));
                int minTime=(int)Math.floor(EvenMoreFish.mainConfig.getFishingMinWaitTime()*(1-Float.parseFloat(value)));
                event.getHook().setMinWaitTime(minTime);
                event.getHook().setMaxWaitTime(maxTime);
            }
        }


        if (event.getState() == PlayerFishEvent.State.CAUGHT_FISH) {
            ItemStack fish = getFish(event.getPlayer(), event.getHook().getLocation(), event.getPlayer().getInventory().getItemInMainHand(), true, true);

            if (fish == null) {
                return;
            }

            if(NbtUtils.hasKey(nbtItem,NbtUtils.Keys.DOUBLE_DROP)){
                String change=NbtUtils.getString(nbtItem,NbtUtils.Keys.DOUBLE_DROP);
                if(change!=null){
                    Random rand=new Random();
                    float randDouble = rand.nextFloat();
                    if (randDouble<=Float.parseFloat(change)){
                        fish.setAmount(2);
                        event.getPlayer().sendMessage("恭喜你获得双倍奖励!");
                    }
                }
            }

            // replaces the fishing item with a custom evenmorefish fish.
            Item nonCustom = (Item) event.getCaught();
            if (nonCustom != null) {
                if (fish.getType().isAir()) {
                    nonCustom.remove();
                } else {
                    nonCustom.setItemStack(fish);
                }
            }
        } /*else if (event.getState() == PlayerFishEvent.State.FISHING) {
            if (!EvenMoreFish.decidedRarities.containsKey(event.getPlayer().getUniqueId())) {
                EvenMoreFish.decidedRarities.put(event.getPlayer().getUniqueId(), randomWeightedRarity(event.getPlayer(), 1, null));
            }


            if (EvenMoreFish.decidedRarities.get(event.getPlayer().getUniqueId()).isXmas2021()) {

                if (!Objects.equals(EvenMoreFish.xmas2021Config.getParticleMessage(), "none")) {
                    event.getPlayer().sendMessage(FishUtils.translateHexColorCodes(EvenMoreFish.xmas2021Config.getParticleMessage()));
                }

                if (EvenMoreFish.xmas2021Config.doXmas2021Particles()) {
                    ParticleEngine.renderParticles(event.getHook());
                }
            }
            - if the rarity is exposed by having particles showing
             */

        // } else if (event.getState() == PlayerFishEvent.State.REEL_IN) {
            /* For a failed attempt the player needs to have triggered a FISHING which generates a pre-decided rarity.
            if (EvenMoreFish.decidedRarities.get(event.getPlayer().getUniqueId()).isXmas2021()) {
                EvenMoreFish.decidedRarities.remove(event.getPlayer().getUniqueId());
            }

        } */
    }

    public static boolean isCustomFishAllowed(UUID player) {
        return EvenMoreFish.mainConfig.getEnabled() && (competitionOnlyCheck() || EvenMoreFish.raritiesCompCheckExempt) && !EvenMoreFish.disabledPlayers.contains(player);
    }

    /**
     * Chooses a bait without needing to specify a bait to be used. randomWeightedRarity & getFish methods are used to
     * choose the random fish.
     *
     * @param player   The fisher catching the fish.
     * @param location The location of the fisher.
     *                 {@code @returns} A random fish without any bait application.
     */
    public static Fish chooseNonBaitFish(Player player, Location location) {
        Rarity fishRarity = randomWeightedRarity(player, 1, null, EvenMoreFish.fishCollection.keySet());
        if (fishRarity == null) {
            EvenMoreFish.logger.log(Level.SEVERE, "Could not determine a rarity for fish for " + player.getName());
            return null;
        }

        Fish fish = getFish(fishRarity, location, player, 1, null, true);
        if (fish == null) {
            EvenMoreFish.logger.log(Level.SEVERE, "Could not determine a fish for " + player.getName());
            return null;
        }
        fish.setFisherman(player.getUniqueId());
        return fish;
    }

    public static ItemStack getFish(Player player, Location location, ItemStack fishingRod, boolean runRewards, boolean sendMessages) {

        if (!FishUtils.checkRegion(location, EvenMoreFish.mainConfig.getAllowedRegions())) {
            return null;
        }

        if (!FishUtils.checkWorld(location)) {
            return null;
        }

        if (EvenMoreFish.usingMcMMO) {
            if (ExperienceConfig.getInstance().isFishingExploitingPrevented()) {
                if (UserManager.getPlayer(player).getFishingManager().isExploitingFishing(location.toVector())) {
                    return null;
                }
            }
        }

        if (EvenMoreFish.baitFile.getBaitCatchPercentage() > 0) {
            if (new Random().nextDouble() * 100.0 < EvenMoreFish.baitFile.getBaitCatchPercentage()) {
                Bait caughtBait = BaitNBTManager.randomBaitCatch();
                Message message = new Message(ConfigMessage.BAIT_CAUGHT);
                message.setBaitTheme(caughtBait.getTheme());
                message.setBait(caughtBait.getName());
                message.setPlayer(player.getName());
                message.broadcast(player, true, true);

                return caughtBait.create(player);
            }
        }

        Fish fish;

        if (BaitNBTManager.isBaitedRod(fishingRod) && (!EvenMoreFish.baitFile.competitionsBlockBaits() || !Competition.isActive())) {

            Bait applyingBait = BaitNBTManager.randomBaitApplication(fishingRod);
            fish = applyingBait.chooseFish(player, location);
            if (fish.isWasBaited()) {
                fish.setFisherman(player.getUniqueId());
                try {
                    ItemMeta newMeta = BaitNBTManager.applyBaitedRodNBT(fishingRod, applyingBait, -1).getFishingRod().getItemMeta();
                    fishingRod.setItemMeta(newMeta);
                    EvenMoreFish.metric_baitsUsed++;
                } catch (MaxBaitsReachedException | MaxBaitReachedException exception) {
                    exception.printStackTrace();
                }
            } else {
                fish = chooseNonBaitFish(player, location);
            }
        } else {
            fish = chooseNonBaitFish(player, location);
        }

        fish.init();

        if (runRewards && fish.hasFishRewards()) {
            for (Reward fishReward : fish.getFishRewards()) {
                fishReward.run(player, location);
            }
        }

        EMFFishEvent cEvent = new EMFFishEvent(fish, player);
        Bukkit.getPluginManager().callEvent(cEvent);
        if (cEvent.isCancelled()) return null;

        if (sendMessages && !fish.isSilent()) {
            // puts all the fish information into a format that Messages.renderMessage() can print out nicely

            String length = Float.toString(fish.getLength());
            // Translating the colours because some servers store colour in their fish name
            String name = FishUtils.translateHexColorCodes(fish.getName());
            String rarity = FishUtils.translateHexColorCodes(fish.getRarity().getValue());

            Message message = new Message(ConfigMessage.FISH_CAUGHT);
            message.setPlayer(player.getName());
            message.setRarityColour(fish.getRarity().getColour());
            message.setRarity(rarity);
            message.setLength(length);

            EvenMoreFish.metric_fishCaught++;

            if (fish.getDisplayName() != null) message.setFishCaught(fish.getDisplayName());
            else message.setFishCaught(name);

            if (fish.getRarity().getDisplayName() != null) message.setRarity(fish.getRarity().getDisplayName());
            else message.setRarity(rarity);

            if (fish.getLength() == -1) {
                message.setMessage(ConfigMessage.FISH_LENGTHLESS_CAUGHT);
            }

            // Gets whether it's a serverwide announce or not
            if (fish.getRarity().getAnnounce()) {
                // should we only broadcast this information to rod holders?
                FishUtils.broadcastFishMessage(message, false);
            } else {
                // sends it to just the fisher
                message.broadcast(player, true, true);
            }
        }

        try {
            competitionCheck(fish.clone(), player, location);
        } catch (CloneNotSupportedException e) {
            EvenMoreFish.logger.log(Level.SEVERE, "Failed to create a clone of: " + fish);
            e.printStackTrace();
        }

        if (EvenMoreFish.mainConfig.doingExperimentalFeatures() && EvenMoreFish.mainConfig.isDatabaseOnline()) {
            Fish finalFish = fish;
            new BukkitRunnable() {
                @Override
                public void run() {

                   
                        // increases the fish fished count if the fish is already in the db
                        if (EvenMoreFish.databaseV3.hasFishData(finalFish)) {
                            EvenMoreFish.databaseV3.incrementFish(finalFish);

                            // sets the new leader in top fish, if the player has fished a record fish
                            if (EvenMoreFish.databaseV3.getLargestFishSize(finalFish) < finalFish.getLength()) {
                                EvenMoreFish.databaseV3.updateLargestFish(finalFish, player.getUniqueId());
                            }
                        } else {
                            EvenMoreFish.databaseV3.createFishData(finalFish, player.getUniqueId());
                        }

                        EvenMoreFish.databaseV3.handleFishCatch(player.getUniqueId(), finalFish);
                    
//                    catch (SQLException exception) {
//                        EvenMoreFish.logger.log(Level.SEVERE, "Failed SQL operations whilst writing fish catch data for " + player.getUniqueId() + ". Try restarting or contacting support.");
//                        exception.printStackTrace();
//                    }
                }
            }.runTaskAsynchronously(EvenMoreFish.getProvidingPlugin(EvenMoreFish.class));
        }


        return fish.give(-1);
    }

    public static Rarity randomWeightedRarity(Player fisher, double boostRate, Set<Rarity> boostedRarities, Set<Rarity> totalRarities) {

        if (fisher != null && EvenMoreFish.decidedRarities.containsKey(fisher.getUniqueId())) {
            Rarity chosenRarity = EvenMoreFish.decidedRarities.get(fisher.getUniqueId());
            EvenMoreFish.decidedRarities.remove(fisher.getUniqueId());
            return chosenRarity;
        }

        List<Rarity> allowedRarities = new ArrayList<>();


        int idx = 0;

        /* If allowed rarities has objects, it means we've run through and removed the Christmas rarity. Don't run
           through again */
        if (allowedRarities.isEmpty()) {
            if (fisher != null && EvenMoreFish.permission != null) {
                rarityLoop:
                for (Rarity rarity : EvenMoreFish.fishCollection.keySet()) {
                    if (boostedRarities != null && boostRate == -1 && !boostedRarities.contains(rarity)) {
                        continue;
                    }

                    if (!(rarity.getPermission() == null || EvenMoreFish.permission.has(fisher, rarity.getPermission()))) {
                        continue;
                    }

                    List<Requirement> requirements;
                    if ((requirements = rarity.getRequirements()) != null) {
                        RequirementContext context = new RequirementContext();
                        context.setLocation(fisher.getLocation());
                        context.setPlayer(fisher);
                        for (Requirement requirement : requirements) {
                            if (!requirement.requirementMet(context)) continue rarityLoop;
                        }
                    }

                    allowedRarities.add(rarity);
                }
            } else {
                allowedRarities.addAll(totalRarities);
            }
        }

        double totalWeight = 0;



        for (Rarity r : allowedRarities) {
            int addWeight=0;
            if(fisher!=null){
                ItemStack mainHand=fisher.getInventory().getItemInMainHand();
                NBTItem nbtItem=new NBTItem(mainHand);
                if(NbtUtils.hasKey(nbtItem,r.getValue())){
                    Integer value=NbtUtils.getInteger(nbtItem,r.getValue());
                    addWeight=value==null?0:value;
                }
            }
            if (boostRate != -1.0 && boostedRarities != null && boostedRarities.contains(r)) {
                totalWeight += ((r.getWeight()+addWeight) * boostRate);
            } else {
                totalWeight += (r.getWeight()+addWeight);
            }
        }

        for (double r = Math.random() * totalWeight; idx < allowedRarities.size() - 1; ++idx) {
            Rarity rarity=allowedRarities.get(idx);
            int addWeight=0;
            if(fisher!=null){
                ItemStack mainHand=fisher.getInventory().getItemInMainHand();
                NBTItem nbtItem=new NBTItem(mainHand);
                if(NbtUtils.hasKey(nbtItem,rarity.getValue())){
                    Integer value=NbtUtils.getInteger(nbtItem,rarity.getValue());
                    addWeight=value==null?0:value;
                }
            }
            if (boostRate != -1.0 && boostedRarities != null && boostedRarities.contains(rarity)) {
                r -= (rarity.getWeight()+addWeight) * boostRate;
            } else {
                r -= rarity.getWeight()+addWeight;
            }
            if (r <= 0.0) break;
        }

        if (allowedRarities.isEmpty()) {
            EvenMoreFish.logger.log(Level.SEVERE, "There are no rarities for the user " + fisher.getName() + " to fish. They have received no fish.");
            return null;
        }

        if (!Competition.isActive() && EvenMoreFish.raritiesCompCheckExempt) {
            if (allowedRarities.get(idx).hasCompExemptFish()) return allowedRarities.get(idx);
        } else if (Competition.isActive() || !EvenMoreFish.mainConfig.isCompetitionUnique()) {
            return allowedRarities.get(idx);
        }

        return null;
    }

    private static Fish randomWeightedFish(List<Fish> fishList, double boostRate, List<Fish> boostedFish) {
        double totalWeight = 0;

        for (Fish fish : fishList) {
            // when boostRate is -1, we need to guarantee a fish, so the fishList has already been moderated to only contain
            // boosted fish. The other 2 check that the plugin wants the bait calculations too.
            if (boostRate != -1 && boostedFish != null && boostedFish.contains(fish)) {

                if (fish.getWeight() == 0.0d) totalWeight += (1 * boostRate);
                else
                    totalWeight += fish.getWeight() * boostRate;
            } else {
                if (fish.getWeight() == 0.0d) totalWeight += 1;
                else totalWeight += fish.getWeight();
            }
        }

        int idx = 0;
        for (double r = Math.random() * totalWeight; idx < fishList.size() - 1; ++idx) {

            if (fishList.get(idx).getWeight() == 0.0d) {
                if (boostRate != -1 && boostedFish != null && boostedFish.contains(fishList.get(idx))) {
                    r -= 1 * boostRate;
                } else {
                    r -= 1;
                }
            } else {
                if (boostRate != -1 && boostedFish != null && boostedFish.contains(fishList.get(idx))) {
                    r -= fishList.get(idx).getWeight() * boostRate;
                } else {
                    r -= fishList.get(idx).getWeight();
                }
            }

            if (r <= 0.0) break;
        }

        return fishList.get(idx);
    }

    public static Fish getFish(Rarity r, Location l, Player p, double boostRate, List<Fish> boostedFish, boolean doRequirementChecks) {
        if (r == null) return null;
        // will store all the fish that match the player's biome or don't discriminate biomes

        List<Fish> available = new ArrayList<>();

        // 防止/emf admin重载导致插件无法获得稀有性
        if (EvenMoreFish.fishCollection.get(r) == null)
            r = randomWeightedRarity(p, 1, null, EvenMoreFish.fishCollection.keySet());

        if (doRequirementChecks) {
            RequirementContext context = new RequirementContext();
            context.setLocation(l);
            context.setPlayer(p);

            fishLoop:
            for (Fish f : EvenMoreFish.fishCollection.get(r)) {

                if (!(boostRate != -1 || boostedFish == null || boostedFish.contains(f))) {
                    continue;
                }

                List<Requirement> requirements;
                if ((requirements = f.getRequirements()) == null) {
                    available.add(f);
                } else {
                    for (Requirement requirement : requirements) {
                        if (!requirement.requirementMet(context)) continue fishLoop;
                    }
                    available.add(f);
                }
            }
        } else {
            for (Fish f : EvenMoreFish.fishCollection.get(r)) {

                if (!(boostRate != -1 || boostedFish == null || boostedFish.contains(f))) {
                    continue;
                }

                available.add(f);
            }
        }

        // if the config doesn't define any fish that can be fished in this biome.
        if (available.isEmpty()) {
            EvenMoreFish.logger.log(Level.WARNING, "There are no fish of the rarity " + r.getValue() + " that can be fished at (x=" + l.getX() + ", y=" + l.getY() + ", z=" + l.getZ() + ")");
            return null;
        }

        Fish returningFish;

        // 检查是否需要为鱼做权重计算
        returningFish = randomWeightedFish(available, boostRate, boostedFish);

        if (Competition.isActive() || !EvenMoreFish.mainConfig.isCompetitionUnique() || (EvenMoreFish.raritiesCompCheckExempt && returningFish.isCompExemptFish())) {
            return returningFish;
        } else {
            return null;
        }
    }

    // Checks if it should be giving the player the fish considering the fish-only-in-competition option in config.yml
    public static boolean competitionOnlyCheck() {
        if (EvenMoreFish.mainConfig.isCompetitionUnique()) {
            return Competition.isActive();
        } else {
            return true;
        }
    }

    public static void competitionCheck(Fish fish, Player fisherman, Location location) {
        if (Competition.isActive()) {
            if (!EvenMoreFish.competitionWorlds.isEmpty()) {
                if (location.getWorld() != null) {
                    if (!EvenMoreFish.competitionWorlds.contains(location.getWorld().getName())) {
                        return;
                    }
                } else {
                    EvenMoreFish.logger.log(Level.SEVERE, fisherman.getName() + " was unable to be checked for \"general.required-worlds\" in competitions.yml because their world is null.");
                }
            }

            EvenMoreFish.active.applyToLeaderboard(fish, fisherman);
        }
    }
}
