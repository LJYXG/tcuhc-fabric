/*
 From Gamepiaynmo: https://github.com/Gamepiaynmo/TC-UHC
 */

package me.fallenbreath.tcuhc;

import com.google.common.collect.Lists;
import me.fallenbreath.tcuhc.UhcGameManager.EnumMode;
import me.fallenbreath.tcuhc.UhcGamePlayer.EnumStat;
import me.fallenbreath.tcuhc.task.Task;
import me.fallenbreath.tcuhc.task.TaskFindPlayer;
import me.fallenbreath.tcuhc.task.TaskKeepSpectate;
import me.fallenbreath.tcuhc.task.TaskOnce;
import me.fallenbreath.tcuhc.util.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.EntityDamageSource;
import net.minecraft.entity.damage.ProjectileDamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.DyeableItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.scoreboard.AbstractTeam;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.Team;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.LiteralText;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class UhcPlayerManager
{
	private final UhcGameManager gameManager;
	
	private final List<UhcGamePlayer> allPlayerList = Lists.newArrayList();
	private final List<UhcGamePlayer> combatPlayerList = Lists.newArrayList();
	private final List<UhcGamePlayer> observePlayerList = Lists.newArrayList();
	private final List<UhcGameTeam> teams = Lists.newArrayList();
	
	private int playersPerTeam;
	
	public UhcPlayerManager(UhcGameManager manager) {
		gameManager = manager;
	}
	
	public Optional<ServerPlayerEntity> getPlayerByUUID(UUID id) {
		return Optional.ofNullable(gameManager.getServerPlayerManager().getPlayer(id));
	}
	
	public boolean forceFriendlyView(UhcGamePlayer player) {
		if (player.isAlive()) return false;
		if (!gameManager.getOptions().getBooleanOptionValue("forceViewport")) return false;
		return player.getTeam().getAliveCount() != 0;
	}
	
	public UhcGamePlayer getGamePlayer(PlayerEntity player) {
		for (UhcGamePlayer gamePlayer : allPlayerList) {
			if (gamePlayer.isSamePlayer(player))
				return gamePlayer;
		}
		return null;
	}

	public void onPlayerJoin(ServerPlayerEntity player) {
		UhcGamePlayer gamePlayer = getGamePlayer(player);
		if (gamePlayer == null)
			allPlayerList.add(gamePlayer = new UhcGamePlayer(player));
		if (gameManager.isGamePlaying()) {
			if (combatPlayerList.contains(gamePlayer)) {
				if (gamePlayer.isAlive()) {
					player.setGameMode(GameMode.SURVIVAL);
					// on game player rejoins, adds:
					// - 5s WEAKNESS II
					// - 5s SLOWNESS II
					// - 3s BLINDNESS I
					player.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 100, 1));
					player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 100, 1));
					player.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 60, 0));
				}
				else player.setGameMode(GameMode.SPECTATOR);
			} else {
				player.setGameMode(GameMode.SPECTATOR);
				if (!observePlayerList.contains(gamePlayer))
					observePlayerList.add(gamePlayer);
			}
		} else {
			randomSpawnPosition(player);
			resetHealthAndFood(player);
			if (gameManager.hasGameEnded())
				player.setGameMode(GameMode.SPECTATOR);
			else {
				player.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(20);
				player.setGameMode(GameMode.ADVENTURE);
				regiveConfigItems(player);
				if (gameManager.getConfigManager().isConfiguring())
					player.setInvulnerable(true);
			}
		}
	}
	
	public void regiveConfigItems(ServerPlayerEntity player) {
		if (!gameManager.isGamePlaying()) 
			player.inventory.clear();
		if (gameManager.getConfigManager().isConfiguring()) {
			this.getGamePlayer(player).getColorSelected().ifPresent(color -> {
				ItemStack teamItem = getTeamItem(color);
				EquipmentSlot slot = MobEntity.getPreferredEquipmentSlot(teamItem);
				player.equipStack(slot, teamItem);
			});
			if (gameManager.getConfigManager().isOperator(player))
				player.inventory.insertStack(BookNBT.getConfigBook(gameManager));
			player.inventory.insertStack(BookNBT.getPlayerBook(gameManager));
		}
	}
	
	public void regiveAdjustBook(ServerPlayerEntity player, boolean force) {
		Item current = player.inventory.getMainHandStack().getItem();
		ItemStack book = BookNBT.getAdjustBook(gameManager);
		if (current == Items.WRITTEN_BOOK)
			player.inventory.setStack(player.inventory.selectedSlot, book);
		else if (force) player.inventory.insertStack(book);
	}
	
	public void removeAdjustBook(ServerPlayerEntity player) {
		Item current = player.inventory.getMainHandStack().getItem();
		if (current == Items.WRITTEN_BOOK)
			player.inventory.setStack(player.inventory.selectedSlot, ItemStack.EMPTY);
	}
	
	public void refreshConfigBook() {
		gameManager.getConfigManager().getOperator().getRealPlayer().ifPresent(this::regiveConfigItems);
	}
	
	private ItemStack getTeamItem(UhcGameColor color) {
		ItemStack stack = new ItemStack(Items.LEATHER_CHESTPLATE);
		float[] rgb = color.dyeColor.getColorComponents();
		int r = (int) (rgb[0] * 255);
		int g = (int) (rgb[1] * 255);
		int b = (int) (rgb[2] * 255);
		((DyeableItem)Items.LEATHER_CHESTPLATE).setColor(stack, (((r << 8) | g) << 8) | b);
		stack.setCustomName(new LiteralText(color.dyeColor.toString()));
		return stack;
	}
	
	public void randomSpawnPosition(ServerPlayerEntity player) {
		BlockPos pos = SpawnPlatform.getRandomSpawnPosition(UhcGameManager.rand);
		player.updatePosition(pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5);
		player.requestTeleport(player.getPos().getX(), player.getPos().getY(), player.getPos().getZ());
		player.fallDistance = 0.0f;
	}
	
	public void resetHealthAndFood(ServerPlayerEntity player) {
		player.setHealth(player.getMaxHealth());
		player.getHungerManager().add(20, 20);
	}
	
	public Collection<UhcGamePlayer> getAllPlayers() {
		return allPlayerList;
	}
	
	public Iterable<UhcGamePlayer> getCombatPlayers() {
		return combatPlayerList;
	}
	
	public Iterable<UhcGamePlayer> getObservePlayers() {
		return observePlayerList;
	}
	
	public Iterable<UhcGameTeam> getTeams() {
		return teams;
	}
	
	public boolean isObserver(UhcGamePlayer player) {
		return observePlayerList.contains(player);
	}
	
	public void onPlayerChat(ServerPlayerEntity player, String msg) {
		if (msg == null) return;
		if (!gameManager.isGamePlaying()) {
			gameManager.broadcastMessage(chatMessage(player, msg, false));
			return;
		}
		if (msg.startsWith("p ")) {
			gameManager.broadcastMessage(chatMessage(player, msg.substring(2), false));
			return;
		}
		UhcGamePlayer gamePlayer = getGamePlayer(player);
		if (gamePlayer.getTeam() == null || gamePlayer.getTeam().getAliveCount() == 0) {
			gameManager.broadcastMessage(chatMessage(player, msg, false));
			return;
		}
		String message = chatMessage(player, msg, true);
		gamePlayer.getTeam().getPlayers().forEach(other -> other.getRealPlayer().ifPresent(playermp -> playermp.sendMessage(new LiteralText(message), false)));
	}
	
	private String chatMessage(PlayerEntity player, String msg, boolean secret) {
		UhcGamePlayer gamePlayer = getGamePlayer(player);
		Formatting color = gamePlayer.getTeam() == null ? Formatting.WHITE : gamePlayer.getTeam().getTeamColor().chatColor;
		return Formatting.AQUA.toString() + "[" + Formatting.GOLD + (secret ? "队内" : "全服") + Formatting.AQUA.toString() + "]" +
				color + player.getName().getString() + Formatting.YELLOW + ": " + Formatting.WHITE + msg;
	}
	
	public void onPlayerDeath(ServerPlayerEntity player, DamageSource cause) {
		if (gameManager.isGamePlaying()) {
			UhcGamePlayer gamePlayer = getGamePlayer(player);
			if (combatPlayerList.contains(gamePlayer) && gamePlayer.isAlive()) {
				gamePlayer.setDead(gameManager.getGameTimeRemaining());
				player.setGameMode(GameMode.SPECTATOR);
				if (gameManager.getOptions().getBooleanOptionValue("forceViewport")) {
					gameManager.addTask(new TaskKeepSpectate(gamePlayer));
				}
				if (gamePlayer.getTeam().getAliveCount() == 0) {
					gameManager.checkWinner();
				} else {
					this.deadPotionEffects(gamePlayer.getTeam());
				}

				if (UhcGameManager.getGameMode() == EnumMode.KING && gamePlayer.isKing()) {
					gamePlayer.getTeam().getPlayers().forEach(teamMate -> {
						if (teamMate.isAlive()) {
							gameManager.addTask(new TaskOnce(new Task(){
								@Override
								public void onUpdate() {
									teamMate.getRealPlayer().ifPresent(LivingEntity::kill);
								}
							}));
						}
					});
				}
			}
		}
		ItemEntity entityitem = player.dropStack(PlayerItems.getPlayerItem(player.getEntityName(), player.isOnFire()));
		if (entityitem != null)
		{
			entityitem.setPickupDelay(40);
		}
	}
	
	private void deadPotionEffects(UhcGameTeam team) {
		if (gameManager.getOptions().getBooleanOptionValue("deathBonus")) {
			for (UhcGamePlayer player : team.getPlayers()) {
				if (player.isAlive()) {
					gameManager.addTask(new TaskFindPlayer(player) {
						@Override
						public void onFindPlayer(ServerPlayerEntity playermp) {
							playermp.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 300, 1));
							playermp.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 300, 1));
							playermp.addStatusEffect(new StatusEffectInstance(StatusEffects.HASTE, 300, 1));
							if (UhcGameManager.getGameMode().doDeathRegen()) {
								float playerCnt = player.getTeam().getPlayerCount();
								if (playerCnt > 1 && playersPerTeam > 1) {
									float regen = 4 * playersPerTeam * (playersPerTeam - 1) / playerCnt / (playerCnt - 1);
									playermp.heal((float) (regen - Math.floor(regen)));
									playermp.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 25 * (int) Math.floor(regen), 1));
								}
							}
						}
					});
				}
			}
		}
	}
	
	public void onPlayerRespawn(ServerPlayerEntity player) {
		if (gameManager.isGamePlaying()) {
			UhcGamePlayer gamePlayer = getGamePlayer(player);
			if (!gamePlayer.isAlive()) {
				player.setCameraEntity(player);
			}
		} else this.randomSpawnPosition(player);
	}
	//TODO：日后考虑汉化此处
	public void onPlayerDamaged(ServerPlayerEntity player, DamageSource source, float amount) {
		if (gameManager.isGamePlaying()) {
			String msg = String.format("You got %.2f damage from ", amount);
			String byEnding = source.getAttacker() != null ? " by " + source.getAttacker().getName().getString() : "";
			if (source == DamageSource.IN_FIRE) msg += "fire";
			else if (source == DamageSource.LIGHTNING_BOLT) msg += "lightning blot";
			else if (source == DamageSource.ON_FIRE) msg += "fire";
			else if (source == DamageSource.LAVA) msg += "lava";
			else if (source == DamageSource.HOT_FLOOR) msg += "hot floor";
			else if (source == DamageSource.IN_WALL) msg += "suffocating";
			else if (source == DamageSource.CRAMMING) msg += "cramming";
			else if (source == DamageSource.DROWN) msg += "drown";
			else if (source == DamageSource.STARVE) msg += "starve";
			else if (source == DamageSource.CACTUS) msg += "cactus";
			else if (source == DamageSource.FALL) msg += "falling";
			else if (source == DamageSource.FLY_INTO_WALL) msg += "flying into wall";
			else if (source == DamageSource.OUT_OF_WORLD) msg += "out of world";
			else if (source == DamageSource.GENERIC) msg += "unknown";
			else if (source == DamageSource.MAGIC) msg += "magic";
			else if (source == DamageSource.WITHER) msg += "wither";
			else if (source == DamageSource.ANVIL) msg += "anvil";
			else if (source == DamageSource.FALLING_BLOCK) msg += "falling block";
			else if (source == DamageSource.DRAGON_BREATH) msg += "dragon breath";
			else if (source == DamageSource.SWEET_BERRY_BUSH) msg += "sweet berry bush";
			else if (source.getName().startsWith("explosion")) msg += "explosion" + byEnding;
			else if (source instanceof ProjectileDamageSource) msg += source.getSource().getName().getString() + byEnding;
			else if (source instanceof EntityDamageSource) msg += source.getAttacker().getName().getString();
			else msg += source.getName() + byEnding;
			player.sendMessage(new LiteralText(Formatting.RED + msg), false);
			if (source.getAttacker() instanceof ServerPlayerEntity) {
				((ServerPlayerEntity)source.getAttacker()).sendMessage(new LiteralText(String.format("%sYou dealt %.2f damage to %s", Formatting.BLUE, amount, player.getEntityName())), false);
			}
		}
	}
	
	public Entity onPlayerSpectate(ServerPlayerEntity player, Entity target, Entity origin) {
		UhcGamePlayer gamePlayer = getGamePlayer(player);
		if (gamePlayer.isAlive()) return target;
		if (!SpectateTargetUtil.isCapableTarget(gamePlayer, target)) {
			return SpectateTargetUtil.getCapableTarget(gamePlayer, origin);
		}
		return target;
	}
	
	private Optional<UhcGamePlayer> getPlayerByName(String name) {
		return allPlayerList.stream().filter(player -> player.getName().equals(name)).findFirst();
	}
	
	public void killPlayer(String playerName) {
		getPlayerByName(playerName).ifPresent(player -> {
			player.setDead(gameManager.getGameTimeRemaining());
			player.getRealPlayer().ifPresent(playermp -> playermp.setGameMode(GameMode.SPECTATOR));
			if (gameManager.getOptions().getBooleanOptionValue("forceViewport"))
				gameManager.addTask(new TaskKeepSpectate(player));
			if (player.getTeam() != null) {
				if (player.getTeam().getAliveCount() == 0) {
					gameManager.checkWinner();
				}
				else this.deadPotionEffects(player.getTeam());
				gameManager.broadcastMessage(player.getTeam().getTeamColor().chatColor + player.getName() + Formatting.WHITE + " got -1s.");
			}
		});
	}
	
	public void resurrentPlayer(String playerName) {
		getPlayerByName(playerName).ifPresent(player -> {
			player.deathTime = 0;
			player.isAlive = true;
			player.getStat().setStat(EnumStat.ALIVE_TIME, 0);
			player.getRealPlayer().ifPresent(playermp -> playermp.setGameMode(GameMode.SURVIVAL));
			if (UhcGameManager.getGameMode() == EnumMode.GHOST)
				player.addGhostModeEffect();
			if (player.getTeam() != null) {
				gameManager.broadcastMessage(player.getTeam().getTeamColor().chatColor + player.getName() + Formatting.WHITE + " got +1s.");
			}
		});
	}
	
	public boolean formTeams(boolean auto) {
		this.refreshOnlinePlayers();
		return auto ? this.automaticFormTeams() : this.manuallyFormTeams();
	}
	
	private boolean automaticFormTeams() {
		Optional<ServerPlayerEntity> operator = gameManager.getConfigManager().getOperator().getRealPlayer();
		boolean alright = true;
		for (UhcGamePlayer gamePlayer : getAllPlayers()) {
			UhcGameColor color = gamePlayer.getColorSelected().orElse(null);
			if (color == null) {
				gamePlayer.getRealPlayer().ifPresent(player -> player.sendMessage(new LiteralText(Formatting.DARK_RED + "请选择一个队伍加入！其他人正在等你！！"), false));
				operator.ifPresent(player -> player.sendMessage(new LiteralText(Formatting.DARK_RED + gamePlayer.getName()), false));
				alright = false;
			} else {
				if (color == UhcGameColor.WHITE) observePlayerList.add(gamePlayer);
				else combatPlayerList.add(gamePlayer);
			}
		}
		
		if (!alright) {
			operator.ifPresent(player -> player.sendMessage(new LiteralText(Formatting.DARK_RED + "还有玩家尚未做出选择~"), false));
			return false;
		}
		
		teams.clear();
		switch (UhcGameManager.getGameMode()) {
			case NORMAL:
			case KING: {
				int playerCount = combatPlayerList.size();
				int teamCount = gameManager.getOptions().getIntegerOptionValue("teamCount");
				playersPerTeam = playerCount / teamCount + (playerCount % teamCount == 0 ? 0 : 1);
				int morePlayers = playerCount % teamCount;
				int[] randomTeam = new int[playerCount];
				int posCnt = 0;
				for (int i = 0; i < teamCount; i++) {
					for (int j = (morePlayers > 0 && i >= morePlayers ? 1 : 0); j < playersPerTeam; j++)
						randomTeam[posCnt++] = i;
					teams.add(new UhcGameTeam().setColorTeam(UhcGameColor.getColor(i)));
				}
				for (int i = 0; i < playerCount; i++) {
					int pos = UhcGameManager.rand.nextInt(playerCount - i) + i;
					int temp = randomTeam[i];
					randomTeam[i] = randomTeam[pos];
					randomTeam[pos] = temp;
					teams.get(randomTeam[i]).addPlayer(combatPlayerList.get(i));
				}
				break;
			}
			case SOLO:
			case GHOST: {
				combatPlayerList.stream().map(player -> new UhcGameTeam().setPlayerTeam(player)).forEach(teams::add);
				playersPerTeam = 1;
				break;
			}
			case BOSS: {
				UhcGamePlayer boss = combatPlayerList.get(UhcGameManager.rand.nextInt(combatPlayerList.size()));
				teams.add(new UhcGameTeam().setColorTeam(UhcGameColor.RED).addPlayer(boss));
				UhcGameTeam team = new UhcGameTeam().setColorTeam(UhcGameColor.BLUE);
				combatPlayerList.stream().filter(player -> player != boss).forEach(team::addPlayer);
				teams.add(team);
				playersPerTeam = combatPlayerList.size() - 1;
				break;
			}
		}
		return true;
	}
	
	private boolean manuallyFormTeams() {
		Optional<ServerPlayerEntity> operator = gameManager.getConfigManager().getOperator().getRealPlayer();
		boolean alright = true;
		for (UhcGamePlayer gamePlayer : getAllPlayers()) {
			UhcGameColor color = gamePlayer.getColorSelected().orElse(null);
			if (color == null) {
				gamePlayer.getRealPlayer().ifPresent(player -> player.sendMessage(new LiteralText(Formatting.DARK_RED + "请选择一个队伍加入！其他人正在等你！！"), false));
				operator.ifPresent(player -> player.sendMessage(new LiteralText(Formatting.DARK_RED + gamePlayer.getName()), false));
				alright = false;
			} else {
				if (color == UhcGameColor.WHITE) observePlayerList.add(gamePlayer);
				else combatPlayerList.add(gamePlayer);
			}
		}
		
		if (!alright) {
			operator.ifPresent(player -> player.sendMessage(new LiteralText(Formatting.DARK_RED + "还有玩家尚未做出选择~"), false));
			return false;
		}
		
		teams.clear();
		switch (UhcGameManager.getGameMode()) {
			case NORMAL:
			case KING: {
				int playerCount = combatPlayerList.size();
				int teamCount = gameManager.getOptions().getIntegerOptionValue("teamCount");
				for (int i = 0; i < teamCount; i++) {
					teams.add(new UhcGameTeam().setColorTeam(UhcGameColor.getColor(i)));
				}

				List<UhcGamePlayer> randomPlayers = Lists.newArrayList();
				combatPlayerList.forEach(player -> {
					UhcGameColor color = player.getColorSelected().orElse(UhcGameColor.WHITE);
					if (color != UhcGameColor.BLACK) {
						teams.get(color.getId()).addPlayer(player);
					} else {
						randomPlayers.add(player);
					}
				});

				for (int i = 0; i < teamCount; i++) {
					int pos = UhcGameManager.rand.nextInt(teamCount);
					UhcGameTeam temp = teams.get(i);
					teams.set(i, teams.get(pos));
					teams.set(pos, temp);
				}

				for (int i = 0; i < randomPlayers.size(); i++) {
					int pos = UhcGameManager.rand.nextInt(randomPlayers.size());
					UhcGamePlayer temp = randomPlayers.get(i);
					randomPlayers.set(i, randomPlayers.get(pos));
					randomPlayers.set(pos, temp);
				}

				randomPlayers.forEach(player -> {
					UhcGameTeam team = teams.get(0);
					for (int i = 1; i < teamCount; i++) {
						if (teams.get(i).getPlayerCount() < team.getPlayerCount())
							team = teams.get(i);
					}

					team.addPlayer(player);
				});

				playersPerTeam = teams.stream().mapToInt(UhcGameTeam::getPlayerCount).max().orElse(0);
				break;
			}
			case SOLO:
			case GHOST: {
				combatPlayerList.stream().map(player -> new UhcGameTeam().setPlayerTeam(player)).forEach(teams::add);
				playersPerTeam = 1;
				break;
			}
			case BOSS: {
				UhcGamePlayer boss = null;
				for (UhcGamePlayer player : combatPlayerList) {
					if (player.getColorSelected().orElse(UhcGameColor.BLUE) == UhcGameColor.RED) {
						if (boss == null) boss = player;
						else {
							player.getRealPlayer().ifPresent(playermp -> playermp.sendMessage(new LiteralText(Formatting.DARK_RED + "不能有多个BOSS！！"), false));
							alright = false;
						}
					}
				}
				if (!alright) {
					operator.ifPresent(player -> player.sendMessage(new LiteralText(Formatting.DARK_RED + "BOSS数量超出1个！！"), false));
					return false;
				}
				teams.add(new UhcGameTeam().setColorTeam(UhcGameColor.RED).addPlayer(boss));
				UhcGameTeam team = new UhcGameTeam().setColorTeam(UhcGameColor.BLUE);
				final UhcGamePlayer playerBoss = boss;
				combatPlayerList.stream().filter(player -> player != playerBoss).forEach(team::addPlayer);
				teams.add(team);
				playersPerTeam = combatPlayerList.size() - 1;
				break;
			}
		}
		
		return true;
	}
	
	protected UhcGamePlayer getBossPlayer() {
		if (UhcGameManager.getGameMode() == EnumMode.BOSS) {
			return teams.get(0).getPlayers().iterator().next();
		}
		return null;
	}
	
	public void setupIngameTeams() {
		Scoreboard scoreboard = gameManager.getMainScoreboard();
		for (Object team : scoreboard.getTeams().toArray())
			scoreboard.removeTeam((Team)team);
		boolean teamFire = gameManager.getOptions().getBooleanOptionValue("friendlyFire");
		boolean teamColl = gameManager.getOptions().getBooleanOptionValue("teamCollision");
		for (UhcGameTeam team : teams) {
			Team spTeam = scoreboard.addTeam(team.getTeamName());
			spTeam.setPrefix(new LiteralText(team.getTeamColor().chatColor.toString()));
			spTeam.setSuffix(new LiteralText(Formatting.RESET.toString()));
			spTeam.setFriendlyFireAllowed(teamFire);
			spTeam.setCollisionRule(teamColl ? AbstractTeam.CollisionRule.ALWAYS : AbstractTeam.CollisionRule.PUSH_OTHER_TEAMS);
			gameManager.broadcastMessage(team.getColorfulTeamName() + " Members:");
			for (UhcGamePlayer player : team.getPlayers()) {
				scoreboard.addPlayerToTeam(player.getName(), spTeam);
				String message = "    " + team.getTeamColor().chatColor + player.getName();
				if (player.isKing()) message += " [KING]";
				gameManager.broadcastMessage(message);
			}
		}
	}
	
	private void addInitialEquipments(BlockPos pos, int playerCnt) {
		World world = gameManager.getOverWorld();
		BlockEntity te = world.getBlockEntity(pos);
		if (!(te instanceof ChestBlockEntity)) return;
		ChestBlockEntity chest = (ChestBlockEntity) te;
		chest.setStack(0, new ItemStack(Items.WOODEN_AXE));
		chest.setStack(1, new ItemStack(Items.WOODEN_SWORD));
		if (gameManager.getOptions().getBooleanOptionValue("greenhandProtect"))
			chest.setStack(2, new ItemStack(Items.GOLDEN_APPLE, playerCnt));
	}
	
	public void spreadPlayers() {
		
		class TaskInitPlayer extends TaskFindPlayer {
			private final BlockPos homePos;
			private final double health;
			public TaskInitPlayer(UhcGamePlayer player, BlockPos pos, double health) {
				super(player);
				this.homePos = pos;
				this.health = health;
			}
			@Override
			public void onFindPlayer(ServerPlayerEntity player) {
				BlockPos newpos = homePos.add(UhcGameManager.rand.nextInt(5) - 2, 0, UhcGameManager.rand.nextInt(5) - 2);
				player.updatePosition(newpos.getX() + 0.5, newpos.getY() + 0.5, newpos.getZ() + 0.5);
				player.teleport(newpos.getX() + 0.5, newpos.getY() + 0.5, newpos.getZ() + 0.5);
				player.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(health);
				player.fallDistance = 0.0f;
				player.inventory.clear();
			}
		}
		
		World world = gameManager.getOverWorld();
		int borderStart = gameManager.getOptions().getIntegerOptionValue("borderStart");
		switch (UhcGameManager.getGameMode()) {
			case NORMAL:
			case KING: {
				SpawnPosition spawnPosition = new SpawnPosition(teams.size(), borderStart);
				for (UhcGameTeam team : teams) {
					final BlockPos pos = gameManager.buildSmallHouse(spawnPosition.nextPos(), team.getTeamColor().dyeColor);
					this.addInitialEquipments(pos, team.getPlayerCount());
					double teamHealth = 20.0 * this.playersPerTeam / team.getPlayerCount();
					team.getPlayers().forEach(player -> gameManager.addTask(new TaskInitPlayer(player, pos, teamHealth)));
				}
				break;
			}
			case SOLO:
			case GHOST:
			case BOSS: {
				SpawnPosition spawnPosition = new SpawnPosition(combatPlayerList.size(), borderStart);
				double maxHealth = 20.0 * playersPerTeam;
				for (UhcGamePlayer player : combatPlayerList) {
					final BlockPos pos = gameManager.buildSmallHouse(spawnPosition.nextPos(), player.getTeam().getTeamColor().dyeColor);
					this.addInitialEquipments(pos, 1);
					gameManager.addTask(new TaskInitPlayer(player, pos, player.getTeam().getPlayerCount() == 1 ? maxHealth : 20));
				}
				break;
			}
		}
		
		for (UhcGamePlayer player : observePlayerList) {
			player.getRealPlayer().ifPresent(playermp -> playermp.setGameMode(GameMode.SPECTATOR));
		}
	}
	
	public void refreshOnlinePlayers() {
		List<UhcGamePlayer> toRemove = Lists.newArrayList();
		allPlayerList.stream().filter(player -> !player.getRealPlayer().isPresent()).forEach(toRemove::add);
		allPlayerList.removeAll(toRemove);
		combatPlayerList.clear();
		observePlayerList.clear();
		teams.forEach(UhcGameTeam::clearTeam);
		teams.clear();
	}

}
