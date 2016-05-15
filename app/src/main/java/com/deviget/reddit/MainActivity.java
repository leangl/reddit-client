package com.deviget.reddit;

import android.app.ProgressDialog;
import android.content.Intent;
import android.net.Uri;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.squareup.picasso.Picasso;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import retrofit.RestAdapter;
import retrofit.client.OkClient;
import retrofit.converter.GsonConverter;
import rx.Observable;
import rx.Subscriber;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;

/**
 * Simple Reddit client example
 */
public class MainActivity extends AppCompatActivity {

    public static final int PAGE_SIZE = 10;
    public static final String REDDIT_URI = "http://www.reddit.com";
    public static final String SAVED_ENTRIES = "ENTRIES";

    private ArrayList<RedditPost> mPosts;
    private ListView mListView;
    private PostsAdapter mAdapter;
    private Api mApi;
    private Subscription mCurrentApiRequest;
    private ProgressDialog mProgressDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mPosts = new ArrayList<>();
        mListView = (ListView) findViewById(R.id.list);
        mAdapter = new PostsAdapter();
        mListView.setAdapter(mAdapter);

        mApi = new RestAdapter.Builder()
                .setEndpoint(REDDIT_URI)
                .setLogLevel(RestAdapter.LogLevel.FULL).setLog(msg -> Log.d("API", msg))
                .build().create(Api.class);

        if (savedInstanceState != null) {
            restorePosts(savedInstanceState);
        } else {
            fetchNextPage(); // load first page
        }
    }

    /**
     * Fetches next page of posts or first if invoked for the first time
     */
    private void fetchNextPage() {
        if (mCurrentApiRequest == null) { // prevent concurrent requests
            // Fetch next page (or first if after is null)
            String after = null;
            if (!mPosts.isEmpty()) {
                // Fetch posts after the last previously loaded post
                RedditPost lastPost = mPosts.get(mPosts.size() - 1);
                after = lastPost.name;
            }
            showLoading();
            // Call Reddit Api
            mCurrentApiRequest = mApi.top(after, PAGE_SIZE)
                    .observeOn(AndroidSchedulers.mainThread())
                    .flatMap(response -> {
                        JsonObject data = response.get("data").getAsJsonObject();
                        return Observable.from(data.get("children").getAsJsonArray());
                    })
                    .map(data -> new RedditPost(((JsonObject) data).get("data").getAsJsonObject()))
                    .buffer(PAGE_SIZE)
                    .subscribe(response -> { // SUCCESSFUL response
                        hideLoading();
                        mCurrentApiRequest = null;
                        mPosts.addAll(response);
                        mAdapter.notifyDataSetChanged();
                    }, error -> { // ERROR :/
                        hideLoading();
                        mCurrentApiRequest = null;
                        Log.e("API", "Error loading posts", error);
                        Toast.makeText(this, R.string.error, Toast.LENGTH_SHORT).show();
                    });
        }
    }

    /**
     * Show loading dialog
     */
    private void showLoading() {
        if (mProgressDialog == null) {
            runOnUiThread(() -> {
                mProgressDialog = ProgressDialog.show(this, null, getString(R.string.loading), true, false);
                mProgressDialog.setOnCancelListener(dialog -> mProgressDialog = null);
                mProgressDialog.setOnDismissListener(dialog -> mProgressDialog = null);
            });
        }
    }

    /**
     * Hide loading dialog
     */
    private void hideLoading() {
        if (mProgressDialog != null) {
            mProgressDialog.dismiss();
        }
    }

    /**
     * Removes Reddit tags (what's in between "[" and "]") from the given String
     */
    private String stripTags(String text) {
        return text.replaceAll("(?s)\\[.*?\\]", "");
    }

    private class PostsAdapter extends BaseAdapter {

        @Override
        public int getCount() {
            return mPosts.size();
        }

        @Override
        public RedditPost getItem(int position) {
            return mPosts.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(MainActivity.this).inflate(R.layout.reddit_post, parent, false);
                convertView.setTag(new Holder(convertView));
            }
            Holder holder = (Holder) convertView.getTag();

            RedditPost post = getItem(position);

            holder.title.setText(stripTags(post.title));
            holder.author.setText(post.author);

            // Set comments in units of thousands (ie 2.3k)
            if (post.numComments < 1000) {
                holder.comments.setText(post.numComments + "");
            } else {
                holder.comments.setText((post.numComments / 100) / 10d + "k");
            }

            // Set entry date in format like the official reddit client
            long elapsed = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis()) - post.createdUtc;
            if (TimeUnit.SECONDS.toMinutes(elapsed) < 2) {
                holder.date.setText("now");
            } else if (TimeUnit.SECONDS.toDays(elapsed) >= 1) {
                holder.date.setText(TimeUnit.SECONDS.toDays(elapsed) + "d");
            } else if (TimeUnit.SECONDS.toHours(elapsed) >= 1) {
                holder.date.setText(TimeUnit.SECONDS.toHours(elapsed) + "h");
            } else if (TimeUnit.SECONDS.toMinutes(elapsed) >= 1) {
                holder.date.setText(TimeUnit.SECONDS.toMinutes(elapsed) + "m");
            } else {
                holder.date.setText("now");
            }

            // Set thumbnail (if present)
            if (!post.thumbnail.isEmpty()) {
                holder.thumb.setVisibility(View.VISIBLE);
                // for those having a picture (besides the thumbnail), allow the user to tap on the
                // thumbnail to be sent to the full sized picture (so just opening the URL would be OK).
                if (!post.url.isEmpty()) {
                    holder.thumb.setOnClickListener(v -> {
                        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(post.url));
                        startActivity(browserIntent);
                    });
                } else {
                    holder.thumb.setOnClickListener(null);
                }
                Picasso.with(MainActivity.this)
                        .load(post.thumbnail)
                        .placeholder(R.drawable.ic_reddit_alien)
                        .fit().centerCrop().into(holder.thumb);
            } else { // no thumbnail present
                holder.thumb.setVisibility(View.GONE);
            }

            // Load next page when user reached the bottom of the list
            if (position >= mPosts.size() - 1) {
                fetchNextPage();
            }

            return convertView;
        }
    }

    class Holder {

        final ImageView thumb;
        final TextView title;
        final TextView author;
        final TextView date;
        final TextView comments;

        Holder(View v) {
            thumb = (ImageView) v.findViewById(R.id.thumb);
            title = (TextView) v.findViewById(R.id.title);
            author = (TextView) v.findViewById(R.id.author);
            date = (TextView) v.findViewById(R.id.date);
            comments = (TextView) v.findViewById(R.id.comments);
        }
    }


    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable(SAVED_ENTRIES, mPosts);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        restorePosts(savedInstanceState);
    }

    /**
     * Restores previously loaded posts from the given Bundle
     */
    private void restorePosts(Bundle savedInstanceState) {
        mPosts = (ArrayList<RedditPost>) savedInstanceState.getSerializable(SAVED_ENTRIES);
    }

}
