package com.oheers.fish;

import com.oheers.fish.addons.AddonManager;
import com.oheers.fish.addons.DefaultAddons;
import com.oheers.fish.api.EMFAPI;
import com.oheers.fish.api.plugin.EMFPlugin;
import com.oheers.fish.baits.Bait;
import com.oheers.fish.baits.BaitApplicationListener;
import com.oheers.fish.competition.AutoRunner;
import com.oheers.fish.competition.Competition;
import com.oheers.fish.competition.CompetitionQueue;
import com.oheers.fish.competition.JoinChecker;
import com.oheers.fish.config.*;
import com.oheers.fish.config.messages.Messages;
import com.oheers.fish.database.*;
import com.oheers.fish.events.*;
import com.oheers.fish.exceptions.InvalidTableException;
import com.oheers.fish.fishing.FishingProcessor;
import com.oheers.fish.fishing.items.Fish;
import com.oheers.fish.fishing.items.Names;
import com.oheers.fish.fishing.items.Rarity;
import com.oheers.fish.gui.FillerStyle;
import com.oheers.fish.rods.Rod;
import com.oheers.fish.selling.InteractHandler;
import com.oheers.fish.selling.SellGUI;
import com.oheers.fish.utils.AntiCraft;
import com.oheers.fish.utils.HeadDBIntegration;
import com.oheers.fish.utils.ItemFactory;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import de.tr7zw.changeme.nbtapi.NBTCompound;
import de.tr7zw.changeme.nbtapi.NBTItem;
import me.arcaniax.hdb.api.HeadDatabaseAPI;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.permission.Permission;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;
import org.bstats.charts.SingleLineChart;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class EvenMoreFish extends JavaPlugin implements EMFPlugin {
    private Random random = new Random();

    public static final int METRIC_ID = 11054;
    public static final int MSG_CONFIG_VERSION = 16;
    public static final int MAIN_CONFIG_VERSION = 14;
    public static final int COMP_CONFIG_VERSION = 1;
    public static FishFile fishFile;
    public static RaritiesFile raritiesFile;
    public static BaitFile baitFile;

    public static RodFile rodFile;

    public static Map<String,Rod> rodMap=new HashMap<>();

    public static Messages msgs;

    public static MainConfig mainConfig;
    public static CompetitionConfig competitionConfig;
    public static Xmas2022Config xmas2022Config;
    public static GUIConfig guiConfig;
    public static List<String> competitionWorlds = new ArrayList<>();
    public static Permission permission = null;
    public static Economy econ = null;
    public static Map<Integer, Set<String>> fish = new HashMap<>();
    public static Map<String, Bait> baits = new HashMap<>();
    public static Map<Rarity, List<Fish>> fishCollection = new HashMap<>();
    public static Rarity xmasRarity;
    public static final Map<Integer, Fish> xmasFish = new HashMap<>();
    public static List<UUID> disabledPlayers = new ArrayList<>();
    public static ItemStack customNBTRod;
    public static boolean checkingEatEvent;
    public static boolean checkingIntEvent;
    // Do some fish in some rarities have the comp-check-exempt: true.
    public static boolean raritiesCompCheckExempt = false;
    public static Competition active;
    public static CompetitionQueue competitionQueue;
    public static Logger logger;
    public static PluginManager pluginManager;
    public static List<SellGUI> guis;
    public static int metric_fishCaught = 0;
    public static int metric_baitsUsed = 0;
    public static int metric_baitsApplied = 0;
    // this is for pre-deciding a rarity and running particles if it will be chosen
    // it's a work-in-progress solution and probably won't stick.
    public static Map<UUID, Rarity> decidedRarities;
    public static boolean isUpdateAvailable;
    public static boolean usingPAPI;
    public static boolean usingMcMMO;
    public static boolean usingHeadsDB;

    public static WorldGuardPlugin wgPlugin;
    public static String guardPL;
    public static boolean papi;
    public static DatabaseV3 databaseV3;
    public static HeadDatabaseAPI HDBapi;
    private static EvenMoreFish instance;
    public static FillerStyle guiFillerStyle;
    private EMFAPI api;

    private AddonManager addonManager;

    public static EvenMoreFish getInstance() {
        return instance;
    }

    public AddonManager getAddonManager() {
        return addonManager;
    }

    @Override
    public void onEnable() {
        instance = this;
        this.api = new EMFAPI();


        guis = new ArrayList<>();
        decidedRarities = new HashMap<>();

        logger = getLogger();
        pluginManager = getServer().getPluginManager();

        getConfig().options().copyDefaults();
        saveDefaultConfig();

        mainConfig = new MainConfig(this);
        msgs = new Messages(this);

        //加载其他依赖插件
        saveAdditionalDefaultAddons();
        this.addonManager = new AddonManager(this);
        this.addonManager.load();

        //加载配置文件
        fishFile = new FishFile(this);
        rodFile=new RodFile(this);
        raritiesFile = new RaritiesFile(this);
        baitFile = new BaitFile(this);
        competitionConfig = new CompetitionConfig(this);
        xmas2022Config = new Xmas2022Config(this);
        if (mainConfig.debugSession()) {
            guiConfig = new GUIConfig(this);
        }
        if (guiConfig != null) {
            guiFillerStyle = guiConfig.getFillerStyle("main-menu");
        }

        usingPAPI = getServer().getPluginManager().getPlugin("PlaceholderAPI") != null;

        if (mainConfig.requireNBTRod()) {
            customNBTRod = createCustomNBTRod();
        }

        if (mainConfig.isEconomyEnabled()) {
            // could not set up economy.
            if (!setupEconomy()) {
                EvenMoreFish.logger.log(Level.WARNING, "EvenMoreFish won't be hooking into economy. If this wasn't by choice in config.yml, please install Economy handling plugins.");
            }
        }

        if (!setupPermissions()) {
            Bukkit.getServer().getLogger().log(Level.SEVERE, "EvenMoreFish couldn't hook into Vault permissions. Disabling to prevent serious problems.");
            getServer().getPluginManager().disablePlugin(this);
        }

        // checks against both support region plugins and sets an active plugin (worldguard is priority)
        if (checkWG()) {
            guardPL = "worldguard";
        } else if (checkRP()) {
            guardPL = "redprotect";
        }

        competitionWorlds = competitionConfig.getRequiredWorlds();

        Names names = new Names();
        names.loadRarities(fishFile.getConfig(), raritiesFile.getConfig());
        names.loadBaits(baitFile.getConfig());

        if (!names.regionCheck && mainConfig.getAllowedRegions().isEmpty()) {
            guardPL = null;
        }

        competitionQueue = new CompetitionQueue();
        competitionQueue.load();

        // async check for updates on the spigot page
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            isUpdateAvailable = checkUpdate();
            try {
                checkConfigVers();
            } catch (IOException exception) {
                logger.log(Level.WARNING, "Could not update messages.yml");
            }
        });

        listeners();
        commands();

        if (!mainConfig.debugSession()) {
            metrics();
        }

        AutoRunner.init();

        wgPlugin = getWorldGuard();
        checkPapi();

        if (mainConfig.databaseEnabled() && mainConfig.doingExperimentalFeatures()) {

            DataManager.init();

            databaseV3 = new DatabaseV3(this);
            //load user reports into cache
            new BukkitRunnable() {
                @Override
                public void run() {
                    EvenMoreFish.databaseV3.createTables(false);
                    for (Player player : getServer().getOnlinePlayers()) {
                        UserReport playerReport = databaseV3.readUserReport(player.getUniqueId());
                        if (playerReport == null) {
                            EvenMoreFish.logger.warning("Could not read report for player (" + player.getUniqueId() + ")");
                            continue;
                        }
                        DataManager.getInstance().putUserReportCache(player.getUniqueId(), playerReport);
                    }
                }
            }.runTaskAsynchronously(this);

        }

        logger.log(Level.INFO, "EvenMoreFish by Oheers : Enabled");
    }

    @Override
    public void onDisable() {
        terminateSellGUIS();
        saveUserData();

        // Ends the current competition in case the plugin is being disabled when the server will continue running
        if (Competition.isActive()) {
            active.end();
        }

        if (mainConfig.databaseEnabled() && mainConfig.doingExperimentalFeatures()) {
            databaseV3.shutdown();
        }
        logger.log(Level.INFO, "EvenMoreFish by Oheers : Disabled");
    }

    private void saveAdditionalDefaultAddons() {
        if (!mainConfig.useAdditionalAddons()) {
            return;
        }

        for (final String fileName : Arrays.stream(DefaultAddons.values())
                .map(DefaultAddons::getFullFileName)
                .collect(Collectors.toList())) {
            final File addonFile = new File(getDataFolder(), "addons/" + fileName);
            final File jarFile = new File(getDataFolder(), "addons/" + fileName.replace(".addon", ".jar"));
            if (!jarFile.exists()) {
                try {
                    this.saveResource("addons/" + fileName, true);
                    addonFile.renameTo(jarFile);
                } catch (IllegalArgumentException e) {
                    debug(Level.WARNING, String.format("Default addon %s does not exist.", fileName));
                }
            }
        }
    }

    public static void debug(final String message) {
        debug(Level.INFO, message);
    }

    public static void debug(final Level level, final String message) {
        getInstance().getLogger().log(level, () -> message);
    }

    private void listeners() {

        getServer().getPluginManager().registerEvents(new JoinChecker(), this);
        getServer().getPluginManager().registerEvents(new FishingProcessor(), this);
        getServer().getPluginManager().registerEvents(new InteractHandler(this), this);
        getServer().getPluginManager().registerEvents(new UpdateNotify(), this);
        getServer().getPluginManager().registerEvents(new SkullSaver(), this);
        getServer().getPluginManager().registerEvents(new BaitApplicationListener(), this);

        optionalListeners();
    }

    private void optionalListeners() {
        if (checkingEatEvent) {
            getServer().getPluginManager().registerEvents(FishEatEvent.getInstance(), this);
        }

        if (checkingIntEvent) {
            getServer().getPluginManager().registerEvents(FishInteractEvent.getInstance(), this);
        }

        if (mainConfig.blockCrafting()) {
            getServer().getPluginManager().registerEvents(new AntiCraft(), this);
        }

        if (Bukkit.getPluginManager().getPlugin("mcMMO") != null) {
            usingMcMMO = true;
            if (mainConfig.disableMcMMOTreasure()) {
                getServer().getPluginManager().registerEvents(McMMOTreasureEvent.getInstance(), this);
            }
        }

        if (Bukkit.getPluginManager().getPlugin("HeadDatabase") != null) {
            usingHeadsDB = true;
            getServer().getPluginManager().registerEvents(new HeadDBIntegration(), this);
        }

        if (Bukkit.getPluginManager().getPlugin("AureliumSkills") != null) {
            if (mainConfig.disableAureliumSkills()) {
                getServer().getPluginManager().registerEvents(AureliumSkillsFishingEvent.getInstance(), this);
            }
        }
    }

    private void metrics() {
        Metrics metrics = new Metrics(this, METRIC_ID);

        metrics.addCustomChart(new SingleLineChart("fish_caught", () -> {
            int returning = metric_fishCaught;
            metric_fishCaught = 0;
            return returning;
        }));

        metrics.addCustomChart(new SingleLineChart("baits_applied", () -> {
            int returning = metric_baitsApplied;
            metric_baitsApplied = 0;
            return returning;
        }));

        metrics.addCustomChart(new SingleLineChart("baits_used", () -> {
            int returning = metric_baitsUsed;
            metric_baitsUsed = 0;
            return returning;
        }));

        metrics.addCustomChart(new SimplePie("experimental_features", () -> mainConfig.doingExperimentalFeatures() ? "true" : "false"));
    }

    private void commands() {
        getCommand("evenmorefish").setExecutor(new CommandCentre(this));
        CommandCentre.loadTabCompletes();
    }

    private boolean setupPermissions() {
        RegisteredServiceProvider<Permission> rsp = getServer().getServicesManager().getRegistration(Permission.class);
        permission = rsp.getProvider();
        return permission != null;
    }

    private boolean setupEconomy() {
        if (mainConfig.isEconomyEnabled()) {
            RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
            if (rsp == null) {
                return false;
            }
            econ = rsp.getProvider();
            return econ != null;
        } else {
            return false;
        }

    }

    // gets called on server shutdown to simulate all player's closing their /emf shop GUIs
    private void terminateSellGUIS() {
        getServer().getOnlinePlayers().forEach(player -> {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof SellGUI) {
                player.closeInventory();
            }
        });
    }

    private void saveUserData() {
        //really slow, we should execute this via a runnable.
        if (!(EvenMoreFish.mainConfig.doingExperimentalFeatures() && mainConfig.isDatabaseOnline())) {
            return;
        }

        saveFishReports();
        saveUserReports();

        DataManager.getInstance().uncacheAll();
    }

    private void saveFishReports() {
        ConcurrentMap<UUID, List<FishReport>> allReports = DataManager.getInstance().getAllFishReports();
        logger.info("Saving " + allReports.keySet().size() + " fish reports.");
        for (Map.Entry<UUID, List<FishReport>> entry : allReports.entrySet()) {
            databaseV3.writeFishReports(entry.getKey(), entry.getValue());

            try {
                if (!databaseV3.hasUser(entry.getKey(), Table.EMF_USERS)) {
                    databaseV3.createUser(entry.getKey());
                }
            } catch (InvalidTableException exception) {
                logger.log(Level.SEVERE, "Fatal error when storing data for " + entry.getKey() + ", their data in primary storage has been deleted.");
            }
        }
    }

    private void saveUserReports() {
        logger.info("Saving " + DataManager.getInstance().getAllUserReports().size() + " user reports.");
        for (UserReport report : DataManager.getInstance().getAllUserReports()) {
            databaseV3.writeUserReport(report.getUUID(), report);
        }
    }

    public ItemStack createCustomNBTRod() {
        ItemFactory itemFactory = new ItemFactory("nbt-rod-item", false);
        itemFactory.enableDefaultChecks();
        itemFactory.setItemDisplayNameCheck(true);
        itemFactory.setItemLoreCheck(true);
        NBTItem nbtItem = new NBTItem(itemFactory.createItem(null, 0));
        NBTCompound emfCompound = nbtItem.getOrCreateCompound(NbtUtils.Keys.EMF_COMPOUND);
        emfCompound.setBoolean(NbtUtils.Keys.EMF_ROD_NBT, true);
        return nbtItem.getItem();
    }

    public void reload() {

        terminateSellGUIS();

        setupEconomy();

        fish = new HashMap<>();
        fishCollection = new HashMap<>();

        reloadConfig();
        saveDefaultConfig();

        Names names = new Names();
        names.loadRarities(fishFile.getConfig(), raritiesFile.getConfig());
        names.loadBaits(baitFile.getConfig());

        HandlerList.unregisterAll(FishEatEvent.getInstance());
        HandlerList.unregisterAll(FishInteractEvent.getInstance());
        HandlerList.unregisterAll(McMMOTreasureEvent.getInstance());
        optionalListeners();

        mainConfig.reload();
        msgs.reload();
        competitionConfig.reload();
        xmas2022Config.reload();
        if (mainConfig.debugSession()) {
            guiConfig.reload();
        }

        competitionWorlds = competitionConfig.getRequiredWorlds();

        if (mainConfig.requireNBTRod()) {
            customNBTRod = createCustomNBTRod();
        }

        competitionQueue.load();
    }

    // Checks for updates, surprisingly
    private boolean checkUpdate() {


        String[] spigotVersion = new UpdateChecker(this, 91310).getVersion().split("\\.");
        String[] serverVersion = getDescription().getVersion().split("\\.");

        for (int i = 0; i < serverVersion.length; i++) {
            if (i < spigotVersion.length) {
                if (Integer.parseInt(spigotVersion[i]) > Integer.parseInt(serverVersion[i])) {
                    return true;
                }
            } else {
                return false;
            }
        }
        return false;
    }

    private void checkConfigVers() throws IOException {

        if (msgs.configVersion() < MSG_CONFIG_VERSION) {
            ConfigUpdater.updateMessages(msgs.configVersion());
            getLogger().log(Level.WARNING, "Your messages.yml config is not up to date. The plugin may have automatically added the extra features but you may wish to" +
                    " modify them to suit your server.");

            msgs.reload();
        }

        if (mainConfig.configVersion() < MAIN_CONFIG_VERSION) {
            ConfigUpdater.updateConfig(mainConfig.configVersion());
            getLogger().log(Level.WARNING, "Your config.yml config is not up to date. The plugin may have automatically added the extra features but you may wish to" +
                    " modify them to suit your server.");

            reloadConfig();
        }

        if (competitionConfig.configVersion() < COMP_CONFIG_VERSION) {
            getLogger().log(Level.WARNING, "Your competitions.yml config is not up to date. Certain new configurable features may have been added, and without" +
                    " an updated config, you won't be able to modify them. To update, either delete your competitions.yml file and restart the server to create a new" +
                    " fresh one, or go through the recent updates, adding in missing values. https://www.spigotmc.org/resources/evenmorefish.91310/updates/");

            competitionConfig.reload();
        }

        ConfigUpdater.clearUpdaters();
    }

    /* Gets the worldguard plugin, returns null and assumes the player has this functionality disabled if it
       can't find the plugin. */
    private WorldGuardPlugin getWorldGuard() {
        return (WorldGuardPlugin) this.getServer().getPluginManager().getPlugin("WorldGuard");
    }

    private void checkPapi() {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            papi = true;
            new PlaceholderReceiver(this).register();
        }

    }

    private boolean checkRP() {
        Plugin pRP = Bukkit.getPluginManager().getPlugin("RedProtect");
        return (pRP != null);
    }

    private boolean checkWG() {
        Plugin pWG = Bukkit.getPluginManager().getPlugin("WorldGuard");
        return (pWG != null);
    }

    public EMFAPI getAPI() {
        return this.api;
    }

    public Random getRandom() {
        return random;
    }
}
