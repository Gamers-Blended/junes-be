package com.gamersblended.junes.service;

import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.model.CityResponse;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;

@Slf4j
@Service
public class GeoLocationService {

    private DatabaseReader databaseReader;

    @PostConstruct
    public void init() throws IOException {
        // Load from resources
        ClassPathResource resource = new ClassPathResource("GeoLite2-City.mmdb");
        databaseReader = new DatabaseReader.Builder(resource.getFile()).build();
        log.info("GeoLite2 database loaded");
    }

    public String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (null == ip || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (null == ip || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // Handle multiple IPs in X-Forwarded-For
        if (null != ip && ip.contains(", ")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    public String getLocation(String ipAddress) {
        try {
            InetAddress inetAddress = InetAddress.getByName(ipAddress);
            CityResponse response = databaseReader.city(inetAddress);

            String city = response.city().name();
            String continent = response.continent().name();
            String country = response.country().name();

            // City, country, continent
            if (null != city && null != continent) {
                return city + ", " + continent + ", " + country;
            } else if (null != continent) {
                return continent + ", " + country;
            } else {
                return country;
            }

        } catch (Exception ex) {
            log.error("Error in finding location of ipAddress: {}", ipAddress);
            return "Unknown location";
        }
    }

    @PreDestroy
    public void cleanup() throws IOException {
        if (null != databaseReader) {
            databaseReader.close();
        }
    }
}
