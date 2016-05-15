package com.deviget.reddit;

import android.webkit.URLUtil;

import com.google.gson.JsonObject;

import java.io.Serializable;

/**
 * Represents a Reddit post
 */
public class RedditPost implements Serializable {

    public final String title;
    public final String author;
    public final int numComments;
    public final String name;
    public final long createdUtc;
    public final String thumbnail;
    public final String url;

    public RedditPost(JsonObject data) {
        this.title = data.get("title").getAsString();
        this.author = data.get("author").getAsString();
        this.numComments = data.get("num_comments").getAsInt();
        this.name = data.get("name").getAsString();
        this.createdUtc = data.get("created_utc").getAsLong();
        String thumbnailUri = data.get("thumbnail").getAsString();
        if (URLUtil.isValidUrl(thumbnailUri)) {
            thumbnail = thumbnailUri;
        } else {
            thumbnail = "";
        }
        String dataUri = data.get("url").getAsString();
        if (URLUtil.isValidUrl(dataUri)) {
            url = dataUri;
        } else {
            url = "";
        }
    }

}
