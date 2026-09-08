package com.gamersblended.junes.service;

import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.exception.AddressNotFoundException;
import com.maxmind.geoip2.model.CityResponse;
import com.maxmind.geoip2.record.City;
import com.maxmind.geoip2.record.Continent;
import com.maxmind.geoip2.record.Country;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GeoLocationServiceTest {

    @Mock
    private DatabaseReader databaseReader;

    private GeoLocationService geoLocationService;

    @BeforeEach
    void setUp() {
        geoLocationService = new GeoLocationService();
        ReflectionTestUtils.setField(geoLocationService, "databaseReader", databaseReader);
    }

    private static City city(String name) {
        return new City(List.of("en"), null, 1L, namesMap(name));
    }

    private static Continent continent(String name) {
        return new Continent(List.of("en"), "NA", 2L, namesMap(name));
    }

    private static Country country(String name) {
        return new Country(List.of("en"), null, 3L, false, "US", namesMap(name));
    }

    private static Map<String, String> namesMap(String name) {
        return null == name ? Map.of() : Map.of("en", name);
    }

    // ---- getClientIp ----

    @Test
    void getClientIp_returnsXForwardedForHeader_whenPresent() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "203.0.113.1");

        String ip = geoLocationService.getClientIp(request);

        assertThat(ip).isEqualTo("203.0.113.1");
    }

    @Test
    void getClientIp_returnsFirstIp_whenXForwardedForHasMultipleIps() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "203.0.113.1, 198.51.100.2, 192.0.2.3");

        String ip = geoLocationService.getClientIp(request);

        assertThat(ip).isEqualTo("203.0.113.1");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"unknown", ""})
    void getClientIp_fallsBackToXRealIp_whenXForwardedForMissingUnknownOrEmpty(String xForwardedFor) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (null != xForwardedFor) {
            request.addHeader("X-Forwarded-For", xForwardedFor);
        }
        request.addHeader("X-Real-IP", "198.51.100.2");

        String ip = geoLocationService.getClientIp(request);

        assertThat(ip).isEqualTo("198.51.100.2");
    }

    @Test
    void getClientIp_fallsBackToRemoteAddr_whenNoHeadersPresent() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.0.2.3");

        String ip = geoLocationService.getClientIp(request);

        assertThat(ip).isEqualTo("192.0.2.3");
    }

    @Test
    void getClientIp_fallsBackToRemoteAddr_whenBothHeadersAreUnknown() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "Unknown");
        request.addHeader("X-Real-IP", "UNKNOWN");
        request.setRemoteAddr("192.0.2.3");

        String ip = geoLocationService.getClientIp(request);

        assertThat(ip).isEqualTo("192.0.2.3");
    }

    // ---- getLocation ----

    @Test
    void getLocation_returnsCityContinentCountry_whenAllPresent() throws Exception {
        CityResponse response = new CityResponse(
                city("Tokyo"), continent("Asia"), country("Japan"),
                null, null, null, null, null, null, null);
        when(databaseReader.city(any())).thenReturn(response);

        String location = geoLocationService.getLocation("203.0.113.1");

        assertThat(location).isEqualTo("Tokyo, Asia, Japan");
    }

    @Test
    void getLocation_returnsContinentAndCountry_whenCityMissing() throws Exception {
        CityResponse response = new CityResponse(
                city(null), continent("Europe"), country("Germany"),
                null, null, null, null, null, null, null);
        when(databaseReader.city(any())).thenReturn(response);

        String location = geoLocationService.getLocation("203.0.113.1");

        assertThat(location).isEqualTo("Europe, Germany");
    }

    @Test
    void getLocation_returnsCountryOnly_whenContinentMissing() throws Exception {
        CityResponse response = new CityResponse(
                city("Springfield"), continent(null), country("United States"),
                null, null, null, null, null, null, null);
        when(databaseReader.city(any())).thenReturn(response);

        String location = geoLocationService.getLocation("203.0.113.1");

        assertThat(location).isEqualTo("United States");
    }

    @Test
    void getLocation_returnsUnknownLocation_whenAddressNotFoundInDatabase() throws Exception {
        when(databaseReader.city(any())).thenThrow(new AddressNotFoundException("not found"));

        String location = geoLocationService.getLocation("203.0.113.1");

        assertThat(location).isEqualTo("Unknown location");
    }

    @Test
    void getLocation_returnsUnknownLocation_whenIpAddressIsInvalid() {
        // Out-of-range numeric literal fails InetAddress parsing immediately, without a DNS lookup.
        String location = geoLocationService.getLocation("999.999.999.999");

        assertThat(location).isEqualTo("Unknown location");
    }

    @Test
    void getLocation_returnsUnknownLocation_whenDatabaseReaderThrowsIOException() throws Exception {
        when(databaseReader.city(any())).thenThrow(new java.io.IOException("db read error"));

        String location = geoLocationService.getLocation("203.0.113.1");

        assertThat(location).isEqualTo("Unknown location");
    }
}
