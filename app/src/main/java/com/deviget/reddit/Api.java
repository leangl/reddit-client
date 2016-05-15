package com.deviget.reddit;

import com.google.gson.JsonObject;

import retrofit.http.GET;
import retrofit.http.Query;
import rx.Observable;

/**
 * Reddit Api interface for Retrofit
 */
public interface Api {

    @GET("/top.json")
    Observable<JsonObject> top(@Query("after") String after, @Query("limit") int limit);

}
