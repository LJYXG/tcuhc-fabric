/*
 From Gamepiaynmo: https://github.com/Gamepiaynmo/TC-UHC
 */

package me.fallenbreath.tcuhc.options;

import com.google.common.collect.Maps;
import me.fallenbreath.tcuhc.UhcGameManager;
import me.fallenbreath.tcuhc.UhcGameManager.EnumMode;
import me.fallenbreath.tcuhc.UhcGamePlayer;
import me.fallenbreath.tcuhc.task.Task;
import net.minecraft.world.Difficulty;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Stream;

public class Options {
	private static final Logger LOGGER = LogManager.getLogger();
	public static Options instance = new Options(new File("uhc.properties"));
	
	private final Map<String, Option> configOptions = Maps.newHashMap();
	private final Properties uhcProperties = new Properties();
	private final File uhcOptionsFile;
	
	public final Task taskSaveProperties = new Task() {
		@Override
		public void onUpdate() {
			Options.this.savePropertiesFile();
		}
		@Override
		public boolean hasFinished() { return false; }
	};
	
	public final Task taskReselectTeam = new Task() {
		@Override
		public void onUpdate() {
			for (UhcGamePlayer player : UhcGameManager.instance.getUhcPlayerManager().getAllPlayers()) {
				player.setColorSelected(null);
				player.getRealPlayer().ifPresent(playermp -> {
					UhcGameManager.instance.getUhcPlayerManager().regiveConfigItems(playermp);
					playermp.setInvulnerable(true);
				});
			}
		}
		@Override
		public boolean hasFinished() { return false; }
	};
	
	private Options(File optionsFile) {
		instance = this;
		uhcOptionsFile = optionsFile;
		//此处仅对部分本人所理解 本人服务器用到的部分进行了汉化 qwq
		addOption(new Option("gameMode", "游戏模式", new OptionType.EnumType(EnumMode.class), EnumMode.NORMAL).addTask(taskReselectTeam).setDescription("UHC 游戏模式, normal 为默认模式, solo 为一人即一个队伍, boss此为solo的一个变种,等等。"));
		addOption(new Option("randomTeams", "随机队伍", new OptionType.BooleanType(), true).addTask(taskReselectTeam).setDescription("决定是随机组队还是手动组队，此选项在单人模式下不起作用！"));
		addOption(new Option("teamCount", "Team Count", new OptionType.IntegerType(2, 8, 1), 4).addTask(taskReselectTeam).setDescription("Count of different teams, only works on normal mode."));

		addOption(new Option("difficulty", "游戏难度", new OptionType.EnumType(Difficulty.class), Difficulty.HARD).setDescription("游戏的难度设置。"));
		addOption(new Option("daylightCycle", "是否永昼", new OptionType.BooleanType(), true).setDescription("是否关闭游戏的日夜循环，即是否永昼。"));
		addOption(new Option("friendlyFire", "队伍内交火", new OptionType.BooleanType(), false).setDescription("是否允许同一队伍玩家互相伤害。"));
		addOption(new Option("teamCollision", "Team Coll", new OptionType.BooleanType(), true).setDescription("Can team members collide with each other."));
		addOption(new Option("greenhandProtect", "新手保护", new OptionType.BooleanType(), false).setDescription("在游戏刚开始的一段时间内免除伤害。"));
		addOption(new Option("forceViewport", "Force View", new OptionType.BooleanType(), true).setDescription("Force viewport on team members after death."));
		addOption(new Option("deathBonus", "Death Bonus", new OptionType.BooleanType(), true).setDescription("Few potion effects on other members after death."));

		addOption(new Option("borderStart", "边界初始值", new OptionType.IntegerType(100, 2000000, 100), 2000).setDescription("世界边界的初始大小。"));
		addOption(new Option("borderEnd", "边界结束值", new OptionType.IntegerType(10, 2000000, 10), 200).setDescription("世界边界缩小结束后的边界大小。"));
		addOption(new Option("borderFinal", "边界最终值", new OptionType.IntegerType(10, 2000000, 10), 50).setDescription("世界边界在下届洞穴关闭后最终的大小。"));

		addOption(new Option("gameTime", "总游戏时间", new OptionType.IntegerType(0, 1000000, 100), 5400).setDescription("游戏的总游戏时长。"));
		addOption(new Option("borderStartTime", "边界开始收缩时间", new OptionType.IntegerType(0, 1000000, 100), 1800).setDescription("世界边界开始收缩的时间。"));
		addOption(new Option("borderEndTime", "边界停止收缩时间", new OptionType.IntegerType(0, 1000000, 100), 4800).setDescription("世界边界停止收缩的时间。"));
		addOption(new Option("netherCloseTime", "下届关闭时间", new OptionType.IntegerType(0, 1000000, 100), 4800).setDescription("下届以及末地关闭的时间。"));
		addOption(new Option("caveCloseTime", "洞穴关闭时间", new OptionType.IntegerType(0, 1000000, 100), 5100).setDescription("洞穴关闭的时间。"));
		addOption(new Option("greenhandTime", "新手保护时间", new OptionType.IntegerType(0, 1000000, 100), 4800).setDescription("新手时间（无敌时间）的时间长度。"));

		addOption(new Option("merchantFrequency", "奸商", new OptionType.FloatType(0.0f, 10.0f, 0.05f), 1.0f).setNeedToSave().setDescription("奸商出现的频率。"));
		addOption(new Option("oreFrequency", "矿物", new OptionType.IntegerType(0, 100, 1), 4).setNeedToSave().setDescription("钻石，青金石，黄金这些矿物的可变频率。"));
		addOption(new Option("chestFrequency", "宝箱", new OptionType.FloatType(0.0f, 10.0f, 0.1f), 1.0f).setNeedToSave().setDescription("出现宝箱的频率。"));
		addOption(new Option("trappedChestFrequency", "空宝箱", new OptionType.FloatType(0.0f, 1.0f, 0.05f), 0.2f).setNeedToSave().setDescription("空宝箱的频率。"));
		addOption(new Option("chestItemFrequency", "Chest Loots", new OptionType.FloatType(0.0f, 10.0f, 0.1f), 1.0f).setNeedToSave().setDescription("宝箱中可变物品的频率。"));
		addOption(new Option("mobCount", "怪物数", new OptionType.IntegerType(10, 300, 10), 70).setNeedToSave().setDescription("调整世界中怪物的数量上限。（没个玩家对应的）"));

		loadPropertiesFile();
		savePropertiesFile();
	}
	
