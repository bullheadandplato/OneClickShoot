package com.topratedappps.oneclickshot.listview;

import android.content.Context;
import android.net.Uri;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.squareup.picasso.Picasso;
import com.topratedappps.oneclickshot.R;
import com.topratedappps.oneclickshot.model.Screenshot;

import java.io.File;
import java.util.ArrayList;

/**
 * Created by bullhead on 11/5/17.
 */

public class HomeScreenAdapter extends RecyclerView.Adapter<HomeScreenAdapter.ViewHolder> {
    private Context context;
    private ArrayList<Screenshot> screenshots;

    public HomeScreenAdapter(Context context, ArrayList<Screenshot> screenshots) {
        this.context = context;
        this.screenshots = screenshots;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        return new ViewHolder(LayoutInflater.from(context)
                .inflate(R.layout.screenshot_list_item, parent, false));
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        Screenshot screenshot = screenshots.get(position);
        holder.titleView.setText(screenshot.getName());
        Picasso picasso = new Picasso.Builder(context)
                .listener(new Picasso.Listener() {
                    @Override
                    public void onImageLoadFailed(Picasso picasso, Uri uri, Exception exception) {
                        Log.d("hrllo", "onImageLoadFailed: " + uri);
                    }
                }).build();
        picasso.load(new File(screenshot.getFilepath()))
                .placeholder(R.drawable.ic_launcher_background)
                .into(holder.imageView);
    }

    @Override
    public int getItemCount() {
        return screenshots.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private ImageView imageView;
        private TextView titleView;

        public ViewHolder(View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.img_cap_list);
            titleView = itemView.findViewById(R.id.text_title_sc);
            itemView.findViewById(R.id.btn_share).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    share(screenshots.get(getAdapterPosition()));
                }
            });
            itemView.findViewById(R.id.btn_delete).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    delete(screenshots.get(getAdapterPosition()));
                }
            });
        }
    }

    private void delete(Screenshot screenshot) {
    }

    private void share(Screenshot screenshot) {
    }
}
