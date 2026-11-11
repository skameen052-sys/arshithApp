
package com.example.arshithconnectattendance;

import android.content.Intent;
import android.media.MediaPlayer;
import android.net.Uri;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import android.os.Handler;
import android.os.Looper;

public class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.ViewHolder> {

    public interface OnMessageLongClickListener {
        void onLongClick(Message message);
    }

    private final List<Message> messages = new ArrayList<>();

    private final String currentUserAuthUid;
    private final OnMessageLongClickListener longClickListener;

    private MediaPlayer mediaPlayer;
    private String playingAudioUrl = "";
    private ViewHolder activeAudioHolder;

    private final Handler audioHandler = new Handler(Looper.getMainLooper());

    private final Runnable audioProgressRunnable = new Runnable() {
        @Override
        public void run() {
            if (mediaPlayer == null || activeAudioHolder == null) {
                return;
            }

            try {
                if (mediaPlayer.isPlaying()) {
                    int current = mediaPlayer.getCurrentPosition();
                    int duration = mediaPlayer.getDuration();

                    activeAudioHolder.audioSeekBar.setMax(
                            Math.max(duration, 1)
                    );

                    activeAudioHolder.audioSeekBar.setProgress(current);

                    activeAudioHolder.audioCurrentTime.setText(
                            formatDuration(current)
                    );

                    audioHandler.postDelayed(this, 300);
                }
            } catch (Exception ignored) {
                stopAudio();
            }
        }
    };

    public MessageAdapter(String currentUserAuthUid) {
        this(currentUserAuthUid, null);
    }

    public MessageAdapter(
            String currentUserAuthUid,
            OnMessageLongClickListener listener
    ) {
        this.currentUserAuthUid = currentUserAuthUid == null
                ? ""
                : currentUserAuthUid;

        this.longClickListener = listener;
    }

    public void setMessages(List<Message> data) {
        messages.clear();

        if (data != null) {
            messages.addAll(data);
        }

        stopAudio();
        notifyDataSetChanged();
    }

