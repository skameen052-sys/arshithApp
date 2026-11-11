package com.example.arshithconnectattendance.chatnew.adapters;

import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.arshithconnectattendance.chatnew.model.ChatMessage;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ChatMessageAdapter
        extends RecyclerView.Adapter<ChatMessageAdapter.MessageViewHolder> {

    private static final int TYPE_SENT = 1;
    private static final int TYPE_RECEIVED = 2;

    private static final int ORANGE_START = Color.rgb(255, 138, 61);
    private static final int ORANGE_END = Color.rgb(242, 85, 0);
    private static final int ORANGE_DARK = Color.rgb(217, 74, 0);

    private static final int MAIN_TEXT = Color.rgb(30, 26, 23);
    private static final int SECONDARY_TEXT = Color.rgb(140, 128, 121);
    private static final int LIGHT_REPLY = Color.rgb(255, 239, 226);
    private static final int SOFT_BORDER = Color.rgb(242, 227, 215);

    private static final int SENT_TICK_GREY = Color.rgb(190, 190, 190);
    private static final int READ_TICK_BLUE = Color.rgb(33, 150, 243);

    public interface MessageActionListener {
        void onReply(ChatMessage message);
        void onEdit(ChatMessage message);
        void onDelete(ChatMessage message);
        void onForward(ChatMessage message);
    }

    private final String currentUid;
    private final List<ChatMessage> messages = new ArrayList<>();

    private MessageActionListener actionListener;

    // Group-chat sender information.
    private boolean isGroupChat = false;
    private final Map<String, String> senderNames = new HashMap<>();

    public ChatMessageAdapter(String currentUid) {
        this.currentUid = currentUid;
    }

    public ChatMessageAdapter(
            String currentUid,
            MessageActionListener actionListener
    ) {
        this.currentUid = currentUid;
        this.actionListener = actionListener;
    }

    public void setMessageActionListener(MessageActionListener listener) {
        this.actionListener = listener;
    }

    // Enable or disable group-chat display.
    public void setGroupChat(boolean groupChat) {
        this.isGroupChat = groupChat;
        notifyDataSetChanged();
    }

    // Set sender UID-to-name mapping from the activity.
    public void setSenderNames(Map<String, String> names) {
        senderNames.clear();

        if (names != null) {
            senderNames.putAll(names);
        }

        notifyDataSetChanged();
    }

    // Keep pending local attachments when Firestore refreshes messages.
    public void setMessages(List<ChatMessage> newMessages) {
        List<ChatMessage> pending = new ArrayList<>();

        for (ChatMessage message : messages) {
            if (message.isPendingUpload()) {
                pending.add(message);
            }
        }

        messages.clear();

        if (newMessages != null) {
            messages.addAll(newMessages);
        }

        for (ChatMessage pendingMessage : pending) {
            boolean alreadyPresent = false;

            for (ChatMessage message : messages) {
                if (pendingMessage.getLocalUploadId() != null
                        && pendingMessage.getLocalUploadId()
                        .equals(message.getLocalUploadId())) {
                    alreadyPresent = true;
                    break;
                }
            }

            if (!alreadyPresent) {
                messages.add(pendingMessage);
            }
        }

        notifyDataSetChanged();
    }

    // Add an attachment bubble immediately when upload begins.
    public void addPendingAttachment(ChatMessage message) {
        if (message == null) return;

        message.setPendingUpload(true);
        message.setUploading(true);

        messages.add(message);
        notifyItemInserted(messages.size() - 1);
    }

    // Update the progress percentage of a pending upload.
    public void updatePendingAttachment(
            String uploadId,
            int progress
    ) {
        if (uploadId == null) return;

        for (int i = 0; i < messages.size(); i++) {
            ChatMessage message = messages.get(i);

            if (uploadId.equals(message.getLocalUploadId())) {
                message.setUploadProgress(progress);
                notifyItemChanged(i);
                return;
            }
        }
    }

    // Remove the temporary bubble after upload completes or fails.
    public void removePendingAttachment(String uploadId) {
        if (uploadId == null) return;

        for (int i = 0; i < messages.size(); i++) {
            ChatMessage message = messages.get(i);

            if (uploadId.equals(message.getLocalUploadId())) {
                messages.remove(i);
                notifyItemRemoved(i);
                return;
            }
        }
    }

    private boolean isDarkMode(Context context) {
        int nightMode = context.getResources()
                .getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;

        return nightMode == Configuration.UI_MODE_NIGHT_YES;
    }

    @Override
    public int getItemViewType(int position) {
        ChatMessage message = messages.get(position);

        return currentUid != null
                && currentUid.equals(message.getSenderId())
                ? TYPE_SENT
                : TYPE_RECEIVED;
    }

    @NonNull
    @Override
    public MessageViewHolder onCreateViewHolder(
            @NonNull ViewGroup parent,
            int viewType
    ) {
        Context context = parent.getContext();
        boolean dark = isDarkMode(context);

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(
                dp(context, 10),
                dp(context, 3),
                dp(context, 10),
                dp(context, 3)
        );

        LinearLayout bubble = new LinearLayout(context);
        bubble.setOrientation(LinearLayout.VERTICAL);
        bubble.setPadding(
                dp(context, 14),
                dp(context, 9),
                dp(context, 12),
                dp(context, 7)
        );

        bubble.setBackground(
                createBubbleBackground(context, viewType)
        );
        bubble.setElevation(dp(context, 2));
        bubble.setMinimumWidth(dp(context, 125));

        // Sender name shown above received group messages.
        TextView senderName = new TextView(context);
        senderName.setTextSize(12);
        senderName.setTypeface(null, Typeface.BOLD);
        senderName.setTextColor(Color.rgb(0, 137, 123));
        senderName.setVisibility(View.GONE);
        senderName.setMaxLines(1);
        senderName.setEllipsize(TextUtils.TruncateAt.END);

        LinearLayout.LayoutParams senderNameParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        senderNameParams.bottomMargin = dp(context, 4);
        bubble.addView(senderName, senderNameParams);

        // Reply preview.
        LinearLayout replyContainer = new LinearLayout(context);
        replyContainer.setOrientation(LinearLayout.HORIZONTAL);
        replyContainer.setGravity(Gravity.CENTER_VERTICAL);
        replyContainer.setPadding(
                dp(context, 7),
                dp(context, 6),
                dp(context, 7),
                dp(context, 6)
        );

        replyContainer.setBackground(
                roundedBackground(
                        dark ? Color.rgb(65, 48, 40) : LIGHT_REPLY,
                        dp(context, 9)
                )
        );
        replyContainer.setVisibility(View.GONE);

        View replyAccent = new View(context);
        replyAccent.setBackgroundColor(
                dark ? Color.rgb(255, 177, 123) : ORANGE_DARK
        );

        replyContainer.addView(
                replyAccent,
                new LinearLayout.LayoutParams(
                        dp(context, 3),
                        ViewGroup.LayoutParams.MATCH_PARENT
                )
        );

        TextView replyText = new TextView(context);
        replyText.setTextColor(
                dark ? Color.rgb(255, 218, 193) : ORANGE_DARK
        );
        replyText.setTextSize(12);
        replyText.setMaxLines(2);
        replyText.setEllipsize(TextUtils.TruncateAt.END);
        replyText.setPadding(dp(context, 8), 0, dp(context, 4), 0);

        replyContainer.addView(
                replyText,
                new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1
                )
        );

        LinearLayout.LayoutParams replyParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
        replyParams.bottomMargin = dp(context, 8);
        bubble.addView(replyContainer, replyParams);

        // Image container.
        FrameLayout imageFrame = new FrameLayout(context);
        imageFrame.setVisibility(View.GONE);
        imageFrame.setBackgroundColor(Color.TRANSPARENT);

        ImageView imageView = new ImageView(context);
        imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        imageView.setBackground(
                roundedBackground(Color.TRANSPARENT, dp(context, 12))
        );
        imageView.setClipToOutline(true);

        FrameLayout.LayoutParams imageFrameParams =
                new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                );
        imageFrame.addView(imageView, imageFrameParams);

        LinearLayout imageProgressOverlay = createProgressOverlay(context);

        FrameLayout.LayoutParams imageProgressParams =
                new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                );
        imageFrame.addView(imageProgressOverlay, imageProgressParams);

        LinearLayout.LayoutParams imageParams =
                new LinearLayout.LayoutParams(
                        dp(context, 230),
                        dp(context, 210)
                );
        imageParams.bottomMargin = dp(context, 6);

        bubble.addView(imageFrame, imageParams);

        // Document icon.
        TextView documentIcon = new TextView(context);
        documentIcon.setText("📄");
        documentIcon.setTextSize(30);
        documentIcon.setGravity(Gravity.CENTER);
        documentIcon.setVisibility(View.GONE);

        bubble.addView(
                documentIcon,
                new LinearLayout.LayoutParams(
                        dp(context, 48),
                        dp(context, 48)
                )
        );

        // Message text.
        TextView messageText = new TextView(context);
        messageText.setTextColor(dark ? Color.WHITE : MAIN_TEXT);
        messageText.setTextSize(15.5f);
        messageText.setLineSpacing(dp(context, 2), 1f);
        messageText.setTextIsSelectable(false);

        int screenWidth = context.getResources()
                .getDisplayMetrics().widthPixels;

        int maxBubbleWidth = (int) (screenWidth * 0.75f);

        messageText.setMaxWidth(maxBubbleWidth - dp(context, 26));
        messageText.setMinWidth(0);
        messageText.setMaxLines(Integer.MAX_VALUE);
        messageText.setEllipsize(null);
        messageText.setSingleLine(false);
        messageText.setHorizontallyScrolling(false);
        messageText.setIncludeFontPadding(true);
        messageText.setBreakStrategy(
                android.text.Layout.BREAK_STRATEGY_HIGH_QUALITY
        );
        messageText.setHyphenationFrequency(
                android.text.Layout.HYPHENATION_FREQUENCY_NORMAL
        );

        bubble.addView(messageText);

        // Document upload progress.
        LinearLayout documentProgressOverlay = createProgressOverlay(context);
        documentProgressOverlay.setVisibility(View.GONE);

        LinearLayout.LayoutParams documentProgressParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
        documentProgressParams.topMargin = dp(context, 5);

        bubble.addView(
                documentProgressOverlay,
                documentProgressParams
        );

        // Forwarded label.
        TextView forwardedText = new TextView(context);
        forwardedText.setText("↪ Forwarded");
        forwardedText.setTextColor(
                dark ? Color.rgb(220, 190, 170) : SECONDARY_TEXT
        );
        forwardedText.setTextSize(11);
        forwardedText.setTypeface(null, Typeface.ITALIC);
        forwardedText.setVisibility(View.GONE);

        LinearLayout.LayoutParams forwardedParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
        forwardedParams.topMargin = dp(context, 4);
        bubble.addView(forwardedText, forwardedParams);

        // Edited label.
        TextView editedText = new TextView(context);
        editedText.setText("Edited");
        editedText.setTextColor(
                dark ? Color.rgb(220, 190, 170) : SECONDARY_TEXT
        );
        editedText.setTextSize(10);
        editedText.setVisibility(View.GONE);
        bubble.addView(editedText);

        // Timestamp and read/delivery status.
        LinearLayout metadata = new LinearLayout(context);
        metadata.setOrientation(LinearLayout.HORIZONTAL);
        metadata.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        metadata.setMinimumWidth(dp(context, 100));

        TextView timeText = new TextView(context);
        timeText.setTextColor(
                viewType == TYPE_SENT
                        ? Color.rgb(255, 239, 226)
                        : SECONDARY_TEXT
        );
        timeText.setTextSize(10);
        timeText.setSingleLine(true);
        timeText.setIncludeFontPadding(false);

        TextView readStatus = new TextView(context);
        readStatus.setTextColor(
                viewType == TYPE_SENT ? Color.WHITE : ORANGE_DARK
        );
        readStatus.setTextSize(11);
        readStatus.setSingleLine(true);
        readStatus.setIncludeFontPadding(false);
        readStatus.setTypeface(null, Typeface.BOLD);
        readStatus.setPadding(dp(context, 5), 0, 0, 0);

        metadata.addView(timeText);
        metadata.addView(readStatus);

        LinearLayout.LayoutParams metadataParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
        metadataParams.gravity = Gravity.END;
        metadataParams.topMargin = dp(context, 5);

        bubble.addView(metadata, metadataParams);

        LinearLayout.LayoutParams bubbleParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
        bubbleParams.gravity = viewType == TYPE_SENT
                ? Gravity.END
                : Gravity.START;

        root.addView(bubble, bubbleParams);

        root.setLayoutParams(
                new RecyclerView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                )
        );

        return new MessageViewHolder(
                root,
                bubble,
                senderName,
                replyContainer,
                replyText,
                imageFrame,
                imageView,
                imageProgressOverlay,
                documentIcon,
                messageText,
                documentProgressOverlay,
                forwardedText,
                editedText,
                timeText,
                readStatus
        );
    }

    private LinearLayout createProgressOverlay(Context context) {
        LinearLayout overlay = new LinearLayout(context);
        overlay.setOrientation(LinearLayout.VERTICAL);
        overlay.setGravity(Gravity.CENTER);
        overlay.setPadding(
                dp(context, 10),
                dp(context, 8),
                dp(context, 10),
                dp(context, 8)
        );
        overlay.setBackgroundColor(0x99000000);

        TextView percentage = new TextView(context);
        percentage.setTag("upload_percentage");
        percentage.setText("Uploading 0%");
        percentage.setTextColor(Color.WHITE);
        percentage.setTextSize(13);
        percentage.setGravity(Gravity.CENTER);
        percentage.setTypeface(null, Typeface.BOLD);

        ProgressBar progressBar = new ProgressBar(
                context,
                null,
                android.R.attr.progressBarStyleHorizontal
        );
        progressBar.setTag("upload_progress");
        progressBar.setMax(100);
        progressBar.setProgress(0);

        LinearLayout.LayoutParams progressParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(context, 5)
                );
        progressParams.topMargin = dp(context, 7);

        overlay.addView(percentage);
        overlay.addView(progressBar, progressParams);

        overlay.setVisibility(View.GONE);
        return overlay;
    }

    private void updateProgressOverlay(
            LinearLayout overlay,
            int progress,
            boolean uploading
    ) {
        if (overlay == null) return;

        TextView percentage =
                overlay.findViewWithTag("upload_percentage");

        ProgressBar progressBar =
                overlay.findViewWithTag("upload_progress");

        if (uploading) {
            overlay.setVisibility(View.VISIBLE);

            if (percentage != null) {
                percentage.setText("Uploading " + progress + "%");
            }

            if (progressBar != null) {
                progressBar.setProgress(progress);
            }
        } else {
            overlay.setVisibility(View.GONE);
        }
    }

    private GradientDrawable createBubbleBackground(
            Context context,
            int viewType
    ) {
        GradientDrawable background = new GradientDrawable();

        float radius = dp(context, 20);
        float smallRadius = dp(context, 6);

        if (viewType == TYPE_SENT) {
            background.setCornerRadii(new float[]{
                    radius, radius,
                    radius, radius,
                    smallRadius, smallRadius,
                    radius, radius
            });

            background.setColors(new int[]{
                    ORANGE_START,
                    ORANGE_END
            });

            background.setOrientation(
                    GradientDrawable.Orientation.TL_BR
            );
        } else {
            background.setCornerRadii(new float[]{
                    radius, radius,
                    radius, radius,
                    radius, radius,
                    smallRadius, smallRadius
            });

            background.setColor(Color.WHITE);
            background.setStroke(dp(context, 1), SOFT_BORDER);
        }

        return background;
    }

    @Override
    public void onBindViewHolder(
            @NonNull MessageViewHolder holder,
            int position
    ) {
        ChatMessage message = messages.get(position);
        Context context = holder.itemView.getContext();

        boolean dark = isDarkMode(context);

        boolean isSent = currentUid != null
                && currentUid.equals(message.getSenderId());

        // WhatsApp-style sender name for received group messages.
        if (isGroupChat && !isSent) {
            String name = senderNames.get(message.getSenderId());

            if (name == null || name.trim().isEmpty()) {
                name = "Member";
            }

            holder.senderName.setText(name);
            holder.senderName.setVisibility(View.VISIBLE);
        } else {
            holder.senderName.setVisibility(View.GONE);
        }

        String messageType = message.getType() == null
                ? "text"
                : message.getType();

        boolean isDeleted = message.isDeleted();
        boolean uploading = message.isPendingUpload()
                && message.isUploading();

        int progress = message.getUploadProgress();

        // Reset recycled views.
        holder.imageFrame.setVisibility(View.GONE);
        holder.documentIcon.setVisibility(View.GONE);
        holder.messageText.setVisibility(View.VISIBLE);
        holder.editedText.setVisibility(View.GONE);
        holder.replyContainer.setVisibility(View.GONE);
        holder.documentProgressOverlay.setVisibility(View.GONE);

        holder.messageText.setTypeface(null, Typeface.NORMAL);
        holder.messageText.setTextColor(
                isSent ? Color.WHITE : Color.rgb(45, 40, 36)
        );

        holder.forwardedText.setVisibility(
                message.isForwarded() && !isDeleted
                        ? View.VISIBLE
                        : View.GONE
        );

        holder.timeText.setTextColor(
                isSent
                        ? Color.rgb(255, 240, 225)
                        : Color.rgb(140, 128, 121)
        );

        holder.readStatus.setVisibility(View.GONE);
        holder.readStatus.setText("");

        Glide.with(holder.imageView).clear(holder.imageView);

        holder.imageView.setOnClickListener(null);
        holder.itemView.setOnClickListener(null);
        holder.itemView.setOnLongClickListener(null);

        updateProgressOverlay(
                holder.imageProgressOverlay,
                progress,
                false
        );

        // Reply preview.
        String replyId = message.getReplyToMessageId();

        if (!isDeleted
                && replyId != null
                && !replyId.trim().isEmpty()) {

            String originalText = message.getReplyToText();
            String originalType = message.getReplyToType();

            if (originalText == null || originalText.trim().isEmpty()) {
                originalText = "Message";
            }

            if ("image".equals(originalType)) {
                originalText = "📷 " + originalText;
            } else if ("document".equals(originalType)) {
                originalText = "📄 " + originalText;
            }

            holder.replyText.setText("↪ " + originalText);
            holder.replyContainer.setVisibility(View.VISIBLE);
        }

        if (isDeleted) {
            holder.messageText.setText("This message was deleted.");
            holder.messageText.setTypeface(null, Typeface.ITALIC);
            holder.messageText.setTextColor(
                    dark
                            ? Color.rgb(190, 180, 172)
                            : Color.rgb(145, 130, 120)
            );

        } else if ("image".equals(messageType)) {

            holder.messageText.setVisibility(View.GONE);
            holder.imageFrame.setVisibility(View.VISIBLE);

            String imageSource = uploading
                    ? message.getLocalUri()
                    : message.getMediaUrl();

            if (imageSource != null && !imageSource.trim().isEmpty()) {
                Object source = uploading
                        ? Uri.parse(imageSource)
                        : imageSource;

                Glide.with(holder.imageView)
                        .load(source)
                        .placeholder(android.R.drawable.ic_menu_gallery)
                        .error(android.R.drawable.ic_menu_report_image)
                        .centerCrop()
                        .into(holder.imageView);

                if (!uploading) {
                    holder.imageView.setOnClickListener(v ->
                            showFullScreenImage(
                                    v.getContext(),
                                    message.getMediaUrl()
                            )
                    );
                }
            } else {
                holder.imageFrame.setVisibility(View.GONE);
                holder.messageText.setVisibility(View.VISIBLE);
                holder.messageText.setText("Image unavailable");
            }

            updateProgressOverlay(
                    holder.imageProgressOverlay,
                    progress,
                    uploading
            );

        } else if ("document".equals(messageType)) {

            holder.documentIcon.setVisibility(View.VISIBLE);

            String fileName = message.getText();

            holder.messageText.setText(
                    fileName == null || fileName.trim().isEmpty()
                            ? "Document"
                            : fileName
            );

            holder.messageText.setTypeface(null, Typeface.BOLD);

            updateProgressOverlay(
                    holder.documentProgressOverlay,
                    progress,
                    uploading
            );

            String documentUrl = message.getMediaUrl();

            if (!uploading
                    && documentUrl != null
                    && !documentUrl.trim().isEmpty()) {
                holder.itemView.setOnClickListener(v ->
                        openDocument(v.getContext(), documentUrl)
                );
            }

        } else {
            String text = message.getText();
            holder.messageText.setText(text == null ? "" : text);

            if (message.isEdited()) {
                holder.editedText.setVisibility(View.VISIBLE);
            }
        }

        // Timestamp.
        Date createdAt = message.getCreatedAt();

        if (createdAt != null) {
            SimpleDateFormat format = new SimpleDateFormat(
                    "hh:mm a",
                    Locale.getDefault()
            );
            holder.timeText.setText(format.format(createdAt));
        } else {
            holder.timeText.setText(
                    uploading ? "Sending..." : ""
            );
        }

        // Read and delivery ticks.
        if (isSent && !uploading) {
            boolean isRead = false;
            boolean isDelivered = false;

            List<String> readBy = message.getReadBy();

            if (readBy != null) {
                for (String uid : readBy) {
                    if (uid != null
                            && !uid.equals(message.getSenderId())) {
                        isRead = true;
                        break;
                    }
                }
            }

            List<String> deliveredTo = message.getDeliveredTo();

            if (deliveredTo != null) {
                for (String uid : deliveredTo) {
                    if (uid != null
                            && !uid.equals(message.getSenderId())) {
                        isDelivered = true;
                        break;
                    }
                }
            }

            if (isRead) {
                holder.readStatus.setText("✓✓");
                holder.readStatus.setTextColor(READ_TICK_BLUE);
            } else if (isDelivered) {
                holder.readStatus.setText("✓✓");
                holder.readStatus.setTextColor(SENT_TICK_GREY);
            } else {
                holder.readStatus.setText("✓");
                holder.readStatus.setTextColor(SENT_TICK_GREY);
            }

            holder.readStatus.setVisibility(View.VISIBLE);
        }

        // Disable message actions while uploading.
        if (!uploading) {
            holder.itemView.setOnLongClickListener(v -> {
                showMessageActions(v.getContext(), message);
                return true;
            });
        }
    }

    // Full-screen image viewer.
    private void showFullScreenImage(
            Context context,
            String imageUrl
    ) {
        if (imageUrl == null || imageUrl.trim().isEmpty()) {
            return;
        }

        Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        FrameLayout container = new FrameLayout(context);
        container.setBackgroundColor(Color.BLACK);

        ImageView fullImage = new ImageView(context);
        fullImage.setScaleType(ImageView.ScaleType.FIT_CENTER);

        FrameLayout.LayoutParams imageParams =
                new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                );

        container.addView(fullImage, imageParams);

        TextView close = new TextView(context);
        close.setText("✕");
        close.setTextColor(Color.WHITE);
        close.setTextSize(25);
        close.setGravity(Gravity.CENTER);
        close.setBackground(
                roundedBackground(0x66000000, dp(context, 30))
        );

        FrameLayout.LayoutParams closeParams =
                new FrameLayout.LayoutParams(
                        dp(context, 48),
                        dp(context, 48),
                        Gravity.TOP | Gravity.END
                );

        closeParams.setMargins(
                dp(context, 15),
                dp(context, 25),
                dp(context, 15),
                0
        );

        container.addView(close, closeParams);

        close.setOnClickListener(v -> dialog.dismiss());
        container.setOnClickListener(v -> dialog.dismiss());

        dialog.setContentView(container);
        dialog.setCancelable(true);

        Window window = dialog.getWindow();

        if (window != null) {
            window.setBackgroundDrawableResource(
                    android.R.color.transparent
            );
            window.setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            );
            window.setStatusBarColor(Color.BLACK);
            window.setNavigationBarColor(Color.BLACK);
        }

        dialog.show();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            );
        }

        Glide.with(context)
                .load(imageUrl)
                .fitCenter()
                .into(fullImage);
    }

    private void showMessageActions(
            Context context,
            ChatMessage message
    ) {
        if (message == null
                || message.isDeleted()
                || message.isPendingUpload()) {
            return;
        }

        boolean isMine = currentUid != null
                && currentUid.equals(message.getSenderId());

        boolean isTextMessage = "text".equals(message.getType());

        List<String> options = new ArrayList<>();
        List<Integer> actions = new ArrayList<>();

        options.add("Reply");
        actions.add(1);

        options.add("Forward");
        actions.add(5);

        if (isTextMessage) {
            options.add("Copy");
            actions.add(2);
        }

        if (isMine && isTextMessage) {
            options.add("Edit");
            actions.add(3);
        }

        if (isMine) {
            options.add("Delete");
            actions.add(4);
        }

        new AlertDialog.Builder(context)
                .setTitle("Message options")
                .setItems(
                        options.toArray(new String[0]),
                        (dialog, which) -> {
                            int action = actions.get(which);

                            if (actionListener == null) {
                                Toast.makeText(
                                        context,
                                        "Message actions are not connected yet.",
                                        Toast.LENGTH_SHORT
                                ).show();
                                return;
                            }

                            switch (action) {
                                case 1:
                                    actionListener.onReply(message);
                                    break;

                                case 2:
                                    copyMessage(context, message.getText());
                                    break;

                                case 3:
                                    actionListener.onEdit(message);
                                    break;

                                case 4:
                                    new AlertDialog.Builder(context)
                                            .setTitle("Delete message")
                                            .setMessage(
                                                    "Are you sure you want to delete this message?"
                                            )
                                            .setNegativeButton("Cancel", null)
                                            .setPositiveButton(
                                                    "Delete",
                                                    (d, w) ->
                                                            actionListener.onDelete(message)
                                            )
                                            .show();
                                    break;

                                case 5:
                                    actionListener.onForward(message);
                                    break;
                            }
                        }
                )
                .show();
    }

    private void copyMessage(Context context, String text) {
        if (text == null || text.trim().isEmpty()) {
            Toast.makeText(
                    context,
                    "Nothing to copy.",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        ClipboardManager clipboard =
                (ClipboardManager) context.getSystemService(
                        Context.CLIPBOARD_SERVICE
                );

        if (clipboard != null) {
            clipboard.setPrimaryClip(
                    ClipData.newPlainText("Chat message", text)
            );

            Toast.makeText(
                    context,
                    "Message copied.",
                    Toast.LENGTH_SHORT
            ).show();
        }
    }

    private void openDocument(
            Context context,
            String documentUrl
    ) {
        try {
            Intent intent = new Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse(documentUrl)
            );

            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            context.startActivity(
                    Intent.createChooser(intent, "Open document")
            );

        } catch (Exception e) {
            Toast.makeText(
                    context,
                    "No app found to open this document.",
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    private GradientDrawable roundedBackground(
            int color,
            int radius
    ) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    private static int dp(Context context, int value) {
        return (int) (
                value * context.getResources()
                        .getDisplayMetrics().density + 0.5f
        );
    }

    static class MessageViewHolder extends RecyclerView.ViewHolder {

        LinearLayout bubble;
        TextView senderName;

        LinearLayout replyContainer;
        LinearLayout imageProgressOverlay;
        LinearLayout documentProgressOverlay;

        TextView replyText;
        FrameLayout imageFrame;
        ImageView imageView;
        TextView documentIcon;
        TextView messageText;
        TextView forwardedText;
        TextView editedText;
        TextView timeText;
        TextView readStatus;

        MessageViewHolder(
                @NonNull View itemView,
                LinearLayout bubble,
                TextView senderName,
                LinearLayout replyContainer,
                TextView replyText,
                FrameLayout imageFrame,
                ImageView imageView,
                LinearLayout imageProgressOverlay,
                TextView documentIcon,
                TextView messageText,
                LinearLayout documentProgressOverlay,
                TextView forwardedText,
                TextView editedText,
                TextView timeText,
                TextView readStatus
        ) {
            super(itemView);

            this.bubble = bubble;
            this.senderName = senderName;
            this.replyContainer = replyContainer;
            this.replyText = replyText;
            this.imageFrame = imageFrame;
            this.imageView = imageView;
            this.imageProgressOverlay = imageProgressOverlay;
            this.documentIcon = documentIcon;
            this.messageText = messageText;
            this.documentProgressOverlay = documentProgressOverlay;
            this.forwardedText = forwardedText;
            this.editedText = editedText;
            this.timeText = timeText;
            this.readStatus = readStatus;
        }
    }
}