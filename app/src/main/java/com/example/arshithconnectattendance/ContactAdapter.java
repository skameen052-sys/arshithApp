package com.example.arshithconnectattendance;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ContactAdapter extends RecyclerView.Adapter<ContactAdapter.Holder> {

    public interface Listener {
        void onContactClick(Contact contact);
    }

    private final List<Contact> contacts = new ArrayList<>();
    private final Set<String> selectedIds = new HashSet<>();
    private final Listener listener;
    private boolean groupMode;

    public ContactAdapter(Listener listener, boolean groupMode) {
        this.listener = listener;
        this.groupMode = groupMode;
    }

    public void setContacts(List<Contact> data) {
        contacts.clear();
        if (data != null) contacts.addAll(data);
        notifyDataSetChanged();
    }

    public void setSelectedIds(Set<String> ids) {
        selectedIds.clear();
        if (ids != null) selectedIds.addAll(ids);
        notifyDataSetChanged();
    }

    public void setGroupMode(boolean enabled) {
        groupMode = enabled;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_contact, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        Contact contact = contacts.get(position);
        holder.name.setText(contact.getName());

        String subtitle = contact.getDesignation();
        if (!contact.getDepartment().isEmpty()) {
            subtitle += (subtitle.isEmpty() ? "" : " • ") + contact.getDepartment();
        }
        subtitle += (subtitle.isEmpty() ? "" : " • ") + contact.getRole();
        holder.subtitle.setText(subtitle);
        holder.email.setText(contact.getEmail());
        holder.email.setVisibility(contact.getEmail().isEmpty() ? View.GONE : View.VISIBLE);

        String name = contact.getName().trim();
        holder.avatar.setText(name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase());

        holder.check.setVisibility(groupMode ? View.VISIBLE : View.GONE);
        holder.check.setOnCheckedChangeListener(null);
        holder.check.setChecked(selectedIds.contains(contact.getUid()));

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onContactClick(contact);
        });
        holder.check.setOnClickListener(v -> {
            if (listener != null) listener.onContactClick(contact);
        });
    }

    @Override
    public int getItemCount() {
        return contacts.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        TextView avatar, name, subtitle, email;
        CheckBox check;

        Holder(View view) {
            super(view);
            avatar = view.findViewById(R.id.txtContactAvatar);
            name = view.findViewById(R.id.txtContactName);
            subtitle = view.findViewById(R.id.txtContactSubtitle);
            email = view.findViewById(R.id.txtContactEmail);
            check = view.findViewById(R.id.chkContact);
        }
    }
}
