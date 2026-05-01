package com.cugcoding.forum.oss;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "obs")
public class ObsConfig {
    private String endpoint;
    private String ak;
    private String sk;
    private String bucket;
    private String host;
    private String prefix = "forum/";

    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
    public String getAk() { return ak; }
    public void setAk(String ak) { this.ak = ak; }
    public String getSk() { return sk; }
    public void setSk(String sk) { this.sk = sk; }
    public String getBucket() { return bucket; }
    public void setBucket(String bucket) { this.bucket = bucket; }
    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }
    public String getPrefix() { return prefix; }
    public void setPrefix(String prefix) { this.prefix = prefix; }
}