	public void loadPropertiesFile() {
		if (uhcOptionsFile.exists()) {
			try (FileInputStream input = new FileInputStream(uhcOptionsFile)) {
				uhcProperties.load(input);
			} catch (Exception e) {
				LOGGER.warn("Failed to load {}", uhcOptionsFile, e);
			}
		} else {
			LOGGER.warn("{} does not exist", uhcOptionsFile);
		}

		for (Entry<Object, Object> entry : uhcProperties.entrySet()) {
			configOptions.get(entry.getKey()).setInitialValue((String) entry.getValue());
		}
	}
	
	public void savePropertiesFile() {
		try (FileOutputStream output = new FileOutputStream(uhcOptionsFile)) {
			configOptions.values().forEach(opt -> uhcProperties.setProperty(opt.getId(), opt.getStringValue()));
			uhcProperties.store(output, "UHC Game Properties");
		} catch (Exception e) {
			LOGGER.warn("Failed to save {}", this.uhcOptionsFile, e);
		}
	}
	
	private void addOption(Option option) {
		configOptions.put(option.getId(), option);
	}
	
	public Optional<Option> getOption(String option) {
		return Optional.ofNullable(configOptions.get(option));
	}

	public Stream<String> getOptionIdStream() {
		return configOptions.keySet().stream();
	}
	
	public void setOptionValue(String option, Object value) {
		getOption(option).ifPresent(opt -> {
			opt.setValue(value);
		});
	}
	
	public void incOptionValue(String option) {
		getOption(option).ifPresent(Option::incValue);
	}
	
	public void decOptionValue(String option) {
		getOption(option).ifPresent(Option::decValue);
	}
	
	public Object getOptionValue(String option) {
		return getOption(option).map(Option::getValue).orElse(null);
	}
	
	public int getIntegerOptionValue(String option) {
		return (int) getOptionValue(option);
	}
	
	public float getFloatOptionValue(String option) {
		return (float) getOptionValue(option);
	}
	
	public String getStringOptionValue(String option) {
		return (String) getOptionValue(option);
	}
	
	public boolean getBooleanOptionValue(String option) {
		return (boolean) getOptionValue(option);
	}

	public void resetOptions(boolean generate) {
		configOptions.values().stream().filter(opt -> opt.needToSave() == generate).forEach(Option::reset);
	}

}
