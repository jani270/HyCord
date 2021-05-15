package io.github.dediamondpro.hycord.features.discord;

import de.jcm.discordgamesdk.Result;
import de.jcm.discordgamesdk.lobby.Lobby;
import de.jcm.discordgamesdk.lobby.LobbyTransaction;
import de.jcm.discordgamesdk.lobby.LobbyType;
import de.jcm.discordgamesdk.user.DiscordUser;
import io.github.dediamondpro.hycord.core.Utils;
import io.github.dediamondpro.hycord.options.Settings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ConcurrentModificationException;
import java.util.HashMap;

import static io.github.dediamondpro.hycord.features.discord.RichPresence.discordRPC;

public class LobbyManager {
    ResourceLocation micTexture = new ResourceLocation("hycord", "microphone.png");
    ResourceLocation muteTexture = new ResourceLocation("hycord", "microphone_mute.png");
    ResourceLocation deafenTexture = new ResourceLocation("hycord", "deafen.png");

    public static HashMap<Long, Boolean> talkingData = new HashMap<>();
    public static HashMap<Long, DiscordUser> users = new HashMap<>();
    public static HashMap<Long, ResourceLocation> pictures = new HashMap<>();
    public static HashMap<Long, BufferedImage> bufferedPictures = new HashMap<>();
    public static Long currentUser;
    public static Long lobbyId = null;

    public static void createVoice(int capacity, LobbyType privacy, String game, String topic, boolean locked) {
        LobbyTransaction transaction = discordRPC.lobbyManager().getLobbyCreateTransaction();
        transaction.setCapacity(capacity);
        transaction.setLocked(locked);
        transaction.setType(privacy);
        transaction.setMetadata("type", "voice");
        transaction.setMetadata("game", game);
        transaction.setMetadata("topic", topic);

        discordRPC.lobbyManager().createLobby(transaction, LobbyManager::startVoice);
    }

