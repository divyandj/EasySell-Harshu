package com.easysell;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Toast; // Import Toast

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import java.util.List;

public class MediaPagerAdapter extends RecyclerView.Adapter<MediaPagerAdapter.MediaViewHolder> {

    private final Context context;
    private final List<MediaItem> mediaItems;

    public MediaPagerAdapter(Context context, List<MediaItem> mediaItems) {
        this.context = context;
        this.mediaItems = mediaItems;
    }

    @NonNull
    @Override
    public MediaViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_media_detail, parent, false);
        return new MediaViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MediaViewHolder holder, int position) {
        MediaItem item = mediaItems.get(position);
        holder.bind(item);
    }

    @Override
    public int getItemCount() {
        return mediaItems != null ? mediaItems.size() : 0;
    }

    // --- ViewHolder Class ---
    static class MediaViewHolder extends RecyclerView.ViewHolder {
        ImageView imageView;
        ImageView videoIcon;

        public MediaViewHolder(@NonNull View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.media_image_view);
            videoIcon = itemView.findViewById(R.id.video_play_icon);
        }

        /**
         * Binds a MediaItem to the view, setting up image/thumbnail loading
         * and appropriate click listeners.
         * @param item The MediaItem to display.
         */
        void bind(MediaItem item) {
            // Reset listeners first for recycled views
            itemView.setOnClickListener(null);
            itemView.setClickable(false);

            if ("video".equals(item.getType())) {
                // --- VIDEO HANDLING ---
                videoIcon.setVisibility(View.VISIBLE);
                Glide.with(itemView.getContext())
                        .load(item.getUrl()) // Load thumbnail from video URL
                        .placeholder(R.color.gray_200) // Placeholder color
                        .error(R.drawable.ic_launcher_foreground) // Error placeholder
                        .fitCenter() // Use fitCenter to avoid cropping thumbnail
                        .into(imageView);

                itemView.setClickable(true); // Make video item clickable
                itemView.setOnClickListener(v -> {
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    Uri videoUri = Uri.parse(item.getUrl());
                    intent.setDataAndType(videoUri, "video/*");
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

                    try {
                        itemView.getContext().startActivity(intent);
                    } catch (android.content.ActivityNotFoundException ex) {
                        Toast.makeText(itemView.getContext(), "No app found to play this video.", Toast.LENGTH_SHORT).show();
                        Log.e("MediaPagerAdapter", "ActivityNotFoundException for video URL: " + item.getUrl(), ex);
                    }
                });

            } else { // --- IMAGE HANDLING ---
                videoIcon.setVisibility(View.GONE);
                Glide.with(itemView.getContext())
                        .load(item.getUrl())
                        .placeholder(R.color.gray_200)
                        .error(R.drawable.ic_launcher_foreground)
                        .fitCenter() // Use fitCenter to avoid cropping
                        .into(imageView);

                itemView.setClickable(true); // Make image item clickable
                itemView.setOnClickListener(v -> {
                    Intent intent = new Intent(itemView.getContext(), FullScreenImageActivity.class);
                    intent.putExtra(FullScreenImageActivity.EXTRA_IMAGE_URL, item.getUrl());
                    itemView.getContext().startActivity(intent);
                });
            }
        }
    }
}