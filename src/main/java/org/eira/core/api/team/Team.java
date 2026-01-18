package org.eira.core.api.team;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Represents a team of players working together.
 * 
 * <p>Teams can be used for:
 * <ul>
 *   <li>Cooperative adventures and escape rooms</li>
 *   <li>Competitive gameplay</li>
 *   <li>Shared progress tracking</li>
 *   <li>Group communication</li>
 * </ul>
 * 
 * <h2>Creating Teams</h2>
 * <pre>{@code
 * Team team = EiraAPI.get().teams().create("Red Dragons")
 *     .withColor(ChatFormatting.RED)
 *     .withMaxSize(4)
 *     .build();
 * }</pre>
 * 
 * <h2>Team Communication</h2>
 * <pre>{@code
 * team.broadcast("Found a clue!");
 * team.showTitle("Chapter 2", "The Investigation");
 * }</pre>
 */
public interface Team {
    
    /**
     * Get the unique identifier for this team.
     */
    UUID getId();
    
    /**
     * Get the team's display name.
     */
    String getName();
    
    /**
     * Set the team's display name.
     */
    void setName(String name);
    
    /**
     * Get the team's color for display.
     */
    ChatFormatting getColor();
    
    /**
     * Set the team's color.
     */
    void setColor(ChatFormatting color);
    
    /**
     * Get the maximum team size.
     */
    int getMaxSize();
    
    /**
     * Get the current number of members.
     */
    int getSize();
    
    /**
     * Check if the team is full.
     */
    default boolean isFull() {
        return getSize() >= getMaxSize();
    }
    
    /**
     * Check if the team is empty.
     */
    default boolean isEmpty() {
        return getSize() == 0;
    }
    
    // ==================== Members ====================
    
    /**
     * Get all member UUIDs.
     */
    List<UUID> getMemberIds();
    
    /**
     * Get all currently online members.
     */
    List<Player> getOnlineMembers();
    
    /**
     * Check if a player is a member.
     */
    boolean hasMember(Player player);
    
    /**
     * Check if a UUID is a member.
     */
    boolean hasMember(UUID playerId);
    
    /**
     * Add a player to the team.
     * 
     * @param player the player to add
     * @return true if added, false if team full or already member
     */
    boolean addMember(Player player);
    
    /**
     * Remove a player from the team.
     * 
     * @param player the player to remove
     * @return true if removed, false if not a member
     */
    boolean removeMember(Player player);
    
    // ==================== Leadership ====================
    
    /**
     * Get the team leader's UUID.
     */
    @Nullable
    UUID getLeaderId();
    
    /**
     * Get the team leader if online.
     */
    Optional<Player> getLeader();
    
    /**
     * Check if a player is the leader.
     */
    boolean isLeader(Player player);
    
    /**
     * Set the team leader.
     */
    void setLeader(Player player);
    
    // ==================== Data ====================
    
    /**
     * Get the team's data storage.
     */
    TeamData getData();
    
    /**
     * Get a tag for filtering/grouping teams.
     */
    @Nullable
    String getTag();
    
    /**
     * Set a tag for filtering/grouping.
     */
    void setTag(@Nullable String tag);
    
    // ==================== Communication ====================
    
    /**
     * Broadcast a message to all online team members.
     */
    void broadcast(String message);
    
    /**
     * Broadcast a component to all online team members.
     */
    void broadcast(Component message);
    
    /**
     * Send a message to a specific member.
     */
    void sendTo(Player player, String message);
    
    /**
     * Play a sound to all online team members.
     */
    void playSound(SoundEvent sound, float volume, float pitch);
    
    /**
     * Show a title to all online team members.
     */
    void showTitle(String title, String subtitle);
    
    /**
     * Show a title with custom timings.
     */
    void showTitle(String title, String subtitle, int fadeIn, int stay, int fadeOut);
    
    /**
     * Show an action bar message to all online team members.
     */
    void showActionBar(String message);
    
    // ==================== Proximity ====================
    
    /**
     * Check if all online members are within a radius of a position.
     */
    boolean areAllMembersNear(BlockPos center, double radius);
    
    /**
     * Get members within a radius of a position.
     */
    List<Player> getMembersNear(BlockPos center, double radius);
    
    /**
     * Get the positions of all online members.
     */
    Map<Player, BlockPos> getMemberPositions();
    
    /**
     * Get the maximum distance between any two online members.
     */
    double getMaxMemberDistance();
    
    // ==================== Lifecycle ====================
    
    /**
     * Disband the team, removing all members.
     */
    void disband();
    
    /**
     * Check if the team has been disbanded.
     */
    boolean isDisbanded();
}
