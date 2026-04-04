package com.easysell;

import android.os.Bundle;
import android.widget.ImageView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.bumptech.glide.Glide;

public class FullScreenImageActivity extends AppCompatActivity {

    public static final String EXTRA_IMAGE_URL = "extra_image_url";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_full_screen_image);

        ImageView fullScreenImageView = findViewById(R.id.full_screen_image_view);
        String imageUrl = getIntent().getStringExtra(EXTRA_IMAGE_URL);

        if (imageUrl != null && !imageUrl.isEmpty()) {
            Glide.with(this)
                    .load(imageUrl)
                    .fitCenter() // Ensure the full image is visible
                    .into(fullScreenImageView);
        } else {
            Toast.makeText(this, "Error: Image URL not found.", Toast.LENGTH_SHORT).show();
            finish(); // Close if no URL
        }

        // Optional: Click the image to close the activity
        fullScreenImageView.setOnClickListener(v -> finish());
    }
}