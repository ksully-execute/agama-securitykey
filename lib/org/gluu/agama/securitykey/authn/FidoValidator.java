package org.gluu.agama.securitykey.authn;

import io.jans.fido2.client.AssertionService;
import io.jans.fido2.client.Fido2ClientFactory;
import io.jans.fido2.model.assertion.AssertionOptions;
import io.jans.fido2.model.assertion.AssertionResult;
import jakarta.ws.rs.core.Response;

import java.io.IOException;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.minidev.json.JSONObject;

import org.gluu.agama.securitykey.NetworkUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FidoValidator {

    private static final Logger logger = LoggerFactory.getLogger(FidoValidator.class);
    
    private String metadataConfiguration;

    public FidoValidator() throws IOException {
        logger.debug("Inspecting fido2 configuration discovery URL");
        String metadataUri = NetworkUtils.urlBeforeContextPath() + "/.well-known/fido2-configuration";

        try (Response response = Fido2ClientFactory.instance()
                .createMetaDataConfigurationService(metadataUri).getMetadataConfiguration()) {

            metadataConfiguration = response.readEntity(String.class);
            int status = response.getStatus();

            if (status != Response.Status.OK.getStatusCode()) {
                String msg = "Problem retrieving fido metadata (code: " + status + ")";
                logger.error(msg + "; response was: " + metadataConfiguration);
                throw new IOException(msg);
            }
        }
    }

    public String assertionRequest(String uid) throws IOException {
        logger.debug("Building an assertion request for {}", uid);
        //Using assertionService as a private class field gives serialization trouble...
        AssertionService assertionService = Fido2ClientFactory.instance().createAssertionService(metadataConfiguration);
        
        AssertionOptions assertionRequest = new AssertionOptions();
        assertionRequest.setUsername(uid);
        
        try (Response response = assertionService.authenticate(assertionRequest)) {
            String content = response.readEntity(String.class);
            int status = response.getStatus();

            if (status != Response.Status.OK.getStatusCode()) {
                String msg = "Assertion request building failed (code: " + status + ")";
                logger.error(msg + "; response was: " + content);
                throw new IOException(msg);
            }
            return content;
        }
    }

    public void verify(String tokenResponse) throws IOException {
        logger.debug("Verifying fido token response");
        AssertionService assertionService = Fido2ClientFactory.instance().createAssertionService(metadataConfiguration);
        ObjectMapper mapper = new ObjectMapper();
        AssertionResult assertionResult = mapper.readValue(tokenResponse, AssertionResult.class);
        
        try (Response response = assertionService.verify(assertionResult)) {
            int status = response.getStatus();

            if (status != Response.Status.OK.getStatusCode()) {
                String msg = "Verification step failed (code: " + status + ")";
                logger.error(msg + "; response was: " + response.readEntity(String.class));
                throw new IOException(msg);
            }
        }
    }
}