    public void addMessage(Message message) {
        if (message == null) return;

        messages.add(message);
        notifyItemInserted(messages.size() - 1);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(
            @NonNull ViewGroup parent,
            int viewType
    ) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_message, parent, false);

        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(
            @NonNull ViewHolder holder,
            int position
    ) {
        Message message = messages.get(position);

        String senderUid = getSenderAuthUid(message);

        boolean mine = !currentUserAuthUid.isEmpty()
                && currentUserAuthUid.equals(senderUid);

        holder.sender.setText(
                mine ? "You" : message.getSenderName()
        );

        holder.sender.setVisibility(
                mine ? View.GONE : View.VISIBLE
        );

        String attachmentUrl = message.getAttachmentUrl();
        String messageType = message.getMessageType();

        boolean hasAttachment = attachmentUrl != null
                && !attachmentUrl.trim().isEmpty();

        boolean isAudio = !message.isDeleted()
                && hasAttachment
                && "audio".equalsIgnoreCase(messageType);

        // MESSAGE CONTENT

        String displayContent;

        if (message.isDeleted()) {
            displayContent = "This message was deleted";
        } else if (hasAttachment) {
            if ("image".equalsIgnoreCase(messageType)) {
                displayContent = "📷 Photo";
            } else if ("audio".equalsIgnoreCase(messageType)) {
                displayContent = "🎵 Audio message";
            } else {
                displayContent = "📎 " + message.getAttachmentName();
            }
        } else {
            displayContent = message.getContent();
        }

        if (message.isPinned() && !message.isDeleted()) {
            displayContent = "📌 " + displayContent;
        }

        holder.content.setText(displayContent);
        holder.content.setOnClickListener(null);

        // ATTACHMENT

        if (!message.isDeleted() && hasAttachment && !isAudio) {

            holder.content.setTextColor(0xFF4F35A5);

            holder.content.setOnClickListener(
                    v -> openAttachment(v, message)
            );

            holder.attachmentName.setVisibility(View.VISIBLE);

            holder.attachmentName.setText(
                    "Open attachment: " + message.getAttachmentName()
            );

            holder.attachmentName.setOnClickListener(
                    v -> openAttachment(v, message)
            );

        } else {

            holder.content.setTextColor(0xFF24213B);

            holder.attachmentName.setVisibility(View.GONE);
            holder.attachmentName.setOnClickListener(null);
        }

        // AUDIO PLAYER

        holder.audioLayout.setVisibility(
                isAudio ? View.VISIBLE : View.GONE
        );

        holder.audioPlayButton.setOnClickListener(null);
        holder.audioSeekBar.setOnSeekBarChangeListener(null);

        if (isAudio) {

            String audioName = message.getAttachmentName();

            if (audioName == null || audioName.trim().isEmpty()) {
                audioName = "Voice message";
            }

            holder.audioFileName.setText(audioName);

            if (attachmentUrl.equals(playingAudioUrl)
                    && mediaPlayer != null) {

                try {
                    int duration = mediaPlayer.getDuration();
                    int current = mediaPlayer.getCurrentPosition();

                    holder.audioSeekBar.setMax(Math.max(duration, 1));
                    holder.audioSeekBar.setProgress(current);

                    holder.audioCurrentTime.setText(
                            formatDuration(current)
                    );

                    holder.audioDuration.setText(
                            formatDuration(duration)
                    );

                    holder.audioPlayButton.setImageResource(
                            mediaPlayer.isPlaying()
                                    ? android.R.drawable.ic_media_pause
                                    : android.R.drawable.ic_media_play
                    );

                    activeAudioHolder = holder;

                    if (mediaPlayer.isPlaying()) {
                        audioHandler.removeCallbacks(audioProgressRunnable);
                        audioHandler.post(audioProgressRunnable);
                    }

                } catch (Exception ignored) {
                    resetAudioViews(holder);
                }

            } else {
                resetAudioViews(holder);
            }

            holder.audioPlayButton.setOnClickListener(v -> {
                if (attachmentUrl.equals(playingAudioUrl)
                        && mediaPlayer != null) {
                    toggleAudio(holder);
                } else {
                    playAudio(holder, attachmentUrl);
                }
            });

            holder.audioSeekBar.setOnSeekBarChangeListener(
                    new SeekBar.OnSeekBarChangeListener() {

                        @Override
                        public void onProgressChanged(
                                SeekBar seekBar,
                                int progress,
                                boolean fromUser
                        ) {
                            if (fromUser
                                    && attachmentUrl.equals(playingAudioUrl)
                                    && mediaPlayer != null) {

                                holder.audioCurrentTime.setText(
                                        formatDuration(progress)
                                );
                            }
                        }

                        @Override
                        public void onStartTrackingTouch(SeekBar seekBar) {
                        }

                        @Override
                        public void onStopTrackingTouch(SeekBar seekBar) {

                            if (attachmentUrl.equals(playingAudioUrl)
                                    && mediaPlayer != null) {

                                try {
                                    mediaPlayer.seekTo(
                                            seekBar.getProgress()
                                    );
                                } catch (Exception ignored) {
                                }
                            }
                        }
                    }
            );
        }

        // REPLY PREVIEW

        if (!message.getReplyToContent().isEmpty()) {

            holder.replyLayout.setVisibility(View.VISIBLE);

            holder.replySender.setText(
                    message.getReplyToSender()
            );

            holder.replyContent.setText(
                    message.getReplyToContent()
            );

        } else {
            holder.replyLayout.setVisibility(View.GONE);
        }

        // MESSAGE BUBBLE

        holder.root.setGravity(
                mine ? Gravity.END : Gravity.START
        );

        holder.bubble.setBackgroundResource(
                mine
                        ? R.drawable.bg_message_outgoing
                        : R.drawable.bg_message_incoming
        );

        LinearLayout.LayoutParams params =
                (LinearLayout.LayoutParams)
                        holder.bubble.getLayoutParams();

        params.width = ViewGroup.LayoutParams.WRAP_CONTENT;

        params.setMargins(
                mine ? 45 : 0,
                3,
                mine ? 0 : 45,
                3
        );

        holder.bubble.setLayoutParams(params);

        // MESSAGE TIME

        String time = formatTime(message);

        holder.time.setText(
                message.isEdited()
                        ? time + " · edited"
                        : time
        );

        holder.time.setTextColor(0xFF777777);

        // DELIVERY AND READ RECEIPTS

        if (mine) {

            List<String> members = new ArrayList<>();

            if (message.getParticipantIds() != null) {
                members.addAll(message.getParticipantIds());
            }

            members.remove(currentUserAuthUid);

            List<String> uniqueMembers = new ArrayList<>();

            for (String member : members) {
                if (member != null
                        && !member.trim().isEmpty()
                        && !uniqueMembers.contains(member)) {

                    uniqueMembers.add(member);
                }
            }

            List<String> deliveredTo = message.getDeliveredTo();
            List<String> readBy = message.getReadBy();

            boolean delivered = !uniqueMembers.isEmpty()
                    && deliveredTo.containsAll(uniqueMembers);

            boolean read = !uniqueMembers.isEmpty()
                    && readBy.containsAll(uniqueMembers);

            if (read) {
                holder.status.setText("✓✓");
                holder.status.setTextColor(0xFF1688E8);

            } else if (delivered) {
                holder.status.setText("✓✓");
                holder.status.setTextColor(0xFF777777);

            } else {
                holder.status.setText("✓");
                holder.status.setTextColor(0xFF777777);
            }

            holder.status.setVisibility(View.VISIBLE);

        } else {
            holder.status.setVisibility(View.GONE);
        }

        // LONG PRESS

        holder.itemView.setOnLongClickListener(v -> {

            if (longClickListener == null) {
                return false;
            }

            longClickListener.onLongClick(message);
            return true;
        });
    }

