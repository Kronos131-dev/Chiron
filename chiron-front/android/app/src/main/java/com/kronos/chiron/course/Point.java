package com.kronos.chiron.course;

import org.json.JSONException;
import org.json.JSONObject;

public final class Point {

    public final double lat;
    public final double lon;
    public final long t;
    public final Double alt;
    public final boolean coupure;

    public Point(double lat, double lon, long t, Double alt, boolean coupure) {
        this.lat = lat;
        this.lon = lon;
        this.t = t;
        this.alt = alt;
        this.coupure = coupure;
    }

    public JSONObject enJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("lat", lat);
        json.put("lon", lon);
        json.put("t", t);
        json.put("alt", alt == null ? JSONObject.NULL : alt);
        if (coupure) json.put("coupure", true);
        return json;
    }
}
