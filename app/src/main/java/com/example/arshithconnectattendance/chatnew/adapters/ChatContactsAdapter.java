package com.example.arshithconnectattendance.chatnew.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.arshithconnectattendance.R;
import com.example.arshithconnectattendance.chatnew.model.ChatUser;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ChatContactsAdapter
        extends RecyclerView.Adapter<ChatContactsAdapter.ContactViewHolder> {

    public interface OnContactClickListener {
        void onContactClick(ChatUser user);
    }

    private final List<ChatUser> allUsers = new ArrayList<>();
    private final List<ChatUser> visibleUsers = new ArrayList<>();

    private final Map<String, ChatUser> selectedUsers = new LinkedHashMap<>();

    private final OnContactClickListener listener;

    private boolean selectionMode = false;

    public ChatContactsAdapter(
            List<ChatUser> users,
            OnContactClickListener listener
    ) {
        this.listener = listener;

        if (users != null) {
            allUsers.addAll(users);
            visibleUsers.addAll(users);
        }
    }

    @NonNull
    @Override
    public ContactViewHolder onCreateViewHolder(
            @NonNull ViewGroup parent,
            int viewType
    ) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_chat_contact, parent, false);

        return new ContactViewHolder(view);
    }

    @Override
    public void onBindViewHolder(
            @NonNull ContactViewHolder holder,
            int position
    ) {
        ChatUser user = visibleUsers.get(position);

        holder.tvContactName.setText(
                user.getName() == null ? "Employee" : user.getName()
        );

        holder.tvContactAbout.setText(
                user.getAbout() == null ? "Available" : user.getAbout()
        );

        String photoUrl = user.getPhotoUrl();

        if (photoUrl != null && !photoUrl.trim().isEmpty()) {
            Glide.with(holder.itemView.getContext())
                    .load(photoUrl)
                    .placeholder(R.drawable.ic_profile_placeholder)
                    .error(R.drawable.ic_profile_placeholder)
                    .circleCrop()
                    .into(holder.imgContactPhoto);
        } else {
            holder.imgContactPhoto.setImageResource(
                    R.drawable.ic_profile_placeholder
            );
        }

        holder.cbSelectContact.setVisibility(
                selectionMode ? View.VISIBLE : View.GONE
        );

        holder.imgOpenChat.setVisibility(
                selectionMode ? View.GONE : View.VISIBLE
        );

        String userId = user.getUid();

        holder.cbSelectContact.setOnCheckedChangeListener(null);

        holder.cbSelectContact.setChecked(
                selectionMode
                        && userId != null
                        && selectedUsers.containsKey(userId)
        );

        holder.cbSelectContact.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (selectionMode) {
                updateSelection(user, isChecked);
            }
        });

        holder.itemView.setOnClickListener(v -> {
            if (selectionMode) {
                boolean currentlySelected =
                        userId != null && selectedUsers.containsKey(userId);

                updateSelection(user, !currentlySelected);

                holder.cbSelectContact.setOnCheckedChangeListener(null);
                holder.cbSelectContact.setChecked(!currentlySelected);

                holder.cbSelectContact.setOnCheckedChangeListener(
                        (buttonView, isChecked) -> {
                            if (selectionMode) {
                                updateSelection(user, isChecked);
                            }
                        }
                );
            } else if (listener != null) {
                listener.onContactClick(user);
            }
        });
    }

    private void updateSelection(ChatUser user, boolean selected) {
        if (user == null || user.getUid() == null) {
            return;
        }

        String uid = user.getUid();

        if (selected) {
            selectedUsers.put(uid, user);
        } else {
            selectedUsers.remove(uid);
        }
    }

    @Override
    public int getItemCount() {
        return visibleUsers.size();
    }

    public void setSelectionMode(boolean enabled) {
        selectionMode = enabled;

        if (!enabled) {
            selectedUsers.clear();
        }

        notifyDataSetChanged();
    }

    public boolean isSelectionMode() {
        return selectionMode;
    }

    public List<ChatUser> getSelectedUsers() {
        return new ArrayList<>(selectedUsers.values());
    }

    public int getSelectedCount() {
        return selectedUsers.size();
    }

    public void clearSelection() {
        selectedUsers.clear();
        notifyDataSetChanged();
    }

    public void updateList(List<ChatUser> users) {
        allUsers.clear();
        visibleUsers.clear();

        if (users != null) {
            allUsers.addAll(users);
            visibleUsers.addAll(users);
        }

        notifyDataSetChanged();
    }

    public void filter(String query) {
        visibleUsers.clear();

        if (query == null || query.trim().isEmpty()) {
            visibleUsers.addAll(allUsers);
        } else {
            String search = query.toLowerCase(Locale.ROOT).trim();

            for (ChatUser user : allUsers) {
                String name = user.getName() == null
                        ? ""
                        : user.getName().toLowerCase(Locale.ROOT);

                String email = user.getEmail() == null
                        ? ""
                        : user.getEmail().toLowerCase(Locale.ROOT);

                if (name.contains(search) || email.contains(search)) {
                    visibleUsers.add(user);
                }
            }
        }

        notifyDataSetChanged();
    }

    static class ContactViewHolder extends RecyclerView.ViewHolder {

        ImageView imgContactPhoto;
        ImageView imgOpenChat;
        TextView tvContactName;
        TextView tvContactAbout;
        CheckBox cbSelectContact;

        ContactViewHolder(@NonNull View itemView) {
            super(itemView);

            imgContactPhoto = itemView.findViewById(R.id.imgContactPhoto);
            imgOpenChat = itemView.findViewById(R.id.imgOpenChat);
            tvContactName = itemView.findViewById(R.id.tvContactName);
            tvContactAbout = itemView.findViewById(R.id.tvContactAbout);
            cbSelectContact = itemView.findViewById(R.id.cbSelectContact);
        }
    }
}