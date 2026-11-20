package com.project.back_end_proyecto.service.publicplaces;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import java.util.List;
import java.util.Map;

class WikimediaCommonsPhotoServiceTest {
    @Test
    void followsExactWikidataReferenceAndRequiresCommonsLicenseMetadata() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        var service = new WikimediaCommonsPhotoService(builder.build());
        server.expect(requestTo(org.hamcrest.Matchers.containsString("www.wikidata.org/w/api.php")))
                .andRespond(withSuccess("""
                        {"entities":{"Q123":{"claims":{"P18":[{"mainsnak":{"datavalue":{"value":"Specific Hotel.jpg"}}}]}}}}
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo(org.hamcrest.Matchers.containsString("commons.wikimedia.org/w/api.php")))
                .andRespond(withSuccess("""
                        {"query":{"pages":{"1":{"imageinfo":[{"thumburl":"https://upload.wikimedia.org/wikipedia/commons/thumb/a/a1/Specific_Hotel.jpg/1200px-Specific_Hotel.jpg","descriptionurl":"https://commons.wikimedia.org/wiki/File:Specific_Hotel.jpg","extmetadata":{"LicenseShortName":{"value":"CC BY-SA 4.0"},"LicenseUrl":{"value":"https://creativecommons.org/licenses/by-sa/4.0/"},"Artist":{"value":"Hotel photographer"}}}]}}}}
                        """, MediaType.APPLICATION_JSON));

        var photos = service.verifyPlaceImages(List.of(new LugarPublico("geoapify:1", "Specific Hotel", null,
                "Hotel", "Abu Dhabi", null, null, null, Map.of("wikidata", "Q123"))));

        server.verify();
        assertEquals("Hotel photographer", photos.get("geoapify:1").attribution());
        assertEquals("CC BY-SA 4.0", photos.get("geoapify:1").license());
    }

    @Test
    void rejectsInvalidWikidataReferenceWithoutMakingAnExternalRequest() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        var service = new WikimediaCommonsPhotoService(builder.build());

        var photos = service.verifyPlaceImages(List.of(new LugarPublico("place", "Hotel", null,
                "Hotel", "Abu Dhabi", null, null, null, Map.of("wikidata", "Q0"))));

        assertTrue(photos.isEmpty());
        server.verify();
    }

    @Test
    void returnsPhotoOnlyWithVerifiableCommonsLicenseAndAttribution() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        var service = new WikimediaCommonsPhotoService(builder.build());
        server.expect(requestTo(org.hamcrest.Matchers.containsString("commons.wikimedia.org/w/api.php")))
                .andRespond(withSuccess("""
                        {"query":{"pages":{"1":{"imageinfo":[{"thumburl":"https://upload.wikimedia.org/wikipedia/commons/thumb/a/a1/Hotel.jpg/1200px-Hotel.jpg","url":"https://upload.wikimedia.org/wikipedia/commons/a/a1/Hotel.jpg","descriptionurl":"https://commons.wikimedia.org/wiki/File:Hotel.jpg","extmetadata":{"LicenseShortName":{"value":"CC BY-SA 4.0"},"LicenseUrl":{"value":"https://creativecommons.org/licenses/by-sa/4.0/"},"Artist":{"value":"<b>Example Artist</b>"}}}]}}}}
                        """, MediaType.APPLICATION_JSON));

        var photo = service.verifyPlaceImage("https://commons.wikimedia.org/wiki/File:Hotel.jpg");

        server.verify();
        assertNotNull(photo);
        assertEquals("https://upload.wikimedia.org/wikipedia/commons/thumb/a/a1/Hotel.jpg/1200px-Hotel.jpg", photo.url());
        assertEquals("Example Artist", photo.attribution());
        assertEquals("CC BY-SA 4.0", photo.license());
        assertEquals("https://commons.wikimedia.org/wiki/File:Hotel.jpg", photo.sourceUrl());
    }

    @Test
    void rejectsNonCommonsReferencesAndNonReusableLicenses() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        var service = new WikimediaCommonsPhotoService(builder.build());
        assertNull(service.verifyPlaceImage("https://example.com/hotel.jpg"));
        server.expect(requestTo(org.hamcrest.Matchers.containsString("commons.wikimedia.org/w/api.php")))
                .andRespond(withSuccess("""
                        {"query":{"pages":{"1":{"imageinfo":[{"url":"https://upload.wikimedia.org/wikipedia/commons/a/a1/Hotel.jpg","descriptionurl":"https://commons.wikimedia.org/wiki/File:Hotel.jpg","extmetadata":{"LicenseShortName":{"value":"CC BY-NC 4.0"},"LicenseUrl":{"value":"https://creativecommons.org/licenses/by-nc/4.0/"},"Artist":{"value":"Artist"}}}]}}}}
                        """, MediaType.APPLICATION_JSON));
        assertNull(service.verifyPlaceImage("https://commons.wikimedia.org/wiki/File:Hotel.jpg"));
        server.verify();
    }

    @Test
    void parsesReusableImageMetadata() {
        Map<String, Object> payload = Map.of("query", Map.of("pages", Map.of("1", Map.of("imageinfo", List.of(
                Map.of("thumburl", "https://upload.wikimedia.org/wikipedia/commons/thumb/a/a1/Hotel.jpg/1200px-Hotel.jpg",
                        "url", "https://upload.wikimedia.org/wikipedia/commons/a/a1/Hotel.jpg",
                        "descriptionurl", "https://commons.wikimedia.org/wiki/File:Hotel.jpg",
                        "extmetadata", Map.of("LicenseShortName", Map.of("value", "CC BY-SA 4.0"),
                                "LicenseUrl", Map.of("value", "https://creativecommons.org/licenses/by-sa/4.0/"),
                                "Artist", Map.of("value", "Example Artist"))))))));
        assertNotNull(WikimediaCommonsPhotoService.parse(payload));
    }
}
