package com.lvpengwei.androidvideoplayer.decoder;

import java.util.HashMap;
import java.util.Map;

public class DecoderRequestHeader {
    private String uri;
    private int maxAnalyzeDurations;
    private int analyzeCnt;
    private int probesize;
    private boolean fpsProbeSizeConfigured;
    private Map<String, Object> extraData;

    public String getUri() {
        return uri;
    }

    public DecoderRequestHeader(String uriParam) {
        this.uri = uriParam;
        extraData = new HashMap<>();
        fpsProbeSizeConfigured = true;
    }

    public DecoderRequestHeader(String uriParam, int max_analyze_duration, int analyzeCnt, int probesize, boolean fpsProbeSizeConfigured) {
        this.uri = uriParam;
        this.maxAnalyzeDurations = max_analyze_duration;
        this.analyzeCnt = analyzeCnt;
        this.probesize = probesize;
        this.fpsProbeSizeConfigured = fpsProbeSizeConfigured;
    }

}
