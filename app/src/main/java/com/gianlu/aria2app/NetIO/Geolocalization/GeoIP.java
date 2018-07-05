package com.gianlu.aria2app.NetIO.Geolocalization;

import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.UiThread;
import android.util.LruCache;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public final class GeoIP {
    private static GeoIP instance;
    private final ExecutorService executorService;
    private final Handler handler;
    private final OkHttpClient client;
    private final LruCache<String, IPDetails> cache;

    private GeoIP() {
        handler = new Handler(Looper.getMainLooper());
        client = new OkHttpClient();
        executorService = Executors.newCachedThreadPool();
        cache = new LruCache<>(50);
    }

    @NonNull
    public static GeoIP get() {
        if (instance == null) instance = new GeoIP();
        return instance;
    }

    @Nullable
    public IPDetails getCached(String ip) {
        return cache.get(ip);
    }

    public void getIPDetails(final String ip, final OnIpDetails listener) {
        if (ip == null) {
            listener.onException(new NullPointerException("ip is null!"));
            return;
        }

        final IPDetails cachedDetails = cache.get(ip);
        if (cachedDetails != null) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    listener.onDetails(cachedDetails);
                }
            });
        } else {
            executorService.execute(new Runnable() {
                @Override
                public void run() {
                    String realIP = ip;
                    if (ip.startsWith("[") && ip.endsWith("]"))
                        realIP = realIP.substring(1, ip.length() - 1);

                    try (Response resp = client.newCall(new Request.Builder()
                            .get().url("https://extreme-ip-lookup.com/json/" + realIP).build()).execute()) {

                        ResponseBody body = resp.body();
                        if (body == null) throw new IOException("Empty body!");

                        JSONObject obj = new JSONObject(body.string());
                        String status = obj.getString("status");
                        if (status.equals("fail")) throw new ServiceException(obj);

                        final IPDetails details = new IPDetails(obj);
                        cache.put(ip, details);

                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                listener.onDetails(details);
                            }
                        });
                    } catch (IOException | JSONException | NullPointerException | IllegalArgumentException | ServiceException ex) {
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                listener.onException(ex);

                            }
                        });
                    }
                }
            });
        }
    }

    public interface OnIpDetails {
        @UiThread
        void onDetails(@NonNull IPDetails details);

        @UiThread
        void onException(@NonNull Exception ex);
    }

    private static class ServiceException extends Exception {
        private ServiceException(JSONObject obj) throws JSONException {
            super(obj.getString("message"));
        }
    }
}