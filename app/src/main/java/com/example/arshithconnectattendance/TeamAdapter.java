package com.example.arshithconnectattendance;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class TeamAdapter
        extends RecyclerView.Adapter<TeamAdapter.ViewHolder> {

    public interface OnConversationClickListener {
        void onClick(ConversationItem item);
    }

    private final List<ConversationItem> items =
            new ArrayList<>();

    private final OnConversationClickListener listener;

    public TeamAdapter(OnConversationClickListener listener) {
        this.listener = listener;
    }

    public void setItems(List<ConversationItem> data) {
        items.clear();

        if (data != null) {
            items.addAll(data);
        }

        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(
            @NonNull ViewGroup parent,
            int viewType
    ) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_team, parent, false);

        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(
            @NonNull ViewHolder holder,
            int position
    ) {
        ConversationItem item = items.get(position);

        holder.name.setText(item.title);
        holder.lastMessage.setText(item.lastMessage);

        String avatar = item.title == null ||
                item.title.trim().isEmpty()
                ? "?"
                : item.title.trim().substring(0, 1).toUpperCase();

        holder.avatar.setText(avatar);

        holder.subtitle.setText(
                item.subtitle == null ? "" : item.subtitle
        );

        if (item.unreadCount > 0) {
            holder.unread.setVisibility(View.VISIBLE);
            holder.unread.setText(
                    String.valueOf(item.unreadCount)
            );
        } else {
            holder.unread.setVisibility(View.GONE);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onClick(item);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {

        TextView avatar;
        TextView name;
        TextView lastMessage;
        TextView subtitle;
        TextView unread;

        ViewHolder(View itemView) {
            super(itemView);

            avatar = itemView.findViewById(R.id.txtTeamAvatar);
            name = itemView.findViewById(R.id.txtTeamName);
            lastMessage = itemView.findViewById(R.id.txtLastMessage);
            subtitle = itemView.findViewById(R.id.txtTeamMembers);
            unread = itemView.findViewById(R.id.txtUnreadCount);
        }
    }
}