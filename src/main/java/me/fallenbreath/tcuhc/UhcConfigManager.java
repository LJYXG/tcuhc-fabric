/*
 From Gamepiaynmo: https://github.com/Gamepiaynmo/TC-UHC
 */

package me.fallenbreath.tcuhc;

import me.fallenbreath.tcuhc.options.Option;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.LiteralText;

public class UhcConfigManager
{
	private UhcGamePlayer operator;
	private boolean isConfiguring;
	private boolean isInputting;
	private Option curOption;
	
	public void startConfiguring(UhcGamePlayer op) {
		operator = op;
		isConfiguring = true;
	}
	
	public void stopConfiguring() {
		isConfiguring = false;
	}
	
	public boolean isConfiguring() {
		return isConfiguring;
	}
	
	public boolean isOperator(ServerPlayerEntity player) {
		return operator.isSamePlayer(player);
	}
	
	public UhcGamePlayer getOperator() {
		return operator;
	}
	
	public void inputOptionValue(Option option) {
		isInputting = true;
		curOption = option;
	}

	public boolean onPlayerChat(ServerPlayerEntity player, String msg) {
		if (isConfiguring && operator.isSamePlayer(player) && isInputting) {
			curOption.setStringValue(msg);
			UhcGameManager.instance.getUhcPlayerManager().refreshConfigBook();
			player.sendMessage(new LiteralText("设置 " + curOption.getName() + " 为 " + curOption.getStringValue()), false);
			isInputting = false;
			return false;
		}
		return true;
	}

}
