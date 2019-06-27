package com.mylive.live.service;

import com.mylive.live.arch.http.Observable;
import com.mylive.live.model.Config;
import com.mylive.live.model.HttpResponse;

import retrofit2.http.GET;

/**
 * Created by Developer Zailong Shi on 2019-06-20.
 */
public interface ConfigService {

    @GET("config")
    Observable<HttpResponse<Config>> getConfig();
}