    // START AUDIO

    private void playAudio(ViewHolder holder, String url) {

        if (url == null || url.trim().isEmpty()) {
            return;
        }

        stopAudio();

        try {
            mediaPlayer = new MediaPlayer();
            playingAudioUrl = url;
            activeAudioHolder = holder;

            holder.audioPlayButton.setImageResource(
                    android.R.drawable.ic_media_pause
            );

            holder.audioCurrentTime.setText("0:00");
            holder.audioDuration.setText("Loading...");
            holder.audioSeekBar.setProgress(0);

            mediaPlayer.setOnPreparedListener(player -> {

                if (mediaPlayer != player) {
                    player.release();
                    return;
                }

                try {
                    int duration = player.getDuration();

                    holder.audioSeekBar.setMax(Math.max(duration, 1));
                    holder.audioDuration.setText(
                            formatDuration(duration)
                    );

                    player.start();

                    holder.audioPlayButton.setImageResource(
                            android.R.drawable.ic_media_pause
                    );

                    audioHandler.removeCallbacks(audioProgressRunnable);
                    audioHandler.post(audioProgressRunnable);

                } catch (Exception error) {
                    stopAudio();
                    Toast.makeText(
                            holder.itemView.getContext(),
                            "Unable to play audio",
                            Toast.LENGTH_SHORT
                    ).show();
                }
            });

            mediaPlayer.setOnCompletionListener(player -> {
                if (mediaPlayer == player) {
                    stopAudio();
                }
            });

            mediaPlayer.setOnErrorListener((player, what, extra) -> {
                if (mediaPlayer == player) {
                    stopAudio();

                    Toast.makeText(
                            holder.itemView.getContext(),
                            "Audio playback failed",
                            Toast.LENGTH_SHORT
                    ).show();
                }

                return true;
            });

            mediaPlayer.setDataSource(url);
            mediaPlayer.prepareAsync();

        } catch (Exception error) {
            stopAudio();

            Toast.makeText(
                    holder.itemView.getContext(),
                    "Unable to load audio",
                    Toast.LENGTH_SHORT
            ).show();
        }
    }

    // PLAY / PAUSE

    private void toggleAudio(ViewHolder holder) {

        if (mediaPlayer == null) {
            return;
        }

        try {
            if (mediaPlayer.isPlaying()) {

                mediaPlayer.pause();

                holder.audioPlayButton.setImageResource(
                        android.R.drawable.ic_media_play
                );

                audioHandler.removeCallbacks(audioProgressRunnable);

            } else {

                mediaPlayer.start();

                holder.audioPlayButton.setImageResource(
                        android.R.drawable.ic_media_pause
                );

                audioHandler.removeCallbacks(audioProgressRunnable);
                audioHandler.post(audioProgressRunnable);
            }

        } catch (Exception ignored) {
            stopAudio();
        }
    }

    // STOP AUDIO

    private void stopAudio() {

        audioHandler.removeCallbacks(audioProgressRunnable);

        if (mediaPlayer != null) {
            try {
                mediaPlayer.stop();
            } catch (Exception ignored) {
            }

            try {
                mediaPlayer.release();
            } catch (Exception ignored) {
            }
        }

        mediaPlayer = null;
        playingAudioUrl = "";

        if (activeAudioHolder != null) {
            resetAudioViews(activeAudioHolder);
        }

        activeAudioHolder = null;
    }

    private void resetAudioViews(ViewHolder holder) {

        holder.audioPlayButton.setImageResource(
                android.R.drawable.ic_media_play
        );

        holder.audioSeekBar.setProgress(0);
        holder.audioCurrentTime.setText("0:00");
        holder.audioDuration.setText("0:00");
    }

