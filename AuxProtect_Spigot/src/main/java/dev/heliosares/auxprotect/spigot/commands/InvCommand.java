package dev.heliosares.auxprotect.spigot.commands;

import dev.heliosares.auxprotect.adapters.message.ClickEvent;
import dev.heliosares.auxprotect.adapters.message.GenericBuilder;
import dev.heliosares.auxprotect.adapters.message.GenericTextColor;
import dev.heliosares.auxprotect.adapters.sender.SenderAdapter;
import dev.heliosares.auxprotect.adapters.sender.SpigotSenderAdapter;
import dev.heliosares.auxprotect.core.APPermission;
import dev.heliosares.auxprotect.core.Command;
import dev.heliosares.auxprotect.core.IAuxProtect;
import dev.heliosares.auxprotect.core.Language;
import dev.heliosares.auxprotect.core.Language.L;
import dev.heliosares.auxprotect.core.PlatformType;
import dev.heliosares.auxprotect.core.commands.LookupCommand;
import dev.heliosares.auxprotect.database.DbEntry;
import dev.heliosares.auxprotect.database.EntryAction;
import dev.heliosares.auxprotect.database.InvDiffManager;
import dev.heliosares.auxprotect.database.Results;
import dev.heliosares.auxprotect.database.SingleItemEntry;
import dev.heliosares.auxprotect.database.SpigotDbEntry;
import dev.heliosares.auxprotect.database.Table;
import dev.heliosares.auxprotect.exceptions.BusyException;
import dev.heliosares.auxprotect.exceptions.CommandException;
import dev.heliosares.auxprotect.exceptions.NotPlayerException;
import dev.heliosares.auxprotect.exceptions.PlatformException;
import dev.heliosares.auxprotect.exceptions.SyntaxException;
import dev.heliosares.auxprotect.spigot.AuxProtectSpigot;
import dev.heliosares.auxprotect.utils.Experience;
import dev.heliosares.auxprotect.utils.InvSerialization;
import dev.heliosares.auxprotect.utils.InvSerialization.PlayerInventoryRecord;
import dev.heliosares.auxprotect.utils.Pane;
import dev.heliosares.auxprotect.utils.Pane.Type;
import dev.heliosares.auxprotect.utils.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class InvCommand<S, P extends IAuxProtect, SA extends SenderAdapter<S, P>> extends Command<S, P, SA> {
    public InvCommand(P plugin) {
        super(plugin, "inv", APPermission.INV, true);
    }

    public static void openSync(IAuxProtect plugin, Player player, Inventory inventory) {
        plugin.runSync(() -> player.openInventory(inventory));
    }

    public static Inventory makeInventory(IAuxProtect plugin_, Player player, OfflinePlayer target,
                                          PlayerInventoryRecord inv, long when) {
        if (plugin_ instanceof AuxProtectSpigot plugin) {
            final Player targetO = target.getPlayer();
            final String targetName = target.getName() == null ? "unknown" : target.getName();
            Pane enderpane = new Pane(Type.SHOW, player);

            Inventory enderinv = Bukkit.getServer().createInventory(enderpane, 27,
                    L.INV_RECOVER_MENU__ENDER_HEADER.translate(targetName, Language.getOptionalS(targetName), TimeUtil.millisToString(System.currentTimeMillis() - when)));
            enderinv.setContents(inv.ender());

            Pane pane = new Pane(Type.SHOW, player);
            String ago = TimeUtil.millisToString(System.currentTimeMillis() - when);
            Inventory mainInv = Bukkit.getServer().createInventory(pane, 54,
                    L.INV_RECOVER_MENU__MAIN_HEADER.translate(targetName, Language.getOptionalS(targetName), TimeUtil.millisToString(System.currentTimeMillis() - when)));
            pane.setInventory(mainInv);
            AtomicBoolean closed = new AtomicBoolean();

            if (APPermission.INV_RECOVER.hasPermission(new SpigotSenderAdapter(plugin, player))) {
                if (targetO != null) {
                    AtomicLong lastClick = new AtomicLong();
                    pane.addButton(49, Material.GREEN_STAINED_GLASS_PANE, () -> plugin.runAsync(() -> {
                        if (System.currentTimeMillis() - lastClick.get() > 500) {
                            lastClick.set(System.currentTimeMillis());
                            return;
                        }
                        if (closed.get()) return;
                        closed.set(true);
                        plugin.runSync(player::closeInventory);
                        player.sendMessage(L.COMMAND__LOOKUP__LOOKING.translate());
                        try {
                            update(plugin, player, when);
                        } catch (BusyException e) {
                            player.sendMessage(L.DATABASE_BUSY.translate());
                            return;
                        } catch (Exception e) {
                            plugin.print(e);
                            player.sendMessage(L.ERROR.translate());
                            return;
                        }
                        plugin.runSync(() -> {
                            PlayerInventoryRecord inv_;
                            inv_ = InvDiffManager.listToPlayerInv(Arrays.asList(mainInv.getContents()), inv.exp());

                            targetO.getInventory().setStorageContents(inv_.storage());
                            targetO.getInventory().setArmorContents(inv_.armor());
                            targetO.getInventory().setExtraContents(inv_.extra());
                            try {
                                Experience.setExp(targetO, inv_.exp());
                            } catch (Exception e) {
                                player.sendMessage(L.INV_RECOVER_MENU__XP_ERROR.translate());
                            }
                            player.closeInventory();
                            plugin.broadcast(L.COMMAND__INV__FORCE_RECOVERED.translate(player.getName(), targetName, Language.getOptionalS(targetName), ago), APPermission.INV_NOTIFY);
                            player.sendMessage(L.COMMAND__INV__SUCCESS.translate(targetName, Language.getOptionalS(targetName)));
                            targetO.sendMessage(L.COMMAND__INV__NOTIFY_PLAYER.translate(player.getName(), TimeUtil.millisToString(System.currentTimeMillis() - when)));
                            targetO.playSound(targetO.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1, 1);
                            plugin.add(new SpigotDbEntry(AuxProtectSpigot.getLabel(player), EntryAction.RECOVER, false, player.getLocation(), AuxProtectSpigot.getLabel(target), "force"));
                        });

                    }), L.INV_RECOVER_MENU__BUTTON__FORCE__LABEL.translate(), L.INV_RECOVER_MENU__BUTTON__FORCE__HOVER.translateList());
                } else {
                    pane.addButton(49, Material.GRAY_STAINED_GLASS_PANE, null,
                            L.INV_RECOVER_MENU__BUTTON__FORCE_UNAVAILABLE__LABEL.translate(), L.INV_RECOVER_MENU__BUTTON__FORCE_UNAVAILABLE__HOVER.translateList());
                }
                pane.addButton(50, Material.GREEN_STAINED_GLASS_PANE, () -> plugin.runAsync(() -> {
                    if (closed.get()) return;
                    closed.set(true);
                    plugin.runSync(player::closeInventory);
                    ItemStack[] output = new ItemStack[45];
                    for (int i = 0; i < output.length; i++) {
                        output[i] = mainInv.getItem(i);
                    }
                    byte[] recover = null;
                    try {
                        recover = InvSerialization.toByteArray(output);
                    } catch (Exception e1) {
                        plugin.warning("Error serializing inventory recovery");
                        plugin.print(e1);
                        player.sendMessage(Language.translate(L.ERROR));
                    }
                    try {
                        plugin.getSqlManager().getUserManager().setPendingInventory(plugin.getSqlManager()
                                        .getUserManager().getUIDFromUUID("$" + target.getUniqueId(), true),
                                recover);
                        update(plugin, player, when);
                    } catch (Exception e) {
                        plugin.print(e);
                        player.sendMessage(L.ERROR.translate());
                        return;
                    }
                    assert target.getName() != null;
                    plugin.broadcast(L.COMMAND__INV__RECOVERED.translate(player.getName(), targetName, Language.getOptionalS(targetName), ago), APPermission.INV_NOTIFY);
                    player.sendMessage(L.COMMAND__INV__SUCCESS.translate(targetName, Language.getOptionalS(targetName)));
                    if (targetO != null) {
                        targetO.sendMessage(L.COMMAND__INV__NOTIFY_PLAYER.translate(player.getName(), TimeUtil.millisToString(System.currentTimeMillis() - when)));
                        targetO.sendMessage(L.COMMAND__INV__NOTIFY_PLAYER_ENSURE_ROOM.translate());
                        GenericBuilder message = new GenericBuilder(plugin);
                        message.newLine();
                        message.append("         ");
                        message.append("[" + L.COMMAND__CLAIMINV__CLAIM_BUTTON__LABEL.translate() + "]")
                                .color(GenericTextColor.GREEN)
                                .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/claiminv"))
                                .hover(L.COMMAND__CLAIMINV__CLAIM_BUTTON__HOVER.translate());
                        message.newLine();
                        message.send(new SpigotSenderAdapter(plugin, targetO));
                        targetO.playSound(targetO.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1, 1);
                    }

                    plugin.add(new SpigotDbEntry(AuxProtectSpigot.getLabel(player), EntryAction.RECOVER, false,
                            player.getLocation(), AuxProtectSpigot.getLabel(target), "regular"));
                }), L.INV_RECOVER_MENU__BUTTON__RECOVER__LABEL.translate(), L.INV_RECOVER_MENU__BUTTON__RECOVER__HOVER.translateList());
            }
            int space = 53;
            pane.addButton(space--, Material.RED_STAINED_GLASS_PANE, player::closeInventory, L.INV_RECOVER_MENU__BUTTON__CLOSE.translate());
            pane.addButton(space--, Material.BLACK_STAINED_GLASS_PANE, () -> player.openInventory(enderinv), L.INV_RECOVER_MENU__BUTTON__ENDER_CHEST.translate());
            // TODO zz backpack goes here
            pane.addButton(space, Material.GREEN_STAINED_GLASS_PANE, null,
                    inv.exp() >= 0 ? (L.INV_RECOVER_MENU__BUTTON__XP__HAD.translate(inv.exp())) : L.INV_RECOVER_MENU__BUTTON__XP__ERROR.translate());

            int i1 = 0;
            for (int i = 9; i < inv.storage().length; i++) {
                if (inv.storage()[i] != null)
                    mainInv.setItem(i1, inv.storage()[i]);
                i1++;
            }
            for (int i = 0; i < 9; i++) {
                if (inv.storage()[i] != null)
                    mainInv.setItem(i1, inv.storage()[i]);
                i1++;
            }
            for (int i = inv.armor().length - 1; i >= 0; i--) {
                if (inv.armor()[i] != null)
                    mainInv.setItem(i1, inv.armor()[i]);
                i1++;
            }
            for (int i = 0; i < inv.extra().length; i++) {
                if (inv.extra()[i] != null)
                    mainInv.setItem(i1, inv.extra()[i]);
                i1++;
            }

            enderpane.onClose((p) -> plugin.getServer().getScheduler().runTaskLater(plugin, () -> player.openInventory(mainInv), 1));
            return mainInv;
        }
        return null;
    }

    public static final DateTimeFormatter ratedByDateFormatter = DateTimeFormatter.ofPattern("ddMMMyy HHmm");

    private static void update(IAuxProtect plugin, Player staff, long time) throws SQLException, BusyException {
        plugin.getSqlManager().execute(
                "UPDATE " + Table.AUXPROTECT_INVENTORY + " SET data=" + plugin.getSqlManager().concat("ifnull(data,'')", "?") + " WHERE time=? AND action_id=?",
                30000L, LocalDateTime.now().format(ratedByDateFormatter) + ": Recovered by " + staff.getName() + "; ",
                time, EntryAction.INVENTORY.id);
    }

    @Override
    public void onCommand(SA sender, String label, String[] args) throws CommandException {
        if (args.length < 2) throw new SyntaxException();
        if (sender.getPlatform().getLevel() != PlatformType.Level.SERVER) throw new PlatformException();
        if (!(sender.getSender() instanceof Player player)) throw new NotPlayerException();

        int index = -1;
        try {
            index = Integer.parseInt(args[1]);
        } catch (NumberFormatException ignored) {

        }
        if (index < 0) {
            throw new SyntaxException();
        }
        Results results = LookupCommand.getResultsFor(player.getUniqueId());
        if (results == null || index >= results.getSize()) {
            throw new CommandException(L.COMMAND__LOOKUP__NO_RESULTS_SELECTED);
        }

        DbEntry entry = results.get(index);
        if (entry == null) {
            throw new CommandException(L.COMMAND__LOOKUP__NO_RESULTS_SELECTED);
        }
        if (entry.getAction().equals(EntryAction.INVENTORY)) {
            PlayerInventoryRecord inv_;
            byte[] blob;
            try {
                blob = entry.getBlob();
            } catch (Exception e1) {
                plugin.warning("Error getting blob from inventory entry");
                plugin.print(e1);
                sender.sendLang(Language.L.ERROR);
                return;
            }
            try {
                inv_ = InvSerialization.toPlayerInventory(blob);
            } catch (Exception e1) {
                plugin.warning("Error deserializing inventory: " + Base64.getEncoder().encodeToString(blob));
                plugin.print(e1);
                sender.sendLang(Language.L.ERROR);
                return;
            }
            final PlayerInventoryRecord inv = inv_;
            OfflinePlayer target;
            try {
                target = Bukkit.getOfflinePlayer(UUID.fromString(entry.getUserUUID().substring(1)));
            } catch (BusyException e) {
                sender.sendLang(L.DATABASE_BUSY);
                return;
            } catch (SQLException e) {
                sender.sendLang(L.ERROR);
                return;
            }
            openSync(plugin, player, makeInventory(plugin, player, target, inv, entry.getTime()));
        } else if (entry.hasBlob() && entry.getAction().getTable() == Table.AUXPROTECT_INVENTORY) {
            Pane pane = new Pane(Type.SHOW, player);
            try {
                Inventory inv;
                if (entry instanceof SingleItemEntry sientry) {
                    inv = InvSerialization.toInventory(pane, L.COMMAND__INV__ITEM_VIEWER.translate(), sientry.getItem());
                } else {
                    inv = InvSerialization.toInventory(entry.getBlob(), pane, L.COMMAND__INV__ITEM_VIEWER.translate());
                }
                pane.setInventory(inv);
                openSync(plugin, player, inv);
            } catch (Exception e1) {
                plugin.warning("Error serializing itemviewer");
                plugin.print(e1);
                sender.sendLang(Language.L.ERROR);
            }
        }
    }

    @Override
    public boolean exists() {
        return plugin.getPlatform().getLevel() == PlatformType.Level.SERVER;
    }

    @Override
    public List<String> onTabComplete(SenderAdapter sender, String label, String[] args) {
        return null;
    }
}