    public static void startVoice(Result result, Lobby lobby) {
        lobbyId = lobby.getId();
        System.out.println("Starting voice chat in Lobby " + lobby.getId());
        if (result != Result.OK) {
            System.out.println("An unknown error occurred.");
            return;
        }
        discordRPC.lobbyManager().connectVoice(lobby, System.out::println);
        currentUser = discordRPC.userManager().getCurrentUser().getUserId();
        for (Long id : discordRPC.lobbyManager().getMemberUserIds(lobby.getId())) {
            discordRPC.userManager().getUser(id, (r, discordUser) -> {
                if (r == Result.OK) {
                    users.put(id, discordUser);
                    talkingData.put(id, false);
                    if (!pictures.containsKey(id)) {
                        try {
                            URL url = new URL("https://cdn.discordapp.com/avatars/" + id + "/" + discordUser.getAvatar() + ".png?size=64");
                            HttpURLConnection httpcon = (HttpURLConnection) url.openConnection();
                            httpcon.addRequestProperty("User-Agent", "");
                            bufferedPictures.put(id, ImageIO.read(httpcon.getInputStream()));
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            });
        }
    }

    public static void joinHandler(Long userId) {
        talkingData.put(userId, false);
        discordRPC.userManager().getUser(userId, (result, discordUser) -> {
            if (result == Result.OK) {
                users.put(userId, discordUser);
                Minecraft.getMinecraft().thePlayer.addChatMessage(new ChatComponentText(EnumChatFormatting.DARK_AQUA + "Hycord > "
                        + EnumChatFormatting.GREEN + discordUser.getUsername() + "#" + discordUser.getDiscriminator() + " joined the voice chat"));
                try {
                    URL url = new URL("https://cdn.discordapp.com/avatars/" + discordUser.getUserId() + "/" + discordUser.getAvatar() + ".png?size=64");
                    HttpURLConnection httpcon = (HttpURLConnection) url.openConnection();
                    httpcon.addRequestProperty("User-Agent", "");
                    bufferedPictures.put(discordUser.getUserId(), ImageIO.read(httpcon.getInputStream()));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    public static void talkHandler(Long userId, Boolean speaking) {
        talkingData.put(userId, speaking);
        System.out.println(userId + " speaking: " + speaking);
    }

    public static void leaveHandler(Long userId) {
        if (userId.equals(currentUser)) {
            talkingData.clear();
            users.clear();
        } else {
            Minecraft.getMinecraft().thePlayer.addChatMessage(new ChatComponentText(EnumChatFormatting.DARK_AQUA + "Hycord > "
                    + EnumChatFormatting.RED + users.get(userId).getUsername() + "#" + users.get(userId).getDiscriminator() + " left the voice chat"));
            talkingData.remove(userId);
            users.remove(userId);
        }
    }

    boolean pressed = false;

    @SubscribeEvent
    void onTick(TickEvent.ClientTickEvent event) {
        if (!Utils.isHypixel()) return;
        if (Keyboard.isKeyDown(Keyboard.KEY_M) && Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) && Keyboard.isKeyDown(Keyboard.KEY_LSHIFT)) {
            if (!pressed) {
                discordRPC.voiceManager().setSelfMute(!discordRPC.voiceManager().isSelfMute());
                System.out.println("muting");
                pressed = true;
            }
        } else if (Keyboard.isKeyDown(Keyboard.KEY_D) && Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) && Keyboard.isKeyDown(Keyboard.KEY_LSHIFT)) {
            if (!pressed) {
                discordRPC.voiceManager().setSelfDeaf(!discordRPC.voiceManager().isSelfDeaf());
                System.out.println("deafening");
                pressed = true;
            }
        } else {
            pressed = false;
        }
    }

    @SubscribeEvent
    void onRender(TickEvent.RenderTickEvent event) {
        if (!Utils.isHypixel()) return;
        try {
            try {
                for (Long id : bufferedPictures.keySet()) {
                    pictures.put(id, Minecraft.getMinecraft().getTextureManager().getDynamicTextureLocation("pic" + id, new DynamicTexture(bufferedPictures.get(id))));
                    bufferedPictures.remove(id);
                }
            } catch (ConcurrentModificationException e) {
                e.printStackTrace();
            }

            if (Minecraft.getMinecraft().currentScreen != null && !(Minecraft.getMinecraft().currentScreen instanceof GuiChat))
                return;
            ScaledResolution sr = new ScaledResolution(Minecraft.getMinecraft());
            if (talkingData.containsKey(currentUser) && talkingData.get(currentUser)) {
                Minecraft.getMinecraft().getTextureManager().bindTexture(micTexture);
                GlStateManager.color(1.0F, 1.0F, 1.0F);
                Gui.drawModalRectWithCustomSizedTexture(sr.getScaledWidth() - 28, sr.getScaledHeight() - 28, 0, 0, 20, 20, 20, 20);
            } else if (discordRPC.voiceManager().isSelfDeaf()) {
                Minecraft.getMinecraft().getTextureManager().bindTexture(deafenTexture);
                GlStateManager.color(1.0F, 1.0F, 1.0F);
                Gui.drawModalRectWithCustomSizedTexture(sr.getScaledWidth() - 28, sr.getScaledHeight() - 28, 0, 0, 20, 20, 20, 20);
            } else if (discordRPC.voiceManager().isSelfMute()) {
                Minecraft.getMinecraft().getTextureManager().bindTexture(muteTexture);
                GlStateManager.color(1.0F, 1.0F, 1.0F);
                Gui.drawModalRectWithCustomSizedTexture(sr.getScaledWidth() - 28, sr.getScaledHeight() - 28, 0, 0, 20, 20, 20, 20);
            }
            int amount = 1;
            for (Long id : talkingData.keySet()) {
                if (users.containsKey(id)) {
                    if (talkingData.get(id)) {
                        if (discordRPC.voiceManager().isLocalMute(id) || (id.equals(currentUser) && (discordRPC.voiceManager().isSelfMute() || discordRPC.voiceManager().isSelfDeaf()))) {
                            Minecraft.getMinecraft().fontRendererObj.drawStringWithShadow(users.get(id).getUsername(), 30, 18 * amount - 8, new Color(255, 0, 0).getRGB());
                        } else {
                            Minecraft.getMinecraft().fontRendererObj.drawStringWithShadow(users.get(id).getUsername(), 30, 18 * amount - 8, 0xFFFFFF);
                        }
                        if (pictures.containsKey(id)) {
                            Minecraft.getMinecraft().getTextureManager().bindTexture(pictures.get(id));
                            GlStateManager.color(1.0F, 1.0F, 1.0F);
                            Gui.drawModalRectWithCustomSizedTexture(7, 18 * amount - 12, 0, 0, 16, 16, 16, 16);
                        }
                        amount++;
                    } else if (Settings.showNonTalking) {
                        if (discordRPC.voiceManager().isLocalMute(id) || (id.equals(currentUser) && (discordRPC.voiceManager().isSelfMute() || discordRPC.voiceManager().isSelfDeaf()))) {
                            Minecraft.getMinecraft().fontRendererObj.drawStringWithShadow(users.get(id).getUsername(), 30, 18 * amount - 8, new Color(255, 0, 0).getRGB());
                        } else {
                            Minecraft.getMinecraft().fontRendererObj.drawStringWithShadow(users.get(id).getUsername(), 30, 18 * amount - 8, 0xaaaaaa);
                        }
                        if (pictures.containsKey(id)) {
                            Minecraft.getMinecraft().getTextureManager().bindTexture(pictures.get(id));
                            GlStateManager.color(0.6F, 0.6F, 0.6F);
                            Gui.drawModalRectWithCustomSizedTexture(7, 18 * amount - 12, 0, 0, 16, 16, 16, 16);
                        }
                        amount++;
                    }
                }
            }
        } catch (IllegalStateException e) {
            e.printStackTrace();
        }
    }

    public static void leave() {
        if (lobbyId == null) return;
        discordRPC.lobbyManager().disconnectVoice(lobbyId,System.out::println);
        discordRPC.lobbyManager().disconnectLobby(lobbyId);
        users.clear();
        talkingData.clear();
        lobbyId = null;
    }

    public static void join(Lobby lobby) {
        discordRPC.lobbyManager().connectLobby(lobby, LobbyManager::startVoice);
        lobbyId = lobby.getId();
    }
}