    // FORMAT AUDIO DURATION

    private String formatDuration(int milliseconds) {

        int totalSeconds = Math.max(0, milliseconds / 1000);

        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;

        return String.format(
                Locale.getDefault(),
                "%d:%02d",
                minutes,
                seconds
        );
    }

    // SENDER AUTH UID

    private String getSenderAuthUid(Message message) {

        if (message == null) {
            return "";
        }

        String authUid = message.getSenderAuthUid();

        if (authUid != null && !authUid.trim().isEmpty()) {
            return authUid;
        }

        String senderId = message.getSenderId();

        return senderId == null ? "" : senderId;
    }

    // OPEN ATTACHMENT

    private void openAttachment(View view, Message message) {

        try {
            String attachmentUrl = message.getAttachmentUrl();

            if (attachmentUrl == null || attachmentUrl.trim().isEmpty()) {
                return;
            }

            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse(attachmentUrl));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            view.getContext().startActivity(intent);

        } catch (Exception error) {

            Toast.makeText(
                    view.getContext(),
                    "Unable to open attachment",
                    Toast.LENGTH_SHORT
            ).show();
        }
    }

    // FORMAT MESSAGE TIME

    private String formatTime(Message message) {

        if (message == null) {
            return "";
        }

        long timestamp = message.getCreatedAtMillis();

        if (timestamp > 0) {

            SimpleDateFormat sdf = new SimpleDateFormat(
                    "hh:mm a",
                    Locale.getDefault()
            );

            return sdf.format(new Date(timestamp));
        }

        String value = message.getCreatedAt();

        if (value == null || value.trim().isEmpty()) {
            return "";
        }

        String[] formats = {
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                "yyyy-MM-dd'T'HH:mm:ss'Z'",
                "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
                "yyyy-MM-dd'T'HH:mm:ssXXX"
        };

        for (String format : formats) {

            try {
                SimpleDateFormat parser = new SimpleDateFormat(
                        format,
                        Locale.US
                );

                parser.setTimeZone(TimeZone.getTimeZone("UTC"));

                Date date = parser.parse(value);

                if (date != null) {

                    SimpleDateFormat output = new SimpleDateFormat(
                            "hh:mm a",
                            Locale.getDefault()
                    );

                    return output.format(date);
                }

            } catch (Exception ignored) {
            }
        }

        return "";
    }

    @Override
    public void onViewRecycled(@NonNull ViewHolder holder) {

        if (holder == activeAudioHolder) {
            stopAudio();
        }

        super.onViewRecycled(holder);
    }

    public void releaseAudio() {
        stopAudio();
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    // VIEW HOLDER

    static class ViewHolder extends RecyclerView.ViewHolder {

        LinearLayout root;
        LinearLayout bubble;
        LinearLayout replyLayout;
        LinearLayout audioLayout;

        TextView sender;
        TextView content;
        TextView time;
        TextView status;

        TextView replySender;
        TextView replyContent;
        TextView attachmentName;

        ImageButton audioPlayButton;
        SeekBar audioSeekBar;
        TextView audioFileName;
        TextView audioCurrentTime;
        TextView audioDuration;

        ViewHolder(View itemView) {

            super(itemView);

            root = itemView.findViewById(R.id.messageRoot);
            bubble = itemView.findViewById(R.id.messageBubble);

            sender = itemView.findViewById(R.id.txtMessageSender);
            content = itemView.findViewById(R.id.txtMessageContent);
            time = itemView.findViewById(R.id.txtMessageTime);
            status = itemView.findViewById(R.id.txtMessageStatus);

            replyLayout = itemView.findViewById(R.id.layoutReplyPreview);
            replySender = itemView.findViewById(R.id.txtReplySender);
            replyContent = itemView.findViewById(R.id.txtReplyContent);

            attachmentName = itemView.findViewById(R.id.txtAttachmentName);

            audioLayout = itemView.findViewById(R.id.layoutAudioPlayer);
            audioPlayButton = itemView.findViewById(R.id.btnAudioPlay);
            audioSeekBar = itemView.findViewById(R.id.seekAudioProgress);
            audioFileName = itemView.findViewById(R.id.txtAudioFileName);
            audioCurrentTime = itemView.findViewById(R.id.txtAudioCurrentTime);
            audioDuration = itemView.findViewById(R.id.txtAudioDuration);
        }
    }
}
