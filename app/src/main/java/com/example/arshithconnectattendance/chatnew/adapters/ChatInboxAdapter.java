
package com.example.arshithconnectattendance.chatnew.adapters;

import android.graphics.Color;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.arshithconnectattendance.R;
import com.example.arshithconnectattendance.chatnew.model.ChatInboxItem;
import com.google.firebase.auth.FirebaseAuth;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class ChatInboxAdapter
        extends RecyclerView.Adapter<ChatInboxAdapter.InboxViewHolder> {

    public interface OnInboxItemClickListener {

        void onInboxItemClick(ChatInboxItem item);

        default void onInboxItemAction(ChatInboxItem item, String action) {
        }

        default void onSelectionChanged(Set<String> selectedConversationIds) {
        }

        default void onInboxBulkAction(
                List<ChatInboxItem> selectedItems,
                String action
        ) {
        }
    }

    private final List<ChatInboxItem> allItems = new ArrayList<>();
    private final List<ChatInboxItem> visibleItems = new ArrayList<>();
    private final Set<String> selectedConversationIds = new LinkedHashSet<>();

    private final OnInboxItemClickListener listener;
    private final String currentUid;

    private String currentSearchQuery = "";
    private String currentFilter = "all";
    private boolean showArchived = false;
    private boolean selectionMode = false;

    public ChatInboxAdapter(
            List<ChatInboxItem> items,
            OnInboxItemClickListener listener
    ) {
        this.listener = listener;

        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            currentUid = FirebaseAuth.getInstance()
                    .getCurrentUser()
                    .getUid();
        } else {
            currentUid = "";
        }

        updateList(items);
    }

    @NonNull
    @Override
    public InboxViewHolder onCreateViewHolder(
            @NonNull ViewGroup parent,
            int viewType
    ) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_chat_inbox, parent, false);

        return new InboxViewHolder(view);
    }

    @Override
    public void onBindViewHolder(
            @NonNull InboxViewHolder holder,
            int position
    ) {
        ChatInboxItem item = visibleItems.get(position);

        // DISPLAY GROUP NAME OR CONTACT NAME
        String displayName;

        if (item.isGroupChat()) {
            displayName = item.getGroupName();

            if (displayName == null || displayName.trim().isEmpty()) {
                displayName = "Group";
            }
        } else {
            displayName = item.getContactName();

            if (displayName == null || displayName.trim().isEmpty()) {
                displayName = "Employee";
            }
        }

        holder.tvContactName.setText(displayName);

        // LAST MESSAGE PREVIEW
        holder.tvLastMessage.setText(getPreviewText(item));

        // UNREAD MESSAGE TEXT STYLE
        if (item.getUnreadCount() > 0) {
            holder.tvLastMessage.setTypeface(null, Typeface.BOLD);
            holder.tvContactName.setTypeface(null, Typeface.BOLD);
        } else {
            holder.tvLastMessage.setTypeface(null, Typeface.NORMAL);
            holder.tvContactName.setTypeface(null, Typeface.NORMAL);
        }

        // MESSAGE TIME
        holder.tvMessageTime.setText(
                formatMessageTime(item.getLastMessageAt())
        );

        // UNREAD COUNT
        int unreadCount = item.getUnreadCount();

        if (unreadCount > 0) {
            holder.tvUnreadCount.setVisibility(View.VISIBLE);
            holder.tvUnreadCount.setText(
                    unreadCount > 99
                            ? "99+"
                            : String.valueOf(unreadCount)
            );
        } else {
            holder.tvUnreadCount.setVisibility(View.GONE);
        }

        // PIN, FAVORITE AND MUTE INDICATORS
        StringBuilder flags = new StringBuilder();

        if (item.isPinned()) {
            flags.append("📌");
        }

        if (item.isFavorite()) {
            if (flags.length() > 0) flags.append("  ");
            flags.append("⭐");
        }

        if (item.isMuted()) {
            if (flags.length() > 0) flags.append("  ");
            flags.append("🔕");
        }

        if (holder.tvInboxFlags != null) {
            if (flags.length() > 0) {
                holder.tvInboxFlags.setVisibility(View.VISIBLE);
                holder.tvInboxFlags.setText(flags.toString());
            } else {
                holder.tvInboxFlags.setVisibility(View.GONE);
            }
        }

        // PROFILE OR GROUP PHOTO
        String photoUrl = item.getContactPhotoUrl();

        if (photoUrl != null && !photoUrl.trim().isEmpty()) {
            Glide.with(holder.imgContactPhoto.getContext())
                    .load(photoUrl)
                    .placeholder(R.drawable.ic_profile_placeholder)
                    .error(R.drawable.ic_profile_placeholder)
                    .circleCrop()
                    .into(holder.imgContactPhoto);
        } else {
            Glide.with(holder.imgContactPhoto.getContext())
                    .load(R.drawable.ic_profile_placeholder)
                    .circleCrop()
                    .into(holder.imgContactPhoto);
        }

        // SELECTED ROW HIGHLIGHT
        boolean selected = selectedConversationIds.contains(
                item.getConversationId()
        );

        holder.itemView.setBackgroundColor(
                selected
                        ? Color.rgb(249, 229, 214)
                        : Color.TRANSPARENT
        );

        // OPEN OR SELECT CONVERSATION
        holder.itemView.setOnClickListener(v -> {
            if (selectionMode) {
                toggleSelection(item);
            } else if (listener != null) {
                listener.onInboxItemClick(item);
            }
        });

        // LONG PRESS TO START MULTI-SELECTION
        holder.itemView.setOnLongClickListener(v -> {
            if (!selectionMode) {
                selectionMode = true;
            }

            toggleSelection(item);
            return true;
        });

        // THREE-DOT MENU
        holder.btnInboxMore.setOnClickListener(v -> {
            if (selectionMode) {
                toggleSelection(item);
            } else {
                showOptionsMenu(v, item);
            }
        });
    }

    // TOGGLE SELECTION
    private void toggleSelection(ChatInboxItem item) {
        String conversationId = item.getConversationId();

        if (conversationId == null || conversationId.trim().isEmpty()) {
            return;
        }

        if (selectedConversationIds.contains(conversationId)) {
            selectedConversationIds.remove(conversationId);
        } else {
            selectedConversationIds.add(conversationId);
        }

        if (selectedConversationIds.isEmpty()) {
            selectionMode = false;
        }

        notifyDataSetChanged();
        notifySelectionChanged();
    }

    private void notifySelectionChanged() {
        if (listener != null) {
            listener.onSelectionChanged(
                    new LinkedHashSet<>(selectedConversationIds)
            );
        }
    }

    public boolean isInSelectionMode() {
        return selectionMode;
    }

    public int getSelectedCount() {
        return selectedConversationIds.size();
    }

    public Set<String> getSelectedConversationIds() {
        return new LinkedHashSet<>(selectedConversationIds);
    }

    public List<ChatInboxItem> getSelectedItems() {
        List<ChatInboxItem> selectedItems = new ArrayList<>();

        for (ChatInboxItem item : allItems) {
            if (selectedConversationIds.contains(item.getConversationId())) {
                selectedItems.add(item);
            }
        }

        return selectedItems;
    }

    public void clearSelection() {
        if (selectedConversationIds.isEmpty() && !selectionMode) {
            return;
        }

        selectedConversationIds.clear();
        selectionMode = false;

        notifyDataSetChanged();
        notifySelectionChanged();
    }

    // BULK ACTION REQUEST
    public void performBulkAction(String action) {
        if (listener == null || selectedConversationIds.isEmpty()) {
            return;
        }

        List<ChatInboxItem> selectedItems = getSelectedItems();

        listener.onInboxBulkAction(selectedItems, action);
    }

    // THREE-DOT OPTIONS MENU
    private void showOptionsMenu(View anchor, ChatInboxItem item) {

        PopupMenu popupMenu = new PopupMenu(anchor.getContext(), anchor);

        popupMenu.getMenu().add(
                0, 1, 0,
                item.isPinned() ? "Unpin chat" : "Pin chat"
        );

        popupMenu.getMenu().add(
                0, 2, 1,
                item.isArchived() ? "Unarchive chat" : "Archive chat"
        );

        popupMenu.getMenu().add(
                0, 3, 2,
                item.isMuted() ? "Unmute chat" : "Mute chat"
        );

        popupMenu.getMenu().add(
                0, 4, 3,
                item.isFavorite()
                        ? "Remove from Favorites"
                        : "Add to Favorites"
        );

        popupMenu.getMenu().add(
                0, 5, 4,
                "Delete for me"
        );

        popupMenu.setOnMenuItemClickListener(menuItem -> {

            String action;

            switch (menuItem.getItemId()) {
                case 1:
                    action = "pin";
                    break;

                case 2:
                    action = "archive";
                    break;

                case 3:
                    action = "mute";
                    break;

                case 4:
                    action = "favorite";
                    break;

                case 5:
                    action = "delete";
                    break;

                default:
                    return false;
            }

            if (listener != null) {
                listener.onInboxItemAction(item, action);
            } else {
                Toast.makeText(
                        anchor.getContext(),
                        "Action unavailable",
                        Toast.LENGTH_SHORT
                ).show();
            }

            return true;
        });

        popupMenu.show();
    }

    // MESSAGE PREVIEW
    private String getPreviewText(ChatInboxItem item) {

        String message = item.getLastMessage();

        if (message == null || message.trim().isEmpty()) {
            message = "Start a conversation";
        }

        String type = item.getLastMessageType();

        if (type != null) {
            switch (type.toLowerCase(Locale.ROOT)) {

                case "image":
                case "photo":
                    message = "Photo";
                    break;

                case "video":
                    message = "Video";
                    break;

                case "file":
                case "document":
                    message = "File";
                    break;

                case "audio":
                case "voice":
                    message = "Voice message";
                    break;

                case "text":
                default:
                    break;
            }
        }

        String senderId = item.getLastSenderId();

        if (senderId != null
                && !senderId.trim().isEmpty()
                && senderId.equals(currentUid)) {
            message = "You: " + message;
        }

        return message;
    }

    @Override
    public int getItemCount() {
        return visibleItems.size();
    }

    // UPDATE INBOX LIST
    public void updateList(List<ChatInboxItem> newItems) {

        allItems.clear();

        if (newItems != null) {
            allItems.addAll(newItems);
        }

        // REMOVE SELECTIONS ONLY FOR CONVERSATIONS THAT NO LONGER EXIST
        Set<String> existingIds = new LinkedHashSet<>();

        for (ChatInboxItem item : allItems) {
            if (item.getConversationId() != null) {
                existingIds.add(item.getConversationId());
            }
        }

        boolean selectionChanged =
                selectedConversationIds.retainAll(existingIds);

        if (selectedConversationIds.isEmpty()) {
            selectionMode = false;
        }

        sortItems();
        applyFilter(currentSearchQuery);

        notifyDataSetChanged();

        if (selectionChanged) {
            notifySelectionChanged();
        }
    }

    // PINNED CHATS FIRST
    private void sortItems() {

        Collections.sort(allItems, (first, second) -> {

            if (first.isPinned() != second.isPinned()) {
                return first.isPinned() ? -1 : 1;
            }

            Date firstDate = first.getLastMessageAt();
            Date secondDate = second.getLastMessageAt();

            if (firstDate == null && secondDate == null) return 0;
            if (firstDate == null) return 1;
            if (secondDate == null) return -1;

            return secondDate.compareTo(firstDate);
        });
    }

    // SEARCH
    public void filter(String query) {

        currentSearchQuery = query == null ? "" : query.trim();

        applyFilter(currentSearchQuery);
        notifyDataSetChanged();
    }

    // ACTIVE / ARCHIVED FILTER
    public void setShowArchived(boolean showArchived) {

        // Do not clear selection during an ordinary Firestore refresh.
        if (this.showArchived != showArchived) {
            this.showArchived = showArchived;
            clearSelection();
        }

        applyFilter(currentSearchQuery);
        notifyDataSetChanged();
    }

    public boolean isShowingArchived() {
        return showArchived;
    }

    // ALL / UNREAD / FAVORITES / GROUPS FILTER
    public void setInboxFilter(String filter) {

        String newFilter = filter == null
                ? "all"
                : filter.trim().toLowerCase(Locale.ROOT);

        // Clear selection only when the actual filter changes.
        if (!currentFilter.equals(newFilter)) {
            currentFilter = newFilter;
            clearSelection();
        }

        applyFilter(currentSearchQuery);
        notifyDataSetChanged();
    }

    public String getInboxFilter() {
        return currentFilter;
    }

    public int getVisibleItemCount() {
        return visibleItems.size();
    }

    public int getTotalUnreadCount() {
        int total = 0;

        for (ChatInboxItem item : allItems) {
            if (item != null && !item.isArchived()) {
                total += Math.max(0, item.getUnreadCount());
            }
        }

        return total;
    }


    // APPLY SEARCH AND CATEGORY FILTERS
    private void applyFilter(String query) {

        visibleItems.clear();

        String searchText = query == null
                ? ""
                : query.trim().toLowerCase(Locale.ROOT);

        for (ChatInboxItem item : allItems) {

            // ACTIVE / ARCHIVED FILTER
            if (item.isArchived() != showArchived) {
                continue;
            }

            // CATEGORY FILTER
            switch (currentFilter) {

                case "unread":
                    if (item.getUnreadCount() <= 0) continue;
                    break;

                case "favorites":
                case "favorite":
                    if (!item.isFavorite()) continue;
                    break;

                case "groups":
                    if (!item.isGroupChat()) continue;
                    break;

                case "all":
                default:
                    break;
            }

            // NO SEARCH QUERY
            if (searchText.isEmpty()) {
                visibleItems.add(item);
                continue;
            }

            // CONTACT NAME SEARCH
            String contactName = item.getContactName() == null
                    ? ""
                    : item.getContactName().toLowerCase(Locale.ROOT);

            // GROUP NAME SEARCH
            String groupName = item.getGroupName() == null
                    ? ""
                    : item.getGroupName().toLowerCase(Locale.ROOT);

            // LAST MESSAGE SEARCH
            String message = getPreviewText(item)
                    .toLowerCase(Locale.ROOT);

            if (contactName.contains(searchText)
                    || groupName.contains(searchText)
                    || message.contains(searchText)) {

                visibleItems.add(item);
            }
        }
    }

    // FORMAT MESSAGE TIME
    private String formatMessageTime(Date date) {

        if (date == null) {
            return "";
        }

        Date now = new Date();

        SimpleDateFormat dateFormat;

        if (isSameDay(date, now)) {
            dateFormat = new SimpleDateFormat(
                    "hh:mm a",
                    Locale.getDefault()
            );
        } else {
            dateFormat = new SimpleDateFormat(
                    "dd/MM/yy",
                    Locale.getDefault()
            );
        }

        return dateFormat.format(date);
    }

    private boolean isSameDay(Date first, Date second) {

        SimpleDateFormat format = new SimpleDateFormat(
                "yyyyMMdd",
                Locale.getDefault()
        );

        return format.format(first).equals(format.format(second));
    }

    // VIEW HOLDER
    static class InboxViewHolder extends RecyclerView.ViewHolder {

        ImageView imgContactPhoto;
        ImageView btnInboxMore;

        TextView tvContactName;
        TextView tvLastMessage;
        TextView tvMessageTime;
        TextView tvUnreadCount;
        TextView tvInboxFlags;

        InboxViewHolder(@NonNull View itemView) {

            super(itemView);

            imgContactPhoto =
                    itemView.findViewById(R.id.imgInboxContactPhoto);

            tvContactName =
                    itemView.findViewById(R.id.tvInboxContactName);

            tvLastMessage =
                    itemView.findViewById(R.id.tvInboxLastMessage);

            tvMessageTime =
                    itemView.findViewById(R.id.tvInboxMessageTime);

            tvUnreadCount =
                    itemView.findViewById(R.id.tvInboxUnreadCount);

            btnInboxMore =
                    itemView.findViewById(R.id.btnInboxMore);

            tvInboxFlags =
                    itemView.findViewById(R.id.tvInboxFlags);
        }
    }
}
