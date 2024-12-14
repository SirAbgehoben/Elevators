package org.abgehoben;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;

public class commands implements CommandExecutor{

    @Override
    public boolean onCommand(CommandSender player, Command command, String label, String[] args) {
        if (label.equalsIgnoreCase("map")) {
            if (player instanceof Player) {
                if (player.hasPermission("map.command")) {
                    player.sendMessage("§8§m+---------------***---------------+");
                    TextComponent message = new TextComponent("§7  Click Below to open the Live-Map:\n");
                    TextComponent link = new TextComponent("§f  Live-Map\n");
                    link.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, "http://map.abgehoben.org/"));
                    player.spigot().sendMessage(message);
                    player.spigot().sendMessage(link);
                    player.sendMessage("§8§m+---------------***---------------+");
                } else {
                    player.sendMessage("You do not have permission to use this command.");
                }
                return true;
            } else {
                player.sendMessage("This command can only be used by a player.");
                return true;
            }
        }
        return false;
    }

}