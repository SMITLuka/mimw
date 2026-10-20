package com.smit.mimw.tenant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.Map;

/**
 * Calls Integration Hub's service API (GET /service/configuration, authenticated with X-Api-Key)
 * to get the effective configuration of the MIMW interface for one company.
 */
public class IntegrationHubClient {

    private static final Logger log = LoggerFactory.getLogger(IntegrationHubClient.class);

    private final RestClient restClient;
    private final String interfaceName;

    public IntegrationHubClient(String baseUrl, String apiKey, String interfaceName) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(15));
        this.restClient = RestClient.builder()
                .baseUrl(stripTrailingSlash(baseUrl))
                .requestFactory(requestFactory)
                .defaultHeader("X-Api-Key", apiKey) //$NON-NLS-1$
                .build();
        this.interfaceName = interfaceName;
    }

    /** Response of GET /service/configuration; only the parts MIMW uses. */
    public record HubConfiguration(Map<String, Object> mandator, Map<String, Object> company, String interfaceName,
            Map<String, String> config, Map<String, String> additionalData) {
    }

    public HubConfiguration fetch(TenantKey key) {
        try {
            HubConfiguration result = restClient.get()
                    .uri(uri -> uri.path("/service/configuration") //$NON-NLS-1$
                            .queryParam("mandatorId", key.mandatorId()) //$NON-NLS-1$
                            .queryParam("companyId", key.companyId()) //$NON-NLS-1$
                            .queryParam("interfaceName", interfaceName) //$NON-NLS-1$
                            .build())
                    .retrieve()
                    .body(HubConfiguration.class);
            if (result == null) {
                throw new TenantConfigurationException("Integration Hub vratio je prazan odgovor za " + key); //$NON-NLS-1$
            }
            log.info("Loaded '{}' configuration for {} from Integration Hub ({} keys)", interfaceName, key, //$NON-NLS-1$
                    result.config() == null ? 0 : result.config().size());
            return result;
        } catch (RestClientResponseException e) {
            HttpStatusCode status = e.getStatusCode();
            String body = e.getResponseBodyAsString();
            if (status.value() == 404) {
                throw new TenantConfigurationException(notFoundMessage(key, body), e);
            }
            if (status.value() == 401 || status.value() == 403) {
                throw new TenantConfigurationException("Integration Hub odbio je API ključ (HTTP " + status.value() //$NON-NLS-1$
                        + "). Provjeri INTEGRATION_HUB_API_KEY u MIMW-u i SERVICE_API_KEYS u Hubu.", e); //$NON-NLS-1$
            }
            throw new TenantConfigurationException("Integration Hub greška HTTP " + status.value() + ": " + body, e); //$NON-NLS-1$ //$NON-NLS-2$
        } catch (RestClientException e) {
            throw new TenantConfigurationException("Integration Hub nije dostupan: " + e.getMessage(), e); //$NON-NLS-1$
        }
    }

    /** Turns Hub's 404 error code into a message an Ares user can act on. */
    private String notFoundMessage(TenantKey key, String body) {
        String b = body == null ? "" : body; //$NON-NLS-1$
        if (b.contains("MANDATOR_NOT_FOUND")) { //$NON-NLS-1$
            return "U Integration Hubu ne postoji mandator s oznakom '" + key.mandatorId() //$NON-NLS-1$
                    + "'. Upišite tu oznaku u polje Mandator ID odgovarajućeg mandatora."; //$NON-NLS-1$
        }
        if (b.contains("COMPANY_NOT_FOUND")) { //$NON-NLS-1$
            return "U Integration Hubu mandator '" + key.mandatorId() + "' nema firmu s oznakom '" + key.companyId() //$NON-NLS-1$ //$NON-NLS-2$
                    + "'. Dodajte firmu ili joj upišite tu oznaku u polje Company ID."; //$NON-NLS-1$
        }
        if (b.contains("INTERFACE_NOT_FOUND")) { //$NON-NLS-1$
            return "U Integration Hubu ne postoji sučelje '" + interfaceName + "'."; //$NON-NLS-1$ //$NON-NLS-2$
        }
        if (b.contains("CONFIGURATION_NOT_FOUND")) { //$NON-NLS-1$
            return "Firma '" + key.companyId() + "' (mandator '" + key.mandatorId() + "') u Integration Hubu nema konfiguraciju '" //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    + interfaceName + "'. Dodajte je pod Configurations te firme."; //$NON-NLS-1$
        }
        return "Integration Hub ne može pronaći postavke za mandator '" + key.mandatorId() + "' / firmu '" //$NON-NLS-1$ //$NON-NLS-2$
                + key.companyId() + "': " + b; //$NON-NLS-1$
    }

    private static String stripTrailingSlash(String url) {
        return url != null && url.endsWith("/") ? url.substring(0, url.length() - 1) : url; //$NON-NLS-1$
    }
}
