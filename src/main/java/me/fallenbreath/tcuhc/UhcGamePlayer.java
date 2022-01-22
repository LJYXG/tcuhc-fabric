/*
 From Gamepiaynmo: https://github.com/Gamepiaynmo/TC-UHC
 */

package me.fallenbreath.tcuhc;

import com.google.common.collect.Maps;
import me.fallenbreath.tcuhc.options.Options;
import me.fallenbreath.tcuhc.task.Taskable;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class UhcGamePlayer extends Taskable {
	
	private static final UhcPlayerManager playerManager = UhcGameManager.instance.getUhcPlayerManager();
	
	private final UUID playerUUID;
	private final String playerName;
	
	protected boolean isAlive;
	private UhcGameTeam team;
	@Nullable
	private UhcGameColor colorSelected = null;
	
	protected int deathTime;
	private BlockPos deathPos = BlockPos.ORIGIN;
	private final PlayerStatistics statistics = new PlayerStatistics();
	
	private int borderReminder;
	
	public UhcGamePlayer(ServerPlayerEntity realPlayer) {
		playerUUID = realPlayer.getUuid();
		playerName = realPlayer.getEntityName();
		isAlive = true;
	}
	
	public UhcGameTeam getTeam() { return team; }
	protected void setTeam(UhcGameTeam team) { this.team = team; }
	public int getDeathTime() { return deathTime; }
	public BlockPos getDeathPos() { return deathPos; }
	public void setColorSelected(@Nullable UhcGameColor color) { colorSelected = color; }
	public Optional<UhcGameColor> getColorSelected() { return Optional.ofNullable(colorSelected); }
	public String getName() { return playerName; }
	public boolean isAlive() { return isAlive; }
	public PlayerStatistics getStat() { return statistics; }
	
	public boolean isSamePlayer(PlayerEntity player) {
		return player != null && playerUUID.equals(player.getUuid());
	}

	public boolean isKing() {
		return this.getTeam() != null && this.getTeam().getKing() == this;
	}
	
	public void setDead(int curTime) {
		if (isAlive) {
			isAlive = false;
			deathTime = curTime;
			deathPos = getRealPlayer().map(Entity::getBlockPos).orElse(BlockPos.ORIGIN);
			statistics.setStat(EnumStat.ALIVE_TIME, Options.instance.getIntegerOptionValue("gameTime") - deathTime);
		}
	}
	
	public void tick() {
		this.updateTasks();
		if (borderReminder > 0) borderReminder--;
	}
	
	public boolean borderRemindCooldown() {
		if (borderReminder == 0) {
			borderReminder = 200;
			return true;
		} else return false;
	}
	
	public Optional<ServerPlayerEntity> getRealPlayer() {
		return playerManager.getPlayerByUUID(playerUUID);
	}

	public void addGhostModeEffect()
	{
		this.getRealPlayer().ifPresent(player -> player.addStatusEffect(new StatusEffectInstance(StatusEffects.INVISIBILITY, Integer.MAX_VALUE, 0, true, false)));
	}

	public enum EnumStat {
		PLAYER_KILLED("玩家击杀"),
		ENTITY_KILLED("实体击杀"),
		DAMAGE_TAKEN("承受的伤害"),
		DAMAGE_DEALT("造成的伤害"),
		FRIENDLY_FIRE("队友之间的伤害"),
		CHEST_FOUND("发现宝箱数量"),
		EMPTY_CHEST_FOUND("发现空宝箱数量"),
		DIAMOND_FOUND("发现钻石的数量"),
		HEALTH_HEALED("恢复的血量"),//原文"Health Healed",此值应该是玩家掉血后通过金屁屁恢复的血量
		ALIVE_TIME("存活时间");
		
		public final String name;
		
		EnumStat(String name) {
			this.name = name;
		}
	}
	
	public static class PlayerStatistics {
		
		private final Map<EnumStat, Float> stats = Maps.newEnumMap(EnumStat.class);
		
		public PlayerStatistics() {
			clear();
		}
		
		public void clear() {
			for (EnumStat stat : EnumStat.values()) {
				stats.put(stat, 0.0f);
			}
		}
		
		public void addStat(EnumStat stat, float value) {
			if (UhcGameManager.instance.isGamePlaying())
				stats.put(stat, stats.get(stat) + value);
		}

		public void setStat(EnumStat stat, float value) {
			if (UhcGameManager.instance.isGamePlaying())
				stats.put(stat, value);
		}
		
		public float getFloatStat(EnumStat stat) {
			return stats.get(stat);
		}
	}

}
