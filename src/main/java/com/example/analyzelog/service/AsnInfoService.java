package com.example.analyzelog.service;

import com.example.analyzelog.model.AsnInfo;
import com.example.analyzelog.repository.AsnInfoRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Service
public class AsnInfoService {

    // quantcdn/as-metadata (CC0), updated daily. JSON array (~55 MB) of
    // {"asn": 1, "metadata": {"handle", "description", "countryCode", "category", …}, …}.
    // The CSV has no category, hence the JSON.
    static final String JSON_URL = "https://raw.githubusercontent.com/quantcdn/asn-info/master/as.json";

    private static final ObjectMapper mapper = new ObjectMapper();

    private final RestClient restClient;
    private final AsnInfoRepository repository;

    public AsnInfoService(RestClient restClient, AsnInfoRepository repository) {
        this.restClient = restClient;
        this.repository = repository;
    }

    // Downloads and parses first, so a failed download never touches the stored names.
    public int refresh() {
        List<AsnInfo> entries = restClient.get().uri(JSON_URL)
                .exchange((request, response) -> parseJson(response.getBody()));
        if (entries == null || entries.isEmpty()) {
            throw new IllegalStateException("No ASN found in " + JSON_URL);
        }
        repository.replaceAll(entries);
        return entries.size();
    }

    public int count() {
        return repository.count();
    }

    // Streams the array one element at a time: the whole file never sits in memory as a tree.
    static List<AsnInfo> parseJson(InputStream json) {
        var entries = new ArrayList<AsnInfo>();
        try (JsonParser parser = mapper.createParser(json)) {
            if (parser.nextToken() != JsonToken.START_ARRAY) {
                throw new IllegalStateException("Expected a JSON array of ASNs");
            }
            while (parser.nextToken() == JsonToken.START_OBJECT) {
                JsonNode node = parser.readValueAsTree();
                JsonNode meta = node.path("metadata");
                if (!node.path("asn").canConvertToLong()) continue;
                entries.add(new AsnInfo(
                        node.path("asn").asString(),
                        text(meta, "handle"),
                        text(meta, "description"),
                        text(meta, "countryCode"),
                        text(meta, "category")));
            }
        }
        return entries;
    }

    private static String text(JsonNode node, String field) {
        String s = node.path(field).asString("");
        return s.isBlank() ? null : s;
    }
}